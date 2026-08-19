package com.rameshta.magnetrail.core.difficulty

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt

const val DIFFICULTY_ANALYZER_VERSION = "magnetrail-difficulty-v2.0"

/**
 * Build-time measurements produced by [DifficultyAnalyzer]. Ratios are always in 0..1.
 * A capped analysis is called out explicitly instead of treating unknown alternatives as fatal.
 */
@Serializable
data class DifficultyMetrics(
    val cleanSolutionLength: Int,
    val successfulOpeningActions: Int,
    val plausibleOpeningActions: Int,
    val averageSuccessfulBranching: Double,
    val maximumSuccessfulBranching: Int,
    val forcedMoveRatio: Double,
    val fatalChoiceRatio: Double,
    val criticalOrderConstraintCount: Int,
    val solutionDivergenceDepth: Int?,
    val magnetControlledSolutionActions: Int,
    val pullSolutionActions: Int,
    val pushSolutionActions: Int,
    val polarityFlipCount: Int,
    val controllingMagnetChangeCount: Int,
    val occlusionDependencyCount: Int,
    val cancellationDependencyCount: Int,
    val wallDependencyCount: Int,
    val solverStatesExplored: Int,
    val solutionCountUpToCap: Int,
    val boardDensity: Double,
    val visualCongestionScore: Double,
    val solutionCountCapped: Boolean,
    val stateAnalysisCapped: Boolean,
    val counterfactualAnalysisCapped: Boolean,
    val unknownAlternativeCount: Int,
    val recoveryWindowCount: Int,
    val immediatelyFailingChoiceCount: Int,
    val analyzerVersion: String = DIFFICULTY_ANALYZER_VERSION,
) {
    init {
        require(forcedMoveRatio in 0.0..1.0)
        require(fatalChoiceRatio in 0.0..1.0)
        require(boardDensity in 0.0..1.0)
        require(visualCongestionScore in 0.0..1.0)
    }

    // Compatibility accessors for M3 generator/report call sites.
    val solutionLength: Int get() = cleanSolutionLength
    val solutionCount: Int get() = solutionCountUpToCap
    val validFirstActionCount: Int get() = successfulOpeningActions
    val averageBranching: Double get() = averageSuccessfulBranching
    val magnetControlledActions: Int get() = magnetControlledSolutionActions
    val polarityFlips: Int get() = polarityFlipCount
    val exploredStateCount: Int get() = solverStatesExplored
}

@Serializable
data class DifficultyWeights(
    val wrongOrderRisk: Int = 25,
    val branchingComplexity: Int = 20,
    val criticalOrderConstraints: Int = 20,
    val magneticComplexity: Int = 20,
    val lookAheadAndDivergence: Int = 10,
    val densityAndReadability: Int = 5,
) {
    init {
        require(
            wrongOrderRisk + branchingComplexity + criticalOrderConstraints + magneticComplexity +
                lookAheadAndDivergence + densityAndReadability == 100,
        ) { "Difficulty weights must sum to 100" }
        require(listOf(
            wrongOrderRisk,
            branchingComplexity,
            criticalOrderConstraints,
            magneticComplexity,
            lookAheadAndDivergence,
            densityAndReadability,
        ).all { it >= 0 }) { "Difficulty weights cannot be negative" }
    }
}

/** All caps and curve targets are serialized with reports so a score can be reproduced. */
@Serializable
data class DifficultyConfig(
    val analyzerVersion: String = DIFFICULTY_ANALYZER_VERSION,
    val solutionCountCap: Int = 64,
    val solverStateCap: Int = 50_000,
    val counterfactualCheckCap: Int = 256,
    val fatalChoiceTarget: Double = 0.65,
    val averageBranchingTarget: Double = 3.0,
    val maximumBranchingTarget: Double = 5.0,
    val criticalConstraintRatioTarget: Double = 0.65,
    val magneticActionRatioTarget: Double = 0.75,
    val dependencyRatioTarget: Double = 0.55,
    val congestionTarget: Double = 0.55,
    val curveExponent: Double = 0.75,
    val weights: DifficultyWeights = DifficultyWeights(),
) {
    init {
        require(solutionCountCap > 0 && solverStateCap > 0 && counterfactualCheckCap > 0)
        require(curveExponent > 0.0)
        require(listOf(
            fatalChoiceTarget,
            averageBranchingTarget,
            maximumBranchingTarget,
            criticalConstraintRatioTarget,
            magneticActionRatioTarget,
            dependencyRatioTarget,
            congestionTarget,
        ).all { it > 0.0 }) { "Difficulty normalization targets must be positive" }
    }
}

@Serializable
enum class DifficultyBandV2(val displayName: String, val range: IntRange) {
    TUTORIAL("Tutorial", 0..15),
    EASY("Easy", 16..30),
    NORMAL("Normal", 31..45),
    MEDIUM("Medium", 46..60),
    HARD("Hard", 61..75),
    VERY_HARD("Very Hard", 76..90),
    EXPERT("Expert", 91..100),
    ;

