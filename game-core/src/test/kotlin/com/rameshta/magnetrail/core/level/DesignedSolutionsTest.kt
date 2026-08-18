package com.rameshta.magnetrail.core.level

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.prototypeCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DesignedSolutionsTest(
    private val level: LevelDefinition,
    private val solutionNumber: Int,
    private val arrowIds: List<String>,
) {
    private val engine = DefaultGameEngine()

    @Test
    fun `designed solution replays successfully`() {
        var state = level.initialState()
        arrowIds.forEachIndexed { moveIndex, arrowId ->
            val result = engine.resolve(state, PlayerAction(arrowId))
            assertTrue(
                "${level.id} solution $solutionNumber failed on move ${moveIndex + 1} ($arrowId): ${result.terminalEvent}",
                result.success,
            )
            state = result.resultingState
        }

        assertTrue("${level.id} solution $solutionNumber did not clear the board", state.arrows.isEmpty())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} solution {1}: {2}")
        fun cases(): List<Array<Any>> = prototypeCatalog().levels.flatMap { level ->
            level.designedSolutions.mapIndexed { index, solution ->
                arrayOf(level, index + 1, solution)
            }
        }
    }
}
