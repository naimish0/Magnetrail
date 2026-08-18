package com.rameshta.magnetrail.core.solver

import com.rameshta.magnetrail.core.arrow
import com.rameshta.magnetrail.core.board
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.prototypeCatalog
import com.rameshta.magnetrail.core.wall
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Polarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {
    private val engine = DefaultGameEngine()
    private val solver = Solver(engine)

    @Test
    fun `solver finds a clean solution for all twelve prototype levels`() {
        prototypeCatalog().levels.forEach { level ->
            val result = solver.solve(level.initialState())

            assertTrue("${level.id} was reported unsolvable", result.solvable)
            assertNotNull("${level.id} did not return a clean solution", result.oneCleanSolution)
            assertTrue(
                "${level.id} found fewer solutions than the authored catalog",
                result.solutionCount >= level.designedSolutions.size,
            )
            assertEquals(level.arrows.size, result.shortestDepth)
            assertTrue("${level.id} did not explore any state", result.exploredStateCount > 0)
            assertTrue("${level.id} has no valid first action", result.validFirstActions.isNotEmpty())
            replay(level.initialState(), requireNotNull(result.oneCleanSolution))
        }
    }

    @Test
    fun `solver reports deadlocked board as unsolvable`() {
        val deadlocked = board(
            arrows = listOf(arrow("A", 2, 2, Direction.EAST)),
            walls = listOf(wall(2, 3)),
        )

        val result = solver.solve(deadlocked)

        assertFalse(result.solvable)
        assertEquals(0, result.solutionCount)
        assertEquals(null, result.oneCleanSolution)
        assertTrue(result.validFirstActions.isEmpty())
    }

    @Test
    fun `solution counting honors its cap without losing solvability`() {
        val level = prototypeCatalog().level("proto-012")

        val result = solver.solve(level.initialState(), solutionLimit = 1)

        assertTrue(result.solvable)
        assertEquals(1, result.solutionCount)
        assertTrue(result.solutionCountCapped)
        assertNotNull(result.oneCleanSolution)
    }

    @Test
    fun `state key includes magnet polarity`() {
        val initial = prototypeCatalog().level("proto-003").initialState()
        val flipped = initial.copy(
            magnets = initial.magnets.map { it.copy(polarity = Polarity.PUSH) },
        )

        assertNotEquals(StateKey.from(initial), StateKey.from(flipped))
    }

    private fun replay(initialState: com.rameshta.magnetrail.core.model.BoardState, actions: List<PlayerAction>) {
        var state = initialState
        actions.forEach { action ->
            val result = engine.resolve(state, action)
            assertTrue("Solver returned invalid action $action", result.success)
            state = result.resultingState
        }
        assertTrue("Solver solution did not clear the board", state.arrows.isEmpty())
    }
}
