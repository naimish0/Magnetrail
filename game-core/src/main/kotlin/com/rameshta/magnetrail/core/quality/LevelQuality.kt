package com.rameshta.magnetrail.core.quality

import com.rameshta.magnetrail.core.difficulty.DifficultyAnalysis
import com.rameshta.magnetrail.core.difficulty.DifficultyBandV2
import com.rameshta.magnetrail.core.model.LevelDefinition
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

const val QUALITY_ANALYZER_VERSION = "magnetrail-quality-v1.0"

@Serializable
data class RecoveryPolicy(
    val recentWindowSize: Int = 4,
    val demandingBandFloor: DifficultyBandV2 = DifficultyBandV2.HARD,
    val recoveryBandDrop: Int = 1,
    val requireRecoveryAfterDemandingCount: Int = 3,
    val minimumAlternativeRecoveryWindows: Int = 1,
) {
    init {
        require(recentWindowSize > 0 && recoveryBandDrop > 0 && requireRecoveryAfterDemandingCount > 0)
        require(minimumAlternativeRecoveryWindows >= 0)
    }
}

@Serializable
data class LevelQualityConfig(
    val analyzerVersion: String = QUALITY_ANALYZER_VERSION,
    val reviewScoreBelow: Int = 70,
    val rejectScoreBelow: Int = 40,
    val tutorialEndLevel: Int = 10,
    val tutorialOpeningActionLimit: Int = 2,
    val excessiveForcedMoveRatio: Double = 0.95,
    val visualCongestionReviewThreshold: Double = 0.65,
    val solverStateReviewThreshold: Int = 20_000,
    val localSimilarityWindow: Int = 8,
    val recoveryPolicy: RecoveryPolicy = RecoveryPolicy(),
) {
    init {
        require(reviewScoreBelow in 0..100 && rejectScoreBelow in 0..reviewScoreBelow)
        require(excessiveForcedMoveRatio in 0.0..1.0)
        require(visualCongestionReviewThreshold in 0.0..1.0)
        require(localSimilarityWindow > 0)
    }
}

@Serializable
enum class LevelQualityStatus { ACCEPT, REVIEW, REJECT }

@Serializable
data class LevelQualityMetrics(
    val solvable: Boolean,
    val searchComplete: Boolean,
    val certifiedSolutionReplayValid: Boolean,
    val structuralSchemaValid: Boolean,
    val stableUniqueId: Boolean,
    val contentHashValid: Boolean,
    val exactDuplicateIds: List<String>,
    val symmetryDuplicateIds: List<String>,
    val localSimilarityIds: List<String>,
    val nonTrivial: Boolean,
    val mechanicClaimsRelevant: Boolean,
    val meaningfulDecisionCount: Int,
    val forcedMoveRatio: Double,
    val openingAmbiguity: Int,
    val visualCongestionScore: Double,
    val solverStatesExplored: Int,
    val gradingMetadataConsistent: Boolean,
    val curriculumPositionAppropriate: Boolean,
    val recoveryWindowCount: Int,
    val analyzerVersion: String = QUALITY_ANALYZER_VERSION,
)

@Serializable
data class LevelQualityScore(
    val rawMetrics: LevelQualityMetrics,
    val qualityScore: Int,
    val qualityStatus: LevelQualityStatus,
    val qualityReasons: List<String>,
    val analyzerVersion: String,
)

object QualityReason {
    const val UNSOLVABLE = "QUALITY_UNSOLVABLE"
    const val SOLVER_INCOMPLETE = "QUALITY_SOLVER_INCOMPLETE"
    const val SOLUTION_REPLAY_FAILED = "QUALITY_SOLUTION_REPLAY_FAILED"
    const val INVALID_SCHEMA = "QUALITY_INVALID_SCHEMA"
    const val DUPLICATE_ID = "QUALITY_DUPLICATE_ID"
    const val CONTENT_HASH_MISMATCH = "QUALITY_CONTENT_HASH_MISMATCH"
    const val EXACT_DUPLICATE = "QUALITY_EXACT_DUPLICATE"
    const val SYMMETRY_DUPLICATE = "QUALITY_SYMMETRY_DUPLICATE"
    const val TRIVIAL_AFTER_TUTORIAL = "QUALITY_TRIVIAL_AFTER_TUTORIAL"
    const val MECHANIC_CLAIM_UNSUPPORTED = "QUALITY_MECHANIC_CLAIM_UNSUPPORTED"
    const val EXCESSIVE_FORCEDNESS = "QUALITY_EXCESSIVE_FORCEDNESS"
    const val TUTORIAL_OPENING_AMBIGUITY = "QUALITY_TUTORIAL_OPENING_AMBIGUITY"
    const val LOCAL_NEAR_DUPLICATE = "QUALITY_LOCAL_NEAR_DUPLICATE"
    const val VISUAL_CONGESTION = "QUALITY_VISUAL_CONGESTION"
    const val SOLVER_COST = "QUALITY_SOLVER_COST"
    const val GRADING_MISMATCH = "QUALITY_GRADING_MISMATCH"
    const val CURRICULUM_MISMATCH = "QUALITY_CURRICULUM_MISMATCH"
    const val NO_RECOVERY_WINDOW = "QUALITY_NO_RECOVERY_WINDOW"
    const val ANALYSIS_CAPPED = "QUALITY_ANALYSIS_CAPPED"
}

