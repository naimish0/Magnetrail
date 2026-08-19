package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Scorer
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchAnalyzer
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchConfig
import com.rameshta.magnetrail.core.difficulty.v4.DIFFICULTY_V4_ANALYZER_VERSION
import com.rameshta.magnetrail.core.difficulty.v4.DIFFICULTY_V4_CALIBRATION_VERSION
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Analyzer
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4CalibrationReport
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4CalibrationScoreRow
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Calibrator
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Config
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4HumanCalibrationDataset
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4HumanLevelRating
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Score
import com.rameshta.magnetrail.core.level.LevelParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

private const val V4_AUDIT_JSON = "MAGNETRAIL_DIFFICULTY_V4_AUDIT.json"
private const val V4_AUDIT_MD = "MAGNETRAIL_DIFFICULTY_V4_AUDIT.md"
private const val V4_DIAGNOSTICS_CSV = "MAGNETRAIL_DIFFICULTY_V4_LEVEL_DIAGNOSTICS.csv"
private const val V4_HUMAN_CALIBRATION_JSON = "MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json"
private const val V4_CALIBRATION_MD = "MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md"

private val difficultyV4Json = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}

@Serializable
data class DifficultyV4LevelDiagnostic(
    val levelId: String,
    val levelNumber: Int,
    val title: String,
    val boardSize: String,
    val boardFingerprint: String,
    val v3Score: Int,
    val v3Confidence: Double,
    val v4: DifficultyV4Score,
)

@Serializable
data class DifficultyV4CampaignAggregate(
    val levelCount: Int,
    val scoredLevelCount: Int,
    val truncatedLevelCount: Int,
    val averageV3Score: Double,
    val averageV4Score: Double?,
    val totalPlausibleChoices: Int,
    val totalSuccessfulChoices: Int,
    val totalSafeSuccessfulChoices: Int,
    val totalMeaningfulSuccessfulChoices: Int,
    val totalFutureDeadEndChoices: Int,
    val aggregateSafeChoiceRatio: Double,
    val aggregateMeaningfulFailureRate: Double,
    val totalMeaningfulDecisionStates: Int,
    val totalHarmfulDecisionStates: Int,
    val aggregateHarmfulDecisionDensity: Double,
    val totalPolarityFlips: Int,
    val totalStrategicallyImpactfulPolarityFlips: Int,
    val aggregatePolarityImpactRatio: Double,
    val rawWinningSequences: Long,
    val canonicalStrategies: Long?,
    val aggregatePermutationRedundancy: Double?,
    val greedySolvedLevelCount: Int,
    val averageRandomCompletionRate: Double,
)

@Serializable
data class DifficultyV4AuditReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "magnetrail-difficulty-v4-audit-1",
    val analyzerVersion: String = DIFFICULTY_V4_ANALYZER_VERSION,
    val calibrationVersion: Int = DIFFICULTY_V4_CALIBRATION_VERSION,
    val calibrationStatus: String = "PROVISIONAL_UNCALIBRATED",
    val campaignCatalogId: String,
    val campaignContentVersion: Int?,
    val campaignGeneratorVersion: Int?,
    val config: DifficultyV4Config,
    val aggregate: DifficultyV4CampaignAggregate,
    val levels: List<DifficultyV4LevelDiagnostic>,
    val limitations: List<String> = listOf(
        "NOT MEASURABLE WITH CURRENT IMPLEMENTATION: human-perceived obviousness.",
        "NOT MEASURABLE WITH CURRENT IMPLEMENTATION: visual fairness and player attention.",
        "V4 is experimental; preliminary human evidence does not make it a production difficulty authority.",
    ),
)

