package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v5.D2_STAGING_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.v5.CertificationPipelineV5
import com.rameshta.magnetrail.core.generation.v5.CertificationResultV5
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesV5
import com.rameshta.magnetrail.core.generation.v5.GenerationRequestV5
import com.rameshta.magnetrail.core.generation.v5.GenerationResultV5
import com.rameshta.magnetrail.core.generation.v5.LevelGeneratorV5
import com.rameshta.magnetrail.core.generation.v5.StructuralDiagnosticsV5
import com.rameshta.magnetrail.core.generation.v5.StructuralDifficultyBandV5
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.level.LevelValidation
import com.rameshta.magnetrail.core.model.LevelDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

private const val D2_REPORT_VERSION = "magnetrail-d2-generation-audit-1"
private const val D2_PROMOTION_MANIFEST_VERSION = "magnetrail-d2-promotion-manifest-1"

@Serializable
data class D2CandidateDiagnostic(
    val levelId: String,
    val number: Int,
    val title: String,
    val seed: Long,
    val attemptsUsed: Int,
    val qualityScore: Int,
    val qualityStatus: String,
    val qualityReasons: List<String>,
    val pacingStatus: String,
    val certified: Boolean,
    val diagnostics: StructuralDiagnosticsV5,
)

@Serializable
data class D2GenerationAggregate(
    val candidateCount: Int,
    val certifiedCount: Int,
    val rejectedAttemptCount: Int,
    val truncatedCandidateCount: Int,
    val difficultyDistribution: Map<String, Int>,
    val gridDistribution: Map<String, Int>,
    val averageSafeChoiceRatio: Double,
    val averageMeaningfulFailureRate: Double,
    val averageHarmfulDecisionDensity: Double,
    val averageObjectRelevance: Double,
    val averageRelevantObjectRatio: Double,
    val averageInteractionDensity: Double,
    val averageDependencyDepth: Double,
    val averagePolarityImpactDepth: Double,
    val cancellationRelevantLevelRate: Double,
    val averageOrderingDepth: Double,
    val averageConsequenceDepth: Double,
    val greedySolvedLevelRate: Double,
    val averageRandomSuccessRate: Double,
    val totalStrategyFamilyCount: Int,
    val averagePermutationRedundancy: Double,
)

@Serializable
data class D2ComparisonValue(
    val oldCampaign: String,
    val d2Candidates: String,
    val status: String,
)

@Serializable
data class D2CampaignGenerationAudit(
    val schemaVersion: Int = 1,
    val reportVersion: String = D2_REPORT_VERSION,
    val generatorVersion: Int = GENERATOR_VERSION_V5,
    val stagingContentVersion: Int = D2_STAGING_CONTENT_VERSION,
    val sourceCampaignPath: String,
    val sourceCampaignSha256: String,
    val sourceCampaignLevelCount: Int,
    val sourceCampaignChangedByTask: Boolean = false,
    val aggregate: D2GenerationAggregate,
    val structuralComparison: Map<String, D2ComparisonValue>,
    val rejectionReasons: Map<String, Int>,
    val levels: List<D2CandidateDiagnostic>,
    val limitations: List<String>,
)

@Serializable
data class D2CalibrationRating(
    val reviewId: String,
    val stagedLevelId: String,
    val sampleGroups: List<String>,
    val humanRating: Int? = null,
    val comments: String? = null,
)

@Serializable
data class D2CalibrationReport(
    val schemaVersion: Int = 1,
    val status: String = "AWAITING_PROJECT_OWNER_RATINGS",
    val ratingScale: Map<Int, String>,
    val blindCatalogPath: String,
    val ratingsAvailable: Int = 0,
    val pearson: Double? = null,
    val spearman: Double? = null,
    val meanAbsoluteError: Double? = null,
    val confidence: String = "NOT MEASURABLE WITH CURRENT IMPLEMENTATION",
    val levels: List<D2CalibrationRating>,
)

@Serializable
data class D2PromotionDecision(
    val currentLevelId: String,
    val proposedCandidateId: String?,
    val decision: String,
    val reason: String,
)

@Serializable
data class D2PromotionManifest(
    val schemaVersion: Int = 1,
    val manifestVersion: String = D2_PROMOTION_MANIFEST_VERSION,
    val status: String = "BLOCKED_PENDING_HUMAN_REVIEW_AND_MIGRATION_PROOF",
    val campaignModified: Boolean = false,
    val promotionAllowed: Boolean = false,
    val migrationProvenSafe: Boolean = false,
    val existingIdsReused: Boolean = false,
    val keep: List<D2PromotionDecision>,
    val tune: List<D2PromotionDecision>,
    val replace: List<D2PromotionDecision>,
    val requiredBeforePromotion: List<String>,
)

