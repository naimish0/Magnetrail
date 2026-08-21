package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyMetrics
import com.rameshta.magnetrail.core.difficulty.DifficultyScorer
import com.rameshta.magnetrail.core.generation.CERTIFICATION_SOLUTION_LIMIT
import com.rameshta.magnetrail.core.generation.CertificationPipeline
import com.rameshta.magnetrail.core.generation.CertificationRequest
import com.rameshta.magnetrail.core.generation.CertificationResult
import com.rameshta.magnetrail.core.generation.CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.GENERATOR_VERSION
import com.rameshta.magnetrail.core.generation.GenerationProfile
import com.rameshta.magnetrail.core.generation.GenerationRequest
import com.rameshta.magnetrail.core.generation.GenerationResult
import com.rameshta.magnetrail.core.generation.LevelGenerator
import com.rameshta.magnetrail.core.generation.v5.CertificationPipelineV5
import com.rameshta.magnetrail.core.generation.v5.CertificationResultV5
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesV5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesD21
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesCampaignV9
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelOrigin
import java.io.File
import java.time.LocalDate
import com.rameshta.magnetrail.core.daily.DailySeed

fun main(arguments: Array<String>) {
    require(arguments.isNotEmpty()) { "Expected a content-tool command" }
    val options = arguments.drop(1).associate { argument ->
        require(argument.startsWith("--") && '=' in argument) { "Invalid option '$argument'" }
        argument.substringAfter("--").substringBefore('=') to argument.substringAfter('=')
    }
    when (arguments.first()) {
        "generate" -> generateCandidates(options)
        "promote" -> promote(options)
        "certify" -> certifyShipped(options)
        "benchmark" -> benchmarkDaily(options)
        "analyze-difficulty", "analyze-quality", "check-duplicates", "audit-pacing", "certify-quality" ->
            runM51(arguments.first(), options)
        "deduplicate-symmetry" -> stageSymmetryDeduplication(options)
        "stage-m52-expansion" -> stageM52Expansion(options)
        "certify-m52-review" -> certifyM52Review(options)
        "analyze-phase0" -> analyzePhase0Current(options)
        "stage-phase0-candidates" -> stagePhase0Candidates(options)
        "plan-phase0-remediation" -> planPhase0Remediation(options)
        "promote-approved-phase0" -> promoteApprovedPhase0(options)
        "finalize-promoted-phase0" -> finalizePromotedPhase0(options)
        "stage-phase1-expansion" -> stagePhase1Expansion(options)
        "promote-approved-phase1" -> promoteApprovedPhase1(options)
        "finalize-promoted-phase1" -> finalizePromotedPhase1(options)
        "analyze-difficulty-v4" -> analyzeCampaignDifficultyV4(options)
        "calibrate-difficulty-v4" -> calibrateDifficultyV4(options)
        "generate-d2-v5" -> generateD2CampaignV5(options)
        "analyze-d2-v5" -> analyzeD2Artifacts(options, "generation")
        "analyze-d2-objects-v5" -> analyzeD2Artifacts(options, "objects")
        "analyze-d2-graphs-v5" -> analyzeD2Artifacts(options, "graphs")
        "refresh-d2-v5" -> refreshD2CampaignV5(options)
        "promote-d2-v5" -> promoteD2Campaign(options)
        "generate-d2.1-spatial-density" -> generateD21SpatialDensityAudit(options)
        "validate-d2.1-spatial-density" -> validateD21SpatialDensityAudit(options)
        "benchmark-generator-v5-repair" -> generateGeneratorV5RepairAudit(options)
        "promote-v5.1-append" -> promoteV51Append(options)
        "generate-infinite-catalog" -> generateInfiniteCatalog(options)
        "generate-campaign-v9-expansion" -> generateCampaignV9Expansion(options)
        "promote-campaign-v9-expansion" -> promoteCampaignV9Expansion(options)
        else -> error("Unknown command '${arguments.first()}'")
    }
}

