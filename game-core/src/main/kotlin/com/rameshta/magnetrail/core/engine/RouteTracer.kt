package com.rameshta.magnetrail.core.engine

import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import kotlin.math.abs

data class RouteTrace(
    val arrowId: String,
    val printedDirection: Direction,
    val effectiveDirection: Direction,
    val controllingMagnet: Magnet?,
    val traversedCells: List<Position>,
    val terminalEvent: TerminalEvent,
    val success: Boolean,
)

fun interface RouteTracer {
    fun trace(state: BoardState, arrow: Arrow): RouteTrace
}

class DeterministicRouteTracer : RouteTracer {
    override fun trace(state: BoardState, arrow: Arrow): RouteTrace {
        require(state.arrow(arrow.id) == arrow) { "Arrow '${arrow.id}' is not present in the supplied board state" }

        val controllingMagnet = findControllingMagnet(state, arrow)
        val effectiveDirection = when (controllingMagnet?.polarity) {
            Polarity.PULL -> Direction.between(arrow.position, controllingMagnet.position)
            Polarity.PUSH -> Direction.between(arrow.position, controllingMagnet.position).opposite()
            null -> arrow.printedDirection
        }

        val traversedCells = mutableListOf<Position>()
        var current = arrow.position
        while (true) {
            val next = current.move(effectiveDirection)
            if (!state.contains(next)) {
                val terminalEvent = if (controllingMagnet?.polarity == Polarity.PULL) {
                    TerminalEvent.InvalidPullExit(
                        magnetId = controllingMagnet.id,
                        lastBoardPosition = current,
                        direction = effectiveDirection,
                    )
                } else {
                    TerminalEvent.Exit(
                        lastBoardPosition = current,
                        direction = effectiveDirection,
                    )
                }
                return RouteTrace(
                    arrowId = arrow.id,
                    printedDirection = arrow.printedDirection,
                    effectiveDirection = effectiveDirection,
                    controllingMagnet = controllingMagnet,
                    traversedCells = traversedCells.toList(),
                    terminalEvent = terminalEvent,
                    success = terminalEvent is TerminalEvent.Exit,
                )
            }

            traversedCells += next

            if (controllingMagnet?.polarity == Polarity.PULL && next == controllingMagnet.position) {
                return RouteTrace(
                    arrowId = arrow.id,
                    printedDirection = arrow.printedDirection,
                    effectiveDirection = effectiveDirection,
                    controllingMagnet = controllingMagnet,
                    traversedCells = traversedCells.toList(),
                    terminalEvent = TerminalEvent.PullCapture(controllingMagnet.id, next),
                    success = true,
                )
            }

            collisionAt(state, selectedArrowId = arrow.id, position = next)?.let { collisionTarget ->
                return RouteTrace(
                    arrowId = arrow.id,
                    printedDirection = arrow.printedDirection,
                    effectiveDirection = effectiveDirection,
                    controllingMagnet = controllingMagnet,
                    traversedCells = traversedCells.toList(),
                    terminalEvent = TerminalEvent.Collision(collisionTarget),
                    success = false,
                )
            }

            current = next
        }
    }

    fun findControllingMagnet(state: BoardState, arrow: Arrow): Magnet? {
        val visibleAlignedMagnets = state.magnets.filter { magnet ->
            isAligned(arrow.position, magnet.position) && hasClearLineOfSight(state, arrow, magnet)
        }
        val nearestDistance = visibleAlignedMagnets.minOfOrNull { distance(arrow.position, it.position) }
            ?: return null
        val nearest = visibleAlignedMagnets.filter { distance(arrow.position, it.position) == nearestDistance }
        return nearest.singleOrNull()
    }

    private fun hasClearLineOfSight(state: BoardState, arrow: Arrow, magnet: Magnet): Boolean {
        val direction = Direction.between(arrow.position, magnet.position)
        var position = arrow.position.move(direction)
        while (position != magnet.position) {
            if (isOccupied(state, selectedArrowId = arrow.id, position = position)) return false
            position = position.move(direction)
        }
        return true
    }

    private fun isAligned(first: Position, second: Position): Boolean =
        first.row == second.row || first.column == second.column

    private fun distance(first: Position, second: Position): Int =
        abs(first.row - second.row) + abs(first.column - second.column)

    private fun isOccupied(state: BoardState, selectedArrowId: String, position: Position): Boolean =
        collisionAt(state, selectedArrowId, position) != null

    private fun collisionAt(
        state: BoardState,
        selectedArrowId: String,
        position: Position,
    ): CollisionTarget? {
        state.arrows.firstOrNull { it.id != selectedArrowId && it.position == position }?.let { arrow ->
            return CollisionTarget(CollisionTargetType.ARROW, position, arrow.id)
        }
        state.walls.firstOrNull { it.position == position }?.let {
            return CollisionTarget(CollisionTargetType.WALL, position)
        }
        state.magnets.firstOrNull { it.position == position }?.let { magnet ->
            return CollisionTarget(CollisionTargetType.MAGNET, position, magnet.id)
        }
        return null
    }
}
