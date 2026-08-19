package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyGateReason
import com.rameshta.magnetrail.core.difficulty.DifficultyGateResult
import com.rameshta.magnetrail.core.difficulty.DifficultyScoreV3
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Config
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Gate
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Scorer
import com.rameshta.magnetrail.core.difficulty.HumanReviewPriorityFactors
import com.rameshta.magnetrail.core.difficulty.HumanReviewPriorityScore
import com.rameshta.magnetrail.core.difficulty.HumanReviewPriorityScorer
import com.rameshta.magnetrail.core.difficulty.Phase0DifficultyTargets
import com.rameshta.magnetrail.core.difficulty.PuzzleDifficultyTarget
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityAnalyzerV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityReasonV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityScoreV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityStatusV2
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchAnalyzer
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchConfig
import com.rameshta.magnetrail.core.generation.CertificationPipeline
import com.rameshta.magnetrail.core.generation.CertificationRequest
import com.rameshta.magnetrail.core.generation.CertificationResult
import com.rameshta.magnetrail.core.generation.GenerationProfile
import com.rameshta.magnetrail.core.generation.PHASE0_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.PHASE0_GENERATOR_VERSION
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelOrigin
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.abs

private data class Phase0InitialAnalysis(
    val level: LevelDefinition,
    val target: PuzzleDifficultyTarget,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
)

@Serializable
data class Phase0LevelDiagnostic(
    val levelId: String,
    val campaignNumber: Int,
    val contentVersion: Int,
    val width: Int,
    val height: Int,
    val arrowCount: Int,
    val magnetCount: Int,
    val wallCount: Int,
    val origin: String,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val geometricSimilaritySignature: String,
    val structuralPatternSignature: String,
    val structuralClusterId: String,
    val structuralSimilarityIds: List<String>,
    val target: PuzzleDifficultyTarget,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
    val quality: PuzzleQualityScoreV2,
    val proposedDecision: String,
    val decisionReasons: List<String>,
    val humanReviewPriority: HumanReviewPriorityScore,
    val manualReviewStatus: String = "PENDING",
)

@Serializable
data class Phase0RangeSummary(
    val displayedRange: String,
    val levelCount: Int,
    val averageDifficulty: Double,
    val medianDifficulty: Double,
    val minimumDifficulty: Int,
    val maximumDifficulty: Int,
    val difficultyBands: Map<String, Int>,
    val qualityStatuses: Map<String, Int>,
    val decisions: Map<String, Int>,
    val averageMinimumSolutionLength: Double,
    val averageMeaningfulDecisionPoints: Double,
    val averageEffectiveBranching: Double,
    val averageForcedMoveRatio: Double,
    val averageDependencyDepth: Double,
    val averageGuessDependentChoices: Double,
    val averageRawEmptySpaceRatio: Double,
    val averageUnusedEmptySpaceRatio: Double,
    val averageIrrelevantEntityRatio: Double,
    val outlierLevelIds: List<String>,
)

@Serializable
data class Phase0AnalysisReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "phase0-analysis-v1",
    val catalogId: String,
    val catalogSchemaVersion: Int,
    val contentVersion: Int,
    val generatorVersion: Int?,
    val searchConfig: PuzzleSearchConfig,
    val difficultyConfig: DifficultyV3Config,
    val levels: List<Phase0LevelDiagnostic>,
    val rangeSummaries: List<Phase0RangeSummary>,
)

@Serializable
data class Phase0CandidateRecord(
    val candidateId: String,
    val seed: Long,
    val generatorVersion: Int,
    val targetId: String,
    val width: Int,
    val height: Int,
    val arrowCount: Int,
    val magnetCount: Int,
    val wallCount: Int,
    val origin: String,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val structuralPatternSignature: String,
    val nearStructuralCandidateIds: List<String>,
    val nearStructuralExistingLevelIds: List<String>,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
    val quality: PuzzleQualityScoreV2,
    val humanReviewPriority: HumanReviewPriorityScore,
    val manualReviewStatus: String = "PENDING",
)

@Serializable
data class Phase0CandidatePoolReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "phase0-candidate-pool-v1",
    val generatorVersion: Int = 3,
    val initialSeed: Long,
    val finalSeed: Long,
    val requestedViableCandidates: Int,
    val viableCandidates: List<Phase0CandidateRecord>,
    val targetQuotas: Map<String, Int>,
    val producedByTarget: Map<String, Int>,
    val shortfalls: Map<String, Int>,
    val observedScoreRanges: Map<String, String>,
    val nearestRejectedExamples: Map<String, List<Phase0RejectedCandidateExample>>,
    val rejectionCounts: Map<String, Int>,
    val constructionComplete: Boolean,
)

@Serializable
data class Phase0RejectedCandidateExample(
    val candidateId: String,
    val seed: Long,
    val score: Int,
    val width: Int,
    val arrowCount: Int,
    val magnetCount: Int,
    val wallCount: Int,
    val meaningfulDecisions: Int,
    val effectiveBranching: Double,
    val dependencyDepth: Int,
    val forcedMoveRatio: Double,
    val mechanicRelevanceRatio: Double,
    val rawEmptySpaceRatio: Double,
    val unusedEmptySpaceRatio: Double,
    val irrelevantEntityRatio: Double,
    val reasonCodes: List<String>,
)

@Serializable
data class Phase0RemediationAssignment(
    val campaignNumber: Int,
    val levelId: String,
    val proposedDecision: String,
    val targetId: String,
    val candidateId: String,
    val candidateSeed: Long,
    val origin: String = "GENERATOR_ASSISTED",
    val implementationScope: String = "BOARD_RECONFIGURATION_FROM_STAGED_CANDIDATE",
    val beforeFingerprint: String,
    val afterFingerprint: String,
    val afterSymmetryFingerprint: String,
    val beforeDifficulty: Int,
    val afterDifficulty: DifficultyScoreV3,
    val afterQuality: PuzzleQualityScoreV2,
    val selectedNearNeighborLevelIds: List<String>,
    val nearNeighborJustification: String?,
    val humanReviewPriority: HumanReviewPriorityScore,
    val manualReviewStatus: String = "PENDING",
    val preservesStableId: Boolean = true,
    val preservesEarnedProgress: Boolean = true,
    val bestRecordDisposition: String = "PRESERVE_AS_LEGACY_AND_VERSION_FUTURE_RECORD_BY_FINGERPRINT",
)

@Serializable
data class Phase0ProposedRemediationReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "phase0-proposed-remediation-v1",
    val status: String = "PROPOSED_NOT_APPROVED_OR_PROMOTED",
    val sourceCatalogId: String,
    val sourceContentVersion: Int,
    val candidateGeneratorVersion: Int,
    val candidatePoolSize: Int,
    val assignments: List<Phase0RemediationAssignment>,
    val decisionCounts: Map<String, Int>,
    val exactFingerprintCount: Int,
    val symmetryFingerprintCount: Int,
    val stableIdCount: Int,
    val automatedApprovalCount: Int = 0,
    val humanApprovalCount: Int = 0,
    val ownerApprovalStatus: String = "PENDING",
    val approvalDate: String? = null,
    val humanPlaytestStatus: String = "NOT_PERFORMED",
)

@Serializable
data class Phase0ContentMigrationRow(
    val campaignNumber: Int,
    val levelId: String,
    val oldFingerprint: String,
    val newFingerprint: String,
    val stableIdPreserved: Boolean,
    val recordPolicy: String,
)

@Serializable
data class Phase0ContentMigrationReport(
    val schemaVersion: Int = 1,
    val migrationId: String = "campaign-content-v4-to-v5",
    val sourceContentVersion: Int,
    val targetContentVersion: Int,
    val sourceGeneratorVersion: Int?,
    val targetGeneratorVersion: Int,
    val rows: List<Phase0ContentMigrationRow>,
    val stableIdsPreserved: Int,
    val fingerprintsChanged: Int,
    val preferenceSchemaVersion: Int = 6,
    val preservesCompletionStarsRewardsUnlocksAndCurrency: Boolean = true,
    val archivesIncomparableBestRecords: Boolean = true,
)

@Serializable
data class Phase0FinalLevelCertification(
    val campaignNumber: Int,
    val levelId: String,
    val width: Int,
    val height: Int,
    val arrowCount: Int,
    val magnetCount: Int,
    val wallCount: Int,
    val origin: String,
    val approvedDecision: String,
    val contentFingerprint: String,
    val symmetryFingerprint: String,
    val previousContentFingerprint: String,
    val target: PuzzleDifficultyTarget,
    val productionCertificationAccepted: Boolean,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
    val quality: PuzzleQualityScoreV2,
    val structuralNearNeighborIds: List<String>,
    val humanReviewPriority: HumanReviewPriorityScore,
    val ownerApprovalStatus: String,
    val humanPlaytestStatus: String,
)

@Serializable
data class Phase0FinalCertificationReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "phase0-final-certification-v1",
    val status: String = "COMPLETE_HUMAN_PLAYTEST_PENDING",
    val catalogId: String,
    val contentVersion: Int,
    val generatorVersion: Int,
    val levels: List<Phase0FinalLevelCertification>,
    val exactFingerprintCount: Int,
    val symmetryFingerprintCount: Int,
    val certifiableCount: Int,
    val gateAcceptedCount: Int,
    val qualityStatuses: Map<String, Int>,
    val guessDependentChoiceCount: Int,
    val stableIdMigrationCount: Int,
    val previousFingerprintMappingCount: Int,
    val progressionGatePassed: Boolean,
    val preferenceSchemaVersion: Int,
    val automatedApprovalCount: Int,
    val ownerApprovedCount: Int,
    val humanPlaytestedCount: Int,
)

