package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round

@Serializable
data class SpatialDensityMetricsV5(
    val boardCells: Int = 0,
    val occupiedCells: Int = 0,
    val emptyCells: Int = 0,
    val occupancyRatio: Double = 0.0,
    val arrowRatio: Double = 0.0,
    val magnetRatio: Double = 0.0,
    val wallRatio: Double = 0.0,
    val totalObjectCount: Int = 0,
    val authoredAlignedMagneticRelationshipCount: Int = 0,
    val authoredLongRangeMagneticRelationshipCount: Int = 0,
    val arrowsWithMultipleAlignedMagnets: Int = 0,
    val arrowBlockerCandidateRelationshipCount: Int = 0,
    val wallOcclusionCandidateRelationshipCount: Int = 0,
    val overlappingAuthoredCellCount: Int = 0,
)

/** Cheap, deterministic spatial diagnostics. Strategic meaning is assessed later by V4/V5. */
object SpatialDensityAnalyzerV5 {
    fun analyze(level: LevelDefinition, longRangeDistance: Int = 4): SpatialDensityMetricsV5 {
        require(longRangeDistance > 0)
        val boardCells = level.width * level.height
        val positions = buildList {
            addAll(level.arrows.map { it.position })
            addAll(level.magnets.map { it.position })
            addAll(level.walls.map { it.position })
        }
        val aligned = level.arrows.flatMap { arrow ->
            level.magnets.filter { magnet -> aligned(arrow.position, magnet.position) }
                .map { magnet -> Triple(arrow.id, arrow.position, magnet.position) }
        }
        val arrowsWithMultiple = aligned.groupingBy { it.first }.eachCount().count { it.value > 1 }
        val arrowBlockers = buildSet {
            level.arrows.forEach { arrow ->
                level.magnets.forEach { magnet ->
                    if (!aligned(arrow.position, magnet.position)) return@forEach
                    level.arrows.filter { it.id != arrow.id }.forEach { blocker ->
                        if (between(arrow.position, blocker.position, magnet.position)) {
                            add("${blocker.id}>${arrow.id}:${magnet.id}")
                        }
                    }
                }
            }
        }
        val wallOccluders = buildSet {
            level.arrows.forEach { arrow ->
                level.magnets.forEach { magnet ->
                    if (!aligned(arrow.position, magnet.position)) return@forEach
                    level.walls.forEach { wall ->
                        if (between(arrow.position, wall.position, magnet.position)) {
                            add("${wall.position.row},${wall.position.column}>${arrow.id}:${magnet.id}")
                        }
                    }
                }
            }
        }
        return SpatialDensityMetricsV5(
            boardCells = boardCells,
            occupiedCells = positions.distinct().size,
            emptyCells = boardCells - positions.distinct().size,
            occupancyRatio = ratio(positions.distinct().size, boardCells),
            arrowRatio = ratio(level.arrows.size, boardCells),
            magnetRatio = ratio(level.magnets.size, boardCells),
            wallRatio = ratio(level.walls.size, boardCells),
            totalObjectCount = positions.size,
            authoredAlignedMagneticRelationshipCount = aligned.size,
            authoredLongRangeMagneticRelationshipCount = aligned.count { (_, arrow, magnet) ->
                distance(arrow, magnet) >= longRangeDistance
            },
            arrowsWithMultipleAlignedMagnets = arrowsWithMultiple,
            arrowBlockerCandidateRelationshipCount = arrowBlockers.size,
            wallOcclusionCandidateRelationshipCount = wallOccluders.size,
            overlappingAuthoredCellCount = positions.size - positions.distinct().size,
        )
    }

    private fun aligned(first: Position, second: Position): Boolean =
        first.row == second.row || first.column == second.column

    private fun between(start: Position, candidate: Position, end: Position): Boolean = when {
        start.row == end.row && candidate.row == start.row ->
            candidate.column in (minOf(start.column, end.column) + 1)..<maxOf(start.column, end.column)
        start.column == end.column && candidate.column == start.column ->
            candidate.row in (minOf(start.row, end.row) + 1)..<maxOf(start.row, end.row)
        else -> false
    }

    private fun distance(first: Position, second: Position): Int =
        abs(first.row - second.row) + abs(first.column - second.column)

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else round(numerator.toDouble() / denominator * 10_000.0) / 10_000.0
}

/** D2.1 profile gate; it never assigns puzzle difficulty. */
object SpatialDensityGateV5 {
    fun isDenseButTrivial(
        occupancyRatio: Double,
        targetOccupancyRatio: Double,
        greedySolveRate: Double,
        safeChoiceRatio: Double,
        meaningfulFailureRate: Double,
    ): Boolean = occupancyRatio >= targetOccupancyRatio && greedySolveRate >= 1.0 &&
        safeChoiceRatio >= 0.95 && meaningfulFailureRate < 0.02