private fun benchmarkDaily(options: Map<String, String>) {
    val campaign = parse(File(options.required("campaign")))
    val generator = LevelGenerator(campaign.levels)
    val timings = mutableListOf<Long>()
    val explored = mutableListOf<Int>()
    val startDate = LocalDate.of(2026, 1, 1)
    repeat(31) { offset ->
        val identity = DailySeed.identity(startDate.plusDays(offset.toLong()))
        val started = System.nanoTime()
        val result = generator.generate(
            GenerationRequest(
                stableId = identity.dailyId,
                sequenceNumber = 1,
                title = "Daily ${identity.localDate}",
                seed = identity.seed,
                profile = GenerationProfile.DAILY_CHALLENGE,
                packId = "daily-challenge",
            ),
        ) as? GenerationResult.Generated ?: error("Daily generation exhausted for ${identity.dailyId}")
        timings += (System.nanoTime() - started) / 1_000_000
        explored += result.metrics.exploredStateCount
    }
    val sorted = timings.sorted()
    val report = buildString {
        appendLine("sample_count=${timings.size}")
        appendLine("host_java=${System.getProperty("java.version")}")
        appendLine("min_ms=${sorted.first()}")
        appendLine("median_ms=${sorted[sorted.size / 2]}")
        appendLine("p95_ms=${sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)]}")
        appendLine("max_ms=${sorted.last()}")
        appendLine("explored_states=${explored.minOrNull()}..${explored.maxOrNull()}")
        appendLine("generator_version=$GENERATOR_VERSION")
        appendLine("profile=${GenerationProfile.DAILY_CHALLENGE.profileId}")
    }
    val output = File(options.required("output"))
    output.parentFile.mkdirs()
    output.writeText(report)
    print(report)
}

private fun generateCandidates(options: Map<String, String>) {
    val prototype = parse(File(options.required("prototype")))
    val output = File(options.required("output")).also { it.mkdirs() }
    val count = options.required("count").toInt()
    val initialSeed = options.required("seed").toLong()
    val profile = GenerationProfile.valueOf(options.required("profile"))
    val templates = HandcraftedBank.expanded(prototype.levels)
    val generator = LevelGenerator(templates)
    val accepted = mutableListOf<LevelDefinition>()
    val rejected = linkedMapOf<String, Int>()
    var seed = initialSeed
    while (accepted.size < count && seed < initialSeed + count * 100L) {
        val number = accepted.size + 1
        when (val result = generator.generate(
            GenerationRequest(
                stableId = "candidate-${profile.name.lowercase()}-${seed}",
                sequenceNumber = number,
                title = "Candidate $number",
                seed = seed,
                profile = profile,
                packId = "staging",
            ),
        )) {
            is GenerationResult.Generated -> {
                if (accepted.none {
                    ContentFingerprint.symmetryNormalized(it) == ContentFingerprint.symmetryNormalized(result.level)
                }) {
                    accepted += result.level
                } else {
                    rejected.increment("duplicate-fingerprint")
                }
                result.rejectedReasons.forEach { (reason, amount) -> rejected.increment(reason, amount) }
            }
            is GenerationResult.Exhausted -> result.rejectedReasons.forEach { (reason, amount) ->
                rejected.increment(reason, amount)
            }
        }
        seed += 1
    }
    check(accepted.size == count) { "Generated ${accepted.size}/$count candidates before bounded seed cap" }
    val catalog = LevelCatalog(2, RULE_VERSION, "magnetrail-staging", accepted, CONTENT_VERSION, GENERATOR_VERSION)
    File(output, "candidates.json").writeText(LevelParser().encodeCatalog(catalog))
    File(output, "generation-report.txt").writeText(
        "count=${accepted.size}\nprofile=${profile.profileId}\nseedRange=$initialSeed..${seed - 1}\nrejections=$rejected\n",
    )
    println("Generated ${accepted.size} certified candidates in $output; shipped content was not modified.")
}