@Serializable
private data class D2GenerationRun(
    val candidateCatalog: String,
    val sourceCampaignSha256Before: String,
    val sourceCampaignSha256After: String,
    val sourceCampaignChanged: Boolean,
    val normalBuildRegeneratesContent: Boolean,
)

fun generateD2CampaignV5(options: Map<String, String>) {
    val campaignFile = File(options.requiredD2("campaign"))
    val output = File(options.requiredD2("output")).also { it.mkdirs() }
    val staging = File(options.requiredD2("staging-output")).also { it.mkdirs() }
    val count = options["count"]?.toInt() ?: 200
    val initialSeed = options["seed"]?.toLong() ?: 5_200_001L
    val attemptsOverride = options["attempts-per-candidate"]?.toInt()
    require(count > 0 && initialSeed != 0L)
    val sourceBytesBefore = campaignFile.readBytes()
    val source = LevelParser().parseCatalog(sourceBytesBefore.decodeToString())
    check(source.levels.size == 200) { "D2 expects the frozen 200-level source campaign" }

    val generator = LevelGeneratorV5()
    val accepted = mutableListOf<LevelDefinition>()
    val diagnostics = mutableListOf<D2CandidateDiagnostic>()
    val rejectionReasons = linkedMapOf<String, Int>()
    var rejectedCandidateAttempts = 0
    val bands = targetBands(count)
    bands.forEachIndexed { index, band ->
        val number = index + 1
        val profile = GenerationProfilesV5.forBand(band)
        val seed = initialSeed + number * 1_000_003L
        val request = GenerationRequestV5(
            stableId = "d2-v5-${band.name.lowercase()}-${number.toString().padStart(3, '0')}",
            sequenceNumber = number,
            title = "D2 blind candidate ${number.toString().padStart(3, '0')}",
            seed = seed,
            profile = profile,
            maxAttempts = attemptsOverride ?: profile.candidateAttemptCap,
        )
        var selected: GenerationResultV5.Generated? = null
        repeat(32) { duplicateRetry ->
            if (selected != null) return@repeat
            when (val result = generator.generate(request.copy(seed = seed + duplicateRetry * 97_000_021L))) {
                is GenerationResultV5.Generated -> {
                    rejectedCandidateAttempts += result.attemptsUsed - 1
                    result.rejectedReasons.forEach { (reason, amount) -> rejectionReasons.incrementD2(reason, amount) }
                    val exact = result.level.metadata?.contentFingerprint ?: ContentFingerprint.of(result.level)
                    val symmetry = ContentFingerprint.symmetryNormalized(result.level)
                    when {
                        accepted.any { it.metadata?.contentFingerprint == exact } ->
                            rejectionReasons.incrementD2("duplicate-exact-fingerprint", 1).also {
                                rejectedCandidateAttempts += 1
                            }
                        accepted.any { ContentFingerprint.symmetryNormalized(it) == symmetry } ->
                            rejectionReasons.incrementD2("duplicate-symmetry-fingerprint", 1).also {
                                rejectedCandidateAttempts += 1
                            }
                        else -> selected = result
                    }
                }
                is GenerationResultV5.Exhausted -> {
                    rejectedCandidateAttempts += result.attemptsUsed
                    result.rejectedReasons.forEach { (reason, amount) -> rejectionReasons.incrementD2(reason, amount) }
                }
            }
        }
        val result = selected ?: error(
            "D2 generation exhausted for candidate $number (${band.name}) including duplicate retries; " +
                "top reasons=${rejectionReasons.entries.sortedByDescending { it.value }.take(8)}",
        )
        run {
                val exact = result.level.metadata?.contentFingerprint ?: ContentFingerprint.of(result.level)
                check(accepted.none { it.metadata?.contentFingerprint == exact })
                accepted += result.level
                diagnostics += D2CandidateDiagnostic(
                    levelId = result.level.id,
                    number = result.level.number,
                    title = result.level.title,
                    seed = requireNotNull(result.level.metadata?.generatorSeed),
                    attemptsUsed = result.attemptsUsed,
                    qualityScore = result.quality.score,
                    qualityStatus = result.quality.status.name,
                    qualityReasons = result.quality.reasonCodes,
                    pacingStatus = pacingStatus(result.diagnostics),
                    certified = true,
                    diagnostics = result.diagnostics,
                )
                println("D2 ${accepted.size}/$count ${band.name}: ${result.level.id} in ${result.attemptsUsed} attempts")
        }
    }
    check(accepted.size == count)
    val exacts = accepted.map { requireNotNull(it.metadata).contentFingerprint }
    check(exacts.size == exacts.toSet().size)
    val symmetries = accepted.map(ContentFingerprint::symmetryNormalized)
    check(symmetries.size == symmetries.toSet().size)

    val candidateCatalog = LevelCatalog(
        schemaVersion = LevelValidation.M3_SCHEMA_VERSION,
        ruleVersion = LevelValidation.SUPPORTED_RULE_VERSION,
        catalogId = "magnetrail-d2-v5-staging",
        levels = accepted,
        contentVersion = D2_STAGING_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
    )
    val catalogFile = File(staging, "D2_CAMPAIGN_V5_CANDIDATES.json")
    catalogFile.writeText(LevelParser().encodeCatalog(candidateCatalog))
    check(LevelParser().parseCatalog(catalogFile.readText()).levels.size == count)

    val aggregate = aggregate(diagnostics, rejectedCandidateAttempts)
    val oldAggregate = readOldV4Aggregate(options["difficulty-v4-audit"]?.let(::File))
    val comparison = comparison(oldAggregate, aggregate)
    val audit = D2CampaignGenerationAudit(
        sourceCampaignPath = campaignFile.path,
        sourceCampaignSha256 = sha256(sourceBytesBefore),
        sourceCampaignLevelCount = source.levels.size,
        aggregate = aggregate,
        structuralComparison = comparison,
        rejectionReasons = rejectionReasons.toSortedMap(),
        levels = diagnostics,
        limitations = listOf(
            "Human ratings are not available for D2 candidates; automated certification is not human approval.",
            "Existing-player progress migration is NOT PROVEN; promotion remains blocked.",
            "9x9 is experimental and excluded from the staging campaign pending separate usability approval.",
            "Human-perceived obviousness and visual fairness are NOT MEASURABLE WITH CURRENT IMPLEMENTATION.",
        ),
    )
    File(output, "D2_CAMPAIGN_GENERATION_AUDIT.json").writeText(D2_JSON.encodeToString(audit))
    File(output, "D2_CAMPAIGN_GENERATION_AUDIT.md").writeText(auditMarkdown(audit))
    File(output, "D2_LEVEL_DIAGNOSTICS.csv").writeText(levelCsv(diagnostics))
    File(output, "D2_OBJECT_RELEVANCE.csv").writeText(objectCsv(diagnostics))
    File(output, "D2_INTERACTION_GRAPH.csv").writeText(interactionCsv(diagnostics))

    val calibration = buildCalibration(diagnostics)
    File(output, "D2_CALIBRATION.json").writeText(D2_JSON.encodeToString(calibration))
    File(output, "D2_CALIBRATION.md").writeText(calibrationMarkdown(calibration))
    val reviewLevels = calibration.levels.mapNotNull { row -> accepted.firstOrNull { it.id == row.stagedLevelId } }
        .mapIndexed { index, level ->
            level.copy(
                id = "d2-review-${(index + 1).toString().padStart(3, '0')}",
                number = index + 1,
                title = "Review puzzle ${(index + 1).toString().padStart(3, '0')}",
                metadata = null,
            )
        }
    val blindCatalog = LevelCatalog(
        schemaVersion = LevelValidation.SUPPORTED_SCHEMA_VERSION,
        ruleVersion = LevelValidation.SUPPORTED_RULE_VERSION,
        catalogId = "magnetrail-d2-blind-review",
        levels = reviewLevels,
    )
    File(staging, "D2_HUMAN_REVIEW_CATALOG.json").writeText(LevelParser().encodeCatalog(blindCatalog))

    val manifest = promotionManifest(source.levels, diagnostics)
    File(output, "D2_PROMOTION_MANIFEST.json").writeText(D2_JSON.encodeToString(manifest))
    File(staging, "D2_GENERATION_RUN.json").writeText(
        D2_JSON.encodeToString(
            D2GenerationRun(
                candidateCatalog = "docs/content/d2/staging/D2_CAMPAIGN_V5_CANDIDATES.json",
                sourceCampaignSha256Before = sha256(sourceBytesBefore),
                sourceCampaignSha256After = sha256(campaignFile.readBytes()),
                sourceCampaignChanged = !sourceBytesBefore.contentEquals(campaignFile.readBytes()),
                normalBuildRegeneratesContent = false,
            ),
        ),
    )
    check(sourceBytesBefore.contentEquals(campaignFile.readBytes())) {
        "Safety violation: D2 changed the production campaign"
    }
    println("Generated $count D2 staging candidates. Production campaign SHA-256 remained ${sha256(sourceBytesBefore)}")
}

