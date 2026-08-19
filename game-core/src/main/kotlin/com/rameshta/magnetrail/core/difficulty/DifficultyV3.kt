package com.rameshta.magnetrail.core.difficulty

import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

const val DIFFICULTY_V3_VERSION = "magnetrail-difficulty-v3.0"
const val PUZZLE_QUALITY_V2_VERSION = "magnetrail-puzzle-quality-v2.0"

@Serializable
data class DifficultyV3Weights(
    val solutionComplexity: Int = 10,
    val meaningfulDecisionComplexity: Int = 25,
    val effectiveBranchingComplexity: Int = 15,
    val dependencyDepth: Int = 15,
    val fairDeadEndComplexity: Int = 10,
    val solutionConstraint: Int = 10,
    val mechanicInteraction: Int = 10,
    val meaningfulSpatialRouting: Int = 5,
) {
    init {
        val values = listOf(
            solutionComplexity,
            meaningfulDecisionComplexity,
            effectiveBranchingComplexity,
            dependencyDepth,
            fairDeadEndComplexity,
            solutionConstraint,
            mechanicInteraction,
            meaningfulSpatialRouting,
        )
        require(values.all { it >= 0 }) { "Difficulty v3 weights cannot be negative" }
        require(values.sum() == 100) { "Difficulty v3 weights must sum to 100" }
    }
}

@Serializable
data class DifficultyV3Config(
    val analyzerVersion: String = DIFFICULTY_V3_VERSION,
    val curveExponent: Double = 0.80,
    val solutionLengthTarget: Double = 7.0,
    val decisionCountTarget: Double = 4.0,
    val decisionDensityTarget: Double = 0.55,
    val effectiveBranchingTarget: Double = 2.2,
    val maximumEffectiveBranchingTarget: Double = 4.0,
    val dependencyDepthTarget: Double = 4.0,
    val interactionDepthTarget: Double = 4.0,
    val fairChoicePerStepTarget: Double = 0.50,
    val deadEndProofDepthTarget: Double = 3.0,
    val constrainedFamilyTarget: Int = 64,
    val routeLengthTarget: Double = 4.0,
    val purposefulEmptyParticipationTarget: Double = 0.65,
    val weights: DifficultyV3Weights = DifficultyV3Weights(),
) {
    init {
        require(curveExponent > 0.0)
        require(listOf(
            solutionLengthTarget,
            decisionCountTarget,
            decisionDensityTarget,
            effectiveBranchingTarget,
            maximumEffectiveBranchingTarget,
            dependencyDepthTarget,
            interactionDepthTarget,
            fairChoicePerStepTarget,
            deadEndProofDepthTarget,
            routeLengthTarget,
            purposefulEmptyParticipationTarget,
        ).all { it > 0.0 })
        require(constrainedFamilyTarget > 1)
    }
}

@Serializable
data class DifficultyV3Components(
    val solutionComplexity: Double,
    val meaningfulDecisionComplexity: Double,
    val effectiveBranchingComplexity: Double,
    val dependencyDepth: Double,
    val fairDeadEndComplexity: Double,
    val solutionConstraint: Double,
    val mechanicInteraction: Double,
    val meaningfulSpatialRouting: Double,
)

@Serializable
data class DifficultyScoreV3(
    val rawMetrics: PuzzleSearchMetrics,
    val normalizedComponents: DifficultyV3Components,
    val weights: DifficultyV3Weights,
    val score: Int,
    val band: DifficultyBandV2,
    val confidence: Double,
    val certifiable: Boolean,
    val analyzerVersion: String,
)

