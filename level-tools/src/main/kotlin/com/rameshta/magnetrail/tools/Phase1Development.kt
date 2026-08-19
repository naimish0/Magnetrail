package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyGateResult
import com.rameshta.magnetrail.core.difficulty.DifficultyScoreV3
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Config
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Gate
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Scorer
import com.rameshta.magnetrail.core.difficulty.HumanReviewPriorityFactors
import com.rameshta.magnetrail.core.difficulty.HumanReviewPriorityScore
import com.rameshta.magnetrail.core.difficulty.HumanReviewPriorityScorer
import com.rameshta.magnetrail.core.difficulty.Phase1DifficultyTargets
import com.rameshta.magnetrail.core.difficulty.PuzzleDifficultyTarget
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityAnalyzerV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityScoreV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityStatusV2
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchAnalyzer
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchConfig
import com.rameshta.magnetrail.core.generation.CertificationPipeline
import com.rameshta.magnetrail.core.generation.CertificationRequest
import com.rameshta.magnetrail.core.generation.CertificationResult
import com.rameshta.magnetrail.core.generation.GenerationProfile
import com.rameshta.magnetrail.core.generation.GenerationRequest
import com.rameshta.magnetrail.core.generation.GenerationResult
import com.rameshta.magnetrail.core.generation.LevelGenerator
import com.rameshta.magnetrail.core.generation.PHASE0_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.PHASE0_GENERATOR_VERSION
import com.rameshta.magnetrail.core.generation.PHASE1_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.PHASE1_GENERATOR_VERSION
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.abs

@Serializable
data class Phase1CandidateDiagnostic(
    val candidateId: String,
    val seed: Long,
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
    val nearPoolCandidateIds: List<String>,
    val nearExistingLevelIds: List<String>,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
    val quality: PuzzleQualityScoreV2,
    val humanReviewPriority: HumanReviewPriorityScore,
    val manualReviewStatus: String = "PENDING",
)

@Serializable
data class Phase1RejectedExample(
    val seed: Long,
    val targetId: String,
    val reasonCodes: List<String>,
)

@Serializable
data class Phase1CandidatePoolReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "phase1-candidate-pool-v1",
    val status: String = "STAGED_NOT_APPROVED_OR_PROMOTED",
    val sourceContentVersion: Int,
    val sourceGeneratorVersion: Int,
    val candidateContentVersion: Int = PHASE1_CONTENT_VERSION,
    val candidateGeneratorVersion: Int = PHASE1_GENERATOR_VERSION,
    val initialSeed: Long,
    val finalSeed: Long,
    val targetQuotas: Map<String, Int>,
    val producedByTarget: Map<String, Int>,
    val rejectionCounts: Map<String, Int>,
    val rejectedExamples: List<Phase1RejectedExample>,
    val candidates: List<Phase1CandidateDiagnostic>,
    val exactFingerprintCount: Int,
    val symmetryFingerprintCount: Int,
    val constructionComplete: Boolean,
    val humanApprovalCount: Int = 0,
)

@Serializable
data class Phase1ProposedAssignment(
    val campaignNumber: Int,
    val levelId: String,
    val candidateId: String,
    val candidateSeed: Long,
    val proposedDecision: String,
    val curationMode: String,
    val origin: String,
    val packId: String,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val target: PuzzleDifficultyTarget,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
    val quality: PuzzleQualityScoreV2,
    val nearSelectedLevelIds: List<String>,
    val nearExistingLevelIds: List<String>,
    val similarityDisposition: String?,
    val humanReviewPriority: HumanReviewPriorityScore,
    val manualReviewStatus: String = "PENDING",
)

@Serializable
data class Phase1ProposalReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "phase1-proposal-v1",
    val status: String = "PROPOSED_NOT_APPROVED_OR_PROMOTED",
    val sourceCatalogId: String,
    val sourceContentVersion: Int,
    val proposedContentVersion: Int = PHASE1_CONTENT_VERSION,
    val proposedGeneratorVersion: Int = PHASE1_GENERATOR_VERSION,
    val candidatePoolSize: Int,
    val assignments: List<Phase1ProposedAssignment>,
    val decisionCounts: Map<String, Int>,
    val curationModeCounts: Map<String, Int>,
    val originCounts: Map<String, Int>,
    val existingLevelsPreserved: Int,
    val proposedCampaignLevelCount: Int,
    val exactFingerprintCount: Int,
    val symmetryFingerprintCount: Int,
    val targetGateAcceptedCount: Int,
    val certifiableCount: Int,
    val guessDependentChoiceCount: Int,
    val qualityStatuses: Map<String, Int>,
    val automatedApprovalCount: Int = 0,
    val humanApprovalCount: Int = 0,
    val ownerApprovalStatus: String = "PENDING",
    val humanPlaytestStatus: String = "NOT_PERFORMED",
)

@Serializable
data class Phase1ContentMigrationReport(
    val schemaVersion: Int = 1,
    val migrationId: String = "campaign-content-v5-to-v6",
    val sourceContentVersion: Int,
    val targetContentVersion: Int,
    val sourceGeneratorVersion: Int,
    val targetGeneratorVersion: Int,
    val preservedExistingLevelIds: Int,
    val preservedExistingFingerprints: Int,
    val addedLevelIds: List<String>,
    val preferenceSchemaVersion: Int = 6,
    val completion150Unlocks151: Boolean = true,
    val level200StopsWithout201: Boolean = true,
    val preservesProgressRewardsEconomyDailySettingsConsentAndAds: Boolean = true,
)

@Serializable
data class Phase1FinalLevelCertification(
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
    val target: PuzzleDifficultyTarget,
    val productionCertificationAccepted: Boolean,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
    val quality: PuzzleQualityScoreV2,
    val structuralNearNewLevelIds: List<String>,
    val structuralNearExistingLevelIds: List<String>,
    val similarityDisposition: String?,
    val humanReviewPriority: HumanReviewPriorityScore,
    val ownerApprovalStatus: String,
    val humanPlaytestStatus: String,
)

@Serializable
data class Phase1FinalCertificationReport(
    val schemaVersion: Int = 1,
    val reportVersion: String = "phase1-final-certification-v1",
    val status: String = "COMPLETE_HUMAN_PLAYTEST_PENDING",
    val catalogId: String,
    val contentVersion: Int,
    val generatorVersion: Int,
    val campaignLevelCount: Int,
    val levels: List<Phase1FinalLevelCertification>,
    val fullCampaignExactFingerprintCount: Int,
    val fullCampaignSymmetryFingerprintCount: Int,
    val fullCampaignDifficultyCertifiableCount: Int,
    val productionCertificationAcceptedCount: Int,
    val targetGateAcceptedCount: Int,
    val qualityStatuses: Map<String, Int>,
    val fullCampaignGuessDependentChoiceCount: Int,
    val preservedExistingStableIdCount: Int,
    val preservedExistingFingerprintCount: Int,
    val addedStableIdCount: Int,
    val progressionGatePassed: Boolean,
    val level200FinaleGatePassed: Boolean,
    val preferenceSchemaVersion: Int,
    val automatedApprovalCount: Int,
    val ownerApprovedCount: Int,
    val humanPlaytestedCount: Int,
)

private data class Phase1RawCandidate(
    val level: LevelDefinition,
    val seed: Long,
    val target: PuzzleDifficultyTarget,
    val difficulty: DifficultyScoreV3,
    val gate: DifficultyGateResult,
)

