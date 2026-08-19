package com.rameshta.magnetrail.core.generation.v5

import kotlinx.serialization.Serializable

const val GENERATOR_VERSION_V5 = 5
const val D2_STAGING_CONTENT_VERSION = 7
const val D2_SELECTION_VERSION = 1
const val D2_SKILL_VERSION = 1

@Serializable
enum class StructuralDifficultyBandV5(val rank: Int) {
    TUTORIAL(0),
    EASY(1),
    MEDIUM(2),
    HARD(3),
    EXPERT(4),
    MASTER(5),
}

@Serializable
enum class MagneticDistanceProfileV5 { SHORT, MEDIUM, LONG, MIXED }

@Serializable
data class MetricRangeV5(val minimum: Double, val maximum: Double) {
    init {
        require(minimum in 0.0..1.0 && maximum in minimum..1.0)
    }

    operator fun contains(value: Double): Boolean = value in minimum..maximum
}

/**
 * Frozen, explicit Generator V5 profile. These are structural targets, not aliases for a
 * Difficulty V4 score. A candidate must be certified with the production engine before it can
 * leave staging.
 */
@Serializable
data class GenerationProfileV5(
    val id: String,
    val difficultyBand: StructuralDifficultyBandV5,
    val gridSizes: List<Int>,
    val minArrows: Int,
    val maxArrows: Int,
    val minMagnets: Int,
    val maxMagnets: Int,
    val minWalls: Int,
    val maxWalls: Int,
    val objectDensityRange: MetricRangeV5,
    val interactionDensityRange: MetricRangeV5,
    val magneticDistanceProfile: MagneticDistanceProfileV5,
    val minArrowDependencyDepth: Int,
    val minPolarityImpactDepth: Int,
    val minCancellationTransitions: Int,
    val minMandatoryOrderingDepth: Int,
    val minConsequenceDepth: Int,
    val minRelevantObjectRatio: Double,
    val maxSafeChoiceRatio: Double,
    val maxGreedySolveRate: Double,
    val maxRandomSuccessRate: Double,
    val minMeaningfulFailureRate: Double,
    val minRecoveryPressure: Double,
    val minStrategicChoiceDensity: Double,
    val minExposureEvents: Int,
    val minAlternativePathCount: Int,
    val minCanonicalStrategies: Int,
    val maxPermutationRedundancy: Double,
    val solverStateCap: Int,
    val analysisStateCap: Int,
    val counterfactualCap: Int,
    val candidateAttemptCap: Int,
    val experimental: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(gridSizes.isNotEmpty() && gridSizes.all { it in 3..9 })
        require(minArrows in 1..maxArrows)
        require(minMagnets in 0..maxMagnets)
        require(minWalls in 0..maxWalls)
        require(minRelevantObjectRatio in 0.0..1.0)
        require(maxSafeChoiceRatio in 0.0..1.0)
        require(maxGreedySolveRate in 0.0..1.0)
        require(maxRandomSuccessRate in 0.0..1.0)
        require(minMeaningfulFailureRate in 0.0..1.0)
        require(minRecoveryPressure in 0.0..1.0)
        require(minStrategicChoiceDensity in 0.0..1.0)
        require(maxPermutationRedundancy in 0.0..1.0)
        require(solverStateCap > 0 && analysisStateCap > 0 && counterfactualCap > 0 && candidateAttemptCap > 0)
    }
}