object DifficultyV3Scorer {
    fun score(
        metrics: PuzzleSearchMetrics,
        config: DifficultyV3Config = DifficultyV3Config(),
        searchConfig: PuzzleSearchConfig = PuzzleSearchConfig(),
    ): DifficultyScoreV3 {
        val length = metrics.minimumSolutionLength.coerceAtLeast(1).toDouble()
        val decisionDensity = metrics.meaningfulDecisionPoints / length
        val decisionActivity = curve(decisionDensity, config.decisionDensityTarget, config.curveExponent)
        val solution = (
            curve(length, config.solutionLengthTarget, config.curveExponent) *
                (0.25 + 0.75 * decisionActivity)
            ).coerceIn(0.0, 1.0)
        val decisions = (
            curve(metrics.meaningfulDecisionPoints.toDouble(), config.decisionCountTarget, config.curveExponent) * 0.65 +
                decisionActivity * 0.35
            ).coerceIn(0.0, 1.0)
        val branching = (
            curve(
                (metrics.averageEffectiveBranchingFactor - 1.0).coerceAtLeast(0.0),
                config.effectiveBranchingTarget - 1.0,
                config.curveExponent,
            ) * 0.70 +
                curve(
                    (metrics.maximumEffectiveBranchingFactor - 1).coerceAtLeast(0).toDouble(),
                    config.maximumEffectiveBranchingTarget - 1.0,
                    config.curveExponent,
                ) * 0.30
            ).coerceIn(0.0, 1.0)
        val dependency = (
            curve(metrics.dependencyDepth.toDouble(), config.dependencyDepthTarget, config.curveExponent) * 0.70 +
                curve(
                    metrics.multiStageInteractionDepth.toDouble(),
                    config.interactionDepthTarget,
                    config.curveExponent,
                ) * 0.30
            ).coerceIn(0.0, 1.0)
        val fairChoiceRatio = metrics.canonicalChoiceMetrics.deceptiveButFairChoices / length
        val fairDeadEnds = (
            curve(fairChoiceRatio, config.fairChoicePerStepTarget, config.curveExponent) * 0.65 +
                curve(
                    metrics.averageDeadEndProofDepth,
                    config.deadEndProofDepthTarget,
                    config.curveExponent,
                ) * 0.35
            ).coerceIn(0.0, 1.0)
        val nonForcedPortion = (1.0 - metrics.forcedMoveRatio).coerceIn(0.0, 1.0)
        val familyConstraint = if (metrics.solutionFamilyCount <= 1) {
            1.0
        } else {
            (1.0 - ln(metrics.solutionFamilyCount.toDouble()) / ln(config.constrainedFamilyTarget.toDouble()))
                .coerceIn(0.0, 1.0)
        }
        val constraint = (
            (nonForcedPortion * 0.65 + familyConstraint * 0.35) *
                (0.20 + 0.80 * curve(metrics.meaningfulDecisionPoints.toDouble(), 3.0, config.curveExponent))
            ).coerceIn(0.0, 1.0)
        val mechanic = (
            curve(metrics.mechanicRelevanceRatio, 0.65, config.curveExponent) * 0.60 +
                curve(
                    (metrics.occlusionDependencyCount + metrics.cancellationDependencyCount +
                        metrics.wallDependencyCount + metrics.controllingMagnetChangeCount).toDouble(),
                    3.0,
                    config.curveExponent,
                ) * 0.40
            ).coerceIn(0.0, 1.0)
        val spatial = (
            curve(metrics.averageRouteLength, config.routeLengthTarget, config.curveExponent) * 0.55 +
                curve(
                    metrics.purposefulSpace.authoredEmptyCellsPurposefulRatio,
                    config.purposefulEmptyParticipationTarget,
                    config.curveExponent,
                ) * 0.45
            ).coerceIn(0.0, 1.0)
        val components = DifficultyV3Components(
            solutionComplexity = round4(solution),
            meaningfulDecisionComplexity = round4(decisions),
            effectiveBranchingComplexity = round4(branching),
            dependencyDepth = round4(dependency),
            fairDeadEndComplexity = round4(fairDeadEnds),
            solutionConstraint = round4(constraint),
            mechanicInteraction = round4(mechanic),
            meaningfulSpatialRouting = round4(spatial),
        )
        val weights = config.weights
        val total = (
            solution * weights.solutionComplexity +
                decisions * weights.meaningfulDecisionComplexity +
                branching * weights.effectiveBranchingComplexity +
                dependency * weights.dependencyDepth +
                fairDeadEnds * weights.fairDeadEndComplexity +
                constraint * weights.solutionConstraint +
                mechanic * weights.mechanicInteraction +
                spatial * weights.meaningfulSpatialRouting
            ).roundToInt().coerceIn(0, 100)
        val certifiable = metrics.solvable && metrics.searchComplete &&
            metrics.confidence >= searchConfig.certifiableConfidenceFloor &&
            metrics.canonicalSolutionArrowIds.size == metrics.minimumSolutionLength
        return DifficultyScoreV3(
            rawMetrics = metrics,
            normalizedComponents = components,
            weights = weights,
            score = total,
            band = DifficultyBandV2.fromScore(total),
            confidence = metrics.confidence,
            certifiable = certifiable,
            analyzerVersion = config.analyzerVersion,
        )
    }