fun stagePhase1Expansion(options: Map<String, String>) {
    val campaignFile = File(requireNotNull(options["campaign"]) { "Missing --campaign" })
    val output = File(requireNotNull(options["output"]) { "Missing --output" }).also { it.mkdirs() }
    val campaign = LevelParser().parseCatalog(campaignFile.readText())
    require(campaign.levels.size == 150) { "Phase 1 staging requires the frozen 150-level campaign" }
    require(campaign.contentVersion == PHASE0_CONTENT_VERSION)
    require(campaign.generatorVersion == PHASE0_GENERATOR_VERSION)

    val initialSeed = options["seed"]?.toLong() ?: 8_100_001L
    val oversizeMultiplier = options["oversize-multiplier"]?.toInt() ?: 4
    val attemptsPerTarget = options["attempts-per-target"]?.toInt() ?: 30_000
    require(oversizeMultiplier >= 3) { "Phase 1 candidate pool must be at least 3x oversized" }
    require(attemptsPerTarget > 0)

    val searchConfig = PuzzleSearchConfig()
    val difficultyConfig = DifficultyV3Config()
    val analyzer = PuzzleSearchAnalyzer(config = searchConfig)
    val qualityAnalyzer = PuzzleQualityAnalyzerV2()
    val stagingCertification = CertificationPipeline()
    val sourceDifficulty = campaign.levels.associate { level ->
        level.id to DifficultyV3Scorer.score(analyzer.analyze(level), difficultyConfig, searchConfig)
    }
    val futureNumbersByTarget = (151..200).groupBy {
        Phase1DifficultyTargets.forCampaignNumber(it).id
    }.toSortedMap()
    val targetQuotas = futureNumbersByTarget.mapValues { (_, values) -> values.size * oversizeMultiplier }
    val targetById = futureNumbersByTarget.mapValues { (_, values) ->
        Phase1DifficultyTargets.forCampaignNumber(values.first())
    }
    val existingExact = campaign.levels.mapTo(mutableSetOf(), ContentFingerprint::exact)
    val existingSymmetry = campaign.levels.mapTo(mutableSetOf(), ContentFingerprint::symmetryNormalized)
    val acceptedExact = mutableSetOf<String>()
    val acceptedSymmetry = mutableSetOf<String>()
    val raw = mutableListOf<Phase1RawCandidate>()
    val rejectionCounts = linkedMapOf<String, Int>()
    val rejectedExamples = mutableListOf<Phase1RejectedExample>()
    var seed = initialSeed

    targetQuotas.forEach { (targetId, quota) ->
        val target = requireNotNull(targetById[targetId])
        val eligibleTemplates = campaign.levels.filter { level ->
            val score = requireNotNull(sourceDifficulty[level.id])
            score.certifiable && DifficultyV3Gate.evaluate(score, target).accepted
        }.ifEmpty {
            campaign.levels.filter { level ->
                val score = requireNotNull(sourceDifficulty[level.id])
                score.certifiable && score.score in (target.minimumScore - 8).coerceAtLeast(0)..
                    (target.maximumScore + 5).coerceAtMost(100) &&
                    score.rawMetrics.canonicalChoiceMetrics.guessDependentChoices == 0
            }
        }
        check(eligibleTemplates.isNotEmpty()) { "No frozen Phase 0 template can seed target $targetId" }
        val generator = LevelGenerator(eligibleTemplates)
        val targetStart = seed
        var produced = 0
        while (produced < quota && seed - targetStart < attemptsPerTarget) {
            val candidateId = "${targetId.removePrefix("phase1-")}-$seed"
            val request = GenerationRequest(
                stableId = candidateId,
                sequenceNumber = futureNumbersByTarget.getValue(targetId).first(),
                title = "Phase 1 staged candidate $seed",
                seed = seed,
                profile = GenerationProfile.PHASE1_ADVANCED_V3,
                packId = "phase1-staging",
                origin = LevelOrigin.GENERATOR_ASSISTED,
                contentVersion = PHASE1_CONTENT_VERSION,
                generatorVersion = PHASE1_GENERATOR_VERSION,
            )
            when (val generated = generator.generate(request)) {
                is GenerationResult.Exhausted -> {
                    generated.rejectedReasons.forEach { (reason, count) ->
                        rejectionCounts.incrementPhase1("GENERATOR:$reason", count)
                    }
                }
                is GenerationResult.Generated -> {
                    val tunedBoard = phase1TunePurposefulSpace(
                        source = generated.level.copy(metadata = null),
                        target = target,
                        analyzer = analyzer,
                        difficultyConfig = difficultyConfig,
                        searchConfig = searchConfig,
                        qualityAnalyzer = qualityAnalyzer,
                        seed = seed,
                    )
                    val tunedCertification = stagingCertification.certify(
                        tunedBoard,
                        CertificationRequest(
                            profile = GenerationProfile.PHASE1_ADVANCED_V3,
                            origin = LevelOrigin.GENERATOR_ASSISTED,
                            packId = "phase1-staging",
                            generatorVersion = PHASE1_GENERATOR_VERSION,
                            generatorSeed = seed,
                            generationProfile = GenerationProfile.PHASE1_ADVANCED_V3.profileId,
                            contentVersion = PHASE1_CONTENT_VERSION,
                        ),
                    )
                    if (tunedCertification !is CertificationResult.Accepted) {
                        rejectionCounts.incrementPhase1("PURPOSEFUL_TUNING_RECERTIFICATION_FAILED")
                        seed += 1
                        continue
                    }
                    val level = tunedCertification.level
                    val difficulty = DifficultyV3Scorer.score(
                        analyzer.analyze(level),
                        difficultyConfig,
                        searchConfig,
                    )
                    val gate = DifficultyV3Gate.evaluate(difficulty, target)
                    val reasons = buildList {
                        if (level.width !in 6..7 || level.height !in 6..7) add("BOARD_SIZE_OUTSIDE_6_OR_7")
                        if (!difficulty.certifiable) add("SEARCH_NOT_CERTIFIABLE")
                        addAll(gate.reasonCodes)
                        if (difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices > 0) {
                            add("GUESS_DEPENDENT_CANONICAL_CHOICE")
                        }
                        if (difficulty.rawMetrics.purposefulSpace.irrelevantEntityRatio > 0.20) {
                            add("IRRELEVANT_ENTITY_RATIO")
                        }
                        if (difficulty.rawMetrics.purposefulSpace.unusedEmptySpaceRatio > 0.72) {
                            add("EXCESSIVE_UNUSED_EMPTY_SPACE")
                        }
                    }.distinct().sorted()
                    val exact = ContentFingerprint.exact(level)
                    val symmetry = ContentFingerprint.symmetryNormalized(level)
                    val duplicateReasons = buildList {
                        if (exact in existingExact || exact in acceptedExact) add("EXACT_DUPLICATE")
                        if (symmetry in existingSymmetry || symmetry in acceptedSymmetry) add("SYMMETRY_DUPLICATE")
                    }
                    val quality = qualityAnalyzer.analyze(
                        difficulty = difficulty,
                        gate = gate,
                        hardDuplicate = duplicateReasons.isNotEmpty(),
                    )
                    val allReasons = (reasons + duplicateReasons +
                        quality.reasonCodes.takeIf { quality.status == PuzzleQualityStatusV2.REJECT }.orEmpty())
                        .distinct().sorted()
                    if (allReasons.isEmpty()) {
                        raw += Phase1RawCandidate(level, seed, target, difficulty, gate)
                        acceptedExact += exact
                        acceptedSymmetry += symmetry
                        produced += 1
                    } else {
                        allReasons.forEach { rejectionCounts.incrementPhase1(it) }
                        if (rejectedExamples.size < 30) {
                            rejectedExamples += Phase1RejectedExample(seed, targetId, allReasons)
                        }
                    }
                }
            }
            seed += 1
        }
        check(produced == quota) {
            "Phase 1 target $targetId produced $produced/$quota candidates within $attemptsPerTarget seeds"
        }
    }

    val rawById = raw.associateBy { it.level.id }
    val poolNear = raw.associate { candidate ->
        candidate.level.id to raw.asSequence()
            .filterNot { it.level.id == candidate.level.id }
            .filter { other -> phase1StructurallyNear(candidate.difficulty, other.difficulty) }
            .map { it.level.id }
            .sorted()
            .toList()
    }
    val existingNear = raw.associate { candidate ->
        candidate.level.id to campaign.levels.asSequence()
            .filter { existing ->
                phase1StructurallyNear(candidate.difficulty, requireNotNull(sourceDifficulty[existing.id]))
            }
            .map(LevelDefinition::id)
            .sorted()
            .toList()
    }
    val patternCounts = raw.groupingBy { it.difficulty.rawMetrics.structuralPatternSignature }.eachCount()
    val diagnostics = raw.sortedWith(compareBy<Phase1RawCandidate> { it.target.id }.thenBy { it.seed }).map { candidate ->
        val nearPool = requireNotNull(poolNear[candidate.level.id])
        val nearExisting = requireNotNull(existingNear[candidate.level.id])
        val similarityCount = nearPool.size + nearExisting.size
        val quality = qualityAnalyzer.analyze(
            candidate.difficulty,
            candidate.gate,
            structuralSimilarityCount = similarityCount,
        )
        check(quality.status != PuzzleQualityStatusV2.REJECT) {
            "Accepted candidate ${candidate.level.id} became a Quality REJECT after similarity analysis"
        }
        Phase1CandidateDiagnostic(
            candidateId = candidate.level.id,
            seed = candidate.seed,
            targetId = candidate.target.id,
            width = candidate.level.width,
            height = candidate.level.height,
            arrowCount = candidate.level.arrows.size,
            magnetCount = candidate.level.magnets.size,
            wallCount = candidate.level.walls.size,
            origin = requireNotNull(candidate.level.metadata).origin.name,
            exactFingerprint = ContentFingerprint.exact(candidate.level),
            symmetryFingerprint = ContentFingerprint.symmetryNormalized(candidate.level),
            structuralPatternSignature = candidate.difficulty.rawMetrics.structuralPatternSignature,
            nearPoolCandidateIds = nearPool,
            nearExistingLevelIds = nearExisting,
            difficulty = candidate.difficulty,
            gate = candidate.gate,
            quality = quality,
            humanReviewPriority = phase1ReviewPriority(
                candidate.difficulty,
                quality,
                similarityCount,
                patternCounts.getValue(candidate.difficulty.rawMetrics.structuralPatternSignature) == 1,
            ),
        )
    }
    val poolReport = Phase1CandidatePoolReport(
        sourceContentVersion = campaign.contentVersion,
        sourceGeneratorVersion = requireNotNull(campaign.generatorVersion),
        initialSeed = initialSeed,
        finalSeed = seed - 1,
        targetQuotas = targetQuotas,
        producedByTarget = diagnostics.groupingBy { it.targetId }.eachCount().toSortedMap(),
        rejectionCounts = rejectionCounts.toSortedMap(),
        rejectedExamples = rejectedExamples,
        candidates = diagnostics,
        exactFingerprintCount = diagnostics.map { it.exactFingerprint }.toSet().size,
        symmetryFingerprintCount = diagnostics.map { it.symmetryFingerprint }.toSet().size,
        constructionComplete = diagnostics.size == targetQuotas.values.sum(),
    )
    check(poolReport.constructionComplete)

    val selectedDiagnostics = mutableListOf<Phase1CandidateDiagnostic>()
    val selectionByNumber = linkedMapOf<Int, Phase1CandidateDiagnostic>()
    futureNumbersByTarget.forEach { (targetId, numbers) ->
        val available = diagnostics.filter { it.targetId == targetId }.toMutableList()
        numbers.sorted().forEachIndexed { index, number ->
            val target = Phase1DifficultyTargets.forCampaignNumber(number)
            val fraction = if (numbers.size == 1) 0.5 else index.toDouble() / (numbers.size - 1)
            val desiredScore = target.minimumScore + (target.maximumScore - target.minimumScore) * fraction
            val selected = available.minWith(
                compareBy<Phase1CandidateDiagnostic> { candidate ->
                    selectedDiagnostics.count { other ->
                        phase1StructurallyNear(
                            requireNotNull(rawById[candidate.candidateId]).difficulty,
                            requireNotNull(rawById[other.candidateId]).difficulty,
                        )
                    }
                }.thenBy { if (it.quality.status == PuzzleQualityStatusV2.ACCEPT) 0 else 1 }
                    .thenBy { it.nearExistingLevelIds.size }
                    .thenBy { abs(it.difficulty.score - desiredScore) }
                    .thenBy { it.humanReviewPriority.score }
                    .thenBy { it.seed },
            )
            available.remove(selected)
            selectedDiagnostics += selected
            selectionByNumber[number] = selected
        }
    }

    val certification = CertificationPipeline()
    val proposedLevels = (151..200).map { number ->
        val selected = requireNotNull(selectionByNumber[number])
        val rawCandidate = requireNotNull(rawById[selected.candidateId])
        val target = Phase1DifficultyTargets.forCampaignNumber(number)
        val base = rawCandidate.level.copy(
            id = phase1LevelId(number),
            number = number,
            title = phase1Title(number),
            metadata = null,
        )
        val certified = certification.certify(
            base,
            CertificationRequest(
                profile = GenerationProfile.PHASE1_ADVANCED_V3,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                packId = phase1Pack(number),
                generatorVersion = PHASE1_GENERATOR_VERSION,
                generatorSeed = selected.seed,
                generationProfile = GenerationProfile.PHASE1_ADVANCED_V3.profileId,
                contentVersion = PHASE1_CONTENT_VERSION,
            ),
        )
        check(certified is CertificationResult.Accepted) {
            "Selected candidate failed stable-ID recertification for level $number: " +
                (certified as CertificationResult.Rejected).reasons.joinToString()
        }
        val level = certified.level
        val difficulty = DifficultyV3Scorer.score(analyzer.analyze(level), difficultyConfig, searchConfig)
        val gate = DifficultyV3Gate.evaluate(difficulty, target)
        check(difficulty.certifiable && gate.accepted) {
            "Selected level $number failed target recertification: ${gate.reasonCodes}"
        }
        level
    }
    val proposedDifficulty = proposedLevels.associate { level ->
        level.id to DifficultyV3Scorer.score(analyzer.analyze(level), difficultyConfig, searchConfig)
    }
    val selectedNear = proposedLevels.associate { level ->
        val difficulty = requireNotNull(proposedDifficulty[level.id])
        level.id to proposedLevels.asSequence()
            .filterNot { it.id == level.id }
            .filter { other -> phase1StructurallyNear(difficulty, requireNotNull(proposedDifficulty[other.id])) }
            .map(LevelDefinition::id)
            .sorted()
            .toList()
    }
    val selectedExistingNear = proposedLevels.associate { level ->
        val difficulty = requireNotNull(proposedDifficulty[level.id])
        level.id to campaign.levels.asSequence()
            .filter { phase1StructurallyNear(difficulty, requireNotNull(sourceDifficulty[it.id])) }
            .map(LevelDefinition::id)
            .sorted()
            .toList()
    }
    val selectedPatternCounts = proposedDifficulty.values
        .groupingBy { it.rawMetrics.structuralPatternSignature }
        .eachCount()
    val tuningReviewNumbers = setOf(155, 160, 165, 170, 175, 180, 185, 190, 195, 200)
    val assignments = proposedLevels.map { level ->
        val selected = requireNotNull(selectionByNumber[level.number])
        val target = Phase1DifficultyTargets.forCampaignNumber(level.number)
        val difficulty = requireNotNull(proposedDifficulty[level.id])
        val gate = DifficultyV3Gate.evaluate(difficulty, target)
        val nearSelectedIds = requireNotNull(selectedNear[level.id])
        val nearExistingIds = requireNotNull(selectedExistingNear[level.id])
        val quality = qualityAnalyzer.analyze(
            difficulty,
            gate,
            structuralSimilarityCount = nearSelectedIds.size + nearExistingIds.size,
        )
        check(quality.status != PuzzleQualityStatusV2.REJECT)
        val curationMode = if (level.number in tuningReviewNumbers) {
            "HEAVY_TUNING_REVIEW_SLOT"
        } else {
            "GENERATOR_ASSISTED_SELECTION"
        }
        val decision = when {
            level.number in tuningReviewNumbers -> "TUNE"
            quality.status == PuzzleQualityStatusV2.REVIEW -> "TUNE"
            else -> "KEEP"
        }
        val similarityDisposition = if (nearSelectedIds.isEmpty() && nearExistingIds.isEmpty()) {
            null
        } else {
            "PENDING_HUMAN_COMPARISON_OR_REPLACEMENT"
        }
        Phase1ProposedAssignment(
            campaignNumber = level.number,
            levelId = level.id,
            candidateId = selected.candidateId,
            candidateSeed = selected.seed,
            proposedDecision = decision,
            curationMode = curationMode,
            origin = requireNotNull(level.metadata).origin.name,
            packId = requireNotNull(level.metadata).packId,
            exactFingerprint = ContentFingerprint.exact(level),
            symmetryFingerprint = ContentFingerprint.symmetryNormalized(level),
            target = target,
            difficulty = difficulty,
            gate = gate,
            quality = quality,
            nearSelectedLevelIds = nearSelectedIds,
            nearExistingLevelIds = nearExistingIds,
            similarityDisposition = similarityDisposition,
            humanReviewPriority = phase1ReviewPriority(
                difficulty,
                quality,
                nearSelectedIds.size + nearExistingIds.size,
                selectedPatternCounts.getValue(difficulty.rawMetrics.structuralPatternSignature) == 1,
            ),
        )
    }
    val fullProposalLevels = campaign.levels + proposedLevels
    check(fullProposalLevels.map { it.number } == (1..200).toList())
    check(fullProposalLevels.take(150).map(ContentFingerprint::exact) == campaign.levels.map(ContentFingerprint::exact))
    val exactCount = fullProposalLevels.map(ContentFingerprint::exact).toSet().size
    val symmetryCount = fullProposalLevels.map(ContentFingerprint::symmetryNormalized).toSet().size
    check(exactCount == 200) { "Phase 1 proposal contains exact duplicates" }
    check(symmetryCount == 200) { "Phase 1 proposal contains symmetry duplicates" }
    check(assignments.all { it.gate.accepted && it.difficulty.certifiable })
    check(assignments.sumOf { it.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices } == 0)
    val finale = assignments.single { it.campaignNumber == 200 }
    check(
        finale.gate.accepted &&
            finale.difficulty.rawMetrics.meaningfulDecisionPoints >= 4 &&
            finale.difficulty.rawMetrics.dependencyDepth >= 3 &&
            finale.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices == 0
    ) {
        "Level 200 proposal is not a credible finale"
    }
    val proposal = Phase1ProposalReport(
        sourceCatalogId = campaign.catalogId,
        sourceContentVersion = campaign.contentVersion,
        candidatePoolSize = diagnostics.size,
        assignments = assignments,
        decisionCounts = assignments.groupingBy { it.proposedDecision }.eachCount().toSortedMap(),
        curationModeCounts = assignments.groupingBy { it.curationMode }.eachCount().toSortedMap(),
        originCounts = assignments.groupingBy { it.origin }.eachCount().toSortedMap(),
        existingLevelsPreserved = campaign.levels.zip(fullProposalLevels.take(150)).count { (old, proposed) ->
            old.id == proposed.id && ContentFingerprint.exact(old) == ContentFingerprint.exact(proposed)
        },
        proposedCampaignLevelCount = fullProposalLevels.size,
        exactFingerprintCount = exactCount,
        symmetryFingerprintCount = symmetryCount,
        targetGateAcceptedCount = assignments.count { it.gate.accepted },
        certifiableCount = assignments.count { it.difficulty.certifiable },
        guessDependentChoiceCount = assignments.sumOf {
            it.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices
        },
        qualityStatuses = assignments.groupingBy { it.quality.status.name }.eachCount().toSortedMap(),
    )

    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    File(output, "M5_3_CANDIDATE_POOL.json").writeText(json.encodeToString(poolReport))
    File(output, "M5_3_CANDIDATE_POOL.md").writeText(phase1CandidatePoolMarkdown(poolReport))
    File(output, "M5_3_PROPOSED_PROMOTION_MANIFEST.json").writeText(json.encodeToString(proposal))
    File(output, "M5_3_PROPOSED_PROMOTION_MANIFEST.md").writeText(phase1ProposalMarkdown(proposal))
    File(output, "M5_3_PROPOSED_DISTRIBUTION.md").writeText(phase1DistributionMarkdown(proposal))
    File(output, "M5_3_PROPOSED_DUPLICATE_REPORT.md").writeText(phase1DuplicateMarkdown(proposal))
    File(output, "M5_3_PROPOSED_PACING_REPORT.md").writeText(phase1PacingMarkdown(proposal))
    File(output, "M5_3_PROPOSED_MANUAL_REVIEW.md").writeText(phase1ManualReviewMarkdown(proposal))
    File(output, "M5_3_PROPOSED_MIGRATION.md").writeText(phase1MigrationMarkdown(proposal))
    File(output, "M5_3_STAGED_CANDIDATES.json").writeText(
        LevelParser().encodeCatalog(
            LevelCatalog(
                schemaVersion = 2,
                ruleVersion = campaign.ruleVersion,
                catalogId = "magnetrail-phase1-staged-candidates",
                levels = raw.mapIndexed { index, candidate ->
                    candidate.level.copy(number = index + 1)
                },
                contentVersion = PHASE1_CONTENT_VERSION,
                generatorVersion = PHASE1_GENERATOR_VERSION,
            ),
        ),
    )
    File(output, "M5_3_PROPOSED_CAMPAIGN_NOT_PROMOTED.json").writeText(
        LevelParser().encodeCatalog(
            LevelCatalog(
                schemaVersion = 2,
                ruleVersion = campaign.ruleVersion,
                catalogId = "magnetrail-phase1-proposed-campaign-not-promoted",
                levels = fullProposalLevels,
                contentVersion = PHASE1_CONTENT_VERSION,
                generatorVersion = PHASE1_GENERATOR_VERSION,
            ),
        ),
    )
    println(
        "Staged ${diagnostics.size} Phase 1 candidates and proposed 50 stable new IDs; " +
            "decisions=${proposal.decisionCounts}, campaign content was not modified, approvals=0.",
    )
}

