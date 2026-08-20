package com.rameshta.magnetrail.core.generation.v5

import kotlinx.serialization.Serializable

const val GENERATOR_VERSION_V5 = 5
const val D2_STAGING_CONTENT_VERSION = 7
const val CAMPAIGN_CONTENT_VERSION = 8
const val D2_SELECTION_VERSION = 1
const val D2_SKILL_VERSION = 1
const val D2_1_SPATIAL_CONFIGURATION_VERSION = 2

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
enum class ConstructionStrategyV5 { PLACEMENT_FIRST, SOLUTION_FIRST }

@Serializable
data class MetricRangeV5(val minimum: Double, val maximum: Double) {
    init {
        require(minimum in 0.0..1.0 && maximum in minimum..1.0)
    }

    operator fun contains(value: Double): Boolean = value in minimum..maximum
}

@Serializable
data class ObjectCountRangeV5(val minimum: Int, val maximum: Int) {
    init {
        require(minimum >= 0 && maximum >= minimum)
    }

    operator fun contains(value: Int): Boolean = value in minimum..maximum
}

/**
 * D2.1-only spatial constraints. Occupancy and object count remain independent from Difficulty V4;
 * these constraints decide whether a board is spatially appropriate before V4 judges its puzzle
 * difficulty.
 */