    private fun curve(value: Double, target: Double, exponent: Double): Double =
        (value.coerceAtLeast(0.0) / target).coerceIn(0.0, 1.0).pow(exponent)
}

@Serializable
data class PuzzleDifficultyTarget(
    val id: String,
    val minimumScore: Int,
    val maximumScore: Int,
    val minSolutionLength: Int = 0,
    val minMeaningfulDecisions: Int = 0,
    val minDependencyDepth: Int = 0,
    val minEffectiveBranching: Double = 0.0,
    val minNonForcedPortion: Double = 0.0,
    val minMechanicRelevance: Double = 0.0,
    val maxGuessDependentRatio: Double = 0.20,
    val maxPlausibleOpenings: Int? = null,
) {
    init {
        require(minimumScore in 0..100 && maximumScore in minimumScore..100)
    }

    val scoreRange: IntRange get() = minimumScore..maximumScore
}

@Serializable
data class DifficultyGateResult(
    val accepted: Boolean,
    val reasonCodes: List<String>,
)

object DifficultyGateReason {
    const val SCORE_OUT_OF_TARGET = "V3_SCORE_OUT_OF_TARGET"
    const val SEARCH_NOT_CERTIFIABLE = "V3_SEARCH_NOT_CERTIFIABLE"
    const val SOLUTION_TOO_SHORT = "V3_SOLUTION_TOO_SHORT"
    const val INSUFFICIENT_DECISIONS = "V3_INSUFFICIENT_MEANINGFUL_DECISIONS"
    const val INSUFFICIENT_DEPENDENCY_DEPTH = "V3_INSUFFICIENT_DEPENDENCY_DEPTH"
    const val INSUFFICIENT_EFFECTIVE_BRANCHING = "V3_INSUFFICIENT_EFFECTIVE_BRANCHING"
    const val EXCESSIVE_FORCEDNESS = "V3_EXCESSIVE_FORCEDNESS"
    const val INSUFFICIENT_MECHANIC_RELEVANCE = "V3_INSUFFICIENT_MECHANIC_RELEVANCE"
    const val GUESS_DEPENDENT = "V3_GUESS_DEPENDENT_DIFFICULTY"
    const val TUTORIAL_AMBIGUITY = "V3_TUTORIAL_AMBIGUITY"
}

object DifficultyV3Gate {
    fun evaluate(score: DifficultyScoreV3, target: PuzzleDifficultyTarget): DifficultyGateResult {
        val metrics = score.rawMetrics
        val plausible = metrics.canonicalChoiceMetrics.plausibleChoices.coerceAtLeast(1)
        val guessRatio = metrics.canonicalChoiceMetrics.guessDependentChoices.toDouble() / plausible
        val nonForced = 1.0 - metrics.forcedMoveRatio
        val reasons = buildList {
            if (score.score !in target.scoreRange) add(DifficultyGateReason.SCORE_OUT_OF_TARGET)
            if (!score.certifiable) add(DifficultyGateReason.SEARCH_NOT_CERTIFIABLE)
            if (metrics.minimumSolutionLength < target.minSolutionLength) add(DifficultyGateReason.SOLUTION_TOO_SHORT)
            if (metrics.meaningfulDecisionPoints < target.minMeaningfulDecisions) {
                add(DifficultyGateReason.INSUFFICIENT_DECISIONS)
            }
            if (metrics.dependencyDepth < target.minDependencyDepth) {
                add(DifficultyGateReason.INSUFFICIENT_DEPENDENCY_DEPTH)
            }
            if (metrics.averageEffectiveBranchingFactor < target.minEffectiveBranching) {
                add(DifficultyGateReason.INSUFFICIENT_EFFECTIVE_BRANCHING)
            }
            if (nonForced < target.minNonForcedPortion) add(DifficultyGateReason.EXCESSIVE_FORCEDNESS)
            if (metrics.mechanicRelevanceRatio < target.minMechanicRelevance) {
                add(DifficultyGateReason.INSUFFICIENT_MECHANIC_RELEVANCE)
            }
            if (guessRatio > target.maxGuessDependentRatio) add(DifficultyGateReason.GUESS_DEPENDENT)
            target.maxPlausibleOpenings?.let { maximum ->
                if (metrics.openingChoiceMetrics.plausibleChoices > maximum) {
                    add(DifficultyGateReason.TUTORIAL_AMBIGUITY)
                }
            }
        }.distinct().sorted()
        return DifficultyGateResult(reasons.isEmpty(), reasons)
    }
}