fun analyzePhase0Current(options: Map<String, String>) {
    val campaignFile = File(requireNotNull(options["campaign"]) { "Missing --campaign=..." })
    val output = File(requireNotNull(options["output"]) { "Missing --output=..." }).also { it.mkdirs() }
    val catalog = LevelParser().parseCatalog(campaignFile.readText())
    require(catalog.levels.size == 150) { "Phase 0 requires the exact 150-level baseline" }
    val searchConfig = PuzzleSearchConfig()
    val difficultyConfig = DifficultyV3Config()
    val searchAnalyzer = PuzzleSearchAnalyzer(config = searchConfig)
    val qualityAnalyzer = PuzzleQualityAnalyzerV2()

    val initial = catalog.levels.sortedBy(LevelDefinition::number).map { level ->
        val target = Phase0DifficultyTargets.forCampaignNumber(level.number)
        val difficulty = DifficultyV3Scorer.score(
            metrics = searchAnalyzer.analyze(level),
            config = difficultyConfig,
            searchConfig = searchConfig,
        )
        Phase0InitialAnalysis(level, target, difficulty, DifficultyV3Gate.evaluate(difficulty, target))
    }
    val exactStructuralCounts = initial.groupingBy { it.difficulty.rawMetrics.structuralPatternSignature }.eachCount()
    val similarities = initial.associate { row ->
        row.level.id to initial.asSequence()
            .filterNot { it.level.id == row.level.id }
            .filter {
                row.difficulty.rawMetrics.structuralPatternSignature ==
                    it.difficulty.rawMetrics.structuralPatternSignature ||
                    structuralDistance(row.difficulty, it.difficulty) <= 0.06
            }
            .map { it.level.id }
            .sorted()
            .toList()
    }
    val branchingAverage = initial.map { it.difficulty.rawMetrics.averageEffectiveBranchingFactor }.average()
    val branchingSpan = initial.maxOf { it.difficulty.rawMetrics.averageEffectiveBranchingFactor } -
        initial.minOf { it.difficulty.rawMetrics.averageEffectiveBranchingFactor }
    val depthAverage = initial.map { it.difficulty.rawMetrics.minimumSolutionLength }.average()
    val depthSpan = initial.maxOf { it.difficulty.rawMetrics.minimumSolutionLength } -
        initial.minOf { it.difficulty.rawMetrics.minimumSolutionLength }
    val seenMechanicCombinations = mutableSetOf<String>()
    val diagnostics = initial.map { row ->
        val similarityIds = similarities.getValue(row.level.id)
        val quality = qualityAnalyzer.analyze(
            difficulty = row.difficulty,
            gate = row.gate,
            structuralSimilarityCount = similarityIds.size,
        )
        val decision = remediationDecision(row, quality, similarityIds)
        val mechanicCombination = row.level.metadata?.mechanicTags.orEmpty().sorted().joinToString("+")
        val newCombination = mechanicCombination.isNotEmpty() && seenMechanicCombinations.add(mechanicCombination)
        val branchingSeverity = normalizedOutlier(
            row.difficulty.rawMetrics.averageEffectiveBranchingFactor,
            branchingAverage,
            branchingSpan,
        )
        val depthSeverity = normalizedOutlier(
            row.difficulty.rawMetrics.minimumSolutionLength.toDouble(),
            depthAverage,
            depthSpan.toDouble(),
        )
        val qualityMarginSeverity = if (quality.status == PuzzleQualityStatusV2.REJECT) {
            1.0
        } else {
            (1.0 - abs(quality.marginAboveReview).toDouble() / 25.0).coerceIn(0.0, 1.0)
        }
        val priority = HumanReviewPriorityScorer.score(
            HumanReviewPriorityFactors(
                difficultyConfidence = row.difficulty.confidence,
                solverTruncated = !row.difficulty.rawMetrics.searchComplete,
                unusualBranchingSeverity = branchingSeverity,
                extremeDifficultySeverity = extremeDifficultySeverity(row.difficulty.score),
                qualityMarginSeverity = qualityMarginSeverity,
                novelStructuralPattern = exactStructuralCounts.getValue(
                    row.difficulty.rawMetrics.structuralPatternSignature,
                ) == 1 && similarityIds.isEmpty(),
                structuralSimilaritySeverity = (similarityIds.size / 5.0).coerceIn(0.0, 1.0),
                newMechanicInteraction = newCombination,
                unusualSolutionDepthSeverity = depthSeverity,
            ),
        )
        Phase0LevelDiagnostic(
            levelId = row.level.id,
            campaignNumber = row.level.number,
            contentVersion = row.level.metadata?.contentVersion ?: catalog.contentVersion,
            width = row.level.width,
            height = row.level.height,
            arrowCount = row.level.arrows.size,
            magnetCount = row.level.magnets.size,
            wallCount = row.level.walls.size,
            origin = row.level.metadata?.origin?.name ?: "UNKNOWN",
            exactFingerprint = ContentFingerprint.exact(row.level),
            symmetryFingerprint = ContentFingerprint.symmetryNormalized(row.level),
            geometricSimilaritySignature = ContentFingerprint.structuralSimilaritySignature(row.level),
            structuralPatternSignature = row.difficulty.rawMetrics.structuralPatternSignature,
            structuralClusterId = row.difficulty.rawMetrics.structuralPatternSignature.take(19),
            structuralSimilarityIds = similarityIds,
            target = row.target,
            difficulty = row.difficulty,
            gate = row.gate,
            quality = quality,
            proposedDecision = decision.first,
            decisionReasons = decision.second,
            humanReviewPriority = priority,
        )
    }
    val report = Phase0AnalysisReport(
        catalogId = catalog.catalogId,
        catalogSchemaVersion = catalog.schemaVersion,
        contentVersion = catalog.contentVersion,
        generatorVersion = catalog.generatorVersion,
        searchConfig = searchConfig,
        difficultyConfig = difficultyConfig,
        levels = diagnostics,
        rangeSummaries = rangeSummaries(diagnostics),
    )
    val json = Json { prettyPrint = true; encodeDefaults = true }
    File(output, "PHASE0_CURRENT_DIAGNOSTICS.json").writeText(json.encodeToString(report))
    File(output, "PHASE0_REMEDIATION_MANIFEST.md").writeText(remediationMarkdown(report))
    File(output, "PHASE0_DISTRIBUTION_REPORT.md").writeText(distributionMarkdown(report))
    File(output, "PHASE0_HUMAN_REVIEW_CHECKLIST.md").writeText(humanReviewMarkdown(report))
    println(
        "Phase 0 v3 analyzed ${diagnostics.size} levels without modifying campaign content; " +
            "decisions=${diagnostics.groupingBy { it.proposedDecision }.eachCount()}, " +
            "completeSearch=${diagnostics.count { it.difficulty.certifiable }}/${diagnostics.size}.",
    )
}

