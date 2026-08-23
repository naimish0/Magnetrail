package com.rameshta.magnetrail.core.solver

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.BoardState

data class FirstSolutionResult(
    val solution: List<PlayerAction>?,
    val exploredStateCount: Int,
    val searchCapReached: Boolean,
)

/**
 * Finds one production-engine solution and stops. This is used for human-calibration candidates,
 * where a replay witness is required but counting every strategy would prematurely act as a
 * difficulty gate. Successful actions always remove one arrow, so the search graph is acyclic.
 */
class FirstSolutionFinder(
    private val engine: GameEngine = DefaultGameEngine(),
) {
    fun find(
        initial: BoardState,
        maxExploredStates: Int,
        preferredActionIds: List<String> = emptyList(),
    ): FirstSolutionResult {
        require(maxExploredStates > 0)
        val preferredRank = preferredActionIds.withIndex().associate { (index, id) -> id to index }
        val exhausted = hashSetOf<StateKey>()
        var explored = 0
        var capReached = false

        fun search(state: BoardState): List<PlayerAction>? {
            if (state.arrows.isEmpty()) return emptyList()
            val key = StateKey.from(state)
            if (key in exhausted) return null
            if (explored >= maxExploredStates) {
                capReached = true
                return null
            }
            explored += 1
            val actions = engine.validActions(state).sortedWith(
                compareBy<PlayerAction> { preferredRank[it.arrowId] ?: Int.MAX_VALUE }
                    .thenBy { if (it.arrowId.startsWith("v10a")) 0 else 1 }
                    .thenBy(PlayerAction::arrowId),
            )
            actions.forEach { action ->
                val result = engine.resolve(state, action)
                search(result.resultingState)?.let { suffix -> return listOf(action) + suffix }
            }
            exhausted += key
            return null
        }

        val solution = search(initial)
        return FirstSolutionResult(solution, explored, capReached)
    }
}