private fun promote(options: Map<String, String>) {
    val prototype = parse(File(options.required("prototype")))
    val campaignBuild = buildCampaign(prototype)
    val fallbacksBuild = buildFallbacks(campaignBuild.catalog.levels)
    validateCatalog(campaignBuild.catalog)
    validateCatalog(fallbacksBuild.catalog)

    File(options.required("campaign")).writeText(LevelParser().encodeCatalog(campaignBuild.catalog))
    File(options.required("fallbacks")).writeText(LevelParser().encodeCatalog(fallbacksBuild.catalog))
    File(options.required("report")).writeText(csvReport(campaignBuild.rows))
    File(options.required("summary")).writeText(summaryReport(campaignBuild, fallbacksBuild))
    println(
        "Promoted ${campaignBuild.catalog.levels.size} campaign levels and " +
            "${fallbacksBuild.catalog.levels.size} daily fallbacks after explicit confirmation.",
    )
}

private fun buildCampaign(prototype: LevelCatalog): CatalogBuild {
    val rawHandcrafted = HandcraftedBank.expanded(prototype.levels)
    check(rawHandcrafted.size == 30) { "Expected 30 handcrafted/tuned levels" }
    val certification = CertificationPipeline()
    val accepted = mutableListOf<LevelDefinition>()
    val rows = mutableListOf<ReportRow>()
    rawHandcrafted.forEach { level ->
        val profile = profileFor(level)
        val pack = packFor(level.number)
        val result = certification.certify(
            level,
            CertificationRequest(profile, LevelOrigin.HANDCRAFTED, pack),
        )
        val certified = result.requireAccepted(level.id)
        accepted += certified.level
        rows += ReportRow(certified.level, certified.metrics, attempts = 0, rejectedBeforeAcceptance = 0)
    }

    val generator = LevelGenerator(rawHandcrafted)
    val fingerprints = accepted.mapTo(mutableSetOf()) { ContentFingerprint.symmetryNormalized(it) }
    val layoutKeys = accepted.mapTo(mutableSetOf(), ::nearDuplicateKey)
    var seedCursor = 310_001L
    for (number in 31..100) {
        val profile = if (number <= 70) GenerationProfile.DEVELOPING_MEDIUM else GenerationProfile.ADVANCED_HARD
        var rejectionCount = 0
        var selected: GenerationResult.Generated? = null
        while (selected == null && seedCursor <= 399_999L) {
            val seed = seedCursor++
            when (val generated = generator.generate(
                GenerationRequest(
                    stableId = "campaign-${number.toString().padStart(3, '0')}",
                    sequenceNumber = number,
                    title = titleFor(number),
                    seed = seed,
                    profile = profile,
                    packId = packFor(number),
                ),
            )) {
                is GenerationResult.Generated -> {
                    rejectionCount += generated.rejectedReasons.values.sum()
                    val fingerprint = ContentFingerprint.symmetryNormalized(generated.level)
                    val layoutKey = nearDuplicateKey(generated.level)
                    if (fingerprint !in fingerprints && layoutKey !in layoutKeys) {
                        fingerprints += fingerprint
                        layoutKeys += layoutKey
                        selected = generated
                    } else {
                        rejectionCount += 1
                    }
                }
                is GenerationResult.Exhausted -> rejectionCount += generated.rejectedReasons.values.sum() + 1
            }
        }
        val generated = checkNotNull(selected) { "Bounded generation exhausted for campaign level $number" }
        accepted += generated.level
        rows += ReportRow(generated.level, generated.metrics, generated.attemptsUsed, rejectionCount)
    }
    return CatalogBuild(
        catalog = LevelCatalog(2, RULE_VERSION, "magnetrail-campaign-v3", accepted, CONTENT_VERSION, GENERATOR_VERSION),
        rows = rows,
        rejectedCandidates = rows.sumOf { it.rejectedBeforeAcceptance },
    )
}

