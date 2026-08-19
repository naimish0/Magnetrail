package com.rameshta.magnetrail.core.difficulty.v4

import kotlinx.serialization.Serializable

const val DIFFICULTY_V4_ANALYZER_VERSION = "magnetrail-difficulty-v4-diagnostic-0"
const val DIFFICULTY_V4_CALIBRATION_VERSION = 0

@Serializable
data class DifficultyV4Config(
    val analyzerVersion: String = DIFFICULTY_V4_ANALYZER_VERSION,
    val calibrationVersion: Int = DIFFICULTY_V4_CALIBRATION_VERSION,
    val maxExpandedStates: Int = 100_000,
    val maxActionResolutions: Int = 1_000_000,
    val maxSearchDepth: Int = 32,
    val maxWinningSequences: Int = 100_000,
    val maxCanonicalStrategyRepresentatives: Int = 100_000,
    val maxCounterfactualStates: Int = 200_000,
    val maxCounterfactualActionResolutions: Int = 2_000_000,
    val maxPolarityCounterfactuals: Int = 100_000,
    val maxObjectCounterfactuals: Int = 128,
    val randomPolicySeeds: List<Long> = defaultDifficultyV4Seeds(),
    val scoreWeights: DifficultyV4ScoreWeights = DifficultyV4ScoreWeights(),
) {
    init {
        require(maxExpandedStates > 0)
        require(maxActionResolutions > 0)
        require(maxSearchDepth > 0)
        require(maxWinningSequences > 0)
        require(maxCanonicalStrategyRepresentatives > 0)
        require(maxCounterfactualStates > 0)
        require(maxCounterfactualActionResolutions > 0)
        require(maxPolarityCounterfactuals > 0)
        require(maxObjectCounterfactuals > 0)
        require(randomPolicySeeds.isNotEmpty())
        require(randomPolicySeeds.size <= 10_000)
    }
}

fun defaultDifficultyV4Seeds(count: Int = 256): List<Long> {
    require(count > 0)
    return List(count) { index -> 0x4D_41_47_4E_45_54L + index * 0x9E37L }
}

@Serializable
data class DifficultyV4ScoreWeights(
    val meaningfulFailure: Double = 15.0,
    val harmfulDecisionDensity: Double = 20.0,
    val consequencePersistence: Double = 10.0,
    val mandatoryOrdering: Double = 15.0,
    val polarityActionability: Double = 10.0,
    val greedyResistance: Double = 10.0,
    val randomResistance: Double = 10.0,
    val recoveryPressure: Double = 5.0,
    val decisionDensity: Double = 5.0,
    val safeChoicePenalty: Double = 20.0,
    val permutationRedundancyPenalty: Double = 15.0,
    val forcedRunPenalty: Double = 10.0,
    val irrelevantStructurePenalty: Double = 5.0,
) {
    init {
        val values = listOf(
            meaningfulFailure,
            harmfulDecisionDensity,
            consequencePersistence,
            mandatoryOrdering,
            polarityActionability,
            greedyResistance,
            randomResistance,
            recoveryPressure,
            decisionDensity,
            safeChoicePenalty,
            permutationRedundancyPenalty,
            forcedRunPenalty,
            irrelevantStructurePenalty,
        )
        require(values.all { it >= 0.0 })
        require(values.take(9).sum() == 100.0) {
            "Difficulty v4 calibration-0 positive weights must sum to 100"
        }
    }
}

@Serializable
enum class DifficultyV4ActionClassification {
    IMMEDIATELY_INVALID,
    SUCCESSFUL_HARMLESS,
    SUCCESSFUL_CAPABILITY_CHANGING,
    SUCCESSFUL_REDUCES_SOLUTIONS,
    SUCCESSFUL_FUTURE_DEAD_END,
    UNKNOWN_TRUNCATED,
}

@Serializable
data class ConsequencePersistenceMetrics(
    val sampleCount: Int,
    val minimumDepth: Int?,
    val maximumDepth: Int?,
    val averageDepth: Double?,
    val medianDepth: Double?,
    val averageMeaningfulDecisionsAffected: Double?,
    val maximumMeaningfulDecisionsAffected: Int?,
)

@Serializable
data class OrderingMetricsV4(
    val totalActionPairs: Int,
    val mandatoryOrderingPairCount: Int?,
    val mandatoryOrderingRatio: Double?,
    val mandatoryOrderingChainDepth: Int?,
    val dependencyGraphDepth: Int?,
    val independentActionCount: Int,
    val flexibleOrderingPairCount: Int?,
    val analysisComplete: Boolean,
)

@Serializable
data class PolarityActionabilityMetrics(
    val polarityFlipCount: Int,
    val strategicallyImpactfulPolarityFlipCount: Int,
    val routeOnlyPolarityFlipCount: Int,
    val polarityImpactRatio: Double,
    val actionabilityChangeCount: Int,
    val solvabilityChangeCount: Int,
    val orderingImpactCount: Int,
    val analysisComplete: Boolean,
)

@Serializable
data class StrategyMetricsV4(
    val rawWinningSequenceCount: Long?,
    val rawWinningSequenceCountCapped: Boolean,
    val canonicalStrategyCount: Int?,
    val meaningfulStrategyFamilyCount: Int?,
    val permutationRedundancy: Double?,
    val commutativeActionPairCount: Int,
    val nonCommutingActionPairCount: Int,
    val testedViableActionPairCount: Int,
    val commutationRatio: Double,
    val analysisComplete: Boolean,
)