object Phase0DifficultyTargets {
    val recoveryNumbers: Set<Int> = setOf(106, 112, 118, 124, 131, 137, 143, 148)

    fun forCampaignNumber(number: Int): PuzzleDifficultyTarget = when (number) {
        in 1..10 -> PuzzleDifficultyTarget(
            id = "phase0-tutorial",
            minimumScore = 5,
            maximumScore = 15,
            maxGuessDependentRatio = 0.0,
            maxPlausibleOpenings = 2,
        )
        in 11..25 -> PuzzleDifficultyTarget(
            id = "phase0-easy",
            minimumScore = 15,
            maximumScore = 30,
            minSolutionLength = 2,
            minNonForcedPortion = 0.10,
            minMechanicRelevance = 0.10,
            maxGuessDependentRatio = 0.10,
        )
        in 26..40 -> PuzzleDifficultyTarget(
            id = "phase0-planning-intro",
            minimumScore = 30,
            maximumScore = 45,
            minSolutionLength = 3,
            minMeaningfulDecisions = 1,
            minDependencyDepth = 1,
            minEffectiveBranching = 1.10,
            minNonForcedPortion = 0.20,
            minMechanicRelevance = 0.20,
        )
        in 41..60 -> mediumTarget("phase0-medium", 45..60)
        in 61..80 -> hardTarget("phase0-hard", 60..75)
        in 81..100 -> veryHardTarget("phase0-very-hard", 75..90)
        in 101..150 -> if (number in recoveryNumbers) {
            mediumTarget("phase0-upper-recovery", 50..65)
        } else if (number % 5 == 0) {
            veryHardTarget("phase0-upper-peak", 76..90)
        } else {
            hardTarget("phase0-upper-hard", 65..80)
        }
        else -> error("Phase 0 target is defined only for campaign numbers 1..150, got $number")
    }

    private fun mediumTarget(id: String, range: IntRange) = PuzzleDifficultyTarget(
        id = id,
        minimumScore = range.first,
        maximumScore = range.last,
        minSolutionLength = 3,
        minMeaningfulDecisions = 1,
        minDependencyDepth = 1,
        minEffectiveBranching = 1.15,
        minNonForcedPortion = 0.25,
        minMechanicRelevance = 0.30,
    )

    private fun hardTarget(id: String, range: IntRange) = PuzzleDifficultyTarget(
        id = id,
        minimumScore = range.first,
        maximumScore = range.last,
        minSolutionLength = 4,
        minMeaningfulDecisions = 2,
        minDependencyDepth = 2,
        minEffectiveBranching = 1.30,
        minNonForcedPortion = 0.35,
        minMechanicRelevance = 0.40,
    )

    private fun veryHardTarget(id: String, range: IntRange) = PuzzleDifficultyTarget(
        id = id,
        minimumScore = range.first,
        maximumScore = range.last,
        minSolutionLength = 5,
        minMeaningfulDecisions = 3,
        minDependencyDepth = 3,
        minEffectiveBranching = 1.50,
        minNonForcedPortion = 0.45,
        minMechanicRelevance = 0.50,
    )
}

/** Phase 1 campaign targets. Recovery slots keep pacing intentional without resetting mastery. */
object Phase1DifficultyTargets {
    val recoveryNumbers: Set<Int> = setOf(154, 158, 166, 172, 180, 188, 196)