private fun buildFallbacks(templates: List<LevelDefinition>): CatalogBuild {
    val generator = LevelGenerator(templates)
    val accepted = mutableListOf<LevelDefinition>()
    val rows = mutableListOf<ReportRow>()
    val fingerprints = mutableSetOf<String>()
    var seed = 880_001L
    while (accepted.size < 7 && seed < 881_001L) {
        val index = accepted.size + 1
        when (val generated = generator.generate(
            GenerationRequest(
                stableId = "daily-fallback-${index.toString().padStart(2, '0')}",
                sequenceNumber = index,
                title = "Daily fallback $index",
                seed = seed,
                profile = GenerationProfile.DAILY_CHALLENGE,
                packId = "daily-fallback",
            ),
        )) {
            is GenerationResult.Generated -> if (fingerprints.add(ContentFingerprint.symmetryNormalized(generated.level))) {
                accepted += generated.level
                rows += ReportRow(generated.level, generated.metrics, generated.attemptsUsed, 0)
            }
            is GenerationResult.Exhausted -> Unit
        }
        seed += 1
    }
    check(accepted.size == 7) { "Could not create seven unique daily fallbacks" }
    return CatalogBuild(
        LevelCatalog(2, RULE_VERSION, "magnetrail-daily-fallback-v1", accepted, CONTENT_VERSION, GENERATOR_VERSION),
        rows,
        0,
    )
}

private fun certifyShipped(options: Map<String, String>) {
    val campaign = parse(File(options.required("campaign")))
    val fallbacks = parse(File(options.required("fallbacks")))
    validateCatalog(campaign)
    validateCatalog(fallbacks, dailyFallback = true)
    check(campaign.levels.size >= 100) { "Campaign has only ${campaign.levels.size} levels" }
    check(fallbacks.levels.isNotEmpty()) { "Daily fallback bank is empty" }
    println(
        "Certified ${campaign.levels.size} campaign levels and ${fallbacks.levels.size} daily fallbacks " +
            "with production engine/solver (solution cap $CERTIFICATION_SOLUTION_LIMIT).",
    )
}

private fun validateCatalog(catalog: LevelCatalog, dailyFallback: Boolean = false) {
    val fingerprints = mutableSetOf<String>()
    val symmetryFingerprints = mutableSetOf<String>()
    val pipeline = CertificationPipeline()
    val pipelineV5 = CertificationPipelineV5()
    catalog.levels.forEach { level ->
        val metadata = requireNotNull(level.metadata) { "${level.id} has no M3 metadata" }
        check(fingerprints.add(metadata.contentFingerprint)) { "Duplicate fingerprint ${metadata.contentFingerprint}" }
        check(symmetryFingerprints.add(ContentFingerprint.symmetryNormalized(level))) {
            "Symmetry duplicate ${level.id}"
        }
        check(metadata.contentFingerprint == ContentFingerprint.of(level)) { "Stale fingerprint for ${level.id}" }
        if (!dailyFallback && metadata.generatorVersion == GENERATOR_VERSION_V5) {
            val profile = (
                GenerationProfilesV5.productionCandidateProfiles +
                    GenerationProfilesD21.benchmarkProfiles +
                    GenerationProfilesCampaignV9.highBands
                )
                .distinctBy { it.id }
                .singleOrNull {
                it.id == metadata.generationProfile
            } ?: error("Unknown V5 profile '${metadata.generationProfile}' for ${level.id}")
            val result = pipelineV5.certify(
                level = level.copy(metadata = null),
                profile = profile,
                seed = requireNotNull(metadata.generatorSeed),
                packId = metadata.packId,
                contentVersion = metadata.contentVersion,
                previousContentFingerprint = metadata.previousContentFingerprint,
            )
            val accepted = result as? CertificationResultV5.Accepted
            if (accepted != null) {
                check(accepted.level.metadata == metadata) { "V5 certification metadata mismatch for ${level.id}" }
                check(accepted.level.designedSolutions == level.designedSolutions) {
                    "V5 certified solution mismatch for ${level.id}"
                }
            } else {
                val rejected = result as CertificationResultV5.Rejected
                val isRecordedV51ExpertWaiver = level.id == "campaign-205" &&
                    metadata.contentFingerprint ==
                    "sha256:8202008ccb4f0fa3488b60883061574998f6426f3d7f5a53193145974cb3026d" &&
                    rejected.reasons.toSet() == setOf(
                        "interaction-density-out-of-profile",
                        "object-participation-below-profile",
                        "interacting-object-ratio-below-profile",
                        "average-object-relevance-below-profile",
                        "participating-wall-ratio-below-profile",
                    )
                check(isRecordedV51ExpertWaiver) {
                    "V5 certification rejected ${level.id}: ${rejected.reasons.joinToString()}"
                }
                println("${level.id}: solver/V4 complete; explicit owner waiver retained for ${rejected.reasons}")
            }
        } else {
            val profile = if (dailyFallback) GenerationProfile.DAILY_CHALLENGE else profileFor(level)
            val result = pipeline.certify(
                level.copy(metadata = null),
                CertificationRequest(
                    profile = profile,
                    origin = metadata.origin,
                    packId = metadata.packId,
                    generatorVersion = metadata.generatorVersion,
                    generatorSeed = metadata.generatorSeed,
                    generationProfile = metadata.generationProfile,
                    contentVersion = metadata.contentVersion,
                    previousContentFingerprint = metadata.previousContentFingerprint,
                ),
            ).requireAccepted(level.id)
            check(result.level.metadata == metadata) { "Certification metadata mismatch for ${level.id}" }
        }
    }
}