fun analyzeCampaignDifficultyV4(options: Map<String, String>) {
    val campaignFile = File(options.requiredV4("campaign"))
    val outputDirectory = File(options.requiredV4("output")).also { it.mkdirs() }
    val config = options["config"]?.takeIf { it.isNotBlank() }?.let { path ->
        difficultyV4Json.decodeFromString<DifficultyV4Config>(File(path).readText())
    } ?: DifficultyV4Config()
    val catalog = LevelParser().parseCatalog(campaignFile.readText())
    val v3SearchConfig = PuzzleSearchConfig()
    val v3Analyzer = PuzzleSearchAnalyzer(config = v3SearchConfig)
    val v4Analyzer = DifficultyV4Analyzer(config = config)
    val levels = catalog.levels.sortedBy { it.number }.mapIndexed { index, level ->
        val v3 = DifficultyV3Scorer.score(v3Analyzer.analyze(level), searchConfig = v3SearchConfig)
        val v4 = v4Analyzer.analyze(level)
        println("Difficulty V4 ${index + 1}/${catalog.levels.size}: ${level.id} = ${v4.score ?: "INCOMPLETE"}")
        DifficultyV4LevelDiagnostic(
            levelId = level.id,
            levelNumber = level.number,
            title = level.title,
            boardSize = "${level.width}x${level.height}",
            boardFingerprint = ContentFingerprint.exact(level),
            v3Score = v3.score,
            v3Confidence = v3.confidence,
            v4 = v4,
        )
    }
    val humanFile = File(outputDirectory, V4_HUMAN_CALIBRATION_JSON)
    if (!humanFile.exists()) {
        humanFile.writeText(
            difficultyV4Json.encodeToString(defaultHumanCalibration(levels)) + "\n",
        )
    }
    // Parsing protects entered ratings from being silently replaced by a malformed schema.
    val human = difficultyV4Json.decodeFromString<DifficultyV4HumanCalibrationDataset>(humanFile.readText())
    val enteredObservationCount = human.levels.sumOf { level ->
        level.ratings.count { it.difficultyRating != null }
    }
    val calibration = DifficultyV4Calibrator.compare(human, levels.map(::calibrationScoreRow))
    val report = DifficultyV4AuditReport(
        calibrationStatus = if (calibration.ratedObservationCount == 0) {
            "PROVISIONAL_UNCALIBRATED"
        } else {
            "HUMAN_EVIDENCE_RECORDED_CALIBRATION_PRELIMINARY"
        },
        campaignCatalogId = catalog.catalogId,
        campaignContentVersion = catalog.contentVersion,
        campaignGeneratorVersion = catalog.generatorVersion,
        config = config,
        aggregate = aggregateV4(levels),
        levels = levels,
    )
    File(outputDirectory, V4_AUDIT_JSON).writeText(difficultyV4Json.encodeToString(report) + "\n")
    File(outputDirectory, V4_DIAGNOSTICS_CSV).writeText(renderDifficultyV4Csv(levels))
    File(outputDirectory, V4_AUDIT_MD).writeText(renderDifficultyV4Audit(report))
    File(outputDirectory, V4_CALIBRATION_MD).writeText(renderDifficultyV4Calibration(calibration))
    println("Wrote isolated V4 diagnostics for ${levels.size} levels to ${outputDirectory.path}")
    println(
        if (calibration.ratedObservationCount == 0) {
            "Awaiting human calibration; ${calibration.excludedStaleBoardRatingCount} stale-board ratings excluded."
        } else {
            "Recorded ${calibration.ratedObservationCount}/$enteredObservationCount usable human observations; " +
                "calibration remains preliminary."
        },
    )
}

fun calibrateDifficultyV4(options: Map<String, String>) {
    val auditFile = File(options.requiredV4("audit"))
    val humanFile = File(options.requiredV4("human-calibration"))
    val outputFile = File(options.requiredV4("output"))
    val audit = difficultyV4Json.decodeFromString<DifficultyV4AuditReport>(auditFile.readText())
    val human = difficultyV4Json.decodeFromString<DifficultyV4HumanCalibrationDataset>(humanFile.readText())
    val calibration = DifficultyV4Calibrator.compare(human, audit.levels.map(::calibrationScoreRow))
    outputFile.parentFile.mkdirs()
    outputFile.writeText(renderDifficultyV4Calibration(calibration))
    println("Human observations: ${calibration.ratedObservationCount}")
    println("Calibration conclusion: ${calibration.alignmentConclusion}")
    println("No production thresholds or campaign content were changed.")
}