object GenerationProfilesV5 {
    val TUTORIAL = GenerationProfileV5(
        id = "v5-tutorial", difficultyBand = StructuralDifficultyBandV5.TUTORIAL,
        gridSizes = listOf(3, 4), minArrows = 1, maxArrows = 3, minMagnets = 0, maxMagnets = 2,
        minWalls = 0, maxWalls = 2, objectDensityRange = MetricRangeV5(0.10, 0.42),
        interactionDensityRange = MetricRangeV5(0.0, 0.45), magneticDistanceProfile = MagneticDistanceProfileV5.SHORT,
        minArrowDependencyDepth = 0, minPolarityImpactDepth = 0, minCancellationTransitions = 0,
        minMandatoryOrderingDepth = 0, minConsequenceDepth = 0, minRelevantObjectRatio = 0.35,
        maxSafeChoiceRatio = 1.0, maxGreedySolveRate = 1.0, maxRandomSuccessRate = 1.0,
        minMeaningfulFailureRate = 0.0, minRecoveryPressure = 0.0, minStrategicChoiceDensity = 0.0,
        minExposureEvents = 0, minAlternativePathCount = 0, minCanonicalStrategies = 1,
        maxPermutationRedundancy = 1.0, solverStateCap = 20_000, analysisStateCap = 20_000,
        counterfactualCap = 48, candidateAttemptCap = 2_000,
    )

    val EASY = GenerationProfileV5(
        id = "v5-easy", difficultyBand = StructuralDifficultyBandV5.EASY,
        gridSizes = listOf(4, 5, 8), minArrows = 2, maxArrows = 5, minMagnets = 1, maxMagnets = 3,
        minWalls = 0, maxWalls = 5, objectDensityRange = MetricRangeV5(0.12, 0.48),
        interactionDensityRange = MetricRangeV5(0.08, 0.60), magneticDistanceProfile = MagneticDistanceProfileV5.MIXED,
        minArrowDependencyDepth = 1, minPolarityImpactDepth = 0, minCancellationTransitions = 0,
        minMandatoryOrderingDepth = 0, minConsequenceDepth = 1, minRelevantObjectRatio = 0.55,
        maxSafeChoiceRatio = 0.95, maxGreedySolveRate = 1.0, maxRandomSuccessRate = 0.95,
        minMeaningfulFailureRate = 0.0, minRecoveryPressure = 0.0, minStrategicChoiceDensity = 0.08,
        minExposureEvents = 0, minAlternativePathCount = 0, minCanonicalStrategies = 1,
        maxPermutationRedundancy = 0.98, solverStateCap = 40_000, analysisStateCap = 40_000,
        counterfactualCap = 64, candidateAttemptCap = 4_000,
    )

    val MEDIUM = GenerationProfileV5(
        id = "v5-medium", difficultyBand = StructuralDifficultyBandV5.MEDIUM,
        gridSizes = listOf(5, 6, 8), minArrows = 3, maxArrows = 6, minMagnets = 1, maxMagnets = 4,
        minWalls = 1, maxWalls = 7, objectDensityRange = MetricRangeV5(0.14, 0.52),
        interactionDensityRange = MetricRangeV5(0.12, 0.70), magneticDistanceProfile = MagneticDistanceProfileV5.MIXED,
        minArrowDependencyDepth = 1, minPolarityImpactDepth = 1, minCancellationTransitions = 0,
        minMandatoryOrderingDepth = 1, minConsequenceDepth = 1, minRelevantObjectRatio = 0.45,
        maxSafeChoiceRatio = 0.94, maxGreedySolveRate = 1.0, maxRandomSuccessRate = 0.92,
        minMeaningfulFailureRate = 0.02, minRecoveryPressure = 0.0, minStrategicChoiceDensity = 0.08,
        minExposureEvents = 1, minAlternativePathCount = 0, minCanonicalStrategies = 1,
        maxPermutationRedundancy = 0.98, solverStateCap = 60_000, analysisStateCap = 60_000,
        counterfactualCap = 96, candidateAttemptCap = 8_000,
    )