fun analyzeD2Artifacts(options: Map<String, String>, mode: String) {
    val auditFile = File(options.requiredD2("audit"))
    val audit = D2_JSON.decodeFromString<D2CampaignGenerationAudit>(auditFile.readText())
    check(audit.levels.isNotEmpty() && audit.aggregate.certifiedCount == audit.aggregate.candidateCount)
    when (mode) {
        "generation" -> check(audit.levels.all { it.certified && !it.diagnostics.truncated })
        "objects" -> check(audit.levels.all { it.diagnostics.objectRelevance.analysisComplete })
        "graphs" -> check(audit.levels.all { it.diagnostics.interactionGraph.nodes.isNotEmpty() })
        else -> error("Unknown D2 artifact analysis mode $mode")
    }
    println("D2 $mode analysis PASS: ${audit.levels.size} certified staging candidates")
}

fun refreshD2CampaignV5(options: Map<String, String>) {
    val campaignFile = File(options.requiredD2("campaign"))
    val campaignBytes = campaignFile.readBytes()
    val catalogFile = File(options.requiredD2("catalog"))
    val auditFile = File(options.requiredD2("audit"))
    val output = File(options.requiredD2("output")).also { it.mkdirs() }
    val catalog = LevelParser().parseCatalog(catalogFile.readText())
    val previous = D2_JSON.decodeFromString<D2CampaignGenerationAudit>(auditFile.readText())
    check(catalog.levels.size == previous.levels.size)
    val oldRows = previous.levels.associateBy { it.levelId }
    val certification = CertificationPipelineV5()
    val refreshed = catalog.levels.mapIndexed { index, level ->
        val metadata = requireNotNull(level.metadata)
        val profile = GenerationProfilesV5.productionCandidateProfiles.single {
            it.id == metadata.generationProfile
        }
        val result = certification.certify(
            level.copy(metadata = null),
            profile,
            requireNotNull(metadata.generatorSeed),
            metadata.packId,
        )
        val accepted = result as? CertificationResultV5.Accepted ?: error(
            "Previously accepted D2 candidate ${level.id} no longer certifies: " +
                (result as CertificationResultV5.Rejected).reasons,
        )
        val old = requireNotNull(oldRows[level.id])
        println("Refreshed D2 diagnostics ${index + 1}/${catalog.levels.size}: ${level.id}")
        old.copy(
            qualityScore = accepted.quality.score,
            qualityStatus = accepted.quality.status.name,
            qualityReasons = accepted.quality.reasonCodes,
            pacingStatus = pacingStatus(accepted.diagnostics),
            diagnostics = accepted.diagnostics,
        )
    }
    val aggregate = aggregate(refreshed, refreshed.sumOf { it.attemptsUsed - 1 })
    val oldAggregate = readOldV4Aggregate(options["difficulty-v4-audit"]?.let(::File))
    val audit = previous.copy(
        sourceCampaignSha256 = sha256(campaignBytes),
        aggregate = aggregate,
        structuralComparison = comparison(oldAggregate, aggregate),
        levels = refreshed,
    )
    auditFile.writeText(D2_JSON.encodeToString(audit))
    File(output, "D2_CAMPAIGN_GENERATION_AUDIT.md").writeText(auditMarkdown(audit))
    File(output, "D2_LEVEL_DIAGNOSTICS.csv").writeText(levelCsv(refreshed))
    File(output, "D2_OBJECT_RELEVANCE.csv").writeText(objectCsv(refreshed))
    File(output, "D2_INTERACTION_GRAPH.csv").writeText(interactionCsv(refreshed))
    check(campaignBytes.contentEquals(campaignFile.readBytes()))
    println("Refreshed ${refreshed.size} D2 candidates without modifying either campaign catalog.")
}