private fun aggregateV4(levels: List<DifficultyV4LevelDiagnostic>): DifficultyV4CampaignAggregate {
    val metrics = levels.map { it.v4.metrics }
    val v4Scores = levels.mapNotNull { it.v4.score }
    val rawSequences = metrics.mapNotNull { it.strategy.rawWinningSequenceCount }.saturatingSum()
    val canonical = metrics.map { it.strategy.canonicalStrategyCount?.toLong() }
    val canonicalSum = canonical.takeIf { values -> values.all { it != null } }
        ?.filterNotNull()?.saturatingSum()
    return DifficultyV4CampaignAggregate(
        levelCount = levels.size,
        scoredLevelCount = v4Scores.size,
        truncatedLevelCount = levels.count { it.v4.searchTruncated },
        averageV3Score = rounded(levels.map { it.v3Score.toDouble() }.averageOrZero()),
        averageV4Score = v4Scores.takeIf { it.isNotEmpty() }?.average()?.let(::rounded),
        totalPlausibleChoices = metrics.sumOf { it.plausibleChoiceCount },
        totalSuccessfulChoices = metrics.sumOf { it.successfulChoiceCount },
        totalSafeSuccessfulChoices = metrics.sumOf { it.safeSuccessfulChoiceCount },
        totalMeaningfulSuccessfulChoices = metrics.sumOf { it.meaningfulSuccessfulChoiceCount },
        totalFutureDeadEndChoices = metrics.sumOf { it.futureDeadEndChoiceCount },
        aggregateSafeChoiceRatio = ratioV4(
            metrics.sumOf { it.safeSuccessfulChoiceCount },
            metrics.sumOf { it.successfulChoiceCount },
        ),
        aggregateMeaningfulFailureRate = ratioV4(
            metrics.sumOf { it.futureDeadEndChoiceCount },
            metrics.sumOf { it.successfulChoiceCount },
        ),
        totalMeaningfulDecisionStates = metrics.sumOf { it.meaningfulDecisionStateCount },
        totalHarmfulDecisionStates = metrics.sumOf { it.harmfulDecisionCount },
        aggregateHarmfulDecisionDensity = ratioV4(
            metrics.sumOf { it.harmfulDecisionCount },
            metrics.sumOf { it.meaningfulDecisionStateCount },
        ),
        totalPolarityFlips = metrics.sumOf { it.polarity.polarityFlipCount },
        totalStrategicallyImpactfulPolarityFlips = metrics.sumOf {
            it.polarity.strategicallyImpactfulPolarityFlipCount
        },
        aggregatePolarityImpactRatio = ratioV4(
            metrics.sumOf { it.polarity.strategicallyImpactfulPolarityFlipCount },
            metrics.sumOf { it.polarity.polarityFlipCount },
        ),
        rawWinningSequences = rawSequences,
        canonicalStrategies = canonicalSum,
        aggregatePermutationRedundancy = canonicalSum?.let { strategies ->
            if (rawSequences == 0L) 0.0 else rounded((rawSequences - strategies).toDouble() / rawSequences)
        },
        greedySolvedLevelCount = metrics.count { it.greedyPolicy.solved },
        averageRandomCompletionRate = rounded(metrics.map { it.randomPolicy.completionRate }.averageOrZero()),
    )
}

private fun defaultHumanCalibration(
    diagnostics: List<DifficultyV4LevelDiagnostic>,
): DifficultyV4HumanCalibrationDataset {
    val groups = linkedMapOf(
        "referenceStrong" to listOf(97, 140, 145, 100, 175, 198, 99, 199, 200, 93, 96, 95, 195, 147, 184, 185, 197, 98, 135, 173),
        "weakControl" to listOf(153, 71, 68, 40, 28, 32, 163, 152, 159, 191, 122, 31, 35, 26, 27),
        "easyControl" to listOf(1, 2, 3, 4, 5),
    )
    val byNumber = diagnostics.associateBy { it.levelNumber }
    val entries = groups.flatMap { (group, numbers) ->
        numbers.map { number ->
            val level = requireNotNull(byNumber[number]) { "Calibration level $number is absent from the campaign" }
            DifficultyV4HumanLevelRating(
                levelId = level.levelId,
                levelNumber = number,
                group = group,
                boardFingerprint = level.boardFingerprint,
            )
        }
    }
    return DifficultyV4HumanCalibrationDataset(groups = groups, levels = entries)
}