fun promoteApprovedPhase1(options: Map<String, String>) {
    require(options["approval"] == "project-owner-approved") {
        "Phase 1 promotion requires explicit project-owner approval"
    }
    val campaignFile = File(requireNotNull(options["campaign"]))
    val sourceText = campaignFile.readText()
    val source = LevelParser().parseCatalog(sourceText)
    val proposalCatalog = LevelParser().parseCatalog(File(requireNotNull(options["proposal-catalog"])).readText())
    val output = File(requireNotNull(options["output"])).also { it.mkdirs() }
    val sourceSnapshot = File(requireNotNull(options["source-snapshot"]))
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    val proposal = json.decodeFromString<Phase1ProposalReport>(
        File(requireNotNull(options["proposal-report"])).readText(),
    )

    require(source.levels.size == 150 && source.contentVersion == PHASE0_CONTENT_VERSION)
    require(source.generatorVersion == PHASE0_GENERATOR_VERSION)
    require(proposalCatalog.levels.size == 200 && proposalCatalog.contentVersion == PHASE1_CONTENT_VERSION)
    require(proposalCatalog.generatorVersion == PHASE1_GENERATOR_VERSION)
    require(proposal.status == "PROPOSED_NOT_APPROVED_OR_PROMOTED")
    require(proposal.ownerApprovalStatus == "PENDING")
    require(proposal.humanApprovalCount == 0 && proposal.automatedApprovalCount == 0)
    require(proposal.humanPlaytestStatus == "NOT_PERFORMED")
    require(proposal.assignments.size == 50)

    val proposalFirst150 = proposalCatalog.levels.take(150)
    check(source.levels.map { it.id to ContentFingerprint.exact(it) } ==
        proposalFirst150.map { it.id to ContentFingerprint.exact(it) }) {
        "Phase 1 proposal altered Levels 1–150"
    }
    check(proposalCatalog.levels.map(LevelDefinition::number) == (1..200).toList())
    check(proposalCatalog.levels.drop(150).map(LevelDefinition::id) ==
        (151..200).map(::phase1LevelId))

    val assignmentById = proposal.assignments.associateBy(Phase1ProposedAssignment::levelId)
    check(assignmentById.size == 50)
    val analyzer = PuzzleSearchAnalyzer(config = PuzzleSearchConfig())
    val difficultyConfig = DifficultyV3Config()
    val certification = CertificationPipeline()
    val approvedNewLevels = proposalCatalog.levels.drop(150).map { proposed ->
        val assignment = requireNotNull(assignmentById[proposed.id])
        check(assignment.campaignNumber == proposed.number)
        check(assignment.exactFingerprint == ContentFingerprint.exact(proposed))
        check(assignment.symmetryFingerprint == ContentFingerprint.symmetryNormalized(proposed))
        check(assignment.gate.accepted && assignment.difficulty.certifiable)
        check(assignment.quality.status != PuzzleQualityStatusV2.REJECT)
        val metadata = requireNotNull(proposed.metadata)
        check(metadata.origin == LevelOrigin.GENERATOR_ASSISTED)
        check(metadata.contentVersion == PHASE1_CONTENT_VERSION)
        check(metadata.generatorVersion == PHASE1_GENERATOR_VERSION)
        check(metadata.generatorSeed == assignment.candidateSeed)
        check(metadata.previousContentFingerprint == null)
        val certified = certification.certify(
            proposed.copy(metadata = null),
            CertificationRequest(
                profile = GenerationProfile.PHASE1_ADVANCED_V3,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                packId = phase1Pack(proposed.number),
                generatorVersion = PHASE1_GENERATOR_VERSION,
                generatorSeed = assignment.candidateSeed,
                generationProfile = GenerationProfile.PHASE1_ADVANCED_V3.profileId,
                contentVersion = PHASE1_CONTENT_VERSION,
            ),
        )
        check(certified is CertificationResult.Accepted) {
            "Production certification rejected approved ${proposed.id}: " +
                (certified as CertificationResult.Rejected).reasons.joinToString()
        }
        check(certified.level.metadata == metadata) { "Metadata drift for ${proposed.id}" }
        check(certified.level.designedSolutions == proposed.designedSolutions) {
            "Certified solution drift for ${proposed.id}"
        }
        val difficulty = DifficultyV3Scorer.score(
            analyzer.analyze(proposed),
            difficultyConfig,
            PuzzleSearchConfig(),
        )
        val gate = DifficultyV3Gate.evaluate(
            difficulty,
            Phase1DifficultyTargets.forCampaignNumber(proposed.number),
        )
        check(difficulty.certifiable && gate.accepted) {
            "Difficulty v3 recertification rejected approved ${proposed.id}: ${gate.reasonCodes}"
        }
        proposed
    }
    val promotedLevels = source.levels + approvedNewLevels
    check(promotedLevels.map(ContentFingerprint::exact).toSet().size == 200)
    check(promotedLevels.map(ContentFingerprint::symmetryNormalized).toSet().size == 200)
    val promoted = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = source.ruleVersion,
        catalogId = source.catalogId,
        levels = promotedLevels,
        contentVersion = PHASE1_CONTENT_VERSION,
        generatorVersion = PHASE1_GENERATOR_VERSION,
    )
    val encoded = LevelParser().encodeCatalog(promoted)
    check(LevelParser().parseCatalog(encoded) == promoted) { "Promoted Phase 1 catalog failed round trip" }

    val approvedAssignments = proposal.assignments.map { row ->
        row.copy(
            similarityDisposition = row.similarityDisposition?.let {
                "OWNER_ACCEPTED_FOR_PROMOTION_NOT_PLAYTESTED"
            },
            manualReviewStatus = "OWNER_APPROVED_NOT_PLAYTESTED",
        )
    }
    val approved = proposal.copy(
        status = "OWNER_APPROVED_AND_PROMOTED",
        assignments = approvedAssignments,
        humanApprovalCount = approvedAssignments.size,
        ownerApprovalStatus = "APPROVED_BY_PROJECT_OWNER",
        humanPlaytestStatus = "NOT_PERFORMED",
    )
    val migration = Phase1ContentMigrationReport(
        sourceContentVersion = source.contentVersion,
        targetContentVersion = promoted.contentVersion,
        sourceGeneratorVersion = requireNotNull(source.generatorVersion),
        targetGeneratorVersion = requireNotNull(promoted.generatorVersion),
        preservedExistingLevelIds = source.levels.zip(promoted.levels.take(150)).count { (old, current) ->
            old.id == current.id && old.number == current.number
        },
        preservedExistingFingerprints = source.levels.zip(promoted.levels.take(150)).count { (old, current) ->
            ContentFingerprint.exact(old) == ContentFingerprint.exact(current)
        },
        addedLevelIds = approvedNewLevels.map(LevelDefinition::id),
    )
    sourceSnapshot.parentFile.mkdirs()
    sourceSnapshot.writeText(sourceText)
    File(output, "M5_3_APPROVED_PROMOTION.json").writeText(json.encodeToString(approved))
    File(output, "M5_3_APPROVED_PROMOTION.md").writeText(phase1ApprovedMarkdown(approved))
    File(output, "M5_3_CONTENT_MIGRATION.json").writeText(json.encodeToString(migration))
    campaignFile.writeText(encoded)
    println(
        "Promoted owner-approved Phase 1 content 5→6: 200 levels, first 150 fingerprints preserved, " +
            "50 owner-approved mappings, human playtesting NOT_PERFORMED.",
    )
}