fun stagePhase0Candidates(options: Map<String, String>) {
    val campaign = LevelParser().parseCatalog(File(requireNotNull(options["campaign"])).readText())
    val diagnosticsFile = File(requireNotNull(options["diagnostics"]))
    val output = File(requireNotNull(options["output"])).also { it.mkdirs() }
    val requested = requireNotNull(options["pool-size"]).toInt()
    val initialSeed = requireNotNull(options["seed"]).toLong()
    val attemptCapPerTarget = options["attempts-per-target"]?.toLong()
    require(requested > 0)
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    val current = json.decodeFromString<Phase0AnalysisReport>(diagnosticsFile.readText())
    val targetRows = current.levels.groupBy { it.target.id }
    val targetOrder = targetRows.keys.sorted()
    val quotas = proportionalQuotas(targetRows.mapValues { it.value.size }, requested)
    val searchConfig = current.searchConfig
    val analyzer = PuzzleSearchAnalyzer(config = searchConfig)
    val qualityAnalyzer = PuzzleQualityAnalyzerV2()
    val acceptedLevels = mutableListOf<LevelDefinition>()
    val acceptedDrafts = mutableListOf<Phase0CandidateDraft>()
    val rejected = linkedMapOf<String, Int>()
    val existingExact = campaign.levels.mapTo(mutableSetOf(), ContentFingerprint::exact)
    val existingSymmetry = campaign.levels.mapTo(mutableSetOf(), ContentFingerprint::symmetryNormalized)
    val observedScores = targetOrder.associateWith { mutableListOf<Int>() }
    val rejectedExamples = targetOrder.associateWith { mutableListOf<Phase0RejectedCandidateExample>() }
    val shortfalls = linkedMapOf<String, Int>()
    val currentById = current.levels.associateBy(Phase0LevelDiagnostic::levelId)
    val rankedTemplates = targetOrder.associateWith { targetId ->
        val target = targetRows.getValue(targetId).first().target
        val shape = Phase0CandidateFactory.shape(targetId)
        campaign.levels.asSequence()
            .filter { it.designedSolutions.isNotEmpty() }
            .filter { it.arrows.size <= shape.arrowRange.last }
            .filter { it.magnets.size in shape.magnetRange }
            .filter { it.walls.size <= shape.wallRange.last }
            .sortedBy { level -> templateDistance(requireNotNull(currentById[level.id]), target) }
            .toList()
            .also { check(it.isNotEmpty()) { "No solution-bearing Phase 0 templates for $targetId" } }
    }
    var finalSeed = initialSeed

    targetOrder.forEachIndexed { targetIndex, targetId ->
        val target = targetRows.getValue(targetId).first().target
        val quota = quotas.getValue(targetId)
        var produced = 0
        var seed = initialSeed + targetIndex * 1_000_000L
        val seedLimit = seed + (attemptCapPerTarget ?: maxOf(25_000L, quota * 10_000L))
        while (produced < quota && seed < seedLimit) {
            val raw = Phase0CandidateFactory.create(seed, targetId, rankedTemplates.getValue(targetId))
            if (raw == null) {
                rejected.incrementPhase0("SOLUTION_PRESERVING_CONSTRUCTION_FAILED")
                seed += 1
                continue
            }
            val metrics = analyzer.analyze(raw)
            if (!metrics.solvable || !metrics.searchComplete) {
                rejected.incrementPhase0(if (metrics.searchComplete) "UNSOLVABLE" else "SEARCH_INCOMPLETE")
                seed += 1
                continue
            }
            val score = DifficultyV3Scorer.score(metrics, current.difficultyConfig, searchConfig)
            observedScores.getValue(targetId) += score.score
            val gate = DifficultyV3Gate.evaluate(score, target)
            val shape = Phase0CandidateFactory.shape(targetId)
            val empty = metrics.purposefulSpace.rawEmptySpaceRatio
            val unusedExceptionThreshold = when {
                score.score <= 30 -> 0.75
                score.score <= 45 -> 0.60
                else -> 0.25
            }
            val purposefulDensityException =
                metrics.purposefulSpace.unusedEmptySpaceRatio <= unusedExceptionThreshold &&
                    metrics.purposefulSpace.irrelevantEntityRatio <= 0.10
            val densityReasons = if (empty !in shape.rawEmptyRange && !purposefulDensityException) {
                listOf("RAW_EMPTY_SPACE_OUT_OF_TARGET")
            } else {
                emptyList()
            }
            val quality = qualityAnalyzer.analyze(score, gate)
            val candidateReasons = (
                gate.reasonCodes + densityReasons +
                    quality.reasonCodes.filterNot { it == PuzzleQualityReasonV2.GATE_FAILURE }
                ).distinct().sorted()
            if (candidateReasons.isNotEmpty() || quality.status != PuzzleQualityStatusV2.ACCEPT) {
                candidateReasons.forEach(rejected::incrementPhase0)
                val example = Phase0RejectedCandidateExample(
                    candidateId = raw.id,
                    seed = seed,
                    score = score.score,
                    width = raw.width,
                    arrowCount = raw.arrows.size,
                    magnetCount = raw.magnets.size,
                    wallCount = raw.walls.size,
                    meaningfulDecisions = metrics.meaningfulDecisionPoints,
                    effectiveBranching = metrics.averageEffectiveBranchingFactor,
                    dependencyDepth = metrics.dependencyDepth,
                    forcedMoveRatio = metrics.forcedMoveRatio,
                    mechanicRelevanceRatio = metrics.mechanicRelevanceRatio,
                    rawEmptySpaceRatio = metrics.purposefulSpace.rawEmptySpaceRatio,
                    unusedEmptySpaceRatio = metrics.purposefulSpace.unusedEmptySpaceRatio,
                    irrelevantEntityRatio = metrics.purposefulSpace.irrelevantEntityRatio,
                    reasonCodes = candidateReasons.ifEmpty { quality.reasonCodes },
                )
                rejectedExamples.getValue(targetId).add(example)
                rejectedExamples.getValue(targetId).sortWith(
                    compareBy<Phase0RejectedCandidateExample> { it.reasonCodes.size }
                        .thenBy { abs(it.score - (target.minimumScore + target.maximumScore) / 2) }
                        .thenBy { it.unusedEmptySpaceRatio + it.irrelevantEntityRatio },
                )
                if (rejectedExamples.getValue(targetId).size > 5) {
                    rejectedExamples.getValue(targetId).removeLast()
                }
                seed += 1
                continue
            }
            val exact = ContentFingerprint.exact(raw)
            val symmetry = ContentFingerprint.symmetryNormalized(raw)
            val structure = metrics.structuralPatternSignature
            when {
                exact in existingExact || acceptedDrafts.any { it.exactFingerprint == exact } ->
                    rejected.incrementPhase0("EXACT_DUPLICATE")
                symmetry in existingSymmetry || acceptedDrafts.any { it.symmetryFingerprint == symmetry } ->
                    rejected.incrementPhase0("SYMMETRY_DUPLICATE")
                else -> {
                    val numbered = raw.copy(
                        number = acceptedLevels.size + 1,
                        designedSolutions = listOf(metrics.canonicalSolutionArrowIds),
                    )
                    acceptedLevels += numbered
                    acceptedDrafts += Phase0CandidateDraft(
                        level = numbered,
                        seed = seed,
                        targetId = targetId,
                        exactFingerprint = exact,
                        symmetryFingerprint = symmetry,
                        structuralPatternSignature = structure,
                        difficulty = score,
                        gate = gate,
                        quality = quality,
                    )
                    produced += 1
                }
            }
            seed += 1
        }
        finalSeed = maxOf(finalSeed, seed - 1)
        if (produced < quota) shortfalls[targetId] = quota - produced
    }
    val records = acceptedDrafts.map { draft ->
        val near = acceptedDrafts.asSequence().filterNot { it.level.id == draft.level.id }
            .filter { structuralDistance(draft.difficulty, it.difficulty) <= 0.08 }
            .map { it.level.id }.sorted().toList()
        val nearExisting = current.levels.asSequence()
            .filter {
                it.structuralPatternSignature == draft.structuralPatternSignature ||
                    structuralDistance(draft.difficulty, it.difficulty) <= 0.08
            }
            .map { it.levelId }.sorted().toList()
        val finalQuality = qualityAnalyzer.analyze(
            difficulty = draft.difficulty,
            gate = draft.gate,
            structuralSimilarityCount = near.size + nearExisting.size,
        )
        Phase0CandidateRecord(
            candidateId = draft.level.id,
            seed = draft.seed,
            generatorVersion = 3,
            targetId = draft.targetId,
            width = draft.level.width,
            height = draft.level.height,
            arrowCount = draft.level.arrows.size,
            magnetCount = draft.level.magnets.size,
            wallCount = draft.level.walls.size,
            origin = "GENERATOR_ASSISTED",
            exactFingerprint = draft.exactFingerprint,
            symmetryFingerprint = draft.symmetryFingerprint,
            structuralPatternSignature = draft.structuralPatternSignature,
            nearStructuralCandidateIds = near,
            nearStructuralExistingLevelIds = nearExisting,
            difficulty = draft.difficulty,
            gate = draft.gate,
            quality = finalQuality,
            humanReviewPriority = HumanReviewPriorityScorer.score(
                HumanReviewPriorityFactors(
                    difficultyConfidence = draft.difficulty.confidence,
                    solverTruncated = !draft.difficulty.rawMetrics.searchComplete,
                    unusualBranchingSeverity = (
                        abs(draft.difficulty.rawMetrics.averageEffectiveBranchingFactor - 1.75) / 1.75
                        ).coerceIn(0.0, 1.0),
                    extremeDifficultySeverity = extremeDifficultySeverity(draft.difficulty.score),
                    qualityMarginSeverity = (
                        1.0 - finalQuality.marginAboveReview.coerceAtLeast(0) / 25.0
                        ).coerceIn(0.0, 1.0),
                    novelStructuralPattern = nearExisting.isEmpty(),
                    structuralSimilaritySeverity = ((near.size + nearExisting.size) / 5.0).coerceIn(0.0, 1.0),
                    newMechanicInteraction = false,
                    unusualSolutionDepthSeverity = (
                        abs(draft.difficulty.rawMetrics.minimumSolutionLength - 5) / 5.0
                        ).coerceIn(0.0, 1.0),
                ),
            ),
        )
    }
    val pool = Phase0CandidatePoolReport(
        initialSeed = initialSeed,
        finalSeed = finalSeed,
        requestedViableCandidates = requested,
        viableCandidates = records,
        targetQuotas = quotas.toSortedMap(),
        producedByTarget = records.groupingBy { it.targetId }.eachCount().toSortedMap(),
        shortfalls = shortfalls.toSortedMap(),
        observedScoreRanges = observedScores.mapValues { (_, values) ->
            if (values.isEmpty()) "none" else "${values.min()}..${values.max()}"
        }.toSortedMap(),
        nearestRejectedExamples = rejectedExamples.mapValues { it.value.toList() }.toSortedMap(),
        rejectionCounts = rejected.toSortedMap(),
        constructionComplete = shortfalls.isEmpty(),
    )
    File(output, "PHASE0_CANDIDATE_POOL.json").writeText(json.encodeToString(pool))
    File(output, "PHASE0_CANDIDATE_POOL.md").writeText(candidatePoolMarkdown(pool))
    File(output, "phase0_candidate_catalog.json").writeText(
        LevelParser().encodeCatalog(
            LevelCatalog(
                // Candidate catalogs are analysis artifacts, not M3-certified production
                // catalogs. Schema 1 keeps them parseable without fabricating metadata.
                schemaVersion = 1,
                ruleVersion = campaign.ruleVersion,
                catalogId = "magnetrail-phase0-candidates-v1",
                levels = acceptedLevels,
                contentVersion = 1,
                generatorVersion = null,
            ),
        ),
    )
    println(
        "Staged ${records.size} v3-gated Phase 0 candidates; checked-in campaign content was not modified. " +
            "Targets=${records.groupingBy { it.targetId }.eachCount()}, shortfalls=${pool.shortfalls}.",
    )
}

fun planPhase0Remediation(options: Map<String, String>) {
    val campaign = LevelParser().parseCatalog(File(requireNotNull(options["campaign"])).readText())
    val output = File(requireNotNull(options["output"])).also { it.mkdirs() }
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    val diagnostics = json.decodeFromString<Phase0AnalysisReport>(
        File(requireNotNull(options["diagnostics"])).readText(),
    )
    val pool = json.decodeFromString<Phase0CandidatePoolReport>(
        File(requireNotNull(options["candidate-report"])).readText(),
    )
    val candidateCatalog = LevelParser().parseCatalog(File(requireNotNull(options["candidate-catalog"])).readText())
    check(pool.constructionComplete) { "Cannot plan remediation from an incomplete candidate pool" }
    check(campaign.levels.size == 150 && diagnostics.levels.size == 150)
    val candidateLevels = candidateCatalog.levels.associateBy(LevelDefinition::id)
    val selected = mutableListOf<Phase0CandidateRecord>()
    val selectionsByLevel = linkedMapOf<String, Phase0CandidateRecord>()

    diagnostics.levels.sortedBy(Phase0LevelDiagnostic::campaignNumber)
        .groupBy { it.target.id }
        .toSortedMap()
        .forEach { (targetId, rows) ->
            val available = pool.viableCandidates.filter { it.targetId == targetId }.toMutableList()
            check(available.size >= rows.size) {
                "Candidate target $targetId has ${available.size} boards for ${rows.size} campaign slots"
            }
            rows.sortedBy(Phase0LevelDiagnostic::campaignNumber).forEachIndexed { index, row ->
                val fraction = if (rows.size == 1) 0.5 else index.toDouble() / (rows.size - 1)
                val desiredScore = row.target.minimumScore +
                    (row.target.maximumScore - row.target.minimumScore) * fraction
                val chosen = available.minWith(
                    compareBy<Phase0CandidateRecord> { candidate ->
                        selected.count {
                            it.structuralPatternSignature == candidate.structuralPatternSignature
                        }
                    }.thenBy { candidate ->
                        selected.count { structuralDistance(it.difficulty, candidate.difficulty) <= 0.04 }
                    }.thenBy { candidate -> abs(candidate.difficulty.score - desiredScore) }
                        .thenBy { it.humanReviewPriority.score }
                        .thenBy { it.seed },
                )
                available.remove(chosen)
                selected += chosen
                selectionsByLevel[row.levelId] = chosen
            }
        }

    val proposedLevels = campaign.levels.sortedBy(LevelDefinition::number).map { current ->
        val chosen = requireNotNull(selectionsByLevel[current.id])
        val board = requireNotNull(candidateLevels[chosen.candidateId])
        board.copy(
            id = current.id,
            number = current.number,
            title = current.title,
            metadata = null,
        )
    }
    check(proposedLevels.map(LevelDefinition::id) == campaign.levels.sortedBy(LevelDefinition::number).map(LevelDefinition::id)) {
        "Proposed remediation changed stable campaign IDs or order"
    }
    val exactCount = proposedLevels.map(ContentFingerprint::exact).toSet().size
    val symmetryCount = proposedLevels.map(ContentFingerprint::symmetryNormalized).toSet().size
    check(exactCount == 150) { "Proposed remediation contains exact duplicates" }
    check(symmetryCount == 150) { "Proposed remediation contains symmetry duplicates" }

    val analyzer = PuzzleSearchAnalyzer(config = diagnostics.searchConfig)
    val qualityAnalyzer = PuzzleQualityAnalyzerV2()
    val analyzed = proposedLevels.associateWith { level ->
        DifficultyV3Scorer.score(analyzer.analyze(level), diagnostics.difficultyConfig, diagnostics.searchConfig)
    }
    val diagnosticsById = diagnostics.levels.associateBy(Phase0LevelDiagnostic::levelId)
    val assignments = proposedLevels.map { level ->
        val before = requireNotNull(diagnosticsById[level.id])
        val chosen = requireNotNull(selectionsByLevel[level.id])
        val difficulty = requireNotNull(analyzed[level])
        val gate = DifficultyV3Gate.evaluate(difficulty, before.target)
        check(gate.accepted && difficulty.certifiable) { "Selected candidate failed replay gate for ${level.id}: ${gate.reasonCodes}" }
        val near = proposedLevels.asSequence().filterNot { it.id == level.id }
            .filter { other ->
                val otherDifficulty = requireNotNull(analyzed[other])
                difficulty.rawMetrics.structuralPatternSignature == otherDifficulty.rawMetrics.structuralPatternSignature ||
                    structuralDistance(difficulty, otherDifficulty) <= 0.04
            }
            .map(LevelDefinition::id).sorted().toList()
        val quality = qualityAnalyzer.analyze(difficulty, gate, structuralSimilarityCount = near.size)
        val justification = when {
            near.isEmpty() -> null
            level.number <= 25 -> "PEDAGOGICAL_VARIATION_PENDING_HUMAN_REVIEW"
            else -> "STRUCTURAL_NEAR_NEIGHBOR_PENDING_HUMAN_REVIEW"
        }
        Phase0RemediationAssignment(
            campaignNumber = level.number,
            levelId = level.id,
            proposedDecision = before.proposedDecision,
            targetId = before.target.id,
            candidateId = chosen.candidateId,
            candidateSeed = chosen.seed,
            beforeFingerprint = before.exactFingerprint,
            afterFingerprint = ContentFingerprint.exact(level),
            afterSymmetryFingerprint = ContentFingerprint.symmetryNormalized(level),
            beforeDifficulty = before.difficulty.score,
            afterDifficulty = difficulty,
            afterQuality = quality,
            selectedNearNeighborLevelIds = near,
            nearNeighborJustification = justification,
            humanReviewPriority = reviewPriority(difficulty, quality, near.size, near.isEmpty()),
        )
    }
    val progressionFailures = Phase0ProgressionPolicy.failures(
        assignments.associate { it.campaignNumber to it.afterDifficulty.score },
    )
    check(progressionFailures.isEmpty()) {
        "Proposed Phase 0 progression failed: ${progressionFailures.joinToString()}"
    }
    check(assignments.filter { it.campaignNumber in 41..60 }.all {
        it.afterDifficulty.rawMetrics.meaningfulDecisionPoints >= 1
    }) { "Levels 41..60 must all contain a genuine decision" }
    check(assignments.filter { it.campaignNumber in 61..80 }.all {
        it.afterDifficulty.rawMetrics.meaningfulDecisionPoints >= 2
    }) { "Levels 61..80 must all meet the Hard decision floor" }
    check(assignments.filter { it.campaignNumber in 81..100 }.all {
        it.afterDifficulty.rawMetrics.meaningfulDecisionPoints >= 3
    }) { "Levels 81..100 must all meet the Very Hard decision floor" }
    val report = Phase0ProposedRemediationReport(
        sourceCatalogId = campaign.catalogId,
        sourceContentVersion = campaign.contentVersion,
        candidateGeneratorVersion = pool.generatorVersion,
        candidatePoolSize = pool.viableCandidates.size,
        assignments = assignments,
        decisionCounts = assignments.groupingBy { it.proposedDecision }.eachCount().toSortedMap(),
        exactFingerprintCount = exactCount,
        symmetryFingerprintCount = symmetryCount,
        stableIdCount = proposedLevels.zip(campaign.levels.sortedBy(LevelDefinition::number))
            .count { (after, before) -> after.id == before.id && after.number == before.number },
    )
    File(output, "PHASE0_PROPOSED_REMEDIATION.json").writeText(json.encodeToString(report))
    File(output, "PHASE0_PROPOSED_REMEDIATION.md").writeText(proposedRemediationMarkdown(report))
    File(output, "PHASE0_PROPOSED_DISTRIBUTION_REPORT.md").writeText(proposedDistributionMarkdown(report))
    File(output, "PHASE0_PROPOSED_HUMAN_REVIEW_CHECKLIST.md").writeText(proposedHumanReviewMarkdown(report))
    File(output, "phase0_proposed_campaign.json").writeText(
        LevelParser().encodeCatalog(
            LevelCatalog(
                schemaVersion = 1,
                ruleVersion = campaign.ruleVersion,
                catalogId = "magnetrail-phase0-proposed-campaign-not-promoted",
                levels = proposedLevels,
            ),
        ),
    )
    println(
        "Planned ${assignments.size} stable-ID Phase 0 remediations from ${pool.viableCandidates.size} candidates; " +
            "checked-in campaign content was not modified and all human statuses remain PENDING.",
    )
}