    fun evaluateAuthored(
        level: LevelDefinition,
        profile: SpatialDensityProfileV5,
    ): List<String> {
        val metrics = SpatialDensityAnalyzerV5.analyze(level, profile.longRangeDistance)
        return buildList {
            if (metrics.overlappingAuthoredCellCount > 0) add("overlapping-authored-objects")
            if (metrics.occupancyRatio < profile.minimumOccupancyRatio) add("occupancy-below-profile")
            if (metrics.occupancyRatio > profile.maximumOccupancyRatio) add("occupancy-above-profile")
            if (level.arrows.size !in profile.arrowCount) add("arrow-count-out-of-spatial-profile")
            if (level.magnets.size !in profile.magnetCount) add("magnet-count-out-of-spatial-profile")
            if (level.walls.size !in profile.wallCount) add("wall-count-out-of-spatial-profile")
            if (metrics.authoredLongRangeMagneticRelationshipCount <
                profile.minimumLongRangeMagneticRelationships
            ) add("long-range-magnetic-relationships-below-profile")
            if (metrics.arrowBlockerCandidateRelationshipCount < profile.minimumArrowBlockerRelationships) {
                add("arrow-blocker-relationships-below-profile")
            }
        }
    }

    fun evaluateMeaningful(
        diagnostics: StructuralDiagnosticsV5,
        profile: SpatialDensityProfileV5,
    ): List<String> = buildList {
        val graph = diagnostics.interactionGraph
        val objectCount = graph.nodes.size
        val interactingRatio = if (objectCount == 0) 0.0 else
            graph.uniqueInteractingObjects.toDouble() / objectCount
        val irrelevantRatio = if (objectCount == 0) 0.0 else
            diagnostics.objectRelevance.irrelevantObjectCount.toDouble() / objectCount
        val walls = diagnostics.objectRelevance.objects.filter { it.objectType == InteractionNodeTypeV5.WALL }
        val participatingWallRatio = if (walls.isEmpty()) 1.0 else walls.count {
            it.classification != ObjectRelevanceClassV5.IRRELEVANT
        }.toDouble() / walls.size
        if (diagnostics.meaningfulLineOfSightInteractionCount <
            profile.minimumMeaningfulLineOfSightInteractions
        ) add("meaningful-los-interactions-below-profile")
        if (diagnostics.meaningfulArrowBlockerRelationshipCount < profile.minimumArrowBlockerRelationships) {
            add("meaningful-arrow-blockers-below-profile")
        }
        if (diagnostics.magneticRelationshipDistances.count { it >= profile.longRangeDistance } <
            profile.minimumLongRangeMagneticRelationships
        ) add("meaningful-long-range-magnetism-below-profile")
        if (interactingRatio < profile.minimumInteractingObjectRatio) add("interacting-object-ratio-below-profile")
        if (diagnostics.objectRelevance.averageScore < profile.minimumAverageObjectRelevance) {
            add("average-object-relevance-below-profile")
        }
        if (irrelevantRatio > profile.maximumIrrelevantObjectRatio) add("irrelevant-object-ratio-above-profile")
        if (diagnostics.controllerChangeCount < profile.minimumControllerChanges) {
            add("controller-changes-below-profile")
        }
        if (diagnostics.cancellationCriticalDecisionCount < profile.minimumCancellationCriticalDecisions) {
            add("meaningful-cancellation-below-profile")
        }
        if (diagnostics.exposureDepth < profile.minimumExposureDepth) add("exposure-depth-below-profile")
        if (diagnostics.persistentConsequenceCount < profile.minimumPersistentConsequenceCount) {
            add("persistent-consequences-below-profile")
        }
        if (participatingWallRatio < profile.minimumParticipatingWallRatio) {
            add("participating-wall-ratio-below-profile")
        }
        val commutativeSafeChoiceProduct =
            diagnostics.viablePairCommutationRatio * diagnostics.safeChoiceRatio
        if (commutativeSafeChoiceProduct > profile.maximumCommutativeSafeChoiceProduct) {
            add("commutative-safe-choice-product-above-profile")
        }
        if (diagnostics.meaningfulOrderingRate < profile.minimumMeaningfulOrderingRate) {
            add("meaningful-ordering-rate-below-profile")
        }
        if (profile.rejectDenseButTrivial && isDenseButTrivial(
                occupancyRatio = diagnostics.spatialDensity.occupancyRatio,
                targetOccupancyRatio = profile.targetOccupancyRatio,
                greedySolveRate = diagnostics.greedySolveRate,
                safeChoiceRatio = diagnostics.safeChoiceRatio,
                meaningfulFailureRate = diagnostics.meaningfulFailureRate,
            )
        ) add("dense-but-trivial")
    }
}
