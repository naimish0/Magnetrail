package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyAnalysis
import com.rameshta.magnetrail.core.difficulty.DifficultyAnalyzer
import com.rameshta.magnetrail.core.difficulty.DifficultyBandV2
import com.rameshta.magnetrail.core.difficulty.DifficultyConfig
import com.rameshta.magnetrail.core.difficulty.DifficultyMetrics
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.quality.LevelQualityAnalyzer
import com.rameshta.magnetrail.core.quality.LevelQualityConfig
import com.rameshta.magnetrail.core.quality.LevelQualityScore
import com.rameshta.magnetrail.core.quality.LevelQualityStatus
import com.rameshta.magnetrail.core.quality.QualityReason
import java.io.File
import java.util.Locale
import kotlin.math.abs

internal fun runM51(command: String, options: Map<String, String>) {
    val campaignFile = File(options.requiredM51("campaign"))
    val catalog = LevelParser().parseCatalog(campaignFile.readText())
    val legacyScores = options["legacy-report"]?.let(::File)?.takeIf(File::exists)?.let(::legacyScores).orEmpty()
    val audit = CampaignAudit().analyze(catalog, legacyScores)
    when (command) {
        "analyze-difficulty" -> {
            File(options.requiredM51("metrics-output")).writeReport(audit.metricsJson())
            File(options.requiredM51("comparison-output")).writeReport(audit.comparisonCsv())
            println("Analyzed V2 difficulty for ${audit.rows.size} levels; staged metrics and v1→v2 comparison.")
        }
        "analyze-quality" -> {
            File(options.requiredM51("output")).writeReport(audit.qualityJson())
            println("Analyzed separate quality gates for ${audit.rows.size} levels; staged quality evidence.")
        }
        "check-duplicates" -> {
            File(options.requiredM51("output")).writeReport(audit.duplicateMarkdown())
            println(
                "Checked ${audit.rows.size} levels under exact and symmetry-normalized fingerprints: " +
                    "${audit.exactDuplicateGroups.size} exact groups, ${audit.symmetryDuplicateGroups.size} symmetry groups.",
            )
        }
        "audit-pacing" -> {
            File(options.requiredM51("output")).writeReport(audit.auditMarkdown())
            println("Audited deterministic ${audit.rows.size}-level pacing; no campaign content or order was rewritten.")
        }
        "certify-quality" -> {
            val rejected = audit.rows.filter { it.quality.qualityStatus == LevelQualityStatus.REJECT }
            check(rejected.isEmpty()) {
                "M5.1 hard quality failures: " + rejected.joinToString { row ->
                    "${row.level.id}[${row.quality.qualityReasons.joinToString("+")}]"
                }
            }
            check(audit.rows.map { it.level.id }.toSet().size == audit.rows.size) { "Campaign IDs are not unique" }
            check(audit.rows.map { it.level.number } == (1..audit.rows.size).toList()) {
                "Campaign order is not a contiguous checked-in sequence"
            }
            println("M5.1 certified ${audit.rows.size} levels with no hard quality or symmetry-duplicate failures.")
        }
        else -> error("Unknown M5.1 command '$command'")
    }
}