private fun calibrationScoreRow(level: DifficultyV4LevelDiagnostic): DifficultyV4CalibrationScoreRow {
    val metrics = level.v4.metrics
    return DifficultyV4CalibrationScoreRow(
        levelId = level.levelId,
        levelNumber = level.levelNumber,
        v3Score = level.v3Score,
        v4Score = level.v4.score,
        v4Confidence = level.v4.confidence,
        boardFingerprint = level.boardFingerprint,
        metrics = linkedMapOf(
            "meaningfulFailureRate" to metrics.meaningfulFailureRate,
            "harmfulDecisionDensity" to metrics.harmfulDecisionDensity,
            "safeChoiceRatio" to metrics.safeChoiceRatio,
            "mandatoryOrderingRatio" to metrics.ordering.mandatoryOrderingRatio,
            "polarityImpactRatio" to metrics.polarity.polarityImpactRatio,
            "permutationRedundancy" to metrics.strategy.permutationRedundancy,
            "greedyResistance" to if (metrics.greedyPolicy.solved) 0.0 else 1.0,
            "randomDeadlockRate" to metrics.randomPolicy.deadlockRate,
            "decisionDensity" to metrics.forcedDecision.decisionDensity,
        ),
    )
}

private fun renderDifficultyV4Csv(levels: List<DifficultyV4LevelDiagnostic>): String = buildString {
    appendLine(
        listOf(
            "levelId", "levelNumber", "title", "boardSize", "arrowCount", "magnetCount", "wallCount",
            "plausibleChoiceCount", "immediatelyInvalidChoiceCount", "successfulChoiceCount", "safeSuccessfulChoiceCount",
            "meaningfulSuccessfulChoiceCount", "harmfulDecisionCount", "meaningfulFailureRate",
            "harmfulDecisionDensity", "mandatoryOrderingPairCount", "mandatoryOrderingRatio",
            "mandatoryOrderingChainDepth", "polarityFlipCount", "strategicallyImpactfulPolarityFlipCount",
            "polarityImpactRatio", "rawWinningSequenceCount", "canonicalStrategyCount",
            "permutationRedundancy", "commutationRatio", "greedySolved", "greedyFailureDepth",
            "randomPolicyCompletionRate", "randomPolicyDeadlockRate", "recoveryPressure",
            "maxRecoveryDepth", "deadEndDepth", "longestForcedRun", "meaningfulDecisionCount",
            "decisionDensity", "maxDecisionGap", "relevantWallCount", "irrelevantWallCount",
            "relevantMagnetCount", "irrelevantMagnetCount", "v3Score", "v4Score", "v4Confidence",
            "searchComplete", "searchStateCount", "searchTruncated", "truncationReason", "metricStatus",
        ).joinToString(","),
    )
    levels.forEach { level ->
        val score = level.v4
        val metrics = score.metrics
        appendLine(
            listOf(
                level.levelId, level.levelNumber, level.title, level.boardSize, metrics.arrowCount,
                metrics.magnetCount, metrics.wallCount, metrics.plausibleChoiceCount,
                metrics.immediatelyInvalidChoiceCount, metrics.successfulChoiceCount, metrics.safeSuccessfulChoiceCount,
                metrics.meaningfulSuccessfulChoiceCount, metrics.harmfulDecisionCount,
                metrics.meaningfulFailureRate, metrics.harmfulDecisionDensity,
                metrics.ordering.mandatoryOrderingPairCount, metrics.ordering.mandatoryOrderingRatio,
                metrics.ordering.mandatoryOrderingChainDepth, metrics.polarity.polarityFlipCount,
                metrics.polarity.strategicallyImpactfulPolarityFlipCount, metrics.polarity.polarityImpactRatio,
                metrics.strategy.rawWinningSequenceCount, metrics.strategy.canonicalStrategyCount,
                metrics.strategy.permutationRedundancy, metrics.strategy.commutationRatio,
                metrics.greedyPolicy.solved, metrics.greedyPolicy.firstDivergenceDepth,
                metrics.randomPolicy.completionRate, metrics.randomPolicy.deadlockRate,
                metrics.recovery.normalizedRecoveryPressure, metrics.recovery.maximumRecoveryDepth,
                metrics.recovery.maximumDeadEndDepth, metrics.forcedDecision.longestForcedRun,
                metrics.forcedDecision.meaningfulDecisionCount, metrics.forcedDecision.decisionDensity,
                metrics.forcedDecision.maximumDecisionGap, metrics.objectRelevance.relevantWallCount,
                metrics.objectRelevance.irrelevantWallCount, metrics.objectRelevance.relevantMagnetCount,
                metrics.objectRelevance.irrelevantMagnetCount, level.v3Score, score.score, score.confidence,
                score.searchComplete, metrics.searchStateCount, score.searchTruncated,
                score.truncationReasons.joinToString("|"),
                metrics.metricStatus.entries.joinToString("|") { "${it.key}:${it.value}" },
            ).joinToString(",", transform = ::csvCell),
        )
    }
}