private fun profileFor(level: LevelDefinition): GenerationProfile {
    level.metadata?.generationProfile?.let { profileId ->
        return GenerationProfile.entries.single { it.profileId == profileId }
    }
    return when {
        level.number <= 9 || level.arrows.size == 1 -> GenerationProfile.INTRO_EASY
        level.arrows.size >= 4 || level.metadata?.difficultyBand == DifficultyBand.ADVANCED ->
            GenerationProfile.ADVANCED_HARD
        else -> GenerationProfile.DEVELOPING_MEDIUM
    }
}

private fun packFor(number: Int): String = when (number) {
    in 1..12 -> "field-basics"
    in 13..30 -> "polarity-workshop"
    in 31..50 -> "hidden-lines"
    in 51..70 -> "competing-fields"
    in 71..85 -> "order-lab"
    else -> "master-rail"
}

private fun titleFor(number: Int): String {
    val stems = listOf("Rail", "Field", "Relay", "Vector", "Polarity", "Cadence", "Circuit", "Alignment")
    return "${stems[(number - 31) % stems.size]} study ${number.toString().padStart(3, '0')}"
}

/** Rejects candidates that differ from an accepted board only by wall dressing. */
private fun nearDuplicateKey(level: LevelDefinition): String =
    ContentFingerprint.symmetryNormalized(level.copy(walls = emptyList()))

private fun csvReport(rows: List<ReportRow>): String = buildString {
    appendLine(
        "level_id,sequence,origin,pack,board,difficulty,primary_mechanic,generator_version,seed," +
            "profile,solution_length,solution_count,solution_capped,valid_first_actions,explored_states," +
            "average_branching,magnet_actions,polarity_flips,difficulty_score,fingerprint,attempts,rejected_before_acceptance",
    )
    rows.forEach { row ->
        val level = row.level
        val metadata = requireNotNull(level.metadata)
        appendLine(
            listOf(
                level.id,
                level.number,
                metadata.origin,
                metadata.packId,
                "${level.width}x${level.height}",
                metadata.difficultyBand,
                metadata.mechanicTags.first(),
                metadata.generatorVersion ?: "authored",
                metadata.generatorSeed ?: "authored",
                metadata.generationProfile ?: "handcrafted",
                row.metrics.solutionLength,
                row.metrics.solutionCount,
                row.metrics.solutionCountCapped,
                row.metrics.validFirstActionCount,
                row.metrics.exploredStateCount,
                "%.2f".format(java.util.Locale.ROOT, row.metrics.averageBranching),
                row.metrics.magnetControlledActions,
                row.metrics.polarityFlips,
                DifficultyScorer.score(row.metrics).score,
                metadata.contentFingerprint,
                row.attempts,
                row.rejectedBeforeAcceptance,
            ).joinToString(","),
        )
    }
}