fun promoteApprovedPhase0(options: Map<String, String>) {
    require(options["approval"] == "project-owner-approved") {
        "Phase 0 promotion requires explicit project-owner approval"
    }
    val campaignFile = File(requireNotNull(options["campaign"]))
    val sourceText = campaignFile.readText()
    val source = LevelParser().parseCatalog(sourceText)
    val proposed = LevelParser().parseCatalog(File(requireNotNull(options["proposal-catalog"])).readText())
    val output = File(requireNotNull(options["output"])).also { it.mkdirs() }
    val sourceSnapshot = File(requireNotNull(options["source-snapshot"]))
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    val proposal = json.decodeFromString<Phase0ProposedRemediationReport>(
        File(requireNotNull(options["proposal-report"])).readText(),
    )
    require(source.contentVersion == 4) { "Expected source content version 4, got ${source.contentVersion}" }
    require(source.levels.size == 150 && proposed.levels.size == 150 && proposal.assignments.size == 150)
    require(proposal.humanApprovalCount == 0 && proposal.ownerApprovalStatus == "PENDING") {
        "Promotion input must be the immutable pre-approval proposal"
    }
    val sourceById = source.levels.associateBy(LevelDefinition::id)
    val proposalById = proposed.levels.associateBy(LevelDefinition::id)
    val assignmentById = proposal.assignments.associateBy(Phase0RemediationAssignment::levelId)
    check(source.levels.map { it.id to it.number } == proposed.levels.map { it.id to it.number }) {
        "Approved proposal changed stable IDs or ordering"
    }
    val certification = CertificationPipeline()
    val searchConfig = PuzzleSearchConfig()
    val analyzer = PuzzleSearchAnalyzer(config = searchConfig)
    val difficultyConfig = DifficultyV3Config()
    val promoted = source.levels.sortedBy(LevelDefinition::number).map { old ->
        val board = requireNotNull(proposalById[old.id])
        val assignment = requireNotNull(assignmentById[old.id])
        check(ContentFingerprint.exact(old) == assignment.beforeFingerprint) {
            "Source fingerprint changed after approval for ${old.id}"
        }
        check(ContentFingerprint.exact(board) == assignment.afterFingerprint) {
            "Approved candidate fingerprint changed for ${old.id}"
        }
        val profile = phase0CertificationProfile(assignment.targetId)
        val certified = certification.certify(
            board.copy(metadata = null),
            CertificationRequest(
                profile = profile,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                packId = requireNotNull(old.metadata).packId,
                generatorVersion = PHASE0_GENERATOR_VERSION,
                generatorSeed = assignment.candidateSeed,
                generationProfile = profile.profileId,
                contentVersion = PHASE0_CONTENT_VERSION,
                previousContentFingerprint = assignment.beforeFingerprint,
            ),
        )
        check(certified is CertificationResult.Accepted) {
            "Legacy certification rejected approved ${old.id}: ${(certified as CertificationResult.Rejected).reasons}"
        }
        val level = certified.level.copy(id = old.id, number = old.number, title = old.title)
        val score = DifficultyV3Scorer.score(analyzer.analyze(level), difficultyConfig, searchConfig)
        val gate = DifficultyV3Gate.evaluate(score, Phase0DifficultyTargets.forCampaignNumber(level.number))
        check(score.certifiable && gate.accepted) {
            "Difficulty v3 recertification rejected ${old.id}: ${gate.reasonCodes}"
        }
        check(ContentFingerprint.exact(level) == assignment.afterFingerprint)
        level
    }
    check(promoted.map(ContentFingerprint::exact).toSet().size == 150)
    check(promoted.map(ContentFingerprint::symmetryNormalized).toSet().size == 150)
    val catalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = source.ruleVersion,
        catalogId = source.catalogId,
        levels = promoted,
        contentVersion = PHASE0_CONTENT_VERSION,
        generatorVersion = PHASE0_GENERATOR_VERSION,
    )
    val encoded = LevelParser().encodeCatalog(catalog)
    val reparsed = LevelParser().parseCatalog(encoded)
    check(reparsed == catalog) { "Promoted catalog failed deterministic parse round trip" }

    val approvedAssignments = proposal.assignments.map {
        it.copy(manualReviewStatus = "OWNER_APPROVED_NOT_PLAYTESTED")
    }
    val approved = proposal.copy(
        status = "OWNER_APPROVED_AND_PROMOTED",
        assignments = approvedAssignments,
        humanApprovalCount = approvedAssignments.size,
        ownerApprovalStatus = "APPROVED_BY_PROJECT_OWNER",
        approvalDate = "2026-08-19",
        humanPlaytestStatus = "NOT_PERFORMED",
    )
    val migration = Phase0ContentMigrationReport(
        sourceContentVersion = source.contentVersion,
        targetContentVersion = catalog.contentVersion,
        sourceGeneratorVersion = source.generatorVersion,
        targetGeneratorVersion = requireNotNull(catalog.generatorVersion),
        rows = promoted.map { level ->
            val assignment = requireNotNull(assignmentById[level.id])
            Phase0ContentMigrationRow(
                campaignNumber = level.number,
                levelId = level.id,
                oldFingerprint = assignment.beforeFingerprint,
                newFingerprint = assignment.afterFingerprint,
                stableIdPreserved = true,
                recordPolicy = "PRESERVE_EARNED_VALUE_ARCHIVE_OLD_BEST_START_NEW_FINGERPRINT_RECORD",
            )
        },
        stableIdsPreserved = 150,
        fingerprintsChanged = promoted.count { level ->
            val metadata = requireNotNull(level.metadata)
            metadata.previousContentFingerprint != metadata.contentFingerprint
        },
    )
    sourceSnapshot.parentFile.mkdirs()
    sourceSnapshot.writeText(sourceText)
    File(output, "PHASE0_APPROVED_REMEDIATION.json").writeText(json.encodeToString(approved))
    File(output, "PHASE0_APPROVED_REMEDIATION.md").writeText(approvedRemediationMarkdown(approved))
    File(output, "PHASE0_CONTENT_MIGRATION.json").writeText(json.encodeToString(migration))
    campaignFile.writeText(encoded)
    println(
        "Promoted owner-approved Phase 0 catalog content ${source.contentVersion}→${catalog.contentVersion}; " +
            "150 stable IDs preserved, 150 board fingerprints versioned, human playtesting NOT_PERFORMED.",
    )
}