    companion object {
        fun fromScore(score: Int): DifficultyBandV2 = entries.single { score in it.range }
    }
}

@Serializable
data class DifficultyComponents(
    val wrongOrderRisk: Double,
    val branchingComplexity: Double,
    val criticalOrderConstraints: Double,
    val magneticComplexity: Double,
    val lookAheadAndDivergence: Double,
    val densityAndReadability: Double,
)

@Serializable
data class DifficultyScoreV2(
    val rawMetrics: DifficultyMetrics,
    val normalizedComponents: DifficultyComponents,
    val weights: DifficultyWeights,
    val score: Int,
    val band: DifficultyBandV2,
    val analyzerVersion: String,
    val cappedFlags: List<String>,
    val unknownFlags: List<String>,
)

object DifficultyScorer {
    fun score(metrics: DifficultyMetrics, config: DifficultyConfig = DifficultyConfig()): DifficultyScoreV2 {
        val length = metrics.cleanSolutionLength.coerceAtLeast(1).toDouble()
        val possibleSelectionsAlongSolution = length * (length + 1.0) / 2.0
        val immediateFailureRatio = metrics.immediatelyFailingChoiceCount / possibleSelectionsAlongSolution
        val wrongOrder = (
            curve(metrics.fatalChoiceRatio, config.fatalChoiceTarget, config.curveExponent) * 0.75 +
                curve(immediateFailureRatio, 0.45, config.curveExponent) * 0.25
            ).coerceIn(0.0, 1.0)
        val branching = (
            curve(metrics.averageSuccessfulBranching - 1.0, config.averageBranchingTarget - 1.0, config.curveExponent) * 0.6 +
                curve(metrics.maximumSuccessfulBranching - 1.0, config.maximumBranchingTarget - 1.0, config.curveExponent) * 0.4
            ).coerceIn(0.0, 1.0)
        val critical = curve(
            metrics.criticalOrderConstraintCount / length,
            config.criticalConstraintRatioTarget,
            config.curveExponent,
        )
        val magneticActionRatio = metrics.magnetControlledSolutionActions / length
        val dependencyRatio = (
            metrics.occlusionDependencyCount + metrics.cancellationDependencyCount + metrics.wallDependencyCount +
                metrics.controllingMagnetChangeCount
            ) / (length * 2.0)
        val magnetic = (
            curve(magneticActionRatio, config.magneticActionRatioTarget, config.curveExponent) * 0.55 +
                curve(dependencyRatio, config.dependencyRatioTarget, config.curveExponent) * 0.3 +
                curve(metrics.polarityFlipCount / length, 0.75, config.curveExponent) * 0.15
            ).coerceIn(0.0, 1.0)
        val divergence = metrics.solutionDivergenceDepth?.let { depth ->
            (1.0 - depth.toDouble() / length).coerceIn(0.0, 1.0)
        } ?: 0.0
        val lookAhead = (
            divergence * 0.55 +
                curve((metrics.cleanSolutionLength - 1).coerceAtLeast(0).toDouble(), 6.0, 0.8) * 0.45
            ).coerceIn(0.0, 1.0)
        val density = (
            curve(metrics.visualCongestionScore, config.congestionTarget, config.curveExponent) * 0.7 +
                curve(metrics.boardDensity, 0.35, config.curveExponent) * 0.3
            ).coerceIn(0.0, 1.0)
        val components = DifficultyComponents(wrongOrder, branching, critical, magnetic, lookAhead, density)
        val weights = config.weights
        val finalScore = (
            wrongOrder * weights.wrongOrderRisk +
                branching * weights.branchingComplexity +
                critical * weights.criticalOrderConstraints +
                magnetic * weights.magneticComplexity +
                lookAhead * weights.lookAheadAndDivergence +
                density * weights.densityAndReadability
            ).roundToInt().coerceIn(0, 100)
        val capped = buildList {
            if (metrics.solutionCountCapped) add("SOLUTION_COUNT_CAPPED")
            if (metrics.stateAnalysisCapped) add("STATE_ANALYSIS_CAPPED")
            if (metrics.counterfactualAnalysisCapped) add("COUNTERFACTUAL_ANALYSIS_CAPPED")
        }
        val unknown = buildList {
            if (metrics.unknownAlternativeCount > 0) add("ALTERNATIVE_SOLVABILITY_UNKNOWN")
        }
        return DifficultyScoreV2(
            rawMetrics = metrics,
            normalizedComponents = components,
            weights = weights,
            score = finalScore,
            band = DifficultyBandV2.fromScore(finalScore),
            analyzerVersion = config.analyzerVersion,
            cappedFlags = capped,
            unknownFlags = unknown,
        )
    }

    private fun curve(value: Double, target: Double, exponent: Double): Double =
        (value.coerceAtLeast(0.0) / target).coerceIn(0.0, 1.0).pow(exponent)
}