private fun renderDifficultyV4Audit(report: DifficultyV4AuditReport): String = buildString {
    val aggregate = report.aggregate
    appendLine("# Magnetrail Difficulty V4 Diagnostic Audit")
    appendLine()
    appendLine("> V4 is an experimental diagnostic model and is not yet a production difficulty authority.")
    appendLine()
    appendLine(
        if (report.calibrationStatus == "PROVISIONAL_UNCALIBRATED") {
            "Status: **Awaiting human calibration.** Scores are provisional calibration version 0."
        } else {
            "Status: **Human evidence recorded; calibration preliminary.** Scores remain provisional calibration version 0."
        },
    )
    appendLine()
    appendLine("## Executive summary")
    appendLine()
    appendLine("The analyzer evaluated ${aggregate.levelCount} campaign levels without modifying campaign content. It separates successful-but-safe choices from consequence-bearing decisions, collapses commuting winning permutations, tests polarity actionability counterfactually, and penalizes forced runs and irrelevant structure.")
    appendLine()
    appendLine("- Scored levels: ${aggregate.scoredLevelCount}/${aggregate.levelCount}")
    appendLine("- Truncated levels: ${aggregate.truncatedLevelCount}")
    appendLine("- Aggregate safe-choice ratio: ${percent(aggregate.aggregateSafeChoiceRatio)}")
    appendLine("- Aggregate meaningful-failure rate: ${percent(aggregate.aggregateMeaningfulFailureRate)}")
    appendLine("- Harmful/meaningful decision-state density: ${percent(aggregate.aggregateHarmfulDecisionDensity)}")
    appendLine("- Polarity impact: ${aggregate.totalStrategicallyImpactfulPolarityFlips}/${aggregate.totalPolarityFlips} (${percent(aggregate.aggregatePolarityImpactRatio)})")
    appendLine("- Greedy-solved levels: ${aggregate.greedySolvedLevelCount}/${aggregate.levelCount}")
    appendLine("- Mean random-success completion rate: ${percent(aggregate.averageRandomCompletionRate)}")
    appendLine("- Raw winning sequences: ${aggregate.rawWinningSequences}")
    appendLine("- Canonical strategy representatives: ${aggregate.canonicalStrategies ?: "INCOMPLETE"}")
    appendLine("- Aggregate permutation redundancy: ${aggregate.aggregatePermutationRedundancy?.let(::percent) ?: "INCOMPLETE"}")
    appendLine("- Mean V3 score: ${fmt(aggregate.averageV3Score)}")
    appendLine("- Mean provisional V4 score: ${aggregate.averageV4Score?.let(::fmt) ?: "INCOMPLETE"}")
    appendLine()
    appendLine("## V3 vs V4")
    appendLine()
    appendLine("V3 and V4 are independent. V3 rewards several activity/branching signals; V4 makes consequence, mandatory order, polarity actionability, greedy/random resistance, and recovery primary while applying explicit penalties for safe choices, permutation redundancy, forced runs, and irrelevant walls/magnets.")
    appendLine()
    appendLine("### Largest provisional downward changes")
    appendLine()
    appendLine("| Level | V3 | V4 | Change | Safe | Redundancy | Harmful density | Ordering | Polarity | Greedy |")
    appendLine("|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|")
    report.levels.filter { it.v4.score != null }.sortedBy { requireNotNull(it.v4.score) - it.v3Score }.take(20)
        .forEach { appendComparisonRow(it) }
    appendLine()
    appendLine("### Highest provisional V4 diagnostics")
    appendLine()
    appendLine("| Level | V3 | V4 | Change | Safe | Redundancy | Harmful density | Ordering | Polarity | Greedy |")
    appendLine("|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|")
    report.levels.filter { it.v4.score != null }.sortedByDescending { it.v4.score }.take(20)
        .forEach { appendComparisonRow(it) }
    appendLine()
    appendLine("### Required reference/control inspection")
    appendLine()
    appendLine("These groups are sampling groups, not objective hard/easy truth.")
    appendLine()
    appendLine("| Level | V3 | V4 | Positive driver | Largest penalty | Confidence |")
    appendLine("|---:|---:|---:|---|---|---:|")
    val inspect = setOf(97, 140, 145, 100, 175, 198, 153, 163, 152, 159, 191, 122)
    report.levels.filter { it.levelNumber in inspect }.sortedBy { it.levelNumber }.forEach { level ->
        val positive = level.v4.contributions.positive.maxByOrNull { it.value }
        val negative = level.v4.contributions.negative.maxByOrNull { it.value }
        appendLine("| ${level.levelNumber} | ${level.v3Score} | ${level.v4.score ?: "null"} | ${positive?.key}:${positive?.value} | ${negative?.key}:${negative?.value} | ${fmt(level.v4.confidence)} |")
    }
    appendLine()
    appendLine("## Metric interpretation")
    appendLine()
    appendLine("A meaningful decision state has at least two successful choices whose outcomes differ by a proven dead end, a future capability signature, a reduction in winning continuations, or non-commutation. A harmful decision state specifically contains both a viable continuation and a successful action with a completely proven unsolvable descendant. Route-only differences are excluded from harm.")
    appendLine()
    appendLine("Greedy is stable authored-order among currently successful arrows. Random-success trials choose uniformly from successful actions using the ${report.config.randomPolicySeeds.size} fixed serialized seeds. Neither policy models visual salience.")
    appendLine()
    appendLine("## Search safety and confidence")
    appendLine()
    appendLine("Bounds: states=${report.config.maxExpandedStates}, action resolutions=${report.config.maxActionResolutions}, depth=${report.config.maxSearchDepth}, winning sequences=${report.config.maxWinningSequences}, canonical representatives=${report.config.maxCanonicalStrategyRepresentatives}, random trials=${report.config.randomPolicySeeds.size}. Any exhausted bound makes the affected metric incomplete and appears as null/status/truncation evidence.")
    appendLine()
    appendLine("## Limitations")
    appendLine()
    report.limitations.forEach { appendLine("- $it") }
    appendLine()
    appendLine(
        if (report.calibrationStatus == "PROVISIONAL_UNCALIBRATED") {
            "Automated analysis is not human validation or human approval. Awaiting human calibration."
        } else {
            "Human evidence is recorded separately from automated evidence. Ranking calibration remains inconclusive until differentiated ratings exist."
        },
    )
}