class LevelQualityAnalyzer(
    private val config: LevelQualityConfig = LevelQualityConfig(),
) {
    fun analyze(
        level: LevelDefinition,
        difficulty: DifficultyAnalysis,
        duplicateId: Boolean = false,
        exactDuplicateIds: List<String> = emptyList(),
        symmetryDuplicateIds: List<String> = emptyList(),
        localSimilarityIds: List<String> = emptyList(),
        structuralSchemaValid: Boolean = true,
        contentHashValid: Boolean = true,
    ): LevelQualityScore {
        val metric = difficulty.metrics
        val metadata = level.metadata
        val solvable = difficulty.certifiedSolution != null && metric.solutionCountUpToCap > 0
        val gradingConsistent = metadata == null || (
            metadata.certifiedSolutionLength == metric.cleanSolutionLength &&
                metadata.grading.parActions == metric.cleanSolutionLength &&
                metadata.grading.twoStarMaxActions >= metadata.grading.parActions
            )
        val relevant = mechanicClaimsAreRelevant(level, difficulty)
        val meaningful = metric.criticalOrderConstraintCount + metric.recoveryWindowCount +
            (metric.maximumSuccessfulBranching - 1).coerceAtLeast(0)
        val nonTrivial = level.number <= config.tutorialEndLevel || metric.cleanSolutionLength >= 2 || meaningful > 0
        val curriculumAppropriate = curriculumAppropriate(level.number, difficulty.score.band)
        val qualityMetrics = LevelQualityMetrics(
            solvable = solvable,
            searchComplete = difficulty.searchComplete,
            certifiedSolutionReplayValid = difficulty.solutionReplayValid,
            structuralSchemaValid = structuralSchemaValid,
            stableUniqueId = !duplicateId,
            contentHashValid = contentHashValid,
            exactDuplicateIds = exactDuplicateIds.sorted(),
            symmetryDuplicateIds = symmetryDuplicateIds.sorted(),
            localSimilarityIds = localSimilarityIds.sorted(),
            nonTrivial = nonTrivial,
            mechanicClaimsRelevant = relevant,
            meaningfulDecisionCount = meaningful,
            forcedMoveRatio = metric.forcedMoveRatio,
            openingAmbiguity = metric.successfulOpeningActions,
            visualCongestionScore = metric.visualCongestionScore,
            solverStatesExplored = metric.solverStatesExplored,
            gradingMetadataConsistent = gradingConsistent,
            curriculumPositionAppropriate = curriculumAppropriate,
            recoveryWindowCount = metric.recoveryWindowCount,
            analyzerVersion = config.analyzerVersion,
        )
        val reasons = buildList {
            if (!solvable) add(QualityReason.UNSOLVABLE)
            if (!difficulty.searchComplete) add(QualityReason.SOLVER_INCOMPLETE)
            if (!difficulty.solutionReplayValid) add(QualityReason.SOLUTION_REPLAY_FAILED)
            if (!structuralSchemaValid) add(QualityReason.INVALID_SCHEMA)
            if (duplicateId) add(QualityReason.DUPLICATE_ID)
            if (!contentHashValid) add(QualityReason.CONTENT_HASH_MISMATCH)
            if (exactDuplicateIds.isNotEmpty()) add(QualityReason.EXACT_DUPLICATE)
            if (symmetryDuplicateIds.isNotEmpty()) add(QualityReason.SYMMETRY_DUPLICATE)
            if (!nonTrivial) add(QualityReason.TRIVIAL_AFTER_TUTORIAL)
            if (!relevant) add(QualityReason.MECHANIC_CLAIM_UNSUPPORTED)
            if (level.number > config.tutorialEndLevel && metric.forcedMoveRatio >= config.excessiveForcedMoveRatio) {
                add(QualityReason.EXCESSIVE_FORCEDNESS)
            }
            if (level.number <= config.tutorialEndLevel &&
                metric.successfulOpeningActions > config.tutorialOpeningActionLimit
            ) add(QualityReason.TUTORIAL_OPENING_AMBIGUITY)
            if (localSimilarityIds.isNotEmpty()) add(QualityReason.LOCAL_NEAR_DUPLICATE)
            if (metric.visualCongestionScore > config.visualCongestionReviewThreshold) add(QualityReason.VISUAL_CONGESTION)
            if (metric.solverStatesExplored > config.solverStateReviewThreshold) add(QualityReason.SOLVER_COST)
            if (!gradingConsistent) add(QualityReason.GRADING_MISMATCH)
            if (!curriculumAppropriate) add(QualityReason.CURRICULUM_MISMATCH)
            if (level.number > config.tutorialEndLevel &&
                metric.recoveryWindowCount < config.recoveryPolicy.minimumAlternativeRecoveryWindows
            ) add(QualityReason.NO_RECOVERY_WINDOW)
            if (metric.stateAnalysisCapped || metric.counterfactualAnalysisCapped) add(QualityReason.ANALYSIS_CAPPED)
        }.distinct().sorted()
        val hardReasons = setOf(
            QualityReason.UNSOLVABLE,
            QualityReason.SOLVER_INCOMPLETE,
            QualityReason.SOLUTION_REPLAY_FAILED,
            QualityReason.INVALID_SCHEMA,
            QualityReason.DUPLICATE_ID,
            QualityReason.CONTENT_HASH_MISMATCH,
            QualityReason.EXACT_DUPLICATE,
            QualityReason.SYMMETRY_DUPLICATE,
        )
        var score = 100.0
        score -= metric.fatalChoiceRatio * 18.0
        score -= metric.visualCongestionScore * 10.0
        if (!relevant) score -= 12
        if (!nonTrivial) score -= 15
        if (QualityReason.EXCESSIVE_FORCEDNESS in reasons) score -= 8
        if (QualityReason.TUTORIAL_OPENING_AMBIGUITY in reasons) score -= 10
        if (QualityReason.LOCAL_NEAR_DUPLICATE in reasons) score -= 8
        if (QualityReason.GRADING_MISMATCH in reasons) score -= 15
        if (QualityReason.CURRICULUM_MISMATCH in reasons) score -= 8
        if (QualityReason.NO_RECOVERY_WINDOW in reasons) score -= 6
        if (QualityReason.ANALYSIS_CAPPED in reasons) score -= 20
        if (reasons.any { it in hardReasons }) score = minOf(score, config.rejectScoreBelow - 1.0)
        val finalScore = score.roundToInt().coerceIn(0, 100)
        val status = when {
            reasons.any { it in hardReasons } || finalScore < config.rejectScoreBelow -> LevelQualityStatus.REJECT
            reasons.isNotEmpty() || finalScore < config.reviewScoreBelow -> LevelQualityStatus.REVIEW
            else -> LevelQualityStatus.ACCEPT
        }
        return LevelQualityScore(qualityMetrics, finalScore, status, reasons, config.analyzerVersion)
    }

    private fun mechanicClaimsAreRelevant(level: LevelDefinition, analysis: DifficultyAnalysis): Boolean {
        val tags = level.metadata?.mechanicTags.orEmpty()
        val metrics = analysis.metrics
        return tags.all { tag ->
            when (tag) {
                "MOVEMENT" -> true
                "PULL" -> metrics.pullSolutionActions > 0 || level.magnets.any { it.polarity.name == "PULL" }
                "PUSH" -> metrics.pushSolutionActions > 0 || level.magnets.any { it.polarity.name == "PUSH" }
                "POLARITY_FLIP" -> metrics.polarityFlipCount > 0
                "WALLS" -> level.walls.isNotEmpty()
                "OCCLUSION" -> metrics.occlusionDependencyCount > 0
                "CANCELLATION" -> metrics.cancellationDependencyCount > 0
                "MULTIPLE_MAGNETS" -> level.magnets.size > 1
                "ORDER_DEPENDENCY" -> metrics.criticalOrderConstraintCount > 0 ||
                    metrics.immediatelyFailingChoiceCount > 0
                else -> false
            }
        }
    }

    private fun curriculumAppropriate(number: Int, band: DifficultyBandV2): Boolean = when (number) {
        in 1..10 -> band <= DifficultyBandV2.NORMAL
        in 11..25 -> band <= DifficultyBandV2.MEDIUM
        in 26..60 -> band in DifficultyBandV2.EASY..DifficultyBandV2.HARD
        in 61..100 -> band >= DifficultyBandV2.NORMAL
        else -> false
    }
}