private fun targetBands(count: Int): List<StructuralDifficultyBandV5> {
    val base = linkedMapOf(
        StructuralDifficultyBandV5.TUTORIAL to 12,
        StructuralDifficultyBandV5.EASY to 35,
        StructuralDifficultyBandV5.MEDIUM to 45,
        StructuralDifficultyBandV5.HARD to 55,
        StructuralDifficultyBandV5.EXPERT to 40,
        StructuralDifficultyBandV5.MASTER to 13,
    )
    if (count != 200) {
        val pattern = listOf(
            StructuralDifficultyBandV5.TUTORIAL,
            StructuralDifficultyBandV5.EASY,
            StructuralDifficultyBandV5.MEDIUM,
            StructuralDifficultyBandV5.HARD,
            StructuralDifficultyBandV5.EXPERT,
            StructuralDifficultyBandV5.MASTER,
        )
        return List(count) { pattern[it % pattern.size] }
    }
    val remaining = base.toMutableMap()
    val pattern = listOf(
        StructuralDifficultyBandV5.HARD,
        StructuralDifficultyBandV5.EASY,
        StructuralDifficultyBandV5.MEDIUM,
        StructuralDifficultyBandV5.EXPERT,
        StructuralDifficultyBandV5.HARD,
        StructuralDifficultyBandV5.MEDIUM,
        StructuralDifficultyBandV5.MASTER,
        StructuralDifficultyBandV5.EASY,
    )
    val result = mutableListOf<StructuralDifficultyBandV5>()
    repeat(12) {
        result += StructuralDifficultyBandV5.TUTORIAL
        remaining[StructuralDifficultyBandV5.TUTORIAL] = remaining.getValue(StructuralDifficultyBandV5.TUTORIAL) - 1
    }
    var cursor = 0
    while (result.size < count) {
        val preferred = pattern[cursor++ % pattern.size]
        val selected = if (remaining.getValue(preferred) > 0) preferred
        else remaining.entries.filter { it.value > 0 }.maxBy { it.value }.key
        result += selected
        remaining[selected] = remaining.getValue(selected) - 1
    }
    return result
}

