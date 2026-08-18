package com.rameshta.magnetrail.core.solver

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.BoardState
import kotlin.math.min

class Solver(
    private val gameEngine: GameEngine = DefaultGameEngine(),
) {
    fun solve(initialState: BoardState, solutionLimit: Int = DEFAULT_SOLUTION_LIMIT): SolverResult {
        require(solutionLimit > 0) { "solutionLimit must be positive" }

        val internalLimit = if (solutionLimit == Int.MAX_VALUE) Int.MAX_VALUE else solutionLimit + 1
        val memo = mutableMapOf<StateKey, StateAnalysis>()
        var exploredStateCount = 0

        fun analyze(state: BoardState): StateAnalysis {
            val key = StateKey.from(state)
            memo[key]?.let { return it }
            exploredStateCount += 1

            if (state.arrows.isEmpty()) {
                return StateAnalysis(solutionCount = 1, oneSolution = emptyList(), shortestDepth = 0)
                    .also { memo[key] = it }
            }

            var solutionCount = 0
            var oneSolution: List<PlayerAction>? = null
            var shortestDepth: Int? = null

            for (action in gameEngine.validActions(state)) {
                val resolution = gameEngine.resolve(state, action)
                check(resolution.success) { "GameEngine returned a failing action from validActions: $action" }
                val child = analyze(resolution.resultingState)
                if (child.solutionCount == 0) continue

                solutionCount = min(internalLimit, solutionCount + child.solutionCount)
                if (oneSolution == null) {
                    oneSolution = listOf(action) + requireNotNull(child.oneSolution)
                }
                child.shortestDepth?.let { childDepth ->
                    shortestDepth = minOf(shortestDepth ?: Int.MAX_VALUE, childDepth + 1)
                }
            }

            return StateAnalysis(solutionCount, oneSolution, shortestDepth).also { memo[key] = it }
        }

        val root = analyze(initialState)
        val validFirstActions = if (initialState.arrows.isEmpty()) {
            emptyList()
        } else {
            gameEngine.validActions(initialState).filter { action ->
                val next = gameEngine.resolve(initialState, action).resultingState
                analyze(next).solutionCount > 0
            }
        }
        val countWasCapped = root.solutionCount > solutionLimit

        return SolverResult(
            solvable = root.solutionCount > 0,
            oneCleanSolution = root.oneSolution,
            solutionCount = min(root.solutionCount, solutionLimit),
            solutionCountCapped = countWasCapped,
            shortestDepth = root.shortestDepth,
            validFirstActions = validFirstActions,
            exploredStateCount = exploredStateCount,
        )
    }

    private data class StateAnalysis(
        val solutionCount: Int,
        val oneSolution: List<PlayerAction>?,
        val shortestDepth: Int?,
    )

    companion object {
        const val DEFAULT_SOLUTION_LIMIT = 100
    }
}