fun finalizePromotedPhase0(options: Map<String, String>) {
    val campaign = LevelParser().parseCatalog(File(requireNotNull(options["campaign"])).readText())
    val source = LevelParser().parseCatalog(File(requireNotNull(options["source-snapshot"])).readText())
    val output = File(requireNotNull(options["output"])).also { it.mkdirs() }
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    val approved = json.decodeFromString<Phase0ProposedRemediationReport>(
        File(requireNotNull(options["approved-report"])).readText(),
    )

    require(campaign.schemaVersion == 2) { "Phase 0 production catalog must use schema 2" }
    require(campaign.contentVersion >= PHASE0_CONTENT_VERSION)
    require(requireNotNull(campaign.generatorVersion) >= PHASE0_GENERATOR_VERSION)
    require(source.contentVersion == 4) { "Expected immutable source content version 4" }
    require(campaign.levels.size >= 150 && source.levels.size == 150 && approved.assignments.size == 150)
    require(approved.status == "OWNER_APPROVED_AND_PROMOTED")
    require(approved.ownerApprovalStatus == "APPROVED_BY_PROJECT_OWNER")
    require(approved.humanApprovalCount == 150 && approved.automatedApprovalCount == 0)
    require(approved.humanPlaytestStatus == "NOT_PERFORMED") {
        "Finalizer cannot infer or fabricate human playtest results"
    }

    val ordered = campaign.levels.filter { it.number in 1..150 }.sortedBy(LevelDefinition::number)
    val orderedSource = source.levels.sortedBy(LevelDefinition::number)
    check(ordered.map { it.id to it.number } == orderedSource.map { it.id to it.number }) {
        "Promoted campaign changed stable IDs, campaign numbers, or ordering"
    }
    val sourceById = orderedSource.associateBy(LevelDefinition::id)
    val approvalById = approved.assignments.associateBy(Phase0RemediationAssignment::levelId)
    check(approvalById.size == 150) { "Approved remediation contains duplicate stable IDs" }

    val searchConfig = PuzzleSearchConfig()
    val difficultyConfig = DifficultyV3Config()
    val analyzer = PuzzleSearchAnalyzer(config = searchConfig)
    val qualityAnalyzer = PuzzleQualityAnalyzerV2()
    val certification = CertificationPipeline()
    val difficultyById = ordered.associate { level ->
        level.id to DifficultyV3Scorer.score(
            metrics = analyzer.analyze(level),
            config = difficultyConfig,
            searchConfig = searchConfig,
        )
    }

    val exactFingerprints = ordered.associate { it.id to ContentFingerprint.exact(it) }
    val symmetryFingerprints = ordered.associate { it.id to ContentFingerprint.symmetryNormalized(it) }
    check(exactFingerprints.values.toSet().size == 150) { "Promoted campaign contains exact duplicates" }
    check(symmetryFingerprints.values.toSet().size == 150) { "Promoted campaign contains symmetry duplicates" }

    val nearNeighbors = ordered.associate { level ->
        val difficulty = requireNotNull(difficultyById[level.id])
        level.id to ordered.asSequence()
            .filterNot { it.id == level.id }
            .filter { other ->
                val otherDifficulty = requireNotNull(difficultyById[other.id])
                difficulty.rawMetrics.structuralPatternSignature ==
                    otherDifficulty.rawMetrics.structuralPatternSignature ||
                    structuralDistance(difficulty, otherDifficulty) <= 0.04
            }
            .map(LevelDefinition::id)
            .sorted()
            .toList()
    }
    val patternCounts = difficultyById.values
        .groupingBy { it.rawMetrics.structuralPatternSignature }
        .eachCount()

    val rows = ordered.map { level ->
        val old = requireNotNull(sourceById[level.id])
        val assignment = requireNotNull(approvalById[level.id])
        val metadata = requireNotNull(level.metadata) { "Missing production metadata for ${level.id}" }
        val exact = requireNotNull(exactFingerprints[level.id])
        val symmetry = requireNotNull(symmetryFingerprints[level.id])
        val oldExact = ContentFingerprint.exact(old)
        check(assignment.campaignNumber == level.number)
        check(assignment.afterFingerprint == exact) { "Approved fingerprint mismatch for ${level.id}" }
        check(assignment.afterSymmetryFingerprint == symmetry) {
            "Approved symmetry fingerprint mismatch for ${level.id}"
        }
        check(assignment.beforeFingerprint == oldExact) { "Source fingerprint mismatch for ${level.id}" }
        check(metadata.contentFingerprint == exact) { "Production content hash mismatch for ${level.id}" }
        check(metadata.previousContentFingerprint == oldExact) {
            "Missing or unsafe previous-fingerprint migration for ${level.id}"
        }
        check(metadata.contentVersion == PHASE0_CONTENT_VERSION)
        check(metadata.generatorVersion == PHASE0_GENERATOR_VERSION)
        check(metadata.origin == LevelOrigin.GENERATOR_ASSISTED)
        check(metadata.generatorSeed == assignment.candidateSeed)

        val target = Phase0DifficultyTargets.forCampaignNumber(level.number)
        check(target.id == assignment.targetId)
        val difficulty = requireNotNull(difficultyById[level.id])
        val gate = DifficultyV3Gate.evaluate(difficulty, target)
        check(difficulty.certifiable && gate.accepted) {
            "Final Difficulty v3 gate rejected ${level.id}: ${gate.reasonCodes}"
        }
        val near = requireNotNull(nearNeighbors[level.id])
        val quality = qualityAnalyzer.analyze(
            difficulty = difficulty,
            gate = gate,
            structuralSimilarityCount = near.size,
        )
        check(quality.status != PuzzleQualityStatusV2.REJECT) {
            "Final Quality rejected ${level.id}: ${quality.reasonCodes}"
        }

        val productionResult = certification.certify(
            level.copy(metadata = null),
            CertificationRequest(
                profile = phase0CertificationProfile(target.id),
                origin = LevelOrigin.GENERATOR_ASSISTED,
                packId = metadata.packId,
                generatorVersion = PHASE0_GENERATOR_VERSION,
                generatorSeed = assignment.candidateSeed,
                generationProfile = phase0CertificationProfile(target.id).profileId,
                contentVersion = PHASE0_CONTENT_VERSION,
                previousContentFingerprint = oldExact,
            ),
        )
        check(productionResult is CertificationResult.Accepted) {
            "Production certification rejected ${level.id}: " +
                (productionResult as CertificationResult.Rejected).reasons.joinToString()
        }
        check(productionResult.level.metadata == metadata) {
            "Regenerated production metadata differs for ${level.id}"
        }
        check(productionResult.level.designedSolutions == level.designedSolutions) {
            "Production solver solution differs for ${level.id}"
        }
        val priority = reviewPriority(
            difficulty = difficulty,
            quality = quality,
            similarityCount = near.size,
            novelStructure = patternCounts.getValue(difficulty.rawMetrics.structuralPatternSignature) == 1,
        )
        Phase0FinalLevelCertification(
            campaignNumber = level.number,
            levelId = level.id,
            width = level.width,
            height = level.height,
            arrowCount = level.arrows.size,
            magnetCount = level.magnets.size,
            wallCount = level.walls.size,
            origin = metadata.origin.name,
            approvedDecision = assignment.proposedDecision,
            contentFingerprint = exact,
            symmetryFingerprint = symmetry,
            previousContentFingerprint = oldExact,
            target = target,
            productionCertificationAccepted = true,
            difficulty = difficulty,
            gate = gate,
            quality = quality,
            structuralNearNeighborIds = near,
            humanReviewPriority = priority,
            ownerApprovalStatus = assignment.manualReviewStatus,
            humanPlaytestStatus = "PENDING",
        )
    }

    val progressionFailures = Phase0ProgressionPolicy.failures(
        rows.associate { it.campaignNumber to it.difficulty.score },
    )
    check(progressionFailures.isEmpty()) {
        "Final campaign progression failed: ${progressionFailures.joinToString()}"
    }
    val guessCount = rows.sumOf {
        it.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices
    }
    check(guessCount == 0) { "Final campaign contains guess-dependent canonical choices" }
    val report = Phase0FinalCertificationReport(
        catalogId = campaign.catalogId,
        contentVersion = PHASE0_CONTENT_VERSION,
        generatorVersion = PHASE0_GENERATOR_VERSION,
        levels = rows,
        exactFingerprintCount = exactFingerprints.values.toSet().size,
        symmetryFingerprintCount = symmetryFingerprints.values.toSet().size,
        certifiableCount = rows.count { it.difficulty.certifiable && it.productionCertificationAccepted },
        gateAcceptedCount = rows.count { it.gate.accepted },
        qualityStatuses = rows.groupingBy { it.quality.status.name }.eachCount().toSortedMap(),
        guessDependentChoiceCount = guessCount,
        stableIdMigrationCount = ordered.zip(orderedSource).count { (after, before) ->
            after.id == before.id && after.number == before.number
        },
        previousFingerprintMappingCount = rows.count { row ->
            row.previousContentFingerprint == ContentFingerprint.exact(requireNotNull(sourceById[row.levelId]))
        },
        progressionGatePassed = true,
        preferenceSchemaVersion = 6,
        automatedApprovalCount = approved.automatedApprovalCount,
        ownerApprovedCount = rows.count { it.ownerApprovalStatus == "OWNER_APPROVED_NOT_PLAYTESTED" },
        humanPlaytestedCount = 0,
    )
    File(output, "PHASE0_FINAL_DIAGNOSTICS.json").writeText(json.encodeToString(report))
    File(output, "PHASE0_FINAL_CERTIFICATION.md").writeText(finalCertificationMarkdown(report))
    File(output, "PHASE0_FINAL_DISTRIBUTION_REPORT.md").writeText(finalDistributionMarkdown(report))
    File(output, "PHASE0_FINAL_HUMAN_REVIEW_CHECKLIST.md").writeText(finalHumanReviewMarkdown(report))
    println(
        "Finalized promoted Phase 0: ${report.certifiableCount}/150 production-certified, " +
            "Quality=${report.qualityStatuses}, guesses=$guessCount, human playtesting PENDING.",
    )
}

private fun phase0CertificationProfile(targetId: String): GenerationProfile = when (targetId) {
    "phase0-tutorial", "phase0-easy" -> GenerationProfile.PHASE0_INTRO_V3
    "phase0-planning-intro", "phase0-medium", "phase0-upper-recovery" ->
        GenerationProfile.PHASE0_PLANNING_V3
    "phase0-hard", "phase0-very-hard", "phase0-upper-hard", "phase0-upper-peak" ->
        GenerationProfile.PHASE0_ADVANCED_V3
    else -> error("Unknown Phase 0 certification target $targetId")
}

private data class Phase0CandidateDraft(
    val level: LevelDefinition,
    val seed: Long,
    val targetId: String,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val structuralPatternSignature: String,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
    val quality: PuzzleQualityScoreV2,
)

private fun proportionalQuotas(sourceCounts: Map<String, Int>, requested: Int): Map<String, Int> {
    require(requested >= sourceCounts.size) {
        "Candidate pool size $requested cannot cover ${sourceCounts.size} Phase 0 target groups"
    }
    val total = sourceCounts.values.sum()
    if (requested >= total) {
        val quotas = sourceCounts.toMutableMap()
        var remaining = requested - total
        // Ten structurally simple tutorials are sufficient to cover the ten tutorial slots.
        // Oversizing is concentrated where alternative dependencies and branches are useful,
        // rather than manufacturing dozens of tutorial near-duplicates.
        val weightedOrder = sourceCounts.entries.asSequence()
            .filterNot { it.key == "phase0-tutorial" }
            .sortedBy { it.key }
            .flatMap { entry -> List(entry.value) { entry.key }.asSequence() }
            .toList()
        var index = 0
        while (remaining > 0) {
            val key = weightedOrder[index % weightedOrder.size]
            quotas[key] = quotas.getValue(key) + 1
            remaining -= 1
            index += 1
        }
        return quotas
    }
    val quotas = sourceCounts.mapValues { (_, count) -> maxOf(1, requested * count / total) }.toMutableMap()
    var assigned = quotas.values.sum()
    val order = sourceCounts.entries.sortedByDescending { it.value }.map { it.key }
    var index = 0
    while (assigned < requested) {
        val key = order[index % order.size]
        quotas[key] = quotas.getValue(key) + 1
        assigned += 1
        index += 1
    }
    while (assigned > requested) {
        val key = order[index % order.size]
        if (quotas.getValue(key) > 1) {
            quotas[key] = quotas.getValue(key) - 1
            assigned -= 1
        }
        index += 1
    }
    return quotas
}

