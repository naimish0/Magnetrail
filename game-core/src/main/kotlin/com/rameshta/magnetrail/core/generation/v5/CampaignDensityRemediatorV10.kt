package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.generation.SeededRandom
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.solver.FirstSolutionFinder
import kotlin.math.ceil

enum class DensityTopologyV10 {
    HORIZONTAL_CASCADES,
    VERTICAL_CASCADES,
    MIXED_CASCADES,
}

data class DensityRemediationSpecV10(
    val targetArrowCount: Int,
    val minimumOccupancyRatio: Double,
    val topology: DensityTopologyV10,
) {
    init {
        require(targetArrowCount > 0)
        require(minimumOccupancyRatio in 0.0..1.0)
    }
}

data class DensityRemediationResultV10(
    val level: LevelDefinition,
    val topology: DensityTopologyV10,
    val auxiliaryActionIds: List<String>,
    val auxiliaryDependencyCount: Int,
)

/**
 * Adds visible, ordered arrow cascades before filling only the remaining density gap with walls.
 * The cascade may exercise magnets and interleave with source actions. A production-engine search
 * supplies a concrete replay witness before the calibration certification pipeline runs.
 */
class CampaignDensityRemediatorV10(
    private val engine: GameEngine = DefaultGameEngine(),
    private val solutionFinder: FirstSolutionFinder = FirstSolutionFinder(engine),
) {
    fun remediate(
        source: LevelDefinition,
        spec: DensityRemediationSpecV10,
        seed: Long,
    ): DensityRemediationResultV10? {
        if (source.arrows.size > spec.targetArrowCount) return null
        val base = source.copy(metadata = null)
        val sourceSolution = base.designedSolutions.firstOrNull() ?: return null
        if (!replayWins(base.initialState(), sourceSolution)) return null

        val random = SeededRandom(seed)
        val requiredExtraArrows = spec.targetArrowCount - base.arrows.size
        val additions = buildCascadeArrows(
            base,
            requiredExtraArrows,
            spec.topology,
            random,
        ) ?: return null
        var candidate = base.copy(
            arrows = base.arrows + additions,
            designedSolutions = emptyList(),
        )
        val preferredActions = additions.map { it.id } + sourceSolution
        var solvedActions = solvedActions(candidate, preferredActions) ?: return null
        val dependencies = initialAuxiliaryDependencies(
            candidate.initialState(),
            additions.mapTo(hashSetOf()) { it.id },
        )
        if (requiredExtraArrows >= 2 && dependencies < 1) {
            return null
        }

        val minimumOccupied = ceil(base.width * base.height * spec.minimumOccupancyRatio).toInt()
        val wallsNeeded = (minimumOccupied - occupiedCount(candidate)).coerceAtLeast(0)
        val emptyCells = allCells(base).filterNot { position ->
            candidate.arrows.any { it.position == position } ||
                candidate.magnets.any { it.position == position } ||
                candidate.walls.any { it.position == position }
        }.toMutableList()
        emptyCells.stableShuffle(random)
        val addedWalls = mutableListOf<Wall>()
        for (position in emptyCells) {
            if (addedWalls.size == wallsNeeded) break
            val proposed = candidate.copy(walls = candidate.walls + addedWalls + Wall(position))
            val proposedSolution = if (replayWins(proposed.initialState(), solvedActions)) {
                solvedActions
            } else {
                // A density wall can invalidate one valid ordering without making the board
                // unsolvable. Search again before rejecting the cell so remediation does not
                // accidentally depend on a single arbitrary witness returned above.
                solvedActions(proposed, preferredActions) ?: continue
            }
            addedWalls += Wall(position)
            solvedActions = proposedSolution
        }
        if (addedWalls.size != wallsNeeded) return null
        candidate = candidate.copy(
            walls = candidate.walls + addedWalls,
            designedSolutions = listOf(solvedActions),
        )
        if (!replayWins(candidate.initialState(), candidate.designedSolutions.single())) return null
        return DensityRemediationResultV10(
            level = candidate,
            topology = spec.topology,
            auxiliaryActionIds = solvedActions.filter { it.startsWith("v10a") },
            auxiliaryDependencyCount = dependencies,
        )
    }

    private fun buildCascadeArrows(
        level: LevelDefinition,
        count: Int,
        topology: DensityTopologyV10,
        random: SeededRandom,
    ): List<Arrow>? {
        if (count == 0) return emptyList()
        val sourceOccupied = buildSet {
            addAll(level.arrows.map { it.position })
            addAll(level.magnets.map { it.position })
            addAll(level.walls.map { it.position })
        }
        val horizontal = buildList {
            for (row in 1..level.height) {
                add(RayV10((1..level.width).map { Position(row, it) }, Direction.WEST))
                add(RayV10((level.width downTo 1).map { Position(row, it) }, Direction.EAST))
            }
        }
        val vertical = buildList {
            for (column in 1..level.width) {
                add(RayV10((1..level.height).map { Position(it, column) }, Direction.NORTH))
                add(RayV10((level.height downTo 1).map { Position(it, column) }, Direction.SOUTH))
            }
        }
        val primary = when (topology) {
            DensityTopologyV10.HORIZONTAL_CASCADES -> horizontal
            DensityTopologyV10.VERTICAL_CASCADES -> vertical
            DensityTopologyV10.MIXED_CASCADES -> horizontal.zipInterleaved(vertical)
        }.toMutableList().also { it.stableShuffle(random) }
        val secondary = when (topology) {
            DensityTopologyV10.HORIZONTAL_CASCADES -> vertical
            DensityTopologyV10.VERTICAL_CASCADES -> horizontal
            DensityTopologyV10.MIXED_CASCADES -> emptyList()
        }.toMutableList().also { it.stableShuffle(random) }
        // Prefer a real cascade over isolated edge arrows. The prior shuffle remains the stable
        // tie-breaker, so seeds still produce different silhouettes within the same topology.
        val rays = (primary + secondary).sortedByDescending { ray ->
            ray.positions.takeWhile { it !in sourceOccupied }.size.coerceAtMost(2)
        }
        val additions = mutableListOf<Arrow>()
        val occupied = sourceOccupied.toMutableSet()
        val desiredCascadeLength = minOf(
            count,
            when {
                count >= 8 -> 6
                else -> 2
            },
        )
        val cascadeRay = rays.firstOrNull { ray ->
            ray.positions.takeWhile { it !in occupied }.size >= desiredCascadeLength
        } ?: rays.filter { ray -> ray.positions.takeWhile { it !in occupied }.size >= 2 }
            .maxByOrNull { ray -> ray.positions.takeWhile { it !in occupied }.size }
        cascadeRay?.let { ray ->
            val prefix = ray.positions.takeWhile { it !in occupied }
            prefix.take(desiredCascadeLength).forEach { position ->
                additions += Arrow(
                    id = "v10a${(additions.size + 1).toString().padStart(2, '0')}",
                    position = position,
                    printedDirection = ray.outward,
                )
                occupied += position
            }
        }
        // The mandatory cascade supplies a real ordering dependency. Seeded free-cell choices for
        // the remaining arrows expand the visual and interactive layout space far beyond a set of
        // reflected edge templates; the production search below still has to solve the result.
        if (additions.size < count) {
            val fallback = allCells(level).filterNot { it in occupied }.flatMap { position ->
                preferredOutwardDirections(position, level).map { direction -> position to direction }
            }.toMutableList().also { it.stableShuffle(random) }
            fallback.forEach { (position, direction) ->
                if (additions.size == count || position in occupied) return@forEach
                val arrow = Arrow(
                    id = "v10a${(additions.size + 1).toString().padStart(2, '0')}",
                    position = position,
                    printedDirection = direction,
                )
                additions += arrow
                occupied += position
            }
        }
        return additions.takeIf { it.size == count }
    }

    private fun preferredOutwardDirections(position: Position, level: LevelDefinition): List<Direction> =
        Direction.entries.sortedBy { direction ->
            when (direction) {
                Direction.NORTH -> position.row - 1
                Direction.SOUTH -> level.height - position.row
                Direction.WEST -> position.column - 1
                Direction.EAST -> level.width - position.column
            }
        }

    private fun solvedActions(level: LevelDefinition, preferredActions: List<String>): List<String>? {
        val result = solutionFinder.find(
            level.initialState(),
            maxExploredStates = 2_500_000,
            preferredActionIds = preferredActions,
        )
        return result.solution?.map(PlayerAction::arrowId)
    }

    /**
     * Counts immediately visible gameplay relationships rather than treating the added arrows as
     * decoration. An auxiliary arrow qualifies when another auxiliary arrow blocks its route or a
     * magnet controls it. Magnet control can deliberately redirect a geometric cascade, so either
     * relationship is sufficient for the calibration board.
     */
    private fun initialAuxiliaryDependencies(state: BoardState, auxiliaryIds: Set<String>): Int =
        auxiliaryIds.count { arrowId ->
            val result = engine.resolve(state, PlayerAction(arrowId))
            (!result.success && result.collisionTarget?.entityId in auxiliaryIds) ||
                result.controllingMagnetId != null
        }

    private fun replayWins(initial: BoardState, actions: List<String>): Boolean {
        var state = initial
        actions.forEach { arrowId ->
            if (state.arrow(arrowId) == null) return false
            val result = engine.resolve(state, PlayerAction(arrowId))
            if (!result.success) return false
            state = result.resultingState
        }
        return state.arrows.isEmpty()
    }

    private fun occupiedCount(level: LevelDefinition): Int =
        level.arrows.size + level.magnets.size + level.walls.size

    private fun allCells(level: LevelDefinition): List<Position> = (1..level.height).flatMap { row ->
        (1..level.width).map { column -> Position(row, column) }
    }

    private fun <T> MutableList<T>.stableShuffle(random: SeededRandom) {
        for (index in lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val value = this[index]
            this[index] = this[swap]
            this[swap] = value
        }
    }

    private fun <T> List<T>.zipInterleaved(other: List<T>): List<T> = buildList {
        for (index in 0 until maxOf(this@zipInterleaved.size, other.size)) {
            this@zipInterleaved.getOrNull(index)?.let(::add)
            other.getOrNull(index)?.let(::add)
        }
    }

    private data class RayV10(val positions: List<Position>, val outward: Direction)
}
