package com.rameshta.magnetrail

import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {
    @Test
    fun `initial level loads correctly`() {
        val viewModel = viewModel()

        with(viewModel.uiState.value) {
            assertEquals("proto-001", currentLevel.id)
            assertEquals(1, currentLevel.number)
            assertEquals(initialState, boardState)
            assertEquals(12, levels.size)
            assertTrue(inputEnabled)
            assertFalse(isComplete)
        }
    }

    @Test
    fun `valid tap creates one in-flight result and disables input`() {
        val viewModel = viewModel()

        viewModel.onAction(GameAction.LaunchArrow("A"))

        val state = viewModel.uiState.value
        assertNotNull(state.inFlightResult)
        assertEquals("A", state.inFlightResult?.selectedArrowId)
        assertFalse(state.inputEnabled)
        assertEquals(state.initialState, state.boardState)
    }

    @Test
    fun `second tap during animation is ignored`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.SelectLevel(index = 1))
        viewModel.onAction(GameAction.LaunchArrow("B"))
        val firstResult = viewModel.uiState.value.inFlightResult

        viewModel.onAction(GameAction.LaunchArrow("A"))

        assertSame(firstResult, viewModel.uiState.value.inFlightResult)
    }

    @Test
    fun `successful completion commits exactly the engine resulting state`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.LaunchArrow("A"))
        val result = requireNotNull(viewModel.uiState.value.inFlightResult)

        viewModel.onAction(GameAction.AnimationCompleted)

        val state = viewModel.uiState.value
        assertEquals(result.resultingState, state.boardState)
        assertEquals(listOf(result.originalState), state.undoHistory)
        assertNull(state.inFlightResult)
    }

    @Test
    fun `failed action preserves original state and does not add undo history`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.SelectLevel(index = 1))
        val original = viewModel.uiState.value.boardState
        viewModel.onAction(GameAction.LaunchArrow("A"))
        assertFalse(requireNotNull(viewModel.uiState.value.inFlightResult).success)

        viewModel.onAction(GameAction.AnimationCompleted)

        val state = viewModel.uiState.value
        assertSame(original, state.boardState)
        assertTrue(state.undoHistory.isEmpty())
        assertTrue(state.inputEnabled)
        assertEquals(1, state.moves)
        assertEquals(1, state.overloads)
    }

    @Test
    fun `successful controlled action exposes documented polarity change`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.SelectLevel(index = 2))

        viewModel.onAction(GameAction.LaunchArrow("A"))

        val change = requireNotNull(viewModel.uiState.value.inFlightResult?.polarityChange)
        assertEquals("M1", change.magnetId)
        assertEquals(Polarity.PULL, change.from)
        assertEquals(Polarity.PUSH, change.to)
    }

    @Test
    fun `undo restores an arrow and magnet polarity together`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.SelectLevel(index = 5))
        viewModel.onAction(GameAction.LaunchArrow("B"))
        viewModel.onAction(GameAction.AnimationCompleted)
        assertNull(viewModel.uiState.value.boardState.arrow("B"))
        assertEquals(Polarity.PUSH, viewModel.uiState.value.boardState.magnet("M1")?.polarity)

        viewModel.onAction(GameAction.Undo)

        assertNotNull(viewModel.uiState.value.boardState.arrow("B"))
        assertEquals(Polarity.PULL, viewModel.uiState.value.boardState.magnet("M1")?.polarity)
        assertTrue(viewModel.uiState.value.undoHistory.isEmpty())
    }

    @Test
    fun `restart restores exact initial state and clears history`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.SelectLevel(index = 4))
        val initial = viewModel.uiState.value.initialState
        viewModel.onAction(GameAction.LaunchArrow("A"))
        viewModel.onAction(GameAction.AnimationCompleted)
        assertTrue(viewModel.uiState.value.undoHistory.isNotEmpty())

        viewModel.onAction(GameAction.Restart)

        val state = viewModel.uiState.value
        assertSame(initial, state.boardState)
        assertTrue(state.undoHistory.isEmpty())
        assertFalse(state.isComplete)
        assertEquals(0, state.moves)
        assertEquals(0, state.overloads)
    }

    @Test
    fun `undo preserves accepted action accounting`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.SelectLevel(index = 1))
        viewModel.onAction(GameAction.LaunchArrow("A"))
        viewModel.onAction(GameAction.AnimationCompleted)
        viewModel.onAction(GameAction.LaunchArrow("B"))
        viewModel.onAction(GameAction.AnimationCompleted)
        viewModel.onAction(GameAction.Undo)

        assertEquals(2, viewModel.uiState.value.moves)
        assertEquals(1, viewModel.uiState.value.overloads)
    }

    @Test
    fun `level change resets committed state and history`() {
        val viewModel = viewModel()
        viewModel.onAction(GameAction.LaunchArrow("A"))
        viewModel.onAction(GameAction.AnimationCompleted)
        assertTrue(viewModel.uiState.value.undoHistory.isNotEmpty())

        viewModel.onAction(GameAction.SelectLevel(index = 11))

        val state = viewModel.uiState.value
        assertEquals("proto-012", state.currentLevel.id)
        assertEquals(state.initialState, state.boardState)
        assertEquals(4, state.boardState.arrows.size)
        assertTrue(state.undoHistory.isEmpty())
        assertFalse(state.isComplete)
    }

    @Test
    fun `win appears only after final animation completion`() {
        val viewModel = viewModel()

        viewModel.onAction(GameAction.LaunchArrow("A"))
        assertFalse(viewModel.uiState.value.isComplete)
        assertFalse(viewModel.uiState.value.inputEnabled)

        viewModel.onAction(GameAction.AnimationCompleted)
        assertTrue(viewModel.uiState.value.isComplete)
        assertFalse(viewModel.uiState.value.inputEnabled)
    }

    @Test
    fun `deadlock is surfaced without mutating the board`() {
        val deadlockedLevel = LevelDefinition(
            id = "deadlocked-test",
            number = 1,
            title = "Deadlocked",
            width = 4,
            height = 4,
            arrows = listOf(Arrow("A", Position(2, 2), Direction.EAST)),
            magnets = emptyList(),
            walls = listOf(Wall(Position(2, 3))),
            designedSolutions = emptyList(),
        )
        val catalog = LevelCatalog(1, "magnetrail-core-1", "deadlock-test", listOf(deadlockedLevel))
        val viewModel = GameViewModel(catalog)
        val original = viewModel.uiState.value.boardState

        assertTrue(viewModel.uiState.value.isDeadlocked)
        assertSame(original, viewModel.uiState.value.boardState)
    }

    private fun viewModel(): GameViewModel = GameViewModel(
        catalog = prototypeCatalog(),
        debugUnlockAll = true,
    )

    private fun prototypeCatalog(): LevelCatalog {
        val resource = checkNotNull(javaClass.getResource("/Magnetrail_Prototype_Levels_v1.json"))
        return LevelParser().parseCatalog(resource.readText())
    }
}
