package com.rameshta.magnetrail.core.engine

import com.rameshta.magnetrail.core.model.BoardState

interface GameEngine {
    fun resolve(state: BoardState, action: PlayerAction): ResolutionResult

    fun validActions(state: BoardState): List<PlayerAction>

    fun isDeadlocked(state: BoardState): Boolean
}

class DefaultGameEngine(
    private val routeTracer: RouteTracer = DeterministicRouteTracer(),
) : GameEngine {
    override fun resolve(state: BoardState, action: PlayerAction): ResolutionResult {
        val arrow = requireNotNull(state.arrow(action.arrowId)) {
            "Arrow '${action.arrowId}' is not present in level '${state.levelId}'"
        }
        val trace = routeTracer.trace(state, arrow)

        val polarityChange = if (trace.success) {
            trace.controllingMagnet?.let { magnet ->
                PolarityChange(magnet.id, magnet.polarity, magnet.polarity.flipped())
            }
        } else {
            null
        }

        val resultingState = if (trace.success) {
            state.copy(
                arrows = state.arrows.filterNot { it.id == arrow.id },
                magnets = state.magnets.map { magnet ->
                    if (magnet.id == polarityChange?.magnetId) {
                        magnet.copy(polarity = polarityChange.to)
                    } else {
                        magnet
                    }
                },
            )
        } else {
            state
        }

        val isWin = resultingState.arrows.isEmpty()
        val isDeadlocked = !isWin && isDeadlocked(resultingState)
        val collisionTarget = (trace.terminalEvent as? TerminalEvent.Collision)?.target

        return ResolutionResult(
            success = trace.success,
            originalState = state,
            resultingState = resultingState,
            selectedArrowId = arrow.id,
            printedDirection = trace.printedDirection,
            effectiveDirection = trace.effectiveDirection,
            controllingMagnetId = trace.controllingMagnet?.id,
            traversedCells = trace.traversedCells,
            terminalEvent = trace.terminalEvent,
            collisionTarget = collisionTarget,
            polarityChange = polarityChange,
            isWin = isWin,
            isDeadlocked = isDeadlocked,
        )
    }

    override fun validActions(state: BoardState): List<PlayerAction> = state.arrows.mapNotNull { arrow ->
        PlayerAction(arrow.id).takeIf { routeTracer.trace(state, arrow).success }
    }

    override fun isDeadlocked(state: BoardState): Boolean =
        state.arrows.isNotEmpty() && validActions(state).isEmpty()
}