private fun aggregate(rows: List<D2CandidateDiagnostic>, rejected: Int): D2GenerationAggregate =
    D2GenerationAggregate(
        candidateCount = rows.size,
        certifiedCount = rows.count { it.certified },
        rejectedAttemptCount = rejected,
        truncatedCandidateCount = rows.count { it.diagnostics.truncated },
        difficultyDistribution = rows.groupingBy { it.diagnostics.difficultyBand.name }.eachCount().toSortedMap(),
        gridDistribution = rows.groupingBy { "${it.diagnostics.columns}x${it.diagnostics.rows}" }.eachCount().toSortedMap(),
        averageSafeChoiceRatio = average(rows) { it.diagnostics.safeChoiceRatio },
        averageMeaningfulFailureRate = average(rows) { it.diagnostics.meaningfulFailureRate },
        averageHarmfulDecisionDensity = average(rows) { it.diagnostics.harmfulDecisionDensity },
        averageObjectRelevance = average(rows) { it.diagnostics.objectRelevance.averageScore },
        averageRelevantObjectRatio = average(rows) { it.diagnostics.objectRelevance.relevantObjectRatio },
        averageInteractionDensity = average(rows) { it.diagnostics.interactionGraph.interactionDensity },
        averageDependencyDepth = average(rows) { it.diagnostics.dependencyDepth.toDouble() },
        averagePolarityImpactDepth = average(rows) { it.diagnostics.polarityImpactDepth.toDouble() },
        cancellationRelevantLevelRate = ratio(rows.count { it.diagnostics.cancellationCriticalDecisionCount > 0 }, rows.size),
        averageOrderingDepth = average(rows) { it.diagnostics.mandatoryOrderingDepth.toDouble() },
        averageConsequenceDepth = average(rows) { it.diagnostics.consequenceDepth.toDouble() },
        greedySolvedLevelRate = average(rows) { it.diagnostics.greedySolveRate },
        averageRandomSuccessRate = average(rows) { it.diagnostics.randomSuccessRate },
        totalStrategyFamilyCount = rows.sumOf { it.diagnostics.commutationQuotient ?: 0 },
        averagePermutationRedundancy = average(rows) { it.diagnostics.permutationRedundancy ?: 0.0 },
    )

private fun readOldV4Aggregate(file: File?): JsonObject? = file?.takeIf(File::exists)?.let {
    D2_JSON.parseToJsonElement(it.readText()).jsonObject["aggregate"]?.jsonObject
}

private fun comparison(old: JsonObject?, new: D2GenerationAggregate): Map<String, D2ComparisonValue> {
    fun oldValue(key: String): String = old?.get(key)?.jsonPrimitive?.content
        ?: "NOT MEASURABLE WITH CURRENT IMPLEMENTATION"
    fun measured(oldKey: String, value: Double) = D2ComparisonValue(oldValue(oldKey), format(value), "MEASURED")
    fun unavailable(value: Double) = D2ComparisonValue(
        "NOT MEASURABLE WITH CURRENT IMPLEMENTATION",
        format(value),
        "NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE",
    )
    return linkedMapOf(
        "meaningfulFailureRate" to measured("aggregateMeaningfulFailureRate", new.averageMeaningfulFailureRate),
        "safeChoiceRatio" to measured("aggregateSafeChoiceRatio", new.averageSafeChoiceRatio),
        "harmfulDecisionDensity" to measured("aggregateHarmfulDecisionDensity", new.averageHarmfulDecisionDensity),
        "objectRelevance" to unavailable(new.averageRelevantObjectRatio),
        "interactionDensity" to unavailable(new.averageInteractionDensity),
        "dependencyDepth" to unavailable(new.averageDependencyDepth),
        "polarityImpactDepth" to unavailable(new.averagePolarityImpactDepth),
        "cancellationRelevance" to unavailable(new.cancellationRelevantLevelRate),
        "orderingDepth" to unavailable(new.averageOrderingDepth),
        "consequencePersistence" to unavailable(new.averageConsequenceDepth),
        "greedySolveRate" to D2ComparisonValue(
            old?.get("greedySolvedLevelCount")?.jsonPrimitive?.intOrNull?.let { format(it / 200.0) }
                ?: "NOT MEASURABLE WITH CURRENT IMPLEMENTATION",
            format(new.greedySolvedLevelRate), "MEASURED",
        ),
        "randomSuccessRate" to measured("averageRandomCompletionRate", new.averageRandomSuccessRate),
        "strategyFamilyCount" to D2ComparisonValue(
            oldValue("canonicalStrategies"), new.totalStrategyFamilyCount.toString(), "MEASURED",
        ),
        "permutationRedundancy" to measured("aggregatePermutationRedundancy", new.averagePermutationRedundancy),
    )
}