private fun StringBuilder.appendComparisonRow(level: DifficultyV4LevelDiagnostic) {
    val score = requireNotNull(level.v4.score)
    val metrics = level.v4.metrics
    appendLine("| ${level.levelNumber} | ${level.v3Score} | $score | ${score - level.v3Score} | ${percent(metrics.safeChoiceRatio)} | ${metrics.strategy.permutationRedundancy?.let(::percent) ?: "null"} | ${percent(metrics.harmfulDecisionDensity)} | ${metrics.ordering.mandatoryOrderingRatio?.let(::percent) ?: "null"} | ${percent(metrics.polarity.polarityImpactRatio)} | ${if (metrics.greedyPolicy.solved) "solves" else "fails"} |")
}

private fun renderDifficultyV4Calibration(report: DifficultyV4CalibrationReport): String = buildString {
    appendLine("# Magnetrail Difficulty V4 Human Calibration")
    appendLine()
    appendLine("Status: **${if (report.ratedObservationCount == 0) "Awaiting human calibration." else "Calibration preliminary."}**")
    appendLine()
    appendLine("- Human ratings supplied: ${if (report.ratedObservationCount > 0) "YES" else "NO"}")
    appendLine("- Rated observations: ${report.ratedObservationCount}")
    appendLine("- Distinct rated levels: ${report.distinctRatedLevelCount}")
    appendLine("- Stale-board ratings excluded: ${report.excludedStaleBoardRatingCount}")
    appendLine("- V4 calibrated: NO")
    appendLine("- Evidence: ${report.alignmentConclusion}")
    appendLine("- Warnings: ${report.warnings.joinToString("; ")}")
    appendLine()
    appendLine("## Comparison")
    appendLine()
    appendLine("| Model | N | Pearson | Spearman | MAE (0–100) | Mean rank disagreement |")
    appendLine("|---|---:|---:|---:|---:|---:|")
    appendLine("| V3 | ${report.v3.sampleCount} | ${report.v3.pearson ?: "null"} | ${report.v3.spearman ?: "null"} | ${report.v3.meanAbsoluteError ?: "null"} | ${report.v3.meanRankDisagreement ?: "null"} |")
    appendLine("| V4 | ${report.v4.sampleCount} | ${report.v4.pearson ?: "null"} | ${report.v4.spearman ?: "null"} | ${report.v4.meanAbsoluteError ?: "null"} | ${report.v4.meanRankDisagreement ?: "null"} |")
    appendLine()
    appendLine("Pearson/Spearman are null when fewer than three usable observations exist or either series has no variance. Ratings are mapped linearly from 1–8 to 0–100 only for comparison; this does not alter V4 weights.")
    appendLine()
    appendLine("## Metric-level correlations")
    appendLine()
    if (report.ratedObservationCount == 0) {
        appendLine("No rated observations. Metric correlations are not measurable.")
    } else {
        report.metricPearsonCorrelations.forEach { (metric, correlation) -> appendLine("- $metric: ${correlation ?: "null"}") }
    }
    appendLine()
    appendLine("## Strongest model disagreements")
    appendLine()
    if (report.ratedObservationCount == 0) {
        appendLine("No rated observations; overestimates and underestimates are not measurable.")
    } else {
        appendCalibrationOutliers("V3 overestimates", report.v3StrongestOverestimates)
        appendCalibrationOutliers("V3 underestimates", report.v3StrongestUnderestimates)
        appendCalibrationOutliers("V4 overestimates", report.v4StrongestOverestimates)
        appendCalibrationOutliers("V4 underestimates", report.v4StrongestUnderestimates)
    }
    appendLine()
    appendLine("## Workflow")
    appendLine()
    appendLine("1. Run `./gradlew analyzeCampaignDifficultyV4`.")
    appendLine("2. Open `docs/development/$V4_HUMAN_CALIBRATION_JSON`.")
    appendLine("3. Add a rater and 1–8 ratings to the selected reference/control entries; optional booleans may remain null.")
    appendLine("4. Run `./gradlew calibrateDifficultyV4`.")
    appendLine("5. Review this report. Do not automatically change production thresholds.")
    appendLine()
    appendLine("Automated analysis is not human validation. No campaign promotion is authorized by this report.")
}