fun finalizePromotedPhase1(options: Map<String, String>) {
    val campaign = LevelParser().parseCatalog(File(requireNotNull(options["campaign"])).readText())
    val source = LevelParser().parseCatalog(File(requireNotNull(options["source-snapshot"])).readText())
    val output = File(requireNotNull(options["output"])).also { it.mkdirs() }
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    val approved = json.decodeFromString<Phase1ProposalReport>(
        File(requireNotNull(options["approved-report"])).readText(),
    )

    require(campaign.schemaVersion == 2 && campaign.levels.size == 200)
    require(campaign.contentVersion == PHASE1_CONTENT_VERSION)
    require(campaign.generatorVersion == PHASE1_GENERATOR_VERSION)
    require(source.schemaVersion == 2 && source.levels.size == 150)
    require(source.contentVersion == PHASE0_CONTENT_VERSION)
    require(source.generatorVersion == PHASE0_GENERATOR_VERSION)
    require(approved.status == "OWNER_APPROVED_AND_PROMOTED")
    require(approved.ownerApprovalStatus == "APPROVED_BY_PROJECT_OWNER")
    require(approved.assignments.size == 50 && approved.humanApprovalCount == 50)
    require(approved.automatedApprovalCount == 0)
    require(approved.humanPlaytestStatus == "NOT_PERFORMED") {
        "Phase 1 finalizer cannot infer or fabricate human playtest results"
    }

    val ordered = campaign.levels.sortedBy(LevelDefinition::number)
    val orderedSource = source.levels.sortedBy(LevelDefinition::number)
    check(ordered.map(LevelDefinition::number) == (1..200).toList())
    check(ordered.take(150).map { it.id to it.number } == orderedSource.map { it.id to it.number }) {
        "Phase 1 promotion changed a frozen Levels 1–150 stable identity"
    }
    check(ordered.take(150).map(ContentFingerprint::exact) == orderedSource.map(ContentFingerprint::exact)) {
        "Phase 1 promotion changed a frozen Levels 1–150 fingerprint"
    }
    check(ordered.drop(150).map(LevelDefinition::id) == (151..200).map(::phase1LevelId))

    val exactById = ordered.associate { it.id to ContentFingerprint.exact(it) }
    val symmetryById = ordered.associate { it.id to ContentFingerprint.symmetryNormalized(it) }
    check(exactById.values.toSet().size == 200) { "Phase 1 campaign contains exact duplicates" }
    check(symmetryById.values.toSet().size == 200) { "Phase 1 campaign contains symmetry duplicates" }

    val searchConfig = PuzzleSearchConfig()
    val difficultyConfig = DifficultyV3Config()
    val analyzer = PuzzleSearchAnalyzer(config = searchConfig)
    val qualityAnalyzer = PuzzleQualityAnalyzerV2()
    val certification = CertificationPipeline()
    val difficultyById = ordered.associate { level ->
        level.id to DifficultyV3Scorer.score(analyzer.analyze(level), difficultyConfig, searchConfig)
    }
    check(difficultyById.values.all(DifficultyScoreV3::certifiable)) {
        "Phase 1 full-campaign Difficulty v3 analysis is incomplete"
    }

    val newLevels = ordered.drop(150)
    val existingLevels = ordered.take(150)
    val approvedById = approved.assignments.associateBy(Phase1ProposedAssignment::levelId)
    check(approvedById.size == 50)
    val nearNew = newLevels.associate { level ->
        val difficulty = requireNotNull(difficultyById[level.id])
        level.id to newLevels.asSequence()
            .filterNot { it.id == level.id }
            .filter { phase1StructurallyNear(difficulty, requireNotNull(difficultyById[it.id])) }
            .map(LevelDefinition::id)
            .sorted()
            .toList()
    }
    val nearExisting = newLevels.associate { level ->
        val difficulty = requireNotNull(difficultyById[level.id])
        level.id to existingLevels.asSequence()
            .filter { phase1StructurallyNear(difficulty, requireNotNull(difficultyById[it.id])) }
            .map(LevelDefinition::id)
            .sorted()
            .toList()
    }
    val patternCounts = difficultyById.values
        .groupingBy { it.rawMetrics.structuralPatternSignature }
        .eachCount()

    val rows = newLevels.map { level ->
        val assignment = requireNotNull(approvedById[level.id])
        val metadata = requireNotNull(level.metadata) { "Missing production metadata for ${level.id}" }
        val exact = requireNotNull(exactById[level.id])
        val symmetry = requireNotNull(symmetryById[level.id])
        check(assignment.campaignNumber == level.number)
        check(assignment.exactFingerprint == exact)
        check(assignment.symmetryFingerprint == symmetry)
        check(assignment.manualReviewStatus == "OWNER_APPROVED_NOT_PLAYTESTED")
        check(metadata.contentFingerprint == exact)
        check(metadata.contentVersion == PHASE1_CONTENT_VERSION)
        check(metadata.generatorVersion == PHASE1_GENERATOR_VERSION)
        check(metadata.generatorSeed == assignment.candidateSeed)
        check(metadata.generationProfile == GenerationProfile.PHASE1_ADVANCED_V3.profileId)
        check(metadata.origin == LevelOrigin.GENERATOR_ASSISTED)
        check(metadata.previousContentFingerprint == null)

        val production = certification.certify(
            level.copy(metadata = null),
            CertificationRequest(
                profile = GenerationProfile.PHASE1_ADVANCED_V3,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                packId = metadata.packId,
                generatorVersion = PHASE1_GENERATOR_VERSION,
                generatorSeed = assignment.candidateSeed,
                generationProfile = GenerationProfile.PHASE1_ADVANCED_V3.profileId,
                contentVersion = PHASE1_CONTENT_VERSION,
            ),
        )
        check(production is CertificationResult.Accepted) {
            "Production certification rejected ${level.id}: " +
                (production as CertificationResult.Rejected).reasons.joinToString()
        }
        check(production.level.metadata == metadata) { "Production metadata drift for ${level.id}" }
        check(production.level.designedSolutions == level.designedSolutions) {
            "Production solver/gameplay solution drift for ${level.id}"
        }

        val target = Phase1DifficultyTargets.forCampaignNumber(level.number)
        val difficulty = requireNotNull(difficultyById[level.id])
        val gate = DifficultyV3Gate.evaluate(difficulty, target)
        check(gate.accepted) { "Final target gate rejected ${level.id}: ${gate.reasonCodes}" }
        val newNeighbors = requireNotNull(nearNew[level.id])
        val existingNeighbors = requireNotNull(nearExisting[level.id])
        val quality = qualityAnalyzer.analyze(
            difficulty,
            gate,
            structuralSimilarityCount = newNeighbors.size + existingNeighbors.size,
        )
        check(quality.status != PuzzleQualityStatusV2.REJECT) {
            "Final Quality rejected ${level.id}: ${quality.reasonCodes}"
        }
        check(difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices == 0) {
            "Final player-choice analysis found guess dependence in ${level.id}"
        }
        Phase1FinalLevelCertification(
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
            target = target,
            productionCertificationAccepted = true,
            difficulty = difficulty,
            gate = gate,
            quality = quality,
            structuralNearNewLevelIds = newNeighbors,
            structuralNearExistingLevelIds = existingNeighbors,
            similarityDisposition = assignment.similarityDisposition,
            humanReviewPriority = phase1ReviewPriority(
                difficulty,
                quality,
                newNeighbors.size + existingNeighbors.size,
                patternCounts.getValue(difficulty.rawMetrics.structuralPatternSignature) == 1,
            ),
            ownerApprovalStatus = assignment.manualReviewStatus,
            humanPlaytestStatus = "PENDING",
        )
    }

    val finale = rows.single { it.campaignNumber == 200 }
    val finalePassed = finale.gate.accepted &&
        finale.difficulty.rawMetrics.meaningfulDecisionPoints >= 4 &&
        finale.difficulty.rawMetrics.dependencyDepth >= 3 &&
        finale.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices == 0
    check(finalePassed) { "Level 200 failed the approved finale gate" }
    val fullGuessCount = difficultyById.values.sumOf {
        it.rawMetrics.canonicalChoiceMetrics.guessDependentChoices
    }
    check(fullGuessCount == 0) { "Full campaign contains guess-dependent canonical choices" }

    val report = Phase1FinalCertificationReport(
        catalogId = campaign.catalogId,
        contentVersion = campaign.contentVersion,
        generatorVersion = requireNotNull(campaign.generatorVersion),
        campaignLevelCount = ordered.size,
        levels = rows,
        fullCampaignExactFingerprintCount = exactById.values.toSet().size,
        fullCampaignSymmetryFingerprintCount = symmetryById.values.toSet().size,
        fullCampaignDifficultyCertifiableCount = difficultyById.values.count(DifficultyScoreV3::certifiable),
        productionCertificationAcceptedCount = rows.count(Phase1FinalLevelCertification::productionCertificationAccepted),
        targetGateAcceptedCount = rows.count { it.gate.accepted },
        qualityStatuses = rows.groupingBy { it.quality.status.name }.eachCount().toSortedMap(),
        fullCampaignGuessDependentChoiceCount = fullGuessCount,
        preservedExistingStableIdCount = ordered.take(150).zip(orderedSource).count { (current, old) ->
            current.id == old.id && current.number == old.number
        },
        preservedExistingFingerprintCount = ordered.take(150).zip(orderedSource).count { (current, old) ->
            ContentFingerprint.exact(current) == ContentFingerprint.exact(old)
        },
        addedStableIdCount = rows.size,
        progressionGatePassed = rows.all { it.gate.accepted },
        level200FinaleGatePassed = finalePassed,
        preferenceSchemaVersion = 6,
        automatedApprovalCount = approved.automatedApprovalCount,
        ownerApprovedCount = rows.count { it.ownerApprovalStatus == "OWNER_APPROVED_NOT_PLAYTESTED" },
        humanPlaytestedCount = 0,
    )
    File(output, "M5_3_FINAL_DIAGNOSTICS.json").writeText(json.encodeToString(report))
    File(output, "M5_3_CAMPAIGN_151_200_REPORT.md").writeText(phase1FinalCertificationMarkdown(report))
    File(output, "M5_3_DUPLICATE_REPORT.md").writeText(phase1FinalDuplicateMarkdown(report))
    File(output, "M5_3_PACING_REPORT.md").writeText(phase1FinalPacingMarkdown(report))
    File(output, "M5_3_MANUAL_REVIEW.md").writeText(phase1FinalManualReviewMarkdown(report))
    File(output, "M5_3_MIGRATION.md").writeText(phase1FinalMigrationMarkdown(report))
    File(output, "M5_3_FULL_200_REPORT.md").writeText(
        phase1FullCampaignMarkdown(report, ordered, difficultyById),
    )
    println(
        "Finalized Phase 1: 50/50 production-certified, full campaign 200/200 Difficulty v3 " +
            "certifiable, Quality=${report.qualityStatuses}, guesses=$fullGuessCount, " +
            "human playtesting PENDING.",
    )
}