internal object Phase0ProgressionPolicy {
    private val ranges = listOf(
        "1-10" to 1..10,
        "11-25" to 11..25,
        "26-40" to 26..40,
        "41-60" to 41..60,
        "61-80" to 61..80,
        "81-100" to 81..100,
        "101-125" to 101..125,
        "126-150" to 126..150,
    )

    fun failures(scoresByNumber: Map<Int, Int>): List<String> {
        if ((1..150).any { it !in scoresByNumber }) return listOf("PROGRESSION_MISSING_CAMPAIGN_SCORE")
        val scores = ranges.associate { (label, range) ->
            label to range.map { requireNotNull(scoresByNumber[it]) }.sorted()
        }
        val medians = ranges.associate { (label, _) -> label to median(scores.getValue(label)) }
        return buildList {
            val rising = listOf("1-10", "11-25", "26-40", "41-60", "61-80", "81-100")
            rising.zipWithNext().forEach { (before, after) ->
                if (medians.getValue(after) <= medians.getValue(before)) {
                    add("PROGRESSION_MEDIAN_NOT_RISING:$before:$after")
                }
            }
            if (percentile(scores.getValue("61-80"), 0.25) <= medians.getValue("26-40")) {
                add("PROGRESSION_HARD_LOWER_QUARTILE_TOO_LOW")
            }
            if (percentile(scores.getValue("81-100"), 0.25) <= medians.getValue("61-80")) {
                add("PROGRESSION_VERY_HARD_LOWER_QUARTILE_TOO_LOW")
            }
            if (medians.getValue("101-125") < medians.getValue("61-80")) {
                add("PROGRESSION_UPPER_CAMPAIGN_RESET")
            }
            if (medians.getValue("126-150") < medians.getValue("101-125")) {
                add("PROGRESSION_FINALE_MEDIAN_REGRESSION")
            }
        }
    }

    private fun percentile(values: List<Int>, fraction: Double): Double {
        val index = ((values.size - 1) * fraction).toInt().coerceIn(values.indices)
        return values[index].toDouble()
    }
}

private fun MutableMap<String, Int>.incrementPhase0(reason: String) {
    this[reason] = (this[reason] ?: 0) + 1
}

private fun candidatePoolMarkdown(pool: Phase0CandidatePoolReport): String = buildString {
    appendLine("# Phase 0 staged candidate pool")
    appendLine()
    val status = if (pool.constructionComplete) "COMPLETE STAGING" else "INCOMPLETE STAGING"
    appendLine("Status: **$status — NOT APPROVED OR PROMOTED**")
    appendLine()
    appendLine("- Generator version: ${pool.generatorVersion}")
    appendLine("- Viable candidates: ${pool.viableCandidates.size}")
    appendLine("- Seed search: ${pool.initialSeed}..${pool.finalSeed}")
    appendLine("- Target distribution: ${pool.viableCandidates.groupingBy { it.targetId }.eachCount()}")
    appendLine("- Target quotas: ${pool.targetQuotas}")
    appendLine("- Shortfalls: ${pool.shortfalls}")
    appendLine("- Observed rejected/accepted score ranges: ${pool.observedScoreRanges}")
    appendLine("- Rejections: ${pool.rejectionCounts}")
    appendLine("- Human approvals: 0; every row is `PENDING`.")
    appendLine("- Structural similarity is retained as a Quality/review penalty; it is not mislabeled as hard duplication. Exact and symmetry duplicates remain excluded.")
    appendLine()
    appendLine("| Candidate | Seed | Target | Board / A-M-W | V3 | Length / forced / decisions / spacing / max run | Branch | Dependency | Choices P/I/V/F/G | Quality | Near | Priority | Human |")
    appendLine("|---|---:|---|---|---|---|---:|---:|---|---|---:|---:|---|")
    pool.viableCandidates.forEach { row ->
        val metrics = row.difficulty.rawMetrics
        val choices = metrics.canonicalChoiceMetrics
        appendLine(
            "| ${row.candidateId} | ${row.seed} | ${row.targetId} | ${row.width}x${row.height} / ${row.arrowCount}-${row.magnetCount}-${row.wallCount} | " +
                "${row.difficulty.score}/${row.difficulty.band.displayName} | " +
                "${metrics.minimumSolutionLength}/${metrics.forcedSequenceLength}/${metrics.meaningfulDecisionPoints}/" +
                "${decimal(metrics.averageDecisionSpacing)}/${metrics.maximumForcedRunLength} | " +
                "${decimal(metrics.averageEffectiveBranchingFactor)} | ${metrics.dependencyDepth} | " +
                "${choices.plausibleChoices}/${choices.immediatelyInvalidChoices}/${choices.strategicallyViableChoices}/" +
                "${choices.deceptiveButFairChoices}/${choices.guessDependentChoices} | " +
                "${row.quality.score}/${row.quality.status} | ${row.nearStructuralCandidateIds.size + row.nearStructuralExistingLevelIds.size} | " +
                "${row.humanReviewPriority.score} | ${row.manualReviewStatus} |",
        )
    }
}

private fun proposedRemediationMarkdown(report: Phase0ProposedRemediationReport): String = buildString {
    appendLine("# Phase 0 proposed campaign remediation")
    appendLine()
    appendLine("Status: **PROPOSED — NOT HUMAN-APPROVED OR PROMOTED**")
    appendLine()
    appendLine("This is the required pre-promotion manifest. It maps every current stable level ID to one analyzed candidate. It does not modify the checked-in campaign, production metadata, fingerprints, or player data.")
    appendLine()
    appendLine("- Candidate pool analyzed: ${report.candidatePoolSize}")
    appendLine("- Proposed assignments: ${report.assignments.size}")
    appendLine("- Decisions inherited from current-level diagnosis: ${report.decisionCounts}")
    appendLine("- Stable IDs/order preserved: ${report.stableIdCount}/${report.assignments.size}")
    appendLine("- Exact/symmetry fingerprints: ${report.exactFingerprintCount}/${report.symmetryFingerprintCount}")
    appendLine("- Automated approvals: ${report.automatedApprovalCount}")
    appendLine("- Human approvals: ${report.humanApprovalCount}; every row remains `PENDING`.")
    appendLine()
    appendLine("| # | Stable ID | Decision | Candidate / origin | Target | Before→after v3 | Length / forced / decisions / spacing / max run | Choices P/I/V/F/G | Quality | Near | Review priority | Fingerprint change | Human |")
    appendLine("|---:|---|---|---|---|---:|---|---|---|---:|---:|---|---|")
    report.assignments.forEach { row ->
        val metrics = row.afterDifficulty.rawMetrics
        val choices = metrics.canonicalChoiceMetrics
        appendLine(
            "| ${row.campaignNumber} | ${row.levelId} | ${row.proposedDecision} | ${row.candidateId} / ${row.origin} | ${row.targetId} | " +
                "${row.beforeDifficulty}→${row.afterDifficulty.score} | ${metrics.minimumSolutionLength}/" +
                "${metrics.forcedSequenceLength}/${metrics.meaningfulDecisionPoints}/${decimal(metrics.averageDecisionSpacing)}/" +
                "${metrics.maximumForcedRunLength} | ${choices.plausibleChoices}/${choices.immediatelyInvalidChoices}/" +
                "${choices.strategicallyViableChoices}/${choices.deceptiveButFairChoices}/${choices.guessDependentChoices} | " +
                "${row.afterQuality.score}/${row.afterQuality.status} | ${row.selectedNearNeighborLevelIds.size} | " +
                "${row.humanReviewPriority.score} | `${row.beforeFingerprint.take(15)}…` → `${row.afterFingerprint.take(15)}…` | " +
                "${row.manualReviewStatus} |",
        )
    }
    appendLine()
    appendLine("Promotion remains blocked until the owner reviews this mapping, representative boards from every band are playtested, every higher-band near neighbor is justified or replaced, and the fingerprint-aware best-record migration is implemented and proven safe.")
}

private fun proposedDistributionMarkdown(report: Phase0ProposedRemediationReport): String = buildString {
    appendLine("# Phase 0 proposed campaign distribution")
    appendLine()
    appendLine("Status: **AUTOMATED PROPOSAL — NOT HUMAN-APPROVED OR PROMOTED**")
    appendLine()
    appendLine("| Range | N | Avg / median / min–max v3 | Quality | Avg solution | Avg decisions | Avg branch | Avg forced | Avg dependency | Guess choices | Near-neighbor rows |")
    appendLine("|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|")
    phase0Ranges().forEach { (label, range) ->
        val rows = report.assignments.filter { it.campaignNumber in range }
        val scores = rows.map { it.afterDifficulty.score }.sorted()
        appendLine(
            "| $label | ${rows.size} | ${decimal(scores.average())}/${decimal(median(scores))}/${scores.min()}–${scores.max()} | " +
                "${rows.groupingBy { it.afterQuality.status.name }.eachCount().toSortedMap()} | " +
                "${decimal(rows.map { it.afterDifficulty.rawMetrics.minimumSolutionLength }.average())} | " +
                "${decimal(rows.map { it.afterDifficulty.rawMetrics.meaningfulDecisionPoints }.average())} | " +
                "${decimal(rows.map { it.afterDifficulty.rawMetrics.averageEffectiveBranchingFactor }.average())} | " +
                "${decimal(rows.map { it.afterDifficulty.rawMetrics.forcedMoveRatio }.average())} | " +
                "${decimal(rows.map { it.afterDifficulty.rawMetrics.dependencyDepth }.average())} | " +
                "${rows.sumOf { it.afterDifficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices }} | " +
                "${rows.count { it.selectedNearNeighborLevelIds.isNotEmpty() }} |",
        )
    }
}

private fun proposedHumanReviewMarkdown(report: Phase0ProposedRemediationReport): String = buildString {
    appendLine("# Phase 0 proposed campaign human review checklist")
    appendLine()
    appendLine("No assignment is human-approved. Review in priority order, play every row with a structural near-neighbor, and record any candidate replacement before promotion.")
    appendLine()
    appendLine("| Priority | # | Stable ID | Candidate | Decision | V3 | Quality | Near | Review reasons | Visual | Replay | Choices fair | Placement | Status | Notes |")
    appendLine("|---:|---:|---|---|---|---:|---|---:|---|---|---|---|---|---|---|")
    report.assignments.sortedWith(
        compareByDescending<Phase0RemediationAssignment> { it.humanReviewPriority.score }
            .thenByDescending { it.selectedNearNeighborLevelIds.size }
            .thenBy { it.campaignNumber },
    ).forEach { row ->
        val reasons = (
            row.humanReviewPriority.reasonCodes + row.afterQuality.reasonCodes +
                listOfNotNull(row.nearNeighborJustification)
            ).distinct().joinToString(" ")
        appendLine(
            "| ${row.humanReviewPriority.score} | ${row.campaignNumber} | ${row.levelId} | ${row.candidateId} | " +
                "${row.proposedDecision} | ${row.afterDifficulty.score} | ${row.afterQuality.score}/${row.afterQuality.status} | " +
                "${row.selectedNearNeighborLevelIds.size} | $reasons | ☐ | ☐ | ☐ | ☐ | ${row.manualReviewStatus} | |",
        )
    }
}