private fun buildCalibration(rows: List<D2CandidateDiagnostic>): D2CalibrationReport {
    val selected = linkedMapOf<String, MutableSet<String>>()
    fun add(levelId: String, group: String) { selected.getOrPut(levelId, ::linkedSetOf) += group }
    listOf(
        StructuralDifficultyBandV5.EASY to 10,
        StructuralDifficultyBandV5.MEDIUM to 10,
        StructuralDifficultyBandV5.HARD to 10,
        StructuralDifficultyBandV5.EXPERT to 10,
        StructuralDifficultyBandV5.MASTER to 5,
    ).forEach { (band, count) ->
        rows.filter { it.diagnostics.difficultyBand == band }
            .sortedWith(compareBy<D2CandidateDiagnostic> { it.diagnostics.v4Score ?: 0 }.thenBy { it.levelId })
            .takeSpread(count)
            .forEach { add(it.levelId, "${band.name.lowercase()}-calibration") }
    }
    rows.filter { it.diagnostics.difficultyBand == StructuralDifficultyBandV5.EASY && it.diagnostics.rows == 8 }
        .sortedBy { it.levelId }.take(10).forEach { add(it.levelId, "easy-large-grid-control") }
    rows.filter {
        it.diagnostics.difficultyBand.rank >= StructuralDifficultyBandV5.HARD.rank && it.diagnostics.rows <= 5
    }.sortedByDescending { it.diagnostics.harmfulDecisionDensity }.take(10)
        .forEach { add(it.levelId, "compact-hard-control") }
    return D2CalibrationReport(
        ratingScale = linkedMapOf(
            1 to "Trivial", 2 to "Very Easy", 3 to "Easy", 4 to "Moderate", 5 to "Challenging",
            6 to "Hard", 7 to "Very Hard", 8 to "Expert", 9 to "Extremely Difficult", 10 to "Master",
        ),
        blindCatalogPath = "docs/content/d2/staging/D2_HUMAN_REVIEW_CATALOG.json",
        levels = selected.entries.mapIndexed { index, (levelId, groups) ->
            D2CalibrationRating(
                reviewId = "d2-review-${(index + 1).toString().padStart(3, '0')}",
                stagedLevelId = levelId,
                sampleGroups = groups.toList().sorted(),
            )
        },
    )
}

private fun promotionManifest(
    current: List<LevelDefinition>,
    candidates: List<D2CandidateDiagnostic>,
): D2PromotionManifest {
    val replacements = current.zip(candidates).map { (old, candidate) ->
        D2PromotionDecision(
            currentLevelId = old.id,
            proposedCandidateId = candidate.levelId,
            decision = "REPLACE_PROPOSED_NOT_PROMOTED",
            reason = "V5 candidate is certified for staging; human rating and progress migration remain unresolved.",
        )
    }
    return D2PromotionManifest(
        keep = emptyList(),
        tune = emptyList(),
        replace = replacements,
        requiredBeforePromotion = listOf(
            "Project owner completes blind human calibration ratings.",
            "Old-vs-new player progress, stars, records, rewards, Daily state, settings, and economy migration is proven safe.",
            "Project owner explicitly approves the final KEEP/TUNE/REPLACE decisions.",
            "Promotion task verifies current campaign fingerprint and refuses silent overwrite.",
        ),
    )
}

private fun pacingStatus(diagnostic: StructuralDiagnosticsV5): String = when {
    diagnostic.truncated -> "REJECT_TRUNCATED"
    diagnostic.safeChoiceRatio > 0.90 && diagnostic.difficultyBand.rank >= 3 -> "REVIEW_TOO_SAFE"
    diagnostic.interactionGraph.isolatedObjects > 0 && diagnostic.difficultyBand.rank >= 3 -> "REVIEW_ISOLATED_OBJECTS"
    else -> "PASS_STRUCTURAL"
}