    fun forCampaignNumber(number: Int): PuzzleDifficultyTarget = when {
        number !in 151..200 -> error("Phase 1 target is defined only for campaign numbers 151..200, got $number")
        number in recoveryNumbers -> PuzzleDifficultyTarget(
            id = "phase1-recovery",
            minimumScore = 58,
            maximumScore = 74,
            minSolutionLength = 4,
            minMeaningfulDecisions = 2,
            minDependencyDepth = 2,
            minEffectiveBranching = 1.30,
            minNonForcedPortion = 0.35,
            minMechanicRelevance = 0.40,
            maxGuessDependentRatio = 0.0,
        )
        number in 151..160 -> PuzzleDifficultyTarget(
            id = "phase1-advanced-recall",
            minimumScore = 62,
            maximumScore = 80,
            minSolutionLength = 4,
            minMeaningfulDecisions = 2,
            minDependencyDepth = 2,
            minEffectiveBranching = 1.30,
            minNonForcedPortion = 0.35,
            minMechanicRelevance = 0.40,
            maxGuessDependentRatio = 0.0,
        )
        number in 161..175 -> PuzzleDifficultyTarget(
            id = "phase1-dependency-lattice",
            minimumScore = 70,
            maximumScore = 88,
            minSolutionLength = 5,
            minMeaningfulDecisions = 3,
            minDependencyDepth = 3,
            minEffectiveBranching = 1.45,
            minNonForcedPortion = 0.42,
            minMechanicRelevance = 0.48,
            maxGuessDependentRatio = 0.0,
        )
        number in 176..190 -> PuzzleDifficultyTarget(
            id = "phase1-fair-false-path",
            minimumScore = 74,
            maximumScore = 92,
            minSolutionLength = 5,
            minMeaningfulDecisions = 3,
            minDependencyDepth = 3,
            minEffectiveBranching = 1.50,
            minNonForcedPortion = 0.45,
            minMechanicRelevance = 0.50,
            maxGuessDependentRatio = 0.0,
        )
        else -> PuzzleDifficultyTarget(
            id = "phase1-expert-circuit",
            minimumScore = 78,
            maximumScore = 94,
            minSolutionLength = 5,
            minMeaningfulDecisions = 4,
            minDependencyDepth = 3,
            minEffectiveBranching = 1.60,
            minNonForcedPortion = 0.48,
            minMechanicRelevance = 0.52,
            maxGuessDependentRatio = 0.0,
        )
    }
}

@Serializable
enum class PuzzleQualityStatusV2 { ACCEPT, REVIEW, REJECT }

object PuzzleQualityReasonV2 {
    const val UNSOLVABLE = "PUZZLE_QUALITY_UNSOLVABLE"
    const val SEARCH_INCOMPLETE = "PUZZLE_QUALITY_SEARCH_INCOMPLETE"
    const val LOW_CONFIDENCE = "PUZZLE_QUALITY_LOW_CONFIDENCE"
    const val GUESS_DEPENDENT = "PUZZLE_QUALITY_GUESS_DEPENDENT"
    const val EXCESSIVE_FORCED_RUN = "PUZZLE_QUALITY_EXCESSIVE_FORCED_RUN"
    const val NO_MEANINGFUL_DECISIONS = "PUZZLE_QUALITY_NO_MEANINGFUL_DECISIONS"
    const val UNUSED_SPACE = "PUZZLE_QUALITY_UNUSED_SPACE"
    const val IRRELEVANT_OBJECTS = "PUZZLE_QUALITY_IRRELEVANT_OBJECTS"
    const val LOW_MECHANIC_RELEVANCE = "PUZZLE_QUALITY_LOW_MECHANIC_RELEVANCE"
    const val STRUCTURAL_NEAR_DUPLICATE = "PUZZLE_QUALITY_STRUCTURAL_NEAR_DUPLICATE"
    const val EXACT_OR_SYMMETRY_DUPLICATE = "PUZZLE_QUALITY_HARD_DUPLICATE"
    const val GATE_FAILURE = "PUZZLE_QUALITY_DIFFICULTY_GATE_FAILURE"
    const val LOW_MARGIN = "PUZZLE_QUALITY_LOW_MARGIN"
}