private fun approvedRemediationMarkdown(report: Phase0ProposedRemediationReport): String = buildString {
    appendLine("# Phase 0 approved campaign remediation")
    appendLine()
    appendLine("Status: **OWNER APPROVED AND PROMOTED**")
    appendLine()
    appendLine("- Owner approval: ${report.ownerApprovalStatus} on ${report.approvalDate}")
    appendLine("- Approved assignments: ${report.humanApprovalCount}/${report.assignments.size}")
    appendLine("- Automated approvals represented as human approvals: ${report.automatedApprovalCount}")
    appendLine("- Human playtesting: **${report.humanPlaytestStatus}**")
    appendLine("- Stable IDs: ${report.stableIdCount}/${report.assignments.size}")
    appendLine("- Exact/symmetry fingerprints: ${report.exactFingerprintCount}/${report.symmetryFingerprintCount}")
    appendLine()
    appendLine("Owner approval authorizes the recorded candidate mapping and its reported Quality exceptions. It does not claim that every board was manually played. Automated certification and owner approval remain distinct evidence.")
    appendLine()
    appendLine("| # | Stable ID | Decision | Candidate | V3 | Quality | Similar neighbors | Approval | Playtested |")
    appendLine("|---:|---|---|---|---:|---|---:|---|---|")
    report.assignments.forEach { row ->
        appendLine(
            "| ${row.campaignNumber} | ${row.levelId} | ${row.proposedDecision} | ${row.candidateId} | " +
                "${row.afterDifficulty.score} | ${row.afterQuality.score}/${row.afterQuality.status} | " +
                "${row.selectedNearNeighborLevelIds.size} | ${row.manualReviewStatus} | NO |",
        )
    }
}

private fun finalCertificationMarkdown(report: Phase0FinalCertificationReport): String = buildString {
    appendLine("# Phase 0 final certification")
    appendLine()
    appendLine("Status: **COMPLETE — HUMAN PLAYTEST PENDING**")
    appendLine()
    appendLine("The owner-approved 150-board mapping is promoted. Automated certification proves engine replay, metadata, target gates, structural checks, and migration identity; it does not claim human playtesting or player-experience approval.")
    appendLine()
    appendLine("- Catalog: `${report.catalogId}`; content ${report.contentVersion}; generator ${report.generatorVersion}")
    appendLine("- Production certification and complete Difficulty v3 search: ${report.certifiableCount}/${report.levels.size}")
    appendLine("- Difficulty target gates: ${report.gateAcceptedCount}/${report.levels.size}")
    appendLine("- Quality: ${report.qualityStatuses}; no rejected board")
    appendLine("- Exact/symmetry-unique boards: ${report.exactFingerprintCount}/${report.symmetryFingerprintCount}")
    appendLine("- Canonical guess-dependent choices: ${report.guessDependentChoiceCount}")
    appendLine("- Stable ID and v4→v5 fingerprint mappings: ${report.stableIdMigrationCount}/${report.previousFingerprintMappingCount}")
    appendLine("- Player preference migration schema: ${report.preferenceSchemaVersion}")
    appendLine("- Automated approvals recorded as human approval: ${report.automatedApprovalCount}")
    appendLine("- Owner-approved rows: ${report.ownerApprovedCount}/${report.levels.size}")
    appendLine("- Human-playtested rows: ${report.humanPlaytestedCount}/${report.levels.size}; status remains `PENDING`")
    appendLine()
    appendLine("Per-level solver, choice-quality, forced-sequence, purposeful-space, similarity, and review-priority evidence is in `PHASE0_FINAL_DIAGNOSTICS.json`. The ordered manual queue is in `PHASE0_FINAL_HUMAN_REVIEW_CHECKLIST.md`.")
}

private fun finalDistributionMarkdown(report: Phase0FinalCertificationReport): String = buildString {
    appendLine("# Phase 0 final campaign distribution")
    appendLine()
    appendLine("Status: **PROMOTED AND AUTOMATED-CERTIFIED — HUMAN PLAYTEST PENDING**")
    appendLine()
    appendLine("A long forced sequence is reported separately from a decision-making sequence; solution length is never used as a substitute for decision count.")
    appendLine()
    appendLine("| Range | N | Avg / median / min–max v3 | Quality | Avg solution | Avg forced sequence | Avg decisions | Avg decision spacing | Max forced run | Avg branching | Avg dependency | Choices P/I/V/F/G | Similar rows |")
    appendLine("|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|")
    phase0Ranges().forEach { (label, range) ->
        val rows = report.levels.filter { it.campaignNumber in range }
        val scores = rows.map { it.difficulty.score }.sorted()
        val metrics = rows.map { it.difficulty.rawMetrics }
        appendLine(
            "| $label | ${rows.size} | ${decimal(scores.average())}/${decimal(median(scores))}/${scores.min()}–${scores.max()} | " +
                "${rows.groupingBy { it.quality.status.name }.eachCount().toSortedMap()} | " +
                "${decimal(metrics.map { it.minimumSolutionLength }.average())} | " +
                "${decimal(metrics.map { it.forcedSequenceLength }.average())} | " +
                "${decimal(metrics.map { it.meaningfulDecisionPoints }.average())} | " +
                "${decimal(metrics.map { it.averageDecisionSpacing }.average())} | " +
                "${metrics.maxOf { it.maximumForcedRunLength }} | " +
                "${decimal(metrics.map { it.averageEffectiveBranchingFactor }.average())} | " +
                "${decimal(metrics.map { it.dependencyDepth }.average())} | " +
                "${metrics.sumOf { it.canonicalChoiceMetrics.plausibleChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.immediatelyInvalidChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.strategicallyViableChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.deceptiveButFairChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.guessDependentChoices }} | " +
                "${rows.count { it.structuralNearNeighborIds.isNotEmpty() }} |",
        )
    }
}

private fun finalHumanReviewMarkdown(report: Phase0FinalCertificationReport): String = buildString {
    appendLine("# Phase 0 final human review priority")
    appendLine()
    appendLine("Owner approval authorized promotion. It is not a playtest result. Every row below remains `PENDING` until a person plays and evaluates it from the player's available information.")
    appendLine()
    appendLine("| Priority | # | ID | Decision | Board / A-M-W | V3 / confidence | Solution / forced / decisions / spacing / max run | Choices P/I/V/F/G | Quality / margin | Near | Reasons | Playtested | Notes |")
    appendLine("|---:|---:|---|---|---|---|---|---|---|---:|---|---|---|")
    report.levels.sortedWith(
        compareByDescending<Phase0FinalLevelCertification> { it.humanReviewPriority.score }
            .thenByDescending { it.structuralNearNeighborIds.size }
            .thenBy { it.campaignNumber },
    ).forEach { row ->
        val metrics = row.difficulty.rawMetrics
        val choices = metrics.canonicalChoiceMetrics
        val reasons = (row.humanReviewPriority.reasonCodes + row.quality.reasonCodes).distinct().joinToString(" ")
        appendLine(
            "| ${row.humanReviewPriority.score} | ${row.campaignNumber} | ${row.levelId} | ${row.approvedDecision} | " +
                "${row.width}x${row.height} / ${row.arrowCount}-${row.magnetCount}-${row.wallCount} | " +
                "${row.difficulty.score}/${decimal(row.difficulty.confidence)} | " +
                "${metrics.minimumSolutionLength}/${metrics.forcedSequenceLength}/" +
                "${metrics.meaningfulDecisionPoints}/${decimal(metrics.averageDecisionSpacing)}/" +
                "${metrics.maximumForcedRunLength} | ${choices.plausibleChoices}/" +
                "${choices.immediatelyInvalidChoices}/${choices.strategicallyViableChoices}/" +
                "${choices.deceptiveButFairChoices}/${choices.guessDependentChoices} | " +
                "${row.quality.score}/${row.quality.marginAboveReview} | " +
                "${row.structuralNearNeighborIds.size} | $reasons | ${row.humanPlaytestStatus} | |",
        )
    }
}

private fun reviewPriority(
    difficulty: DifficultyScoreV3,
    quality: PuzzleQualityScoreV2,
    similarityCount: Int,
    novelStructure: Boolean,
): HumanReviewPriorityScore = HumanReviewPriorityScorer.score(
    HumanReviewPriorityFactors(
        difficultyConfidence = difficulty.confidence,
        solverTruncated = !difficulty.rawMetrics.searchComplete,
        unusualBranchingSeverity = (
            abs(difficulty.rawMetrics.averageEffectiveBranchingFactor - 1.75) / 1.75
            ).coerceIn(0.0, 1.0),
        extremeDifficultySeverity = extremeDifficultySeverity(difficulty.score),
        qualityMarginSeverity = (
            1.0 - quality.marginAboveReview.coerceAtLeast(0) / 25.0
            ).coerceIn(0.0, 1.0),
        novelStructuralPattern = novelStructure,
        structuralSimilaritySeverity = (similarityCount / 5.0).coerceIn(0.0, 1.0),
        newMechanicInteraction = false,
        unusualSolutionDepthSeverity = (
            abs(difficulty.rawMetrics.minimumSolutionLength - 5) / 5.0
            ).coerceIn(0.0, 1.0),
    ),
)

private fun remediationDecision(
    row: Phase0InitialAnalysis,
    quality: PuzzleQualityScoreV2,
    similarityIds: List<String>,
): Pair<String, List<String>> {
    val reasons = (row.gate.reasonCodes + quality.reasonCodes +
        similarityIds.takeIf { it.isNotEmpty() }?.let { listOf("STRUCTURAL_SIMILARITY_CLUSTER") }.orEmpty())
        .distinct().sorted()
    val hardStructural = row.gate.reasonCodes.any {
        it in setOf(
            DifficultyGateReason.SEARCH_NOT_CERTIFIABLE,
            DifficultyGateReason.SOLUTION_TOO_SHORT,
            DifficultyGateReason.INSUFFICIENT_DECISIONS,
            DifficultyGateReason.INSUFFICIENT_DEPENDENCY_DEPTH,
            DifficultyGateReason.INSUFFICIENT_EFFECTIVE_BRANCHING,
            DifficultyGateReason.EXCESSIVE_FORCEDNESS,
            DifficultyGateReason.INSUFFICIENT_MECHANIC_RELEVANCE,
            DifficultyGateReason.GUESS_DEPENDENT,
        )
    }
    val decision = when {
        row.gate.accepted && quality.status == PuzzleQualityStatusV2.ACCEPT && similarityIds.isEmpty() -> "KEEP"
        quality.status == PuzzleQualityStatusV2.REJECT || hardStructural ||
            (row.level.number >= 41 && row.difficulty.rawMetrics.meaningfulDecisionPoints == 0) -> "REPLACE"
        else -> "TUNE"
    }
    return decision to reasons
}