private fun auditMarkdown(audit: D2CampaignGenerationAudit): String = buildString {
    appendLine("# D2 Campaign Generation Audit")
    appendLine()
    appendLine("Status: **PASS FOR STAGING; PROMOTION BLOCKED**")
    appendLine()
    appendLine("The production campaign was not modified. Generator V5 produced ${audit.aggregate.candidateCount} " +
        "production-engine-certified candidates in an isolated catalog.")
    appendLine()
    appendLine("## Safety")
    appendLine()
    appendLine("- Source campaign SHA-256: `${audit.sourceCampaignSha256}`")
    appendLine("- Source level count: ${audit.sourceCampaignLevelCount}")
    appendLine("- Campaign changed by D2: **NO**")
    appendLine("- Runtime generation: **NO**")
    appendLine("- Automatic promotion: **NO**")
    appendLine()
    appendLine("## Candidate summary")
    appendLine()
    appendLine("- Generated/certified: ${audit.aggregate.certifiedCount}/${audit.aggregate.candidateCount}")
    appendLine("- Rejected attempts before acceptance: ${audit.aggregate.rejectedAttemptCount}")
    appendLine("- Truncated candidates: ${audit.aggregate.truncatedCandidateCount}")
    appendLine("- Difficulty distribution: ${audit.aggregate.difficultyDistribution}")
    appendLine("- Grid distribution: ${audit.aggregate.gridDistribution}")
    appendLine()
    appendLine("## Structural aggregate")
    appendLine()
    appendLine("| Metric | D2 candidates |")
    appendLine("|---|---:|")
    appendLine("| Safe-choice ratio | ${format(audit.aggregate.averageSafeChoiceRatio)} |")
    appendLine("| Meaningful failure rate | ${format(audit.aggregate.averageMeaningfulFailureRate)} |")
    appendLine("| Harmful decision density | ${format(audit.aggregate.averageHarmfulDecisionDensity)} |")
    appendLine("| Relevant-object ratio | ${format(audit.aggregate.averageRelevantObjectRatio)} |")
    appendLine("| Interaction density | ${format(audit.aggregate.averageInteractionDensity)} |")
    appendLine("| Dependency depth | ${format(audit.aggregate.averageDependencyDepth)} |")
    appendLine("| Polarity impact depth | ${format(audit.aggregate.averagePolarityImpactDepth)} |")
    appendLine("| Cancellation-relevant level rate | ${format(audit.aggregate.cancellationRelevantLevelRate)} |")
    appendLine("| Ordering depth | ${format(audit.aggregate.averageOrderingDepth)} |")
    appendLine("| Consequence depth | ${format(audit.aggregate.averageConsequenceDepth)} |")
    appendLine("| Greedy solved rate | ${format(audit.aggregate.greedySolvedLevelRate)} |")
    appendLine("| Random-success rate | ${format(audit.aggregate.averageRandomSuccessRate)} |")
    appendLine("| Permutation redundancy | ${format(audit.aggregate.averagePermutationRedundancy)} |")
    appendLine()
    appendLine("## Old versus new")
    appendLine()
    appendLine("| Metric | Old 200 | D2 | Status |")
    appendLine("|---|---:|---:|---|")
    audit.structuralComparison.forEach { (metric, value) ->
        appendLine("| $metric | ${value.oldCampaign} | ${value.d2Candidates} | ${value.status} |")
    }
    appendLine()
    appendLine("## Promotion decision")
    appendLine()
    appendLine("Automated certification is not human approval. Promotion is blocked until blind human ratings and " +
        "a safe progress-migration plan are approved.")
    appendLine()
    appendLine("## Limitations")
    appendLine()
    audit.limitations.forEach { appendLine("- $it") }
}

private fun calibrationMarkdown(report: D2CalibrationReport): String = buildString {
    appendLine("# D2 Blind Human Calibration")
    appendLine()
    appendLine("Status: **AWAITING ACTUAL PROJECT-OWNER RATINGS**")
    appendLine()
    appendLine("The isolated review catalog is `${report.blindCatalogPath}`. It is not included in a normal app " +
        "installation. Use it only through an explicitly enabled review harness; the automated band is " +
        "intentionally omitted. Record a 1–10 rating only after playing each puzzle.")
    appendLine()
    appendLine("| Review ID | Rating (1–10) | Comments |")
    appendLine("|---|---:|---|")
    report.levels.forEach { appendLine("| ${it.reviewId} |  |  |") }
    appendLine()
    appendLine("Correlation, MAE, and confidence: **NOT MEASURABLE WITH CURRENT IMPLEMENTATION** until actual " +
        "human ratings are supplied. No automated process will manufacture ratings or rewrite V4 weights.")
}