@Serializable
data class PuzzleQualityScoreV2(
    val score: Int,
    val marginAboveReview: Int,
    val status: PuzzleQualityStatusV2,
    val reasonCodes: List<String>,
    val analyzerVersion: String = PUZZLE_QUALITY_V2_VERSION,
)

class PuzzleQualityAnalyzerV2(
    private val reviewScoreBelow: Int = 75,
    private val rejectScoreBelow: Int = 45,
    private val confidenceFloor: Double = 0.95,
) {
    fun analyze(
        difficulty: DifficultyScoreV3,
        gate: DifficultyGateResult,
        structuralSimilarityCount: Int = 0,
        hardDuplicate: Boolean = false,
    ): PuzzleQualityScoreV2 {
        val metrics = difficulty.rawMetrics
        val plausible = metrics.canonicalChoiceMetrics.plausibleChoices.coerceAtLeast(1)
        val guessRatio = metrics.canonicalChoiceMetrics.guessDependentChoices.toDouble() / plausible
        val unusedSpaceReviewThreshold = when {
            difficulty.score <= 30 -> 0.75
            difficulty.score <= 45 -> 0.60
            else -> 0.40
        }
        val reasons = buildList {
            if (!metrics.solvable) add(PuzzleQualityReasonV2.UNSOLVABLE)
            if (!metrics.searchComplete) add(PuzzleQualityReasonV2.SEARCH_INCOMPLETE)
            if (metrics.confidence < confidenceFloor) add(PuzzleQualityReasonV2.LOW_CONFIDENCE)
            if (guessRatio > 0.0) add(PuzzleQualityReasonV2.GUESS_DEPENDENT)
            if (metrics.minimumSolutionLength >= 3 && metrics.maximumForcedRunLength >= 3) {
                add(PuzzleQualityReasonV2.EXCESSIVE_FORCED_RUN)
            }
            if (
                difficulty.score > 30 && metrics.minimumSolutionLength >= 3 &&
                metrics.meaningfulDecisionPoints == 0
            ) {
                add(PuzzleQualityReasonV2.NO_MEANINGFUL_DECISIONS)
            }
            if (metrics.purposefulSpace.unusedEmptySpaceRatio > unusedSpaceReviewThreshold) {
                add(PuzzleQualityReasonV2.UNUSED_SPACE)
            }
            if (metrics.purposefulSpace.irrelevantEntityRatio > 0.20) {
                add(PuzzleQualityReasonV2.IRRELEVANT_OBJECTS)
            }
            if (metrics.minimumSolutionLength >= 3 && metrics.mechanicRelevanceRatio < 0.25) {
                add(PuzzleQualityReasonV2.LOW_MECHANIC_RELEVANCE)
            }
            if (structuralSimilarityCount > 0) add(PuzzleQualityReasonV2.STRUCTURAL_NEAR_DUPLICATE)
            if (hardDuplicate) add(PuzzleQualityReasonV2.EXACT_OR_SYMMETRY_DUPLICATE)
            if (!gate.accepted) add(PuzzleQualityReasonV2.GATE_FAILURE)
        }.distinct().sorted()
        var score = 100.0
        score -= guessRatio * 40.0
        score -= (metrics.forcedMoveRatio - 0.55).coerceAtLeast(0.0) * 22.0
        score -= (metrics.purposefulSpace.unusedEmptySpaceRatio - 0.25).coerceAtLeast(0.0) * 28.0
        score -= metrics.purposefulSpace.irrelevantEntityRatio * 22.0
        score -= (0.35 - metrics.mechanicRelevanceRatio).coerceAtLeast(0.0) * 20.0
        if (PuzzleQualityReasonV2.NO_MEANINGFUL_DECISIONS in reasons) score -= 14.0
        if (PuzzleQualityReasonV2.STRUCTURAL_NEAR_DUPLICATE in reasons) score -= 10.0
        if (!gate.accepted) score -= 8.0
        val hard = setOf(
            PuzzleQualityReasonV2.UNSOLVABLE,
            PuzzleQualityReasonV2.SEARCH_INCOMPLETE,
            PuzzleQualityReasonV2.LOW_CONFIDENCE,
            PuzzleQualityReasonV2.EXACT_OR_SYMMETRY_DUPLICATE,
        )
        if (reasons.any { it in hard }) score = minOf(score, rejectScoreBelow - 1.0)
        val finalScore = score.roundToInt().coerceIn(0, 100)
        val finalReasons = if (finalScore < reviewScoreBelow) {
            (reasons + PuzzleQualityReasonV2.LOW_MARGIN).distinct().sorted()
        } else {
            reasons
        }
        val status = when {
            finalReasons.any { it in hard } || finalScore < rejectScoreBelow -> PuzzleQualityStatusV2.REJECT
            finalReasons.isNotEmpty() -> PuzzleQualityStatusV2.REVIEW
            else -> PuzzleQualityStatusV2.ACCEPT
        }
        return PuzzleQualityScoreV2(
            score = finalScore,
            marginAboveReview = finalScore - reviewScoreBelow,
            status = status,
            reasonCodes = finalReasons,
        )
    }
}