private fun phase1Pack(number: Int): String = when (number) {
    in 151..160 -> "advanced-recall"
    in 161..175 -> "dependency-lattices"
    in 176..190 -> "fair-false-paths"
    else -> "expert-circuit"
}

private fun phase1Title(number: Int): String = when (number) {
    in 151..160 -> "Advanced Recall ${number - 150}"
    in 161..175 -> "Dependency Lattice ${number - 160}"
    in 176..190 -> "Fair False Path ${number - 175}"
    in 191..199 -> "Expert Circuit ${number - 190}"
    else -> "Expert Circuit Finale"
}

private fun phase1LevelId(number: Int): String = "campaign-${number.toString().padStart(3, '0')}"

private fun phase1TunePurposefulSpace(
    source: LevelDefinition,
    target: PuzzleDifficultyTarget,
    analyzer: PuzzleSearchAnalyzer,
    difficultyConfig: DifficultyV3Config,
    searchConfig: PuzzleSearchConfig,
    qualityAnalyzer: PuzzleQualityAnalyzerV2,
    seed: Long,
): LevelDefinition {
    val engine = DefaultGameEngine()
    fun replays(level: LevelDefinition): Boolean = level.designedSolutions.firstOrNull()?.let { solution ->
        var state = level.initialState()
        solution.all { arrowId ->
            val result = engine.resolve(state, PlayerAction(arrowId))
            if (!result.success) return@all false
            state = result.resultingState
            true
        } && state.arrows.isEmpty()
    } == true

    var candidate = source
    var difficulty = DifficultyV3Scorer.score(analyzer.analyze(candidate), difficultyConfig, searchConfig)
    while (
        difficulty.rawMetrics.purposefulSpace.unusedEmptySpaceRatio > 0.40 &&
        candidate.walls.size < GenerationProfile.PHASE1_ADVANCED_V3.maxWalls
    ) {
        val occupied = buildSet {
            candidate.arrows.forEach { add(it.position) }
            candidate.magnets.forEach { add(it.position) }
            candidate.walls.forEach { add(it.position) }
        }
        val trials = buildList {
            for (row in 1..candidate.height) for (column in 1..candidate.width) {
                val position = Position(row, column)
                if (position in occupied) continue
                val trial = candidate.copy(walls = candidate.walls + Wall(position))
                if (!replays(trial)) continue
                val trialDifficulty = DifficultyV3Scorer.score(
                    analyzer.analyze(trial),
                    difficultyConfig,
                    searchConfig,
                )
                val gate = DifficultyV3Gate.evaluate(trialDifficulty, target)
                val quality = qualityAnalyzer.analyze(trialDifficulty, gate)
                val purposeful = trialDifficulty.rawMetrics.purposefulSpace
                if (
                    trialDifficulty.certifiable && gate.accepted &&
                    quality.status != PuzzleQualityStatusV2.REJECT &&
                    trialDifficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices == 0 &&
                    purposeful.irrelevantEntityRatio <= 0.20 &&
                    purposeful.unusedEmptySpaceRatio <
                    difficulty.rawMetrics.purposefulSpace.unusedEmptySpaceRatio
                ) {
                    add(Triple(trial, trialDifficulty, position))
                }
            }
        }
        val best = trials.minWithOrNull(
            compareBy<Triple<LevelDefinition, DifficultyScoreV3, Position>> {
                it.second.rawMetrics.purposefulSpace.unusedEmptySpaceRatio
            }.thenBy {
                it.second.rawMetrics.purposefulSpace.irrelevantEntityRatio
            }.thenByDescending {
                it.second.rawMetrics.wallDependencyCount
            }.thenByDescending {
                it.second.rawMetrics.meaningfulDecisionPoints
            }.thenBy {
                Math.floorMod(seed + it.third.row * 31L + it.third.column * 17L, 997L)
            },
        ) ?: break
        candidate = best.first
        difficulty = best.second
    }
    return candidate
}