@Serializable
data class GreedyPolicyMetrics(
    val policyId: String = "stable-authored-order-successful-v1",
    val solved: Boolean,
    val actionsBeforeFailure: Int,
    val firstDivergenceDepth: Int?,
    val recoveryRequired: Boolean,
    val recoverable: Boolean?,
    val recoveryDepth: Int?,
)

@Serializable
data class RandomPolicyMetrics(
    val policyId: String = "fixed-seed-uniform-successful-v1",
    val seedCount: Int,
    val completionCount: Int,
    val deadlockCount: Int,
    val completionRate: Double,
    val deadlockRate: Double,
    val averageActions: Double,
    val averageFailures: Double,
    val averageRecoveryDepth: Double,
    val actionCountVariance: Double,
)

@Serializable
data class RecoveryPressureMetrics(
    val recoverableBadDecisionCount: Int,
    val irreversibleBadDecisionCount: Int,
    val averageRecoveryDepth: Double,
    val maximumRecoveryDepth: Int,
    val averageDeadEndDepth: Double,
    val maximumDeadEndDepth: Int,
    val restartPressure: Double,
    val normalizedRecoveryPressure: Double,
)

@Serializable
data class ForcedDecisionMetrics(
    val totalSolutionLength: Int?,
    val forcedSequenceLength: Int?,
    val longestForcedRun: Int?,
    val meaningfulDecisionCount: Int,
    val decisionDensity: Double?,
    val firstMeaningfulDecisionDepth: Int?,
    val lastMeaningfulDecisionDepth: Int?,
    val maximumDecisionGap: Int?,
    val averageDecisionGap: Double?,
)

@Serializable
data class ObjectRelevanceMetrics(
    val totalWallCount: Int,
    val relevantWallCount: Int?,
    val irrelevantWallCount: Int?,
    val wallStrategicRelevanceRatio: Double?,
    val totalMagnetCount: Int,
    val relevantMagnetCount: Int?,
    val irrelevantMagnetCount: Int?,
    val magnetStrategicRelevanceRatio: Double?,
    val analysisComplete: Boolean,
)

@Serializable
data class DifficultyV4Metrics(
    val levelId: String,
    val levelNumber: Int,
    val boardWidth: Int,
    val boardHeight: Int,
    val arrowCount: Int,
    val magnetCount: Int,
    val wallCount: Int,
    val plausibleChoiceCount: Int,
    val immediatelyInvalidChoiceCount: Int,
    val successfulChoiceCount: Int,
    val safeSuccessfulChoiceCount: Int,
    val meaningfulSuccessfulChoiceCount: Int,
    val capabilityChangingSuccessfulChoiceCount: Int,
    val solutionReducingSuccessfulChoiceCount: Int,
    val futureDeadEndChoiceCount: Int,
    val harmfulDecisionCount: Int,
    val meaningfulDecisionStateCount: Int,
    val meaningfulFailureRate: Double,
    val harmfulDecisionDensity: Double,
    val safeChoiceRatio: Double,
    val meaningfulSuccessfulChoiceRatio: Double,
    val consequencePersistence: ConsequencePersistenceMetrics,
    val ordering: OrderingMetricsV4,
    val polarity: PolarityActionabilityMetrics,
    val strategy: StrategyMetricsV4,
    val greedyPolicy: GreedyPolicyMetrics,
    val randomPolicy: RandomPolicyMetrics,
    val recovery: RecoveryPressureMetrics,
    val forcedDecision: ForcedDecisionMetrics,
    val objectRelevance: ObjectRelevanceMetrics,
    val solvable: Boolean?,
    val searchComplete: Boolean,
    val searchStateCount: Int,
    val actionResolutionCount: Int,
    val searchTruncated: Boolean,
    val truncationReasons: List<String>,
    val metricStatus: Map<String, String>,
    val analyzerVersion: String = DIFFICULTY_V4_ANALYZER_VERSION,
)

@Serializable
data class DifficultyV4NormalizedMetrics(
    val meaningfulFailure: Double,
    val harmfulDecisionDensity: Double,
    val consequencePersistence: Double,
    val mandatoryOrdering: Double,
    val polarityActionability: Double,
    val greedyResistance: Double,
    val randomResistance: Double,
    val recoveryPressure: Double,
    val decisionDensity: Double,
    val safeChoicePenalty: Double,
    val permutationRedundancyPenalty: Double,
    val forcedRunPenalty: Double,
    val irrelevantStructurePenalty: Double,
)

@Serializable
data class DifficultyV4Contributions(
    val positive: Map<String, Double>,
    val negative: Map<String, Double>,
    val positiveTotal: Double,
    val negativeTotal: Double,
)

@Serializable
data class DifficultyV4Score(
    val metrics: DifficultyV4Metrics,
    val normalized: DifficultyV4NormalizedMetrics,
    val contributions: DifficultyV4Contributions,
    val score: Int?,
    val confidence: Double,
    val confidenceReasons: List<String>,
    val searchComplete: Boolean,
    val searchTruncated: Boolean,
    val truncationReasons: List<String>,
    val calibrationVersion: Int = DIFFICULTY_V4_CALIBRATION_VERSION,
    val analyzerVersion: String = DIFFICULTY_V4_ANALYZER_VERSION,
    val calibrationStatus: String = "PROVISIONAL_UNCALIBRATED",
)