@Serializable
data class HumanReviewPriorityFactors(
    val difficultyConfidence: Double,
    val solverTruncated: Boolean,
    val unusualBranchingSeverity: Double,
    val extremeDifficultySeverity: Double,
    val qualityMarginSeverity: Double,
    val novelStructuralPattern: Boolean,
    val structuralSimilaritySeverity: Double,
    val newMechanicInteraction: Boolean,
    val unusualSolutionDepthSeverity: Double,
) {
    init {
        require(difficultyConfidence in 0.0..1.0)
        require(unusualBranchingSeverity in 0.0..1.0)
        require(extremeDifficultySeverity in 0.0..1.0)
        require(qualityMarginSeverity in 0.0..1.0)
        require(structuralSimilaritySeverity in 0.0..1.0)
        require(unusualSolutionDepthSeverity in 0.0..1.0)
    }
}

@Serializable
data class HumanReviewPriorityScore(
    val score: Int,
    val factors: HumanReviewPriorityFactors,
    val reasonCodes: List<String>,
    val humanReviewStatus: String = "PENDING",
)

object HumanReviewPriorityScorer {
    fun score(factors: HumanReviewPriorityFactors): HumanReviewPriorityScore {
        var value = (1.0 - factors.difficultyConfidence) * 15.0
        if (factors.solverTruncated) value += 20.0
        value += factors.unusualBranchingSeverity * 10.0
        value += factors.extremeDifficultySeverity * 10.0
        value += factors.qualityMarginSeverity * 15.0
        if (factors.novelStructuralPattern) value += 10.0
        value += factors.structuralSimilaritySeverity * 10.0
        if (factors.newMechanicInteraction) value += 5.0
        value += factors.unusualSolutionDepthSeverity * 5.0
        val reasons = buildList {
            if (factors.difficultyConfidence < 0.95) add("REVIEW_LOW_DIFFICULTY_CONFIDENCE")
            if (factors.solverTruncated) add("REVIEW_SOLVER_TRUNCATED")
            if (factors.unusualBranchingSeverity > 0.5) add("REVIEW_UNUSUAL_BRANCHING")
            if (factors.extremeDifficultySeverity > 0.5) add("REVIEW_EXTREME_DIFFICULTY")
            if (factors.qualityMarginSeverity > 0.5) add("REVIEW_LOW_QUALITY_MARGIN")
            if (factors.novelStructuralPattern) add("REVIEW_NOVEL_STRUCTURE")
            if (factors.structuralSimilaritySeverity > 0.5) add("REVIEW_HIGH_SIMILARITY")
            if (factors.newMechanicInteraction) add("REVIEW_NEW_MECHANIC_INTERACTION")
            if (factors.unusualSolutionDepthSeverity > 0.5) add("REVIEW_UNUSUAL_SOLUTION_DEPTH")
        }
        return HumanReviewPriorityScore(value.roundToInt().coerceIn(0, 100), factors, reasons)
    }
}

private fun round4(value: Double): Double = (value * 10_000.0).roundToInt() / 10_000.0