private fun phase1StructurallyNear(first: DifficultyScoreV3, second: DifficultyScoreV3): Boolean =
    first.rawMetrics.structuralPatternSignature == second.rawMetrics.structuralPatternSignature ||
        phase1StructuralDistance(first, second) <= 0.035

private fun phase1StructuralDistance(first: DifficultyScoreV3, second: DifficultyScoreV3): Double {
    val a = first.rawMetrics
    val b = second.rawMetrics
    return listOf(
        abs(a.minimumSolutionLength - b.minimumSolutionLength) / 7.0,
        abs(a.meaningfulDecisionPoints - b.meaningfulDecisionPoints) / 5.0,
        abs(a.dependencyDepth - b.dependencyDepth) / 5.0,
        abs(a.averageEffectiveBranchingFactor - b.averageEffectiveBranchingFactor) / 3.0,
        abs(a.forcedMoveRatio - b.forcedMoveRatio),
        abs(a.mechanicRelevanceRatio - b.mechanicRelevanceRatio),
        abs(a.solutionFamilyCount - b.solutionFamilyCount).coerceAtMost(64) / 64.0,
    ).average()
}

private fun phase1ReviewPriority(
    difficulty: DifficultyScoreV3,
    quality: PuzzleQualityScoreV2,
    similarityCount: Int,
    novelStructure: Boolean,
): HumanReviewPriorityScore = HumanReviewPriorityScorer.score(
    HumanReviewPriorityFactors(
        difficultyConfidence = difficulty.confidence,
        solverTruncated = !difficulty.rawMetrics.searchComplete,
        unusualBranchingSeverity = (
            abs(difficulty.rawMetrics.averageEffectiveBranchingFactor - 2.1) / 2.1
            ).coerceIn(0.0, 1.0),
        extremeDifficultySeverity = ((difficulty.score - 85).coerceAtLeast(0) / 15.0).coerceIn(0.0, 1.0),
        qualityMarginSeverity = (1.0 - quality.marginAboveReview.coerceAtLeast(0) / 25.0).coerceIn(0.0, 1.0),
        novelStructuralPattern = novelStructure,
        structuralSimilaritySeverity = (similarityCount / 5.0).coerceIn(0.0, 1.0),
        newMechanicInteraction = false,
        unusualSolutionDepthSeverity = (abs(difficulty.rawMetrics.minimumSolutionLength - 6) / 6.0)
            .coerceIn(0.0, 1.0),
    ),
)

private fun MutableMap<String, Int>.incrementPhase1(reason: String, amount: Int = 1) {
    this[reason] = (this[reason] ?: 0) + amount
}

private fun phase1FinalCertificationMarkdown(report: Phase1FinalCertificationReport): String = buildString {
    appendLine("# M5.3 Campaign Levels 151–200 final certification")
    appendLine()
    appendLine("Status: **COMPLETE — HUMAN PLAYTEST PENDING**")
    appendLine()
    appendLine("The owner-approved mapping is promoted. Automated evidence proves solver/gameplay replay, metadata, target gates, structural quality, and migration identity; it is not human playtest approval.")
    appendLine()
    appendLine("- Campaign: ${report.campaignLevelCount} levels; content ${report.contentVersion}; generator ${report.generatorVersion}")
    appendLine("- New production certifications: ${report.productionCertificationAcceptedCount}/${report.levels.size}")
    appendLine("- Full-campaign complete Difficulty v3 searches: ${report.fullCampaignDifficultyCertifiableCount}/${report.campaignLevelCount}")
    appendLine("- New target gates: ${report.targetGateAcceptedCount}/${report.levels.size}; Quality ${report.qualityStatuses}")
    appendLine("- Full-campaign exact/symmetry uniqueness: ${report.fullCampaignExactFingerprintCount}/${report.fullCampaignSymmetryFingerprintCount}")
    appendLine("- Full-campaign canonical guess-dependent choices: ${report.fullCampaignGuessDependentChoiceCount}")
    appendLine("- Frozen Levels 1–150 IDs/fingerprints: ${report.preservedExistingStableIdCount}/${report.preservedExistingFingerprintCount}")
    appendLine("- Owner-approved new rows: ${report.ownerApprovedCount}/${report.levels.size}; automated approvals: ${report.automatedApprovalCount}")
    appendLine("- Human-playtested new rows: ${report.humanPlaytestedCount}/${report.levels.size}; all remain `PENDING`")
    appendLine(
        "- New mechanic evidence (polarity-chain/controller-change/occlusion/cancellation/fatal-choice): " +
            "${report.levels.count { it.difficulty.rawMetrics.polarityFlipCount >= 2 }}/" +
            "${report.levels.count { it.difficulty.rawMetrics.controllingMagnetChangeCount > 0 }}/" +
            "${report.levels.count { it.difficulty.rawMetrics.occlusionDependencyCount > 0 }}/" +
            "${report.levels.count { it.difficulty.rawMetrics.cancellationDependencyCount > 0 }}/" +
            "${report.levels.count { it.difficulty.rawMetrics.deadEndActionCount > 0 }}. " +
            "Cancellation remains represented by frozen earlier campaign levels; no unapproved board was substituted after promotion.",
    )
    appendLine()
    appendLine("| # | ID | Decision | Board / A-M-W | Target | V3 | Solution / forced / decisions / spacing / max run | Choices P/I/V/F/G | Quality | Near new/old | Priority | Playtest |")
    appendLine("|---:|---|---|---|---|---:|---|---|---|---|---:|---|")
    report.levels.forEach { row ->
        val metrics = row.difficulty.rawMetrics
        val choices = metrics.canonicalChoiceMetrics
        appendLine(
            "| ${row.campaignNumber} | ${row.levelId} | ${row.approvedDecision} | " +
                "${row.width}x${row.height} / ${row.arrowCount}-${row.magnetCount}-${row.wallCount} | " +
                "${row.target.id} | ${row.difficulty.score} | ${metrics.minimumSolutionLength}/" +
                "${metrics.forcedSequenceLength}/${metrics.meaningfulDecisionPoints}/" +
                "${phase1Decimal(metrics.averageDecisionSpacing)}/${metrics.maximumForcedRunLength} | " +
                "${choices.plausibleChoices}/${choices.immediatelyInvalidChoices}/" +
                "${choices.strategicallyViableChoices}/${choices.deceptiveButFairChoices}/" +
                "${choices.guessDependentChoices} | ${row.quality.score}/${row.quality.status} | " +
                "${row.structuralNearNewLevelIds.size}/${row.structuralNearExistingLevelIds.size} | " +
                "${row.humanReviewPriority.score} | PENDING |",
        )
    }
}

private fun phase1FinalDuplicateMarkdown(report: Phase1FinalCertificationReport): String = buildString {
    appendLine("# M5.3 final duplicate and structural-similarity report")
    appendLine()
    appendLine("Status: **AUTOMATED-CERTIFIED — HUMAN COMPARISON PENDING**")
    appendLine()
    appendLine("- Full-campaign exact fingerprints: ${report.fullCampaignExactFingerprintCount}/${report.campaignLevelCount}")
    appendLine("- Full-campaign symmetry fingerprints: ${report.fullCampaignSymmetryFingerprintCount}/${report.campaignLevelCount}")
    appendLine("- New rows near another new row: ${report.levels.count { it.structuralNearNewLevelIds.isNotEmpty() }}")
    appendLine("- New rows near Levels 1–150: ${report.levels.count { it.structuralNearExistingLevelIds.isNotEmpty() }}")
    appendLine()
    val flagged = report.levels.filter {
        it.structuralNearNewLevelIds.isNotEmpty() || it.structuralNearExistingLevelIds.isNotEmpty()
    }
    if (flagged.isEmpty()) appendLine("No structural-neighbor rows were found.")
    flagged.forEach { row ->
        appendLine(
            "- `${row.levelId}`: new=${row.structuralNearNewLevelIds}, " +
                "existing=${row.structuralNearExistingLevelIds}; " +
                "${row.similarityDisposition ?: "PENDING_HUMAN_COMPARISON"}",
        )
    }
}