private fun levelCsv(rows: List<D2CandidateDiagnostic>): String = buildString {
    appendLine(
        "level_id,difficulty_band,grid_size,rows,columns,arrow_count,magnet_count,wall_count,object_density," +
            "interaction_density,magnetic_relationship_count,average_magnetic_distance,max_magnetic_distance," +
            "line_of_sight_interactions,arrow_vs_arrow_interactions,magnet_cancellation_count," +
            "polarity_dependent_decisions,polarity_impact_depth,dependency_edges,dependency_depth," +
            "ordering_constraints,meaningful_ordering_rate,safe_choice_ratio,meaningful_failure_rate," +
            "harmful_decision_density,recovery_pressure,consequence_depth,consequence_breadth,exposure_reveal_count," +
            "controller_changes,object_relevance_score,irrelevant_object_count,greedy_solve_rate," +
            "random_success_rate,canonical_strategies,commutation_quotient,permutation_redundancy,v4_score," +
            "v4_confidence,quality_score,solver_state_count,truncation_status,pacing_status,exact_fingerprint," +
            "symmetry_fingerprint,interaction_fingerprint,dependency_fingerprint",
    )
    rows.forEach { row ->
        val d = row.diagnostics
        appendLine(
            listOf(
                row.levelId, d.difficultyBand, "${d.columns}x${d.rows}", d.rows, d.columns, d.arrowCount,
                d.magnetCount, d.wallCount, d.objectDensity, d.interactionGraph.interactionDensity,
                d.magneticRelationshipCount, d.averageMagneticDistance, d.maximumMagneticDistance,
                d.lineOfSightInteractionCount, d.arrowVsArrowInteractionCount, d.magnetCancellationCount,
                d.polarityDependentDecisionCount, d.polarityImpactDepth, d.dependencyEdgeCount, d.dependencyDepth,
                d.orderingConstraintCount, d.meaningfulOrderingRate, d.safeChoiceRatio, d.meaningfulFailureRate,
                d.harmfulDecisionDensity, d.recoveryPressure, d.consequenceDepth, d.consequenceBreadth, d.exposureRevealCount,
                d.controllerChangeCount, d.objectRelevance.averageScore, d.objectRelevance.irrelevantObjectCount,
                d.greedySolveRate, d.randomSuccessRate, d.canonicalStrategyCount, d.commutationQuotient,
                d.permutationRedundancy, d.v4Score, d.v4Confidence, row.qualityScore, d.solverStateCount,
                if (d.truncated) "TRUNCATED" else "COMPLETE", row.pacingStatus, d.exactFingerprint,
                d.symmetryFingerprint, d.interactionFingerprint, d.dependencyFingerprint,
            ).joinToString(",") { csv(it) },
        )
    }
}

private fun objectCsv(rows: List<D2CandidateDiagnostic>): String = buildString {
    appendLine(
        "level_id,object_key,object_type,relevance_class,relevance_score,solvability_changed," +
            "winning_first_action_change,strategy_family_change,dependency_graph_change,polarity_transition_change," +
            "meaningful_decision_change,ordering_constraint_change,line_of_sight_change,cancellation_change," +
            "route_structure_change,analysis_complete",
    )
    rows.forEach { row -> row.diagnostics.objectRelevance.objects.forEach { objectRow ->
        appendLine(
            listOf(
                row.levelId, objectRow.objectKey, objectRow.objectType, objectRow.classification, objectRow.score,
                objectRow.solvabilityChanged, objectRow.winningFirstActionChange, objectRow.strategyFamilyChange,
                objectRow.dependencyGraphChange, objectRow.polarityTransitionChange,
                objectRow.meaningfulDecisionChange, objectRow.orderingConstraintChange,
                objectRow.lineOfSightChange, objectRow.cancellationChange, objectRow.routeStructureChange,
                objectRow.analysisComplete,
            ).joinToString(",") { csv(it) },
        )
    } }
}

private fun interactionCsv(rows: List<D2CandidateDiagnostic>): String = buildString {
    appendLine("level_id,source,target,interaction_type,interaction_density,connected_components,largest_component")
    rows.forEach { row -> row.diagnostics.interactionGraph.edges.forEach { edge ->
        appendLine(
            listOf(
                row.levelId, edge.source, edge.target, edge.type,
                row.diagnostics.interactionGraph.interactionDensity,
                row.diagnostics.interactionGraph.connectedComponents,
                row.diagnostics.interactionGraph.largestConnectedComponent,
            ).joinToString(",") { csv(it) },
        )
    } }
}

private fun <T> List<T>.takeSpread(count: Int): List<T> {
    if (size <= count) return this
    if (count <= 1) return take(count)
    return List(count) { index -> this[(index * lastIndex.toDouble() / (count - 1)).roundToInt()] }.distinct()
}

private fun average(rows: List<D2CandidateDiagnostic>, value: (D2CandidateDiagnostic) -> Double): Double =
    if (rows.isEmpty()) 0.0 else round4(rows.map(value).average())

private fun ratio(numerator: Int, denominator: Int): Double =
    if (denominator == 0) 0.0 else round4(numerator.toDouble() / denominator)

private fun round4(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0
private fun format(value: Double): String = "%.4f".format(java.util.Locale.ROOT, value)
private fun csv(value: Any?): String = "\"${value?.toString().orEmpty().replace("\"", "\"\"")}\""

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

private fun MutableMap<String, Int>.incrementD2(key: String, amount: Int) {
    this[key] = (this[key] ?: 0) + amount
}

private fun Map<String, String>.requiredD2(name: String): String =
    requireNotNull(this[name]) { "Missing --$name" }

private val D2_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}