private fun summaryReport(campaign: CatalogBuild, fallbacks: CatalogBuild): String {
    val levels = campaign.catalog.levels
    val byOrigin = levels.groupingBy { requireNotNull(it.metadata).origin }.eachCount()
    val byDifficulty = levels.groupingBy { requireNotNull(it.metadata).difficultyBand }.eachCount()
    val byBoard = levels.groupingBy { "${it.width}x${it.height}" }.eachCount().toSortedMap()
    val byPack = levels.groupingBy { requireNotNull(it.metadata).packId }.eachCount()
    val explored = levels.map { requireNotNull(it.metadata).exploredStateCount }
    val solutions = levels.map { requireNotNull(it.metadata).solutionCount }
    val seeds = levels.mapNotNull { it.metadata?.generatorSeed }
    return """# Magnetrail M3 content report

Generated by `:level-tools:promoteCampaign` and verified by `certifyCampaignContent`.

- Campaign count: ${levels.size}
- Origin split: $byOrigin
- Difficulty split: $byDifficulty
- Pack split: $byPack
- Board-size split: $byBoard
- Generator version: $GENERATOR_VERSION; generator-assisted seed range: ${seeds.minOrNull()}..${seeds.maxOrNull()}
- Certified solution length range: ${levels.minOf { it.arrows.size }}..${levels.maxOf { it.arrows.size }}
- Capped solution-count range: ${solutions.minOrNull()}..${solutions.maxOrNull()} (cap $CERTIFICATION_SOLUTION_LIMIT)
- Solver explored-state range: ${explored.minOrNull()}..${explored.maxOrNull()}
- Rejected attempts before final unique selections: ${campaign.rejectedCandidates}
- Daily fallback count: ${fallbacks.catalog.levels.size}

Every row in `M3_CONTENT_REPORT.csv` records origin, board size, band, primary mechanic,
seed/profile, solution and branching metrics, solver work, fingerprint, and rejection count.
The original 12 IDs and order are preserved. Levels 13–30 are intentionally hand-tuned
template extensions; levels 31–100 are generator-assisted candidates reviewed through
the checked-in report and acceptance gates. Exact fingerprints and layouts that differ
only by wall dressing are rejected as duplicates.

Normal builds never rewrite content. `generateLevelCandidates` writes only under
`level-tools/build/m3-staging`; promotion is a separate confirmation-gated task.
"""
}

private fun parse(file: File): LevelCatalog = LevelParser().parseCatalog(file.readText())

private fun Map<String, String>.required(key: String): String =
    requireNotNull(this[key]) { "Missing --$key=..." }

private fun CertificationResult.requireAccepted(id: String): CertificationResult.Accepted = when (this) {
    is CertificationResult.Accepted -> this
    is CertificationResult.Rejected -> error("Certification rejected $id: ${reasons.joinToString()}")
}

private fun MutableMap<String, Int>.increment(key: String, amount: Int = 1) {
    this[key] = (this[key] ?: 0) + amount
}

private data class ReportRow(
    val level: LevelDefinition,
    val metrics: DifficultyMetrics,
    val attempts: Int,
    val rejectedBeforeAcceptance: Int,
)

private data class CatalogBuild(
    val catalog: LevelCatalog,
    val rows: List<ReportRow>,
    val rejectedCandidates: Int,
)

private const val RULE_VERSION = "magnetrail-core-1"