private fun structuralDistance(first: DifficultyScoreV3, second: DifficultyScoreV3): Double {
    val a = first.rawMetrics
    val b = second.rawMetrics
    val values = listOf(
        abs(a.minimumSolutionLength - b.minimumSolutionLength) / 7.0,
        abs(a.meaningfulDecisionPoints - b.meaningfulDecisionPoints) / 4.0,
        abs(a.dependencyDepth - b.dependencyDepth) / 4.0,
        abs(a.averageEffectiveBranchingFactor - b.averageEffectiveBranchingFactor) / 2.5,
        abs(a.forcedMoveRatio - b.forcedMoveRatio),
        abs(a.mechanicRelevanceRatio - b.mechanicRelevanceRatio),
        abs(a.solutionFamilyCount - b.solutionFamilyCount).coerceAtMost(32) / 32.0,
    )
    return values.average()
}

private fun templateDistance(
    row: Phase0LevelDiagnostic,
    target: PuzzleDifficultyTarget,
): Double {
    val metrics = row.difficulty.rawMetrics
    // Compaction and purposeful route blockers usually raise structural complexity. Start
    // below the final band instead of choosing already-at-target templates and overshooting.
    val targetMidpoint = (target.minimumScore - 8).coerceAtLeast(3).toDouble()
    val nonForced = 1.0 - metrics.forcedMoveRatio
    return abs(row.difficulty.score - targetMidpoint) +
        (target.minSolutionLength - metrics.minimumSolutionLength).coerceAtLeast(0) * 10.0 +
        (target.minMeaningfulDecisions - metrics.meaningfulDecisionPoints).coerceAtLeast(0) * 18.0 +
        (target.minDependencyDepth - metrics.dependencyDepth).coerceAtLeast(0) * 18.0 +
        (target.minEffectiveBranching - metrics.averageEffectiveBranchingFactor).coerceAtLeast(0.0) * 20.0 +
        (target.minNonForcedPortion - nonForced).coerceAtLeast(0.0) * 30.0 +
        (target.minMechanicRelevance - metrics.mechanicRelevanceRatio).coerceAtLeast(0.0) * 20.0 +
        metrics.canonicalChoiceMetrics.guessDependentChoices * 12.0
}

private fun normalizedOutlier(value: Double, average: Double, span: Double): Double =
    if (span <= 0.0) 0.0 else (abs(value - average) / (span / 2.0)).coerceIn(0.0, 1.0)

private fun extremeDifficultySeverity(score: Int): Double =
    ((score - 80).coerceAtLeast(0) / 20.0).coerceIn(0.0, 1.0)

private fun rangeSummaries(rows: List<Phase0LevelDiagnostic>): List<Phase0RangeSummary> =
    phase0Ranges().map { (label, range) ->
        val group = rows.filter { it.campaignNumber in range }
        val scores = group.map { it.difficulty.score }.sorted()
        Phase0RangeSummary(
            displayedRange = label,
            levelCount = group.size,
            averageDifficulty = decimalValue(scores.average()),
            medianDifficulty = decimalValue(median(scores)),
            minimumDifficulty = scores.minOrNull() ?: 0,
            maximumDifficulty = scores.maxOrNull() ?: 0,
            difficultyBands = group.groupingBy { it.difficulty.band.displayName }.eachCount().toSortedMap(),
            qualityStatuses = group.groupingBy { it.quality.status.name }.eachCount().toSortedMap(),
            decisions = group.groupingBy { it.proposedDecision }.eachCount().toSortedMap(),
            averageMinimumSolutionLength = decimalValue(group.map { it.difficulty.rawMetrics.minimumSolutionLength }.average()),
            averageMeaningfulDecisionPoints = decimalValue(group.map { it.difficulty.rawMetrics.meaningfulDecisionPoints }.average()),
            averageEffectiveBranching = decimalValue(group.map { it.difficulty.rawMetrics.averageEffectiveBranchingFactor }.average()),
            averageForcedMoveRatio = decimalValue(group.map { it.difficulty.rawMetrics.forcedMoveRatio }.average()),
            averageDependencyDepth = decimalValue(group.map { it.difficulty.rawMetrics.dependencyDepth }.average()),
            averageGuessDependentChoices = decimalValue(
                group.map { it.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices }.average(),
            ),
            averageRawEmptySpaceRatio = decimalValue(group.map { it.difficulty.rawMetrics.purposefulSpace.rawEmptySpaceRatio }.average()),
            averageUnusedEmptySpaceRatio = decimalValue(group.map { it.difficulty.rawMetrics.purposefulSpace.unusedEmptySpaceRatio }.average()),
            averageIrrelevantEntityRatio = decimalValue(
                group.map { it.difficulty.rawMetrics.purposefulSpace.irrelevantEntityRatio }.average(),
            ),
            outlierLevelIds = group.filter { !it.gate.accepted || it.quality.status != PuzzleQualityStatusV2.ACCEPT }
                .map { it.levelId },
        )
    }

private fun remediationMarkdown(report: Phase0AnalysisReport): String = buildString {
    val decisions = report.levels.groupingBy { it.proposedDecision }.eachCount()
    appendLine("# Phase 0 proposed remediation manifest")
    appendLine()
    appendLine("Status: **STAGED — OWNER REVIEW REQUIRED BEFORE CONTENT PROMOTION**")
    appendLine()
    appendLine("This report analyzes the current catalog only. It did not overwrite any campaign board. Automated acceptance is not human approval; every manual status remains `PENDING`.")
    appendLine()
    appendLine("- Catalog: `${report.catalogId}` / content ${report.contentVersion}")
    appendLine("- Analyzer: `${report.difficultyConfig.analyzerVersion}`")
    appendLine("- Proposed decisions: $decisions")
    appendLine("- Certifiable complete searches: ${report.levels.count { it.difficulty.certifiable }}/${report.levels.size}")
    appendLine()
    appendLine("| # | ID | Target | V3 | Length / forced / decisions / spacing / max run | Effective branch | Dependency | Choices P/I/V/F/G | Empty / unused / irrelevant objects | Quality | Similar | Proposal | Priority | Human |")
    appendLine("|---:|---|---|---:|---|---:|---:|---|---|---|---:|---|---:|---|")
    report.levels.forEach { row ->
        val metrics = row.difficulty.rawMetrics
        val choices = metrics.canonicalChoiceMetrics
        appendLine(
            "| ${row.campaignNumber} | ${row.levelId} | ${row.target.id} ${row.target.minimumScore}–${row.target.maximumScore} | " +
                "${row.difficulty.score}/${row.difficulty.band.displayName} | ${metrics.minimumSolutionLength}/" +
                "${metrics.forcedSequenceLength}/${metrics.meaningfulDecisionPoints}/${decimal(metrics.averageDecisionSpacing)}/" +
                "${metrics.maximumForcedRunLength} | ${decimal(metrics.averageEffectiveBranchingFactor)} | " +
                "${metrics.dependencyDepth} | ${choices.plausibleChoices}/${choices.immediatelyInvalidChoices}/" +
                "${choices.strategicallyViableChoices}/${choices.deceptiveButFairChoices}/${choices.guessDependentChoices} | " +
                "${percent(metrics.purposefulSpace.rawEmptySpaceRatio)}/${percent(metrics.purposefulSpace.unusedEmptySpaceRatio)}/" +
                "${percent(metrics.purposefulSpace.irrelevantEntityRatio)} | " +
                "${row.quality.score}/${row.quality.status} | ${row.structuralSimilarityIds.size} | ${row.proposedDecision} | " +
                "${row.humanReviewPriority.score} | ${row.manualReviewStatus} |",
        )
    }
    appendLine()
    appendLine("Promotion boundary: generate and analyze replacement/tuning candidates, update this manifest with selected candidate IDs and fingerprints, show the proposal to the owner, and promote only after explicit approval.")
}

private fun distributionMarkdown(report: Phase0AnalysisReport): String = buildString {
    appendLine("# Phase 0 current Difficulty v3 distribution")
    appendLine()
    appendLine("These are pre-remediation measurements. Averages do not waive per-level outliers listed in the JSON report and remediation manifest.")
    appendLine()
    appendLine("| Range | N | Avg / median / min–max | Bands | Avg solution | Avg decisions | Avg effective branch | Avg forced | Avg dependency | Avg guess choices | Empty / unused / irrelevant objects | Proposals | Outliers |")
    appendLine("|---|---:|---|---|---:|---:|---:|---:|---:|---:|---|---|---:|")
    report.rangeSummaries.forEach { row ->
        appendLine(
            "| ${row.displayedRange} | ${row.levelCount} | ${row.averageDifficulty}/${row.medianDifficulty}/${row.minimumDifficulty}–${row.maximumDifficulty} | " +
                "${row.difficultyBands} | ${row.averageMinimumSolutionLength} | ${row.averageMeaningfulDecisionPoints} | " +
                "${row.averageEffectiveBranching} | ${row.averageForcedMoveRatio} | ${row.averageDependencyDepth} | " +
                "${row.averageGuessDependentChoices} | ${percent(row.averageRawEmptySpaceRatio)}/${percent(row.averageUnusedEmptySpaceRatio)}/" +
                "${percent(row.averageIrrelevantEntityRatio)} | " +
                "${row.decisions} | ${row.outlierLevelIds.size} |",
        )
    }
}

private fun humanReviewMarkdown(report: Phase0AnalysisReport): String = buildString {
    appendLine("# Phase 0 human review priority checklist")
    appendLine()
    appendLine("No row is human-approved. Work from the highest priority downward; all Hard/Very Hard target outliers and representatives from every range are included.")
    appendLine()
    appendLine("| Priority | # | ID | Proposal | V3 / confidence | Quality / margin | Review reasons | Visual | Replay | Choices fair | Placement | Status | Reviewer notes |")
    appendLine("|---:|---:|---|---|---|---|---|---|---|---|---|---|---|")
    report.levels.sortedWith(compareByDescending<Phase0LevelDiagnostic> { it.humanReviewPriority.score }.thenBy { it.campaignNumber })
        .forEach { row ->
            appendLine(
                "| ${row.humanReviewPriority.score} | ${row.campaignNumber} | ${row.levelId} | ${row.proposedDecision} | " +
                    "${row.difficulty.score}/${decimal(row.difficulty.confidence)} | ${row.quality.score}/${row.quality.marginAboveReview} | " +
                    "${(row.humanReviewPriority.reasonCodes + row.decisionReasons).distinct().joinToString(" ")} | ☐ | ☐ | ☐ | ☐ | PENDING | |",
            )
        }
}

private fun phase0Ranges(): List<Pair<String, IntRange>> = listOf(
    "1–10" to 1..10,
    "11–25" to 11..25,
    "26–40" to 26..40,
    "41–60" to 41..60,
    "61–80" to 61..80,
    "81–100" to 81..100,
    "101–125" to 101..125,
    "126–150" to 126..150,
)

private fun median(values: List<Int>): Double = when {
    values.isEmpty() -> 0.0
    values.size % 2 == 1 -> values[values.size / 2].toDouble()
    else -> (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
}

private fun decimal(value: Double): String = "%.4f".format(Locale.ROOT, value)

private fun decimalValue(value: Double): Double = decimal(value).toDouble()

private fun percent(value: Double): String = "%.1f%%".format(Locale.ROOT, value * 100.0)