    val HARD = GenerationProfileV5(
        id = "v5-hard", difficultyBand = StructuralDifficultyBandV5.HARD,
        gridSizes = listOf(5, 6, 7, 8), minArrows = 4, maxArrows = 7, minMagnets = 2, maxMagnets = 5,
        minWalls = 1, maxWalls = 9, objectDensityRange = MetricRangeV5(0.16, 0.58),
        interactionDensityRange = MetricRangeV5(0.16, 0.78), magneticDistanceProfile = MagneticDistanceProfileV5.LONG,
        minArrowDependencyDepth = 2, minPolarityImpactDepth = 1, minCancellationTransitions = 0,
        minMandatoryOrderingDepth = 2, minConsequenceDepth = 2, minRelevantObjectRatio = 0.55,
        maxSafeChoiceRatio = 0.90, maxGreedySolveRate = 0.70, maxRandomSuccessRate = 0.82,
        minMeaningfulFailureRate = 0.04, minRecoveryPressure = 0.04, minStrategicChoiceDensity = 0.12,
        minExposureEvents = 1, minAlternativePathCount = 0, minCanonicalStrategies = 1,
        maxPermutationRedundancy = 0.96, solverStateCap = 100_000, analysisStateCap = 100_000,
        counterfactualCap = 128, candidateAttemptCap = 15_000,
    )

    val EXPERT = GenerationProfileV5(
        id = "v5-expert", difficultyBand = StructuralDifficultyBandV5.EXPERT,
        gridSizes = listOf(7, 8), minArrows = 5, maxArrows = 8, minMagnets = 2, maxMagnets = 6,
        minWalls = 2, maxWalls = 11, objectDensityRange = MetricRangeV5(0.17, 0.60),
        interactionDensityRange = MetricRangeV5(0.20, 0.85), magneticDistanceProfile = MagneticDistanceProfileV5.LONG,
        minArrowDependencyDepth = 3, minPolarityImpactDepth = 2, minCancellationTransitions = 1,
        minMandatoryOrderingDepth = 2, minConsequenceDepth = 3, minRelevantObjectRatio = 0.60,
        maxSafeChoiceRatio = 0.88, maxGreedySolveRate = 0.67, maxRandomSuccessRate = 0.78,
        minMeaningfulFailureRate = 0.05, minRecoveryPressure = 0.08, minStrategicChoiceDensity = 0.14,
        minExposureEvents = 2, minAlternativePathCount = 1, minCanonicalStrategies = 1,
        maxPermutationRedundancy = 0.95, solverStateCap = 150_000, analysisStateCap = 150_000,
        counterfactualCap = 160, candidateAttemptCap = 25_000,
    )

    val MASTER = GenerationProfileV5(
        id = "v5-master", difficultyBand = StructuralDifficultyBandV5.MASTER,
        gridSizes = listOf(8), minArrows = 5, maxArrows = 7, minMagnets = 2, maxMagnets = 4,
        minWalls = 2, maxWalls = 8, objectDensityRange = MetricRangeV5(0.14, 0.48),
        interactionDensityRange = MetricRangeV5(0.24, 0.90), magneticDistanceProfile = MagneticDistanceProfileV5.MIXED,
        minArrowDependencyDepth = 3, minPolarityImpactDepth = 2, minCancellationTransitions = 1,
        minMandatoryOrderingDepth = 3, minConsequenceDepth = 3, minRelevantObjectRatio = 0.65,
        maxSafeChoiceRatio = 0.86, maxGreedySolveRate = 0.67, maxRandomSuccessRate = 0.75,
        minMeaningfulFailureRate = 0.06, minRecoveryPressure = 0.10, minStrategicChoiceDensity = 0.16,
        minExposureEvents = 2, minAlternativePathCount = 1, minCanonicalStrategies = 1,
        maxPermutationRedundancy = 0.95, solverStateCap = 200_000, analysisStateCap = 200_000,
        counterfactualCap = 192, candidateAttemptCap = 40_000,
    )

    /** 9x9 remains diagnostic-only until separate usability approval. */
    val MASTER_9X9_EXPERIMENTAL = MASTER.copy(
        id = "v5-master-9x9-experimental",
        gridSizes = listOf(9),
        experimental = true,
    )

    val productionCandidateProfiles: List<GenerationProfileV5> =
        listOf(TUTORIAL, EASY, MEDIUM, HARD, EXPERT, MASTER)

    fun forBand(band: StructuralDifficultyBandV5): GenerationProfileV5 =
        productionCandidateProfiles.single { it.difficultyBand == band }
}