private fun phase1FinalPacingMarkdown(report: Phase1FinalCertificationReport): String = buildString {
    appendLine("# M5.3 final pacing report")
    appendLine()
    appendLine("Status: **AUTOMATED-CERTIFIED — HUMAN PLAYTEST PENDING**")
    appendLine()
    appendLine("Recovery levels: ${Phase1DifficultyTargets.recoveryNumbers.sorted()}. A long forced run is reported separately and never counted as decision depth.")
    appendLine()
    appendLine("| Range | Avg / median / min–max v3 | Avg solution | Avg forced | Avg decisions | Avg spacing | Maximum forced run | Guess choices |")
    appendLine("|---|---|---:|---:|---:|---:|---:|---:|")
    listOf("151–160" to 151..160, "161–175" to 161..175, "176–190" to 176..190, "191–200" to 191..200)
        .forEach { (label, range) ->
            val rows = report.levels.filter { it.campaignNumber in range }
            val scores = rows.map { it.difficulty.score }.sorted()
            val metrics = rows.map { it.difficulty.rawMetrics }
            appendLine(
                "| $label | ${phase1Decimal(scores.average())}/${phase1Decimal(phase1Median(scores))}/" +
                    "${scores.min()}–${scores.max()} | " +
                    "${phase1Decimal(metrics.map { it.minimumSolutionLength }.average())} | " +
                    "${phase1Decimal(metrics.map { it.forcedSequenceLength }.average())} | " +
                    "${phase1Decimal(metrics.map { it.meaningfulDecisionPoints }.average())} | " +
                    "${phase1Decimal(metrics.map { it.averageDecisionSpacing }.average())} | " +
                    "${metrics.maxOf { it.maximumForcedRunLength }} | " +
                    "${metrics.sumOf { it.canonicalChoiceMetrics.guessDependentChoices }} |",
            )
        }
    appendLine()
    report.levels.chunked(10).forEach { rows ->
        appendLine("- ${rows.first().campaignNumber}–${rows.last().campaignNumber}: ${rows.joinToString { "${it.campaignNumber}:${it.difficulty.score}" }}")
    }
}

private fun phase1FinalManualReviewMarkdown(report: Phase1FinalCertificationReport): String = buildString {
    appendLine("# M5.3 final human review priority")
    appendLine()
    appendLine("Owner approval authorized promotion, not a playtest. Review viability from information visible before each move, especially deceptive-but-fair branches and long forced runs.")
    appendLine()
    appendLine("| Priority | # | ID | V3 / confidence | Quality / margin | Choices P/I/V/F/G | Near new/old | Reasons | Visual | Replay | Fair choices | Pacing | Status | Notes |")
    appendLine("|---:|---:|---|---|---|---|---|---|---|---|---|---|---|---|")
    report.levels.sortedWith(
        compareByDescending<Phase1FinalLevelCertification> { it.humanReviewPriority.score }
            .thenByDescending { it.structuralNearNewLevelIds.size + it.structuralNearExistingLevelIds.size }
            .thenBy { it.campaignNumber },
    ).forEach { row ->
        val choices = row.difficulty.rawMetrics.canonicalChoiceMetrics
        val reasons = (row.humanReviewPriority.reasonCodes + row.quality.reasonCodes +
            listOfNotNull(row.similarityDisposition)).distinct().joinToString(" ")
        appendLine(
            "| ${row.humanReviewPriority.score} | ${row.campaignNumber} | ${row.levelId} | " +
                "${row.difficulty.score}/${phase1Decimal(row.difficulty.confidence)} | " +
                "${row.quality.score}/${row.quality.marginAboveReview} | " +
                "${choices.plausibleChoices}/${choices.immediatelyInvalidChoices}/" +
                "${choices.strategicallyViableChoices}/${choices.deceptiveButFairChoices}/" +
                "${choices.guessDependentChoices} | ${row.structuralNearNewLevelIds.size}/" +
                "${row.structuralNearExistingLevelIds.size} | $reasons | ☐ | ☐ | ☐ | ☐ | PENDING | |",
        )
    }
}

private fun phase1FinalMigrationMarkdown(report: Phase1FinalCertificationReport): String = buildString {
    appendLine("# M5.3 final content and progress migration")
    appendLine()
    appendLine("Status: **APPLIED AND AUTOMATED-VERIFIED**")
    appendLine()
    appendLine("- Campaign content 5→${report.contentVersion}; generator 3→${report.generatorVersion}")
    appendLine("- Levels 1–150 stable IDs preserved: ${report.preservedExistingStableIdCount}/150")
    appendLine("- Levels 1–150 fingerprints preserved: ${report.preservedExistingFingerprintCount}/150")
    appendLine("- Added stable IDs: `campaign-151` through `campaign-200` (${report.addedStableIdCount})")
    appendLine("- Preference schema remains ${report.preferenceSchemaVersion}; no destructive record migration is required because existing boards did not change")
    appendLine("- A completed Level 150 unlocks and selects 151 exactly once; an incomplete Level 150 does not; Level 200 clamps without inventing 201")
    appendLine("- Completion, stars, first-clear rewards, currency, Daily state, settings, consent, and ad-frequency state are preserved")
}

private fun phase1FullCampaignMarkdown(
    report: Phase1FinalCertificationReport,
    levels: List<LevelDefinition>,
    difficultyById: Map<String, DifficultyScoreV3>,
): String = buildString {
    appendLine("# M5.3 full 200-level campaign report")
    appendLine()
    appendLine("Status: **AUTOMATED-CERTIFIED — HUMAN PLAYTEST PENDING**")
    appendLine()
    appendLine("All ${report.campaignLevelCount} boards have complete Difficulty v3 searches, unique exact/symmetry fingerprints, and zero canonical guess-dependent choices. Phase 1 changes only add Levels 151–200; Levels 1–150 remain frozen.")
    val originCounts = levels.groupingBy { requireNotNull(it.metadata).origin.name }.eachCount().toSortedMap()
    val bandCounts = difficultyById.values.groupingBy { it.band.name }.eachCount().toSortedMap()
    fun mechanicCount(predicate: (DifficultyScoreV3) -> Boolean): Int = difficultyById.values.count(predicate)
    appendLine()
    appendLine("- Origins: $originCounts")
    appendLine("- Difficulty v3 bands: $bandCounts")
    appendLine("- Frozen Phase 0 Quality: `ACCEPT=81`, `REVIEW=69`, `REJECT=0`; Phase 1 Quality: ${report.qualityStatuses}")
    appendLine(
        "- Full-campaign mechanic evidence (polarity-chain/controller-change/occlusion/cancellation/fatal-choice): " +
            "${mechanicCount { it.rawMetrics.polarityFlipCount >= 2 }}/" +
            "${mechanicCount { it.rawMetrics.controllingMagnetChangeCount > 0 }}/" +
            "${mechanicCount { it.rawMetrics.occlusionDependencyCount > 0 }}/" +
            "${mechanicCount { it.rawMetrics.cancellationDependencyCount > 0 }}/" +
            "${mechanicCount { it.rawMetrics.deadEndActionCount > 0 }}",
    )
    appendLine()
    appendLine("| Range | N | Avg / median / min–max v3 | Avg solution | Avg forced sequence | Avg decisions | Avg spacing | Max forced run | Choices P/I/V/F/G |")
    appendLine("|---|---:|---|---:|---:|---:|---:|---:|---|")
    listOf(
        "1–25" to 1..25,
        "26–50" to 26..50,
        "51–75" to 51..75,
        "76–100" to 76..100,
        "101–125" to 101..125,
        "126–150" to 126..150,
        "151–175" to 151..175,
        "176–200" to 176..200,
    ).forEach { (label, range) ->
        val rows = levels.filter { it.number in range }.map { requireNotNull(difficultyById[it.id]) }
        val scores = rows.map(DifficultyScoreV3::score).sorted()
        val metrics = rows.map(DifficultyScoreV3::rawMetrics)
        appendLine(
            "| $label | ${rows.size} | ${phase1Decimal(scores.average())}/" +
                "${phase1Decimal(phase1Median(scores))}/${scores.min()}–${scores.max()} | " +
                "${phase1Decimal(metrics.map { it.minimumSolutionLength }.average())} | " +
                "${phase1Decimal(metrics.map { it.forcedSequenceLength }.average())} | " +
                "${phase1Decimal(metrics.map { it.meaningfulDecisionPoints }.average())} | " +
                "${phase1Decimal(metrics.map { it.averageDecisionSpacing }.average())} | " +
                "${metrics.maxOf { it.maximumForcedRunLength }} | " +
                "${metrics.sumOf { it.canonicalChoiceMetrics.plausibleChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.immediatelyInvalidChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.strategicallyViableChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.deceptiveButFairChoices }}/" +
                "${metrics.sumOf { it.canonicalChoiceMetrics.guessDependentChoices }} |",
        )
    }
}

private fun phase1CandidatePoolMarkdown(report: Phase1CandidatePoolReport): String = buildString {
    appendLine("# M5.3 Phase 1 staged candidate pool")
    appendLine()
    appendLine("Status: **STAGED — NOT APPROVED OR PROMOTED**")
    appendLine()
    appendLine("- Candidates: ${report.candidates.size}; target quotas: ${report.targetQuotas}")
    appendLine("- Produced: ${report.producedByTarget}")
    appendLine("- Exact/symmetry unique: ${report.exactFingerprintCount}/${report.symmetryFingerprintCount}")
    appendLine("- Seed range: ${report.initialSeed}..${report.finalSeed}")
    appendLine("- Human approvals: ${report.humanApprovalCount}")
    appendLine("- Rejections: ${report.rejectionCounts}")
    appendLine()
    appendLine("Every candidate uses only frozen pre-Insulator mechanics and a 6×6 or 7×7 board. No row is described as handcrafted.")
}

