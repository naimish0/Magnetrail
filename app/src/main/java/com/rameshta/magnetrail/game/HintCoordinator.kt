package com.rameshta.magnetrail.game

import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.solver.Solver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface HintOutcome {
    data class SuggestedArrow(val arrowId: String) : HintOutcome

    data object NoSolution : HintOutcome
}

fun interface HintProvider {
    suspend fun hintFor(state: BoardState): HintOutcome
}

class SolverHintProvider(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val solverFactory: () -> Solver = ::Solver,
) : HintProvider {
    override suspend fun hintFor(state: BoardState): HintOutcome = withContext(dispatcher) {
        ensureActive()
        val result = solverFactory().solve(state, solutionLimit = 1)
        ensureActive()
        result.validFirstActions
            .minByOrNull { it.arrowId }
            ?.let { HintOutcome.SuggestedArrow(it.arrowId) }
            ?: HintOutcome.NoSolution
    }
}