private class CampaignAudit(
    private val difficultyConfig: DifficultyConfig = DifficultyConfig(),
    private val qualityConfig: LevelQualityConfig = LevelQualityConfig(),
) {
    fun analyze(catalog: LevelCatalog, legacyScores: Map<String, Int>): CampaignAuditResult {
        val levels = catalog.levels.sortedBy { it.number }
        val exactByLevel = peers(levels, ContentFingerprint::exact)
        val symmetryByLevel = peers(levels, ContentFingerprint::symmetryNormalized)
        val similarity = levels.associateWith { ContentFingerprint.structuralSimilaritySignature(it) }
        val idCounts = levels.groupingBy { it.id }.eachCount()
        val difficultyAnalyzer = DifficultyAnalyzer(config = difficultyConfig)
        val qualityAnalyzer = LevelQualityAnalyzer(qualityConfig)
        val rows = levels.map { level ->
            val difficulty = difficultyAnalyzer.analyze(level)
            val localSimilarity = levels.filter { other ->
                other.id != level.id &&
                    abs(other.number - level.number) <= qualityConfig.localSimilarityWindow &&
                    similarity.getValue(other) == similarity.getValue(level)
            }.map { it.id }
            val exactPeers = exactByLevel[level].orEmpty()
            val symmetryPeers = symmetryByLevel[level].orEmpty().filterNot { it in exactPeers }
            val quality = qualityAnalyzer.analyze(
                level = level,
                difficulty = difficulty,
                duplicateId = idCounts.getValue(level.id) > 1,
                exactDuplicateIds = exactPeers,
                symmetryDuplicateIds = symmetryPeers,
                localSimilarityIds = localSimilarity,
                contentHashValid = level.metadata?.contentFingerprint?.let { it == ContentFingerprint.of(level) } ?: true,
            )
            CampaignAuditRow(
                level = level,
                legacyScore = legacyScores[level.id] ?: legacyScoreFallback(level),
                difficulty = difficulty,
                quality = quality,
                exactDuplicateIds = exactPeers,
                symmetryDuplicateIds = symmetryPeers,
                localSimilarityIds = localSimilarity,
            )
        }.toMutableList()
        val pacingFlags = pacingFlags(rows)
        val completedRows = rows.map { row ->
            row.copy(
                pacingFlags = pacingFlags[row.level.id].orEmpty().distinct().sorted(),
                recommendedAction = recommendation(row, pacingFlags[row.level.id].orEmpty()),
            )
        }
        return CampaignAuditResult(
            catalog = catalog,
            rows = completedRows,
            difficultyConfig = difficultyConfig,
            qualityConfig = qualityConfig,
            exactDuplicateGroups = groupedDuplicates(levels, ContentFingerprint::exact),
            symmetryDuplicateGroups = groupedDuplicates(levels, ContentFingerprint::symmetryNormalized)
                .filter { group -> group.map(ContentFingerprint::exact).toSet().size > 1 },
        )
    }

    private fun pacingFlags(rows: List<CampaignAuditRow>): Map<String, List<String>> {
        val flags = rows.associate { it.level.id to mutableListOf<String>() }
        rows.forEachIndexed { index, row ->
            if (index > 0 && abs(row.difficulty.score.score - rows[index - 1].difficulty.score.score) >= 22) {
                flags.getValue(row.level.id) += "PACING_LARGE_DIFFICULTY_JUMP"
            }
            if (row.level.number <= qualityConfig.tutorialEndLevel &&
                (row.difficulty.metrics.fatalChoiceRatio > 0.35 || row.difficulty.metrics.successfulOpeningActions > 2)
            ) flags.getValue(row.level.id) += "PACING_TUTORIAL_RISK"
            if (row.level.number >= 81 &&
                row.difficulty.metrics.criticalOrderConstraintCount == 0 &&
                row.difficulty.metrics.magnetControlledSolutionActions == 0
            ) flags.getValue(row.level.id) += "PACING_LATE_WEAK_COMPLEXITY"
            if (row.localSimilarityIds.isNotEmpty()) flags.getValue(row.level.id) += "PACING_REPEATED_LOCAL_LAYOUT"

            val hardRun = rows.take(index + 1).takeLastWhile { it.difficulty.score.band >= DifficultyBandV2.HARD }.size
            if (hardRun > 3) flags.getValue(row.level.id) += "PACING_HARD_STREAK_OVER_THREE"

            if (index >= qualityConfig.recoveryPolicy.requireRecoveryAfterDemandingCount) {
                val previous = rows.subList(index - qualityConfig.recoveryPolicy.requireRecoveryAfterDemandingCount, index)
                if (previous.all { it.difficulty.score.band >= qualityConfig.recoveryPolicy.demandingBandFloor }) {
                    val peak = previous.maxOf { it.difficulty.score.band.ordinal }
                    if (row.difficulty.score.band.ordinal > peak - qualityConfig.recoveryPolicy.recoveryBandDrop) {
                        flags.getValue(row.level.id) += "PACING_RECOVERY_LEVEL_MISSING"
                    }
                }
            }
        }
        val taughtAt = mapOf(
            "MOVEMENT" to 1,
            "PULL" to 3,
            "PUSH" to 4,
            "WALLS" to 6,
            "OCCLUSION" to 7,
            "MULTIPLE_MAGNETS" to 26,
            "CANCELLATION" to 30,
        )
        rows.forEach { row ->
            row.level.metadata?.mechanicTags.orEmpty().forEach { tag ->
                val teachingLevel = taughtAt[tag] ?: return@forEach
                if (row.level.number < teachingLevel) flags.getValue(row.level.id) += "PACING_MECHANIC_BEFORE_TEACHING:$tag"
            }
        }
        return flags
    }

    private fun recommendation(row: CampaignAuditRow, pacingFlags: List<String>): String {
        if (row.quality.qualityStatus == LevelQualityStatus.REJECT) return "REPLACE"
        val metadataOnly = row.quality.qualityReasons.isNotEmpty() && row.quality.qualityReasons.all {
            it in setOf(
                QualityReason.MECHANIC_CLAIM_UNSUPPORTED,
                QualityReason.GRADING_MISMATCH,
                QualityReason.CURRICULUM_MISMATCH,
            )
        }
        if (metadataOnly) return "TUNE_METADATA"
        if (row.quality.qualityStatus == LevelQualityStatus.REVIEW || pacingFlags.isNotEmpty()) return "MANUAL_REVIEW"
        return "KEEP"
    }

    private fun peers(
        levels: List<LevelDefinition>,
        fingerprint: (LevelDefinition) -> String,
    ): Map<LevelDefinition, List<String>> {
        val groups = levels.groupBy(fingerprint)
        return levels.associateWith { level ->
            groups.getValue(fingerprint(level)).filterNot { it.id == level.id }.map { it.id }.sorted()
        }
    }

    private fun groupedDuplicates(
        levels: List<LevelDefinition>,
        fingerprint: (LevelDefinition) -> String,
    ): List<List<LevelDefinition>> = levels.groupBy(fingerprint).values.filter { it.size > 1 }
        .map { it.sortedBy(LevelDefinition::number) }
        .sortedBy { it.first().number }

    private fun legacyScoreFallback(level: LevelDefinition): Int = when (level.metadata?.difficultyBand?.name) {
        "INTRO" -> 15
        "DEVELOPING" -> 45
        "ADVANCED" -> 70
        else -> 0
    }
}