private fun phase1ApprovedMarkdown(report: Phase1ProposalReport): String = buildString {
    appendLine("# M5.3 approved Levels 151–200 promotion")
    appendLine()
    appendLine("Status: **OWNER APPROVED AND PROMOTED — HUMAN PLAYTEST NOT PERFORMED**")
    appendLine()
    appendLine("- Approved assignments: ${report.humanApprovalCount}/${report.assignments.size}")
    appendLine("- Proposed decisions accepted with their reported exceptions: ${report.decisionCounts}")
    appendLine("- Quality: ${report.qualityStatuses}")
    appendLine("- Automated approvals represented as human approval: ${report.automatedApprovalCount}")
    appendLine("- Owner approval: ${report.ownerApprovalStatus}")
    appendLine("- Human playtesting: ${report.humanPlaytestStatus}")
    appendLine()
    appendLine("Owner approval authorizes the displayed mapping and Quality-review exceptions. It does not claim a manual playthrough, fairness validation, or player-experience approval.")
}

private fun phase1ProposalMarkdown(report: Phase1ProposalReport): String = buildString {
    appendLine("# M5.3 proposed Levels 151–200 promotion manifest")
    appendLine()
    appendLine("Status: **PROPOSED — NOT APPROVED OR PROMOTED**")
    appendLine()
    appendLine("This is the mandatory pre-promotion manifest for a batch larger than 20 levels. The checked-in 150-level campaign is unchanged.")
    appendLine()
    appendLine("- Candidate pool analyzed: ${report.candidatePoolSize}")
    appendLine("- Proposed decisions: ${report.decisionCounts}")
    appendLine("- Curation modes: ${report.curationModeCounts}")
    appendLine("- Origins: ${report.originCounts}; generated candidates are not called handcrafted")
    appendLine("- Existing Levels 1–150 preserved: ${report.existingLevelsPreserved}/150")
    appendLine("- Proposed full campaign: ${report.proposedCampaignLevelCount}; exact/symmetry unique ${report.exactFingerprintCount}/${report.symmetryFingerprintCount}")
    appendLine("- Target gates/certifiable: ${report.targetGateAcceptedCount}/${report.certifiableCount}")
    appendLine("- Quality: ${report.qualityStatuses}; canonical guess choices ${report.guessDependentChoiceCount}")
    appendLine("- Automated/human approvals: ${report.automatedApprovalCount}/${report.humanApprovalCount}")
    appendLine()
    appendLine("| # | Stable ID | Decision | Curation | Candidate / seed | Target | V3 | Length / forced / decisions / spacing / max run | Choices P/I/V/F/G | Quality | Near new/old | Priority | Human |")
    appendLine("|---:|---|---|---|---|---|---:|---|---|---|---|---:|---|")
    report.assignments.forEach { row ->
        val metrics = row.difficulty.rawMetrics
        val choices = metrics.canonicalChoiceMetrics
        appendLine(
            "| ${row.campaignNumber} | ${row.levelId} | ${row.proposedDecision} | ${row.curationMode} | " +
                "${row.candidateId} / ${row.candidateSeed} | ${row.target.id} | ${row.difficulty.score} | " +
                "${metrics.minimumSolutionLength}/${metrics.forcedSequenceLength}/" +
                "${metrics.meaningfulDecisionPoints}/${phase1Decimal(metrics.averageDecisionSpacing)}/" +
                "${metrics.maximumForcedRunLength} | ${choices.plausibleChoices}/" +
                "${choices.immediatelyInvalidChoices}/${choices.strategicallyViableChoices}/" +
                "${choices.deceptiveButFairChoices}/${choices.guessDependentChoices} | " +
                "${row.quality.score}/${row.quality.status} | ${row.nearSelectedLevelIds.size}/" +
                "${row.nearExistingLevelIds.size} | ${row.humanReviewPriority.score} | ${row.manualReviewStatus} |",
        )
    }
}

private fun phase1DistributionMarkdown(report: Phase1ProposalReport): String = buildString {
    appendLine("# M5.3 proposed Levels 151–200 distribution")
    appendLine()
    appendLine("Status: **PROPOSED — HUMAN REVIEW REQUIRED**")
    appendLine()
    appendLine("| Range | N | Average / median / min–max v3 | Quality | Avg solution | Avg forced | Avg decisions | Avg branch | Avg dependency | Guess choices |")
    appendLine("|---|---:|---|---|---:|---:|---:|---:|---:|---:|")
    listOf("151–160" to 151..160, "161–175" to 161..175, "176–190" to 176..190, "191–200" to 191..200)
        .forEach { (label, range) ->
            val rows = report.assignments.filter { it.campaignNumber in range }
            val scores = rows.map { it.difficulty.score }.sorted()
            appendLine(
                "| $label | ${rows.size} | ${phase1Decimal(scores.average())}/" +
                    "${phase1Decimal(phase1Median(scores))}/${scores.min()}–${scores.max()} | " +
                    "${rows.groupingBy { it.quality.status.name }.eachCount().toSortedMap()} | " +
                    "${phase1Decimal(rows.map { it.difficulty.rawMetrics.minimumSolutionLength }.average())} | " +
                    "${phase1Decimal(rows.map { it.difficulty.rawMetrics.forcedSequenceLength }.average())} | " +
                    "${phase1Decimal(rows.map { it.difficulty.rawMetrics.meaningfulDecisionPoints }.average())} | " +
                    "${phase1Decimal(rows.map { it.difficulty.rawMetrics.averageEffectiveBranchingFactor }.average())} | " +
                    "${phase1Decimal(rows.map { it.difficulty.rawMetrics.dependencyDepth }.average())} | " +
                    "${rows.sumOf { it.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices }} |",
            )
        }
}

private fun phase1DuplicateMarkdown(report: Phase1ProposalReport): String = buildString {
    appendLine("# M5.3 proposed duplicate and structural-similarity report")
    appendLine()
    appendLine("Status: **PROPOSED — HUMAN JUSTIFICATION OR REPLACEMENT REQUIRED FOR FLAGGED ROWS**")
    appendLine()
    appendLine("- Full 200-level exact fingerprints: ${report.exactFingerprintCount}/200")
    appendLine("- Full 200-level symmetry fingerprints: ${report.symmetryFingerprintCount}/200")
    appendLine("- Structurally near proposed rows: ${report.assignments.count { it.nearSelectedLevelIds.isNotEmpty() }}")
    appendLine("- Rows structurally near Levels 1–150: ${report.assignments.count { it.nearExistingLevelIds.isNotEmpty() }}")
    appendLine()
    report.assignments.filter { it.nearSelectedLevelIds.isNotEmpty() || it.nearExistingLevelIds.isNotEmpty() }
        .forEach { row ->
            appendLine("- `${row.levelId}`: new=${row.nearSelectedLevelIds}, existing=${row.nearExistingLevelIds}; ${row.similarityDisposition}")
        }
}

private fun phase1PacingMarkdown(report: Phase1ProposalReport): String = buildString {
    appendLine("# M5.3 proposed pacing report")
    appendLine()
    appendLine("Status: **PROPOSED — HUMAN PLAYTEST PENDING**")
    appendLine()
    appendLine("Configured recovery levels: ${Phase1DifficultyTargets.recoveryNumbers.sorted()}")
    appendLine("All non-recovery Hard/Expert runs and every recovery transition remain in the manual review queue. Recovery is based on lower structural targets, not random simplification or fewer objects alone.")
    appendLine()
    report.assignments.chunked(10).forEach { rows ->
        appendLine("- ${rows.first().campaignNumber}–${rows.last().campaignNumber}: ${rows.joinToString { "${it.campaignNumber}:${it.difficulty.score}" }}")
    }
}

private fun phase1ManualReviewMarkdown(report: Phase1ProposalReport): String = buildString {
    appendLine("# M5.3 proposed manual review queue")
    appendLine()
    appendLine("No row is human-approved or playtested. Review from the player's available information; owner approval alone must not be recorded as playtesting.")
    appendLine()
    appendLine("| Priority | # | ID | Decision | V3 / confidence | Quality / margin | Similar new/old | Reasons | Visual | Replay | Fair choices | Pacing | Status | Notes |")
    appendLine("|---:|---:|---|---|---|---|---|---|---|---|---|---|---|---|")
    report.assignments.sortedWith(
        compareByDescending<Phase1ProposedAssignment> { it.humanReviewPriority.score }
            .thenByDescending { it.nearSelectedLevelIds.size + it.nearExistingLevelIds.size }
            .thenBy { it.campaignNumber },
    ).forEach { row ->
        val reasons = (row.humanReviewPriority.reasonCodes + row.quality.reasonCodes +
            listOfNotNull(row.similarityDisposition)).distinct().joinToString(" ")
        appendLine(
            "| ${row.humanReviewPriority.score} | ${row.campaignNumber} | ${row.levelId} | " +
                "${row.proposedDecision} | ${row.difficulty.score}/${phase1Decimal(row.difficulty.confidence)} | " +
                "${row.quality.score}/${row.quality.marginAboveReview} | ${row.nearSelectedLevelIds.size}/" +
                "${row.nearExistingLevelIds.size} | $reasons | ☐ | ☐ | ☐ | ☐ | PENDING | |",
        )
    }
}

private fun phase1MigrationMarkdown(report: Phase1ProposalReport): String = buildString {
    appendLine("# M5.3 proposed migration")
    appendLine()
    appendLine("Status: **NOT APPLIED — PROMOTION AWAITS OWNER APPROVAL**")
    appendLine()
    appendLine("Levels 1–150 and their fingerprints remain byte-for-board preserved in the proposal. New stable IDs are `campaign-151` through `campaign-200`; no old ID is renumbered or replaced. On approved promotion, content advances ${report.sourceContentVersion}→${report.proposedContentVersion} and generator ${PHASE0_GENERATOR_VERSION}→${report.proposedGeneratorVersion}.")
    appendLine()
    appendLine("Required implementation after approval: completing Level 150 unlocks 151 exactly once; Continue/Next cross 150→151; Level 200 clamps cleanly; schema-6 records, rewards, economy, Daily state, settings, consent, and ad state remain intact. No production migration or campaign JSON is changed by this proposal.")
}

private fun phase1Median(values: List<Int>): Double = when {
    values.isEmpty() -> 0.0
    values.size % 2 == 1 -> values[values.size / 2].toDouble()
    else -> (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
}

private fun phase1Decimal(value: Double): String = "%.4f".format(Locale.ROOT, value)