@Serializable
data class SpatialDensityProfileV5(
    val minimumOccupancyRatio: Double,
    val targetOccupancyRatio: Double,
    val maximumOccupancyRatio: Double,
    val arrowCount: ObjectCountRangeV5,
    val magnetCount: ObjectCountRangeV5,
    val wallCount: ObjectCountRangeV5,
    val targetArrowShare: Double,
    val targetMagnetShare: Double,
    val targetWallShare: Double,
    val longRangeDistance: Int,
    val minimumLongRangeMagneticRelationships: Int,
    val minimumMeaningfulLineOfSightInteractions: Int,
    val minimumArrowBlockerRelationships: Int,
    val minimumInteractingObjectRatio: Double,
    val minimumAverageObjectRelevance: Double,
    val maximumIrrelevantObjectRatio: Double,
    val minimumControllerChanges: Int,
    val minimumCancellationCriticalDecisions: Int,
    val minimumExposureDepth: Int,
    val minimumPersistentConsequenceCount: Int,
    val minimumParticipatingWallRatio: Double,
    val maximumCommutativeSafeChoiceProduct: Double,
    val minimumMeaningfulOrderingRate: Double,
    val rejectDenseButTrivial: Boolean,
) {
    init {
        require(minimumOccupancyRatio in 0.0..targetOccupancyRatio)
        require(targetOccupancyRatio in minimumOccupancyRatio..maximumOccupancyRatio)
        require(maximumOccupancyRatio in targetOccupancyRatio..1.0)
        require(targetArrowShare in 0.0..1.0)
        require(targetMagnetShare in 0.0..1.0)
        require(targetWallShare in 0.0..1.0)
        require(kotlin.math.abs(targetArrowShare + targetMagnetShare + targetWallShare - 1.0) < 0.0001)
        require(longRangeDistance > 0)
        require(minimumLongRangeMagneticRelationships >= 0)
        require(minimumMeaningfulLineOfSightInteractions >= 0)
        require(minimumArrowBlockerRelationships >= 0)
        require(minimumInteractingObjectRatio in 0.0..1.0)
        require(minimumAverageObjectRelevance in 0.0..1.0)
        require(maximumIrrelevantObjectRatio in 0.0..1.0)
        require(minimumControllerChanges >= 0)
        require(minimumCancellationCriticalDecisions >= 0)
        require(minimumExposureDepth >= 0)
        require(minimumPersistentConsequenceCount >= 0)
        require(minimumParticipatingWallRatio in 0.0..1.0)
        require(maximumCommutativeSafeChoiceProduct in 0.0..1.0)
        require(minimumMeaningfulOrderingRate in 0.0..1.0)
    }
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
    val spatialDensityProfile: SpatialDensityProfileV5? = null,
    val constructionStrategy: ConstructionStrategyV5 = ConstructionStrategyV5.PLACEMENT_FIRST,
    val repairAttemptCap: Int = 0,
    val experimental: Boolean = false,
    val maximumPurposefulEmptyCellRatio: Double = 0.0,
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
        require(repairAttemptCap >= 0)
        require(maximumPurposefulEmptyCellRatio in 0.0..0.60)
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

/**
 * D2.1 profiles deliberately do not replace [GenerationProfilesV5]. They are an isolated,
 * generator-only configuration used for spatial-density staging and diagnostics.
 */
object GenerationProfilesD21 {
    val TUTORIAL = GenerationProfilesV5.TUTORIAL.copy(
        id = "v5-d2.1-tutorial",
        maxWalls = 3,
        objectDensityRange = MetricRangeV5(0.10, 0.40),
        spatialDensityProfile = spatial(
            minimum = 0.10, target = 0.24, maximum = 0.40,
            arrows = 1..3, magnets = 0..2, walls = 0..3,
            shares = Triple(0.45, 0.20, 0.35),
            longDistance = 2, longRelationships = 0, los = 0, arrowBlockers = 0,
            interactingRatio = 0.30, averageRelevance = 0.02, irrelevantRatio = 0.60,
        ),
    )

    val EASY = GenerationProfilesV5.EASY.copy(
        id = "v5-d2.1-easy",
        gridSizes = listOf(4, 5),
        minWalls = 1, maxWalls = 7,
        objectDensityRange = MetricRangeV5(0.18, 0.46),
        interactionDensityRange = MetricRangeV5(0.04, 0.55),
        minArrowDependencyDepth = 0,
        minConsequenceDepth = 0,
        maxSafeChoiceRatio = 1.0,
        maxRandomSuccessRate = 1.0,
        minStrategicChoiceDensity = 0.0,
        maxPermutationRedundancy = 1.0,
        spatialDensityProfile = spatial(
            minimum = 0.18, target = 0.32, maximum = 0.46,
            arrows = 2..5, magnets = 1..3, walls = 1..7,
            shares = Triple(0.45, 0.20, 0.35),
            longDistance = 3, longRelationships = 0, los = 1, arrowBlockers = 1,
            interactingRatio = 0.45, averageRelevance = 0.03, irrelevantRatio = 0.50,
        ),
    )

    val MEDIUM = GenerationProfilesV5.MEDIUM.copy(
        id = "v5-d2.1-medium",
        gridSizes = listOf(5, 6),
        minArrows = 5, maxArrows = 8,
        minWalls = 0, maxWalls = 64, minMagnets = 0, maxMagnets = 64,
        objectDensityRange = MetricRangeV5(1.0, 1.0),
        interactionDensityRange = MetricRangeV5(0.06, 0.58),
        minRelevantObjectRatio = 0.25,
        maxPermutationRedundancy = 1.0,
        spatialDensityProfile = spatial(
            minimum = 1.0, target = 1.0, maximum = 1.0,
            arrows = 5..8, magnets = 0..64, walls = 0..64,
            shares = Triple(0.42, 0.25, 0.33),
            longDistance = 3, longRelationships = 0, los = 2, arrowBlockers = 1,
            interactingRatio = 0.25, averageRelevance = 0.05, irrelevantRatio = 0.75,
            exposureDepth = 1, persistentConsequences = 1, participatingWalls = 0.25,
            maximumCommutativeSafety = 0.88, meaningfulOrdering = 0.08,
        ),
        constructionStrategy = ConstructionStrategyV5.SOLUTION_FIRST,
        repairAttemptCap = 1,
    )

    val HARD = GenerationProfilesV5.HARD.copy(
        id = "v5-d2.1-hard",
        gridSizes = listOf(6, 7),
        minArrows = 6, maxArrows = 10,
        minWalls = 0, maxWalls = 64, minMagnets = 0, maxMagnets = 64,
        objectDensityRange = MetricRangeV5(1.0, 1.0),
        interactionDensityRange = MetricRangeV5(0.02, 0.62),
        minRelevantObjectRatio = 0.25,
        // Raw permutation redundancy is strongly solution-length biased. D2.1 gates the
        // normalized viable-pair commutation rate below instead of weakening strategy quality.
        maxPermutationRedundancy = 1.0,
        spatialDensityProfile = spatial(
            minimum = 1.0, target = 1.0, maximum = 1.0,
            arrows = 6..10, magnets = 0..64, walls = 0..64,
            shares = Triple(0.34, 0.25, 0.41),
            longDistance = 4, longRelationships = 0, los = 3, arrowBlockers = 1,
            interactingRatio = 0.25, averageRelevance = 0.05, irrelevantRatio = 0.75,
            controllerChanges = 1, exposureDepth = 1, persistentConsequences = 2,
            // V4 mandatory-ordering depth remains enforced. This density-normalized auxiliary
            // ratio is disabled because full-occupancy filler cells dilute its denominator.
            participatingWalls = 0.20, maximumCommutativeSafety = 0.85, meaningfulOrdering = 0.0,
        ),
        constructionStrategy = ConstructionStrategyV5.SOLUTION_FIRST,
        repairAttemptCap = 1,
    )

    /** Very Hard is a D2.1 generation profile; V4 still assigns structural difficulty. */
    val VERY_HARD = GenerationProfilesV5.EXPERT.copy(
        id = "v5-d2.1-very-hard",
        gridSizes = listOf(7, 8),
        minArrows = 8, maxArrows = 12,
        minWalls = 0, maxWalls = 64, minMagnets = 0, maxMagnets = 64,
        objectDensityRange = MetricRangeV5(1.0, 1.0),
        interactionDensityRange = MetricRangeV5(0.02, 0.66),
        minRelevantObjectRatio = 0.25,
        maxPermutationRedundancy = 1.0,
        spatialDensityProfile = spatial(
            minimum = 1.0, target = 1.0, maximum = 1.0,
            arrows = 8..12, magnets = 0..64, walls = 0..64,
            shares = Triple(0.28, 0.24, 0.48),
            longDistance = 4, longRelationships = 0, los = 4, arrowBlockers = 2,
            interactingRatio = 0.25, averageRelevance = 0.06, irrelevantRatio = 0.75,
            controllerChanges = 1, cancellationCritical = 1, exposureDepth = 2,
            persistentConsequences = 3, participatingWalls = 0.25,
            maximumCommutativeSafety = 0.84, meaningfulOrdering = 0.13,
        ),
        constructionStrategy = ConstructionStrategyV5.SOLUTION_FIRST,
        repairAttemptCap = 1,
    )

    val EXPERT = GenerationProfilesV5.EXPERT.copy(
        id = "v5-d2.1-expert",
        gridSizes = listOf(8),
        minArrows = 9, maxArrows = 14,
        minWalls = 0, maxWalls = 64, minMagnets = 0, maxMagnets = 64,
        objectDensityRange = MetricRangeV5(0.40, 0.85),
        interactionDensityRange = MetricRangeV5(0.04, 0.70),
        minRelevantObjectRatio = 0.28,
        maxPermutationRedundancy = 1.0,
        spatialDensityProfile = spatial(
            minimum = 0.40, target = 0.55, maximum = 0.85,
            arrows = 9..14, magnets = 0..64, walls = 0..64,
            shares = Triple(0.23, 0.24, 0.53),
            longDistance = 4, longRelationships = 3, los = 6, arrowBlockers = 2,
            interactingRatio = 0.30, averageRelevance = 0.11, irrelevantRatio = 0.72,
            controllerChanges = 2, cancellationCritical = 1, exposureDepth = 2,
            persistentConsequences = 4, participatingWalls = 0.25,
            maximumCommutativeSafety = 0.80, meaningfulOrdering = 0.20,
        ),
        constructionStrategy = ConstructionStrategyV5.SOLUTION_FIRST,
        repairAttemptCap = 1,
        maximumPurposefulEmptyCellRatio = 0.60,
    )

    val MASTER = GenerationProfilesV5.MASTER.copy(
        id = "v5-d2.1-master",
        gridSizes = listOf(8),
        minArrows = 9, maxArrows = 15,
        minWalls = 0, maxWalls = 64, minMagnets = 0, maxMagnets = 64,
        objectDensityRange = MetricRangeV5(0.45, 0.90),
        interactionDensityRange = MetricRangeV5(0.025, 0.74),
        minRelevantObjectRatio = 0.26,
        maxPermutationRedundancy = 1.0,
        spatialDensityProfile = spatial(
            minimum = 0.45, target = 0.60, maximum = 0.90,
            arrows = 9..15, magnets = 0..64, walls = 0..64,
            shares = Triple(0.22, 0.22, 0.56),
            longDistance = 4, longRelationships = 0, los = 8, arrowBlockers = 3,
            interactingRatio = 0.30, averageRelevance = 0.065, irrelevantRatio = 0.74,
            controllerChanges = 2, cancellationCritical = 1, exposureDepth = 2,
            persistentConsequences = 5, participatingWalls = 0.27,
            maximumCommutativeSafety = 0.80, meaningfulOrdering = 0.13,
        ),
        constructionStrategy = ConstructionStrategyV5.SOLUTION_FIRST,
        repairAttemptCap = 1,
        maximumPurposefulEmptyCellRatio = 0.55,
    )

    /** 9x9 remains diagnostics-only until board usability is separately approved. */
    val MASTER_9X9_EXPERIMENTAL = MASTER.copy(
        id = "v5-d2.1-master-9x9-experimental",
        gridSizes = listOf(9),
        experimental = true,
    )

    val benchmarkProfiles: List<GenerationProfileV5> =
        listOf(TUTORIAL, EASY, MEDIUM, HARD, VERY_HARD, EXPERT, MASTER)

    private fun spatial(
        minimum: Double,
        target: Double,
        maximum: Double,
        arrows: IntRange,
        magnets: IntRange,
        walls: IntRange,
        shares: Triple<Double, Double, Double>,
        longDistance: Int,
        longRelationships: Int,
        los: Int,
        arrowBlockers: Int,
        interactingRatio: Double,
        averageRelevance: Double,
        irrelevantRatio: Double,
        controllerChanges: Int = 0,
        cancellationCritical: Int = 0,
        exposureDepth: Int = 0,
        persistentConsequences: Int = 0,
        participatingWalls: Double = 0.0,
        maximumCommutativeSafety: Double = 1.0,
        meaningfulOrdering: Double = 0.0,
    ) = SpatialDensityProfileV5(
        minimumOccupancyRatio = minimum,
        targetOccupancyRatio = target,
        maximumOccupancyRatio = maximum,
        arrowCount = ObjectCountRangeV5(arrows.first, arrows.last),
        magnetCount = ObjectCountRangeV5(magnets.first, magnets.last),
        wallCount = ObjectCountRangeV5(walls.first, walls.last),
        targetArrowShare = shares.first,
        targetMagnetShare = shares.second,
        targetWallShare = shares.third,
        longRangeDistance = longDistance,
        minimumLongRangeMagneticRelationships = longRelationships,
        minimumMeaningfulLineOfSightInteractions = los,
        minimumArrowBlockerRelationships = arrowBlockers,
        minimumInteractingObjectRatio = interactingRatio,
        minimumAverageObjectRelevance = averageRelevance,
        maximumIrrelevantObjectRatio = irrelevantRatio,
        minimumControllerChanges = controllerChanges,
        minimumCancellationCriticalDecisions = cancellationCritical,
        minimumExposureDepth = exposureDepth,
        minimumPersistentConsequenceCount = persistentConsequences,
        minimumParticipatingWallRatio = participatingWalls,
        maximumCommutativeSafeChoiceProduct = maximumCommutativeSafety,
        minimumMeaningfulOrderingRate = meaningfulOrdering,
        rejectDenseButTrivial = minimum >= 0.36,
    )
}