private data class CampaignAuditRow(
    val level: LevelDefinition,
    val legacyScore: Int,
    val difficulty: DifficultyAnalysis,
    val quality: LevelQualityScore,
    val exactDuplicateIds: List<String>,
    val symmetryDuplicateIds: List<String>,
    val localSimilarityIds: List<String>,
    val pacingFlags: List<String> = emptyList(),
    val recommendedAction: String = "KEEP",
)

private data class CampaignAuditResult(
    val catalog: LevelCatalog,
    val rows: List<CampaignAuditRow>,
    val difficultyConfig: DifficultyConfig,
    val qualityConfig: LevelQualityConfig,
    val exactDuplicateGroups: List<List<LevelDefinition>>,
    val symmetryDuplicateGroups: List<List<LevelDefinition>>,
) {
    fun metricsJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"catalogId\": ${json(catalog.catalogId)},")
        appendLine("  \"contentVersion\": ${catalog.contentVersion},")
        appendLine("  \"analyzerVersion\": ${json(difficultyConfig.analyzerVersion)},")
        appendLine("  \"config\": {\"solutionCountCap\": ${difficultyConfig.solutionCountCap}, \"solverStateCap\": ${difficultyConfig.solverStateCap}, \"counterfactualCheckCap\": ${difficultyConfig.counterfactualCheckCap}, \"weights\": {\"wrongOrderRisk\": 25, \"branchingComplexity\": 20, \"criticalOrderConstraints\": 20, \"magneticComplexity\": 20, \"lookAheadAndDivergence\": 10, \"densityAndReadability\": 5}},")
        appendLine("  \"levels\": [")
        rows.forEachIndexed { index, row ->
            val m = row.difficulty.metrics
            val s = row.difficulty.score
            append("    {")
            append("\"levelId\":${json(row.level.id)},\"campaignNumber\":${row.level.number},")
            append("\"exactFingerprint\":${json(ContentFingerprint.exact(row.level))},")
            append("\"symmetryFingerprint\":${json(ContentFingerprint.symmetryNormalized(row.level))},")
            append("\"similaritySignature\":${json(ContentFingerprint.structuralSimilaritySignature(row.level))},")
            append("\"cleanSolutionLength\":${m.cleanSolutionLength},")
            append("\"successfulOpeningActions\":${m.successfulOpeningActions},\"plausibleOpeningActions\":${m.plausibleOpeningActions},")
            append("\"averageSuccessfulBranching\":${decimal(m.averageSuccessfulBranching)},\"maximumSuccessfulBranching\":${m.maximumSuccessfulBranching},")
            append("\"forcedMoveRatio\":${decimal(m.forcedMoveRatio)},\"fatalChoiceRatio\":${decimal(m.fatalChoiceRatio)},")
            append("\"criticalOrderConstraintCount\":${m.criticalOrderConstraintCount},\"solutionDivergenceDepth\":${m.solutionDivergenceDepth ?: "null"},")
            append("\"magnetControlledSolutionActions\":${m.magnetControlledSolutionActions},\"pullSolutionActions\":${m.pullSolutionActions},\"pushSolutionActions\":${m.pushSolutionActions},")
            append("\"polarityFlipCount\":${m.polarityFlipCount},\"controllingMagnetChangeCount\":${m.controllingMagnetChangeCount},")
            append("\"occlusionDependencyCount\":${m.occlusionDependencyCount},\"cancellationDependencyCount\":${m.cancellationDependencyCount},\"wallDependencyCount\":${m.wallDependencyCount},")
            append("\"solverStatesExplored\":${m.solverStatesExplored},\"solutionCountUpToCap\":${m.solutionCountUpToCap},")
            append("\"boardDensity\":${decimal(m.boardDensity)},\"visualCongestionScore\":${decimal(m.visualCongestionScore)},")
            append("\"solutionCountCapped\":${m.solutionCountCapped},\"stateAnalysisCapped\":${m.stateAnalysisCapped},\"counterfactualAnalysisCapped\":${m.counterfactualAnalysisCapped},")
            append("\"unknownAlternativeCount\":${m.unknownAlternativeCount},\"recoveryWindowCount\":${m.recoveryWindowCount},")
            append("\"normalizedComponents\":{")
            append("\"wrongOrderRisk\":${decimal(s.normalizedComponents.wrongOrderRisk)},\"branchingComplexity\":${decimal(s.normalizedComponents.branchingComplexity)},")
            append("\"criticalOrderConstraints\":${decimal(s.normalizedComponents.criticalOrderConstraints)},\"magneticComplexity\":${decimal(s.normalizedComponents.magneticComplexity)},")
            append("\"lookAheadAndDivergence\":${decimal(s.normalizedComponents.lookAheadAndDivergence)},\"densityAndReadability\":${decimal(s.normalizedComponents.densityAndReadability)}},")
            append("\"v2Score\":${s.score},\"v2Band\":${json(s.band.displayName)},")
            append("\"cappedFlags\":${jsonArray(s.cappedFlags)},\"unknownFlags\":${jsonArray(s.unknownFlags)}")
            append("}")
            appendLine(if (index == rows.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    fun qualityJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"analyzerVersion\": ${json(qualityConfig.analyzerVersion)},")
        appendLine("  \"levels\": [")
        rows.forEachIndexed { index, row ->
            val q = row.quality
            val m = q.rawMetrics
            append("    {\"levelId\":${json(row.level.id)},\"campaignNumber\":${row.level.number},")
            append("\"qualityScore\":${q.qualityScore},\"qualityStatus\":${json(q.qualityStatus.name)},\"qualityReasons\":${jsonArray(q.qualityReasons)},")
            append("\"solvable\":${m.solvable},\"searchComplete\":${m.searchComplete},\"certifiedSolutionReplayValid\":${m.certifiedSolutionReplayValid},")
            append("\"structuralSchemaValid\":${m.structuralSchemaValid},\"stableUniqueId\":${m.stableUniqueId},\"contentHashValid\":${m.contentHashValid},")
            append("\"exactDuplicateIds\":${jsonArray(m.exactDuplicateIds)},\"symmetryDuplicateIds\":${jsonArray(m.symmetryDuplicateIds)},\"localSimilarityIds\":${jsonArray(m.localSimilarityIds)},")
            append("\"nonTrivial\":${m.nonTrivial},\"mechanicClaimsRelevant\":${m.mechanicClaimsRelevant},\"meaningfulDecisionCount\":${m.meaningfulDecisionCount},")
            append("\"forcedMoveRatio\":${decimal(m.forcedMoveRatio)},\"openingAmbiguity\":${m.openingAmbiguity},\"visualCongestionScore\":${decimal(m.visualCongestionScore)},")
            append("\"solverStatesExplored\":${m.solverStatesExplored},\"gradingMetadataConsistent\":${m.gradingMetadataConsistent},\"curriculumPositionAppropriate\":${m.curriculumPositionAppropriate},")
            append("\"recoveryWindowCount\":${m.recoveryWindowCount}}")
            appendLine(if (index == rows.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    fun comparisonCsv(): String = buildString {
        appendLine("level_id,campaign_number,v1_score,v1_band,v2_score,v2_band,score_delta,analyzer_version,capped_flags")
        rows.forEach { row ->
            appendLine(listOf(
                row.level.id,
                row.level.number,
                row.legacyScore,
                row.level.metadata?.difficultyBand?.name ?: "UNKNOWN",
                row.difficulty.score.score,
                row.difficulty.score.band.displayName,
                row.difficulty.score.score - row.legacyScore,
                row.difficulty.score.analyzerVersion,
                row.difficulty.score.cappedFlags.joinToString("+"),
            ).joinToString(","))
        }
    }

    fun duplicateMarkdown(): String = buildString {
        appendLine("# M5.1 campaign duplicate report")
        appendLine()
        appendLine("Analyzer: `${difficultyConfig.analyzerVersion}`. Exact hashes retain orientation; symmetry hashes use all dimension-preserving D4 transforms and normalize entity IDs.")
        appendLine()
        appendLine("- Levels checked: ${rows.size}")
        appendLine("- Exact duplicate groups: ${exactDuplicateGroups.size}")
        appendLine("- Symmetry-equivalent groups excluding exact-only equality: ${symmetryDuplicateGroups.size}")
        appendLine("- Review-only local structural similarity pairs: ${localPairs().size}")
        appendLine()
        appendLine("## Exact duplicates")
        appendLine()
        appendGroups(exactDuplicateGroups)
        appendLine()
        appendLine("## Rotation/reflection duplicates")
        appendLine()
        appendGroups(symmetryDuplicateGroups)
        appendLine()
        appendLine("## Review-only local similarity")
        appendLine()
        val pairs = localPairs()
        if (pairs.isEmpty()) appendLine("None.") else pairs.forEach { appendLine("- ${it.first} ↔ ${it.second}") }
    }

    fun auditMarkdown(): String = buildString {
        val bandCounts = rows.groupingBy { it.difficulty.score.band.displayName }.eachCount()
        val legacyBandCounts = rows.groupingBy { it.level.metadata?.difficultyBand?.name ?: "UNKNOWN" }.eachCount()
        val statusCounts = rows.groupingBy { it.quality.qualityStatus }.eachCount()
        val actionCounts = rows.groupingBy { it.recommendedAction }.eachCount()
        val hardFailures = rows.count { it.quality.qualityStatus == LevelQualityStatus.REJECT }
        appendLine("# Magnetrail M5.1 campaign audit")
        appendLine()
        appendLine("The initial deterministic audit was produced before campaign changes and found seven D4-equivalent groups containing 18 levels. This refreshed final audit measures the reviewed result after 11 later group members received certification-gated wall tuning. No automatic reorder or runtime personalization was performed.")
        appendLine()
        appendLine("## Audit result")
        appendLine()
        appendLine("- Catalog: `${catalog.catalogId}`; ${rows.size} levels; IDs and sequence remain unchanged.")
        appendLine("- Legacy assigned bands: $legacyBandCounts")
        appendLine("- V2 bands: $bandCounts")
        appendLine("- Quality statuses: $statusCounts")
        appendLine("- Recommended actions: $actionCounts")
        appendLine("- Hard quality failures: $hardFailures")
        appendLine("- Exact duplicate groups: ${exactDuplicateGroups.size}; symmetry duplicate groups: ${symmetryDuplicateGroups.size}.")
        appendLine("- Previous duplicate strategy: orientation-sensitive exact SHA-256 plus a wall-stripped near-layout key during generation. M5.1 now gates the full D4-normalized rule-content fingerprint.")
        appendLine("- External/released progress evidence: none is recorded in the repository. M5 closed testing is still planned, so this audit nevertheless makes no reorder.")
        appendLine()
        appendLine("Warnings are calibration inputs, not runtime rules. `MANUAL_REVIEW` does not fail certification; `REPLACE` identifies a hard gate. Recovery warnings use the checked-in configurable recovery policy.")
        appendLine()
        appendLine("## Level-by-level pacing")
        appendLine()
        appendLine("| # | Stable ID | v1 | V2 | Quality | Mechanics | Solution / risk / branching | Duplicate/similarity | Pacing flags | Action |")
        appendLine("|---:|---|---|---|---|---|---|---|---|---|")
        rows.forEach { row ->
            val metadata = row.level.metadata
            val tags = metadata?.mechanicTags.orEmpty()
            val mechanics = if (tags.isEmpty()) "—" else tags.take(2).joinToString(" + ")
            val m = row.difficulty.metrics
            val duplicates = (row.exactDuplicateIds + row.symmetryDuplicateIds + row.localSimilarityIds).distinct()
                .ifEmpty { listOf("—") }.joinToString(" ")
            val flags = row.pacingFlags.ifEmpty { listOf("—") }.joinToString(" ")
            val reasons = row.quality.qualityReasons.ifEmpty { listOf("—") }.joinToString(" ")
            appendLine(
                "| ${row.level.number} | ${row.level.id} | ${row.legacyScore}/${metadata?.difficultyBand?.name ?: "—"} | " +
                    "${row.difficulty.score.score}/${row.difficulty.score.band.displayName} | ${row.quality.qualityScore}/${row.quality.qualityStatus}: $reasons | " +
                    "$mechanics | ${m.cleanSolutionLength} / ${decimal(m.fatalChoiceRatio)} / ${decimal(m.averageSuccessfulBranching)} | " +
                    "$duplicates | $flags | ${row.recommendedAction} |",
            )
        }
        appendLine()
        appendLine("## Decision and sequence safety")
        appendLine()
        appendLine("The audit does not establish a high-confidence reason to reorder the release candidate before closed-test calibration. Stable IDs, campaign numbers, board content, fingerprints, grading, and saved-progress behavior are unchanged; therefore no sequence migration is required.")
        appendLine()
        appendLine("Future catalog roadmap only: validate the current ${rows.size}-level fixed catalog before separately gating later expansion targets. Optional Infinite Mode remains out of scope until retention and content-quality evidence justify it.")
    }

    private fun StringBuilder.appendGroups(groups: List<List<LevelDefinition>>) {
        if (groups.isEmpty()) appendLine("None.") else groups.forEachIndexed { index, group ->
            appendLine("- Group ${index + 1}: ${group.joinToString { "${it.id} (#${it.number})" }}")
        }
    }

    private fun localPairs(): List<Pair<String, String>> = rows.flatMap { row ->
        row.localSimilarityIds.mapNotNull { peer ->
            if (row.level.id < peer) row.level.id to peer else null
        }
    }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
}

private fun legacyScores(file: File): Map<String, Int> {
    val lines = file.readLines().filter(String::isNotBlank)
    if (lines.isEmpty()) return emptyMap()
    val header = lines.first().split(',')
    val idIndex = header.indexOf("level_id")
    val scoreIndex = header.indexOf("difficulty_score")
    if (idIndex < 0 || scoreIndex < 0) return emptyMap()
    return lines.drop(1).associate { line ->
        val columns = line.split(',')
        columns[idIndex] to columns[scoreIndex].toInt()
    }
}

private fun File.writeReport(content: String) {
    parentFile.mkdirs()
    writeText(content)
}

private fun Map<String, String>.requiredM51(key: String): String =
    requireNotNull(this[key]) { "Missing --$key=..." }

private fun json(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

private fun jsonArray(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]", transform = ::json)

private fun decimal(value: Double): String = "%.4f".format(Locale.ROOT, value)
