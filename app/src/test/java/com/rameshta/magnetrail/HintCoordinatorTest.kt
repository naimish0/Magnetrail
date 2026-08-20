package com.rameshta.magnetrail

import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.game.HintOutcome
import com.rameshta.magnetrail.game.HintProvider
import com.rameshta.magnetrail.game.SolverHintProvider
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HintCoordinatorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `hint uses current exact polarity and never mutates gameplay state`() = runTest(mainDispatcherRule.dispatcher) {
        val captured = AtomicReference<BoardState>()
        val provider = HintProvider { state ->
            captured.set(state)
            HintOutcome.SuggestedArrow("A")
        }
        val viewModel = GameViewModel(prototypeCatalog(), hintProvider = provider, debugUnlockAll = true)
        viewModel.onAction(GameAction.SelectLevel(5))
        viewModel.onAction(GameAction.LaunchArrow("B"))
        viewModel.onAction(GameAction.AnimationCompleted)
        val before = viewModel.uiState.value
        assertEquals(Polarity.PUSH, before.boardState.magnet("M1")?.polarity)

        viewModel.onAction(GameAction.RequestHint)
        advanceUntilIdle()

        val after = viewModel.uiState.value
        assertEquals(before.boardState, captured.get())
        assertEquals(before.boardState, after.boardState)
        assertEquals(before.moves, after.moves)
        assertEquals("A", after.suggestedArrowId)
        assertEquals(1, after.hintsUsed)
    }

    @Test
    fun `production solver returns a deterministic valid first action`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = GameViewModel(
            catalog = prototypeCatalog(),
            hintProvider = SolverHintProvider(dispatcher = mainDispatcherRule.dispatcher),
            debugUnlockAll = true,
        )
        viewModel.onAction(GameAction.SelectLevel(5))

        viewModel.onAction(GameAction.RequestHint)
        advanceUntilIdle()

        assertEquals("B", viewModel.uiState.value.suggestedArrowId)
        assertEquals("Hint: Try arrow B", viewModel.uiState.value.hintMessage)
    }

    @Test
    fun `restart cancels hint and protects against stale completion`() = runTest(mainDispatcherRule.dispatcher) {
        val deferred = CompletableDeferred<HintOutcome>()
        val viewModel = GameViewModel(
            catalog = prototypeCatalog(),
            hintProvider = HintProvider { deferred.await() },
            debugUnlockAll = true,
        )

        viewModel.onAction(GameAction.RequestHint)
        runCurrent()
        viewModel.onAction(GameAction.Restart)
        deferred.complete(HintOutcome.SuggestedArrow("A"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.suggestedArrowId)
        assertNull(viewModel.uiState.value.hintMessage)
        assertEquals(0, viewModel.uiState.value.hintsUsed)
    }

    @Test
    fun `hint counter increments only for a usable shown hint`() = runTest(mainDispatcherRule.dispatcher) {
        val noSolution = GameViewModel(
            catalog = prototypeCatalog(),
            hintProvider = HintProvider { HintOutcome.NoSolution },
        )
        noSolution.onAction(GameAction.RequestHint)
        advanceUntilIdle()
        assertEquals(0, noSolution.uiState.value.hintsUsed)
        assertNull(noSolution.uiState.value.suggestedArrowId)

        val usable = GameViewModel(
            catalog = prototypeCatalog(),
            hintProvider = HintProvider { HintOutcome.SuggestedArrow("A") },
        )
        usable.onAction(GameAction.RequestHint)
        advanceUntilIdle()
        usable.onAction(GameAction.RequestHint)
        advanceUntilIdle()
        assertEquals(1, usable.uiState.value.hintsUsed)
        assertEquals("A", usable.uiState.value.suggestedArrowId)
    }

    @Test
    fun `deadlocked state disables hint without calling provider`() = runTest(mainDispatcherRule.dispatcher) {
        var providerCalled = false
        val level = LevelDefinition(
            id = "deadlocked-hint-test",
            number = 1,
            title = "Keep trying",
            width = 4,
            height = 4,
            arrows = listOf(Arrow("A", Position(2, 2), Direction.EAST)),
            magnets = emptyList(),
            walls = listOf(Wall(Position(2, 3))),
            designedSolutions = emptyList(),
        )
        val viewModel = GameViewModel(
            catalog = LevelCatalog(1, "magnetrail-core-1", "deadlocked-hint-test", listOf(level)),
            hintProvider = HintProvider {
                providerCalled = true
                HintOutcome.NoSolution
            },
        )

        assertTrue(viewModel.uiState.value.isDeadlocked)
        assertFalse(viewModel.uiState.value.canRequestHint)
        viewModel.onAction(GameAction.RequestHint)
        advanceUntilIdle()

        assertFalse(providerCalled)
        assertNull(viewModel.uiState.value.hintMessage)
    }

    @Test
    fun `successful move clears shown hint`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = GameViewModel(
            catalog = prototypeCatalog(),
            hintProvider = HintProvider { HintOutcome.SuggestedArrow("A") },
        )
        viewModel.onAction(GameAction.RequestHint)
        advanceUntilIdle()
        assertEquals("A", viewModel.uiState.value.suggestedArrowId)

        viewModel.onAction(GameAction.LaunchArrow("A"))
        viewModel.onAction(GameAction.AnimationCompleted)

        assertNull(viewModel.uiState.value.suggestedArrowId)
        assertFalse(viewModel.uiState.value.inputEnabled)
        assertTrue(viewModel.uiState.value.isComplete)
    }

    @Test
    fun `shown hint makes three stars unavailable for the attempt`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = GameViewModel(
            catalog = prototypeCatalog(),
            hintProvider = HintProvider { HintOutcome.SuggestedArrow("A") },
        )
        viewModel.onAction(GameAction.RequestHint)
        advanceUntilIdle()
        viewModel.onAction(GameAction.LaunchArrow("A"))
        viewModel.onAction(GameAction.AnimationCompleted)

        assertEquals(2, viewModel.uiState.value.completionReceipt?.grade?.stars)
    }
}