private fun StringBuilder.appendCalibrationOutliers(
    heading: String,
    rows: List<com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4CalibrationOutlier>,
) {
    appendLine("### $heading")
    appendLine()
    if (rows.isEmpty()) {
        appendLine("None.")
        appendLine()
        return
    }
    appendLine("| Level | Human 0–100 | Model | Signed error |")
    appendLine("|---:|---:|---:|---:|")
    rows.forEach { appendLine("| ${it.levelNumber} | ${fmt(it.humanScore100)} | ${it.predictedScore} | ${fmt(it.signedError)} |") }
    appendLine()
}

private fun csvCell(value: Any?): String {
    val text = value?.toString() ?: ""
    return if (text.any { it == ',' || it == '"' || it == '\n' }) {
        "\"${text.replace("\"", "\"\"")}\""
    } else {
        text
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

private fun List<Long>.saturatingSum(): Long = fold(0L) { total, value ->
    if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
}

private fun ratioV4(numerator: Int, denominator: Int): Double =
    if (denominator == 0) 0.0 else rounded(numerator.toDouble() / denominator)

private fun rounded(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0

private fun percent(value: Double): String = fmt(value * 100.0) + "%"

private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

private fun Map<String, String>.requiredV4(name: String): String =
    this[name] ?: error("Missing --$name option")
