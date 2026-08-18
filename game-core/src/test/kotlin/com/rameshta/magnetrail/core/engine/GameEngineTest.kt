package com.rameshta.magnetrail.core.engine

import com.rameshta.magnetrail.core.arrow
import com.rameshta.magnetrail.core.board
import com.rameshta.magnetrail.core.magnet
import com.rameshta.magnetrail.core.wall
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val engine = DefaultGameEngine()

    @Test
    fun `Pull captures the arrow and flips only its controlling magnet`() {
        val state = board(
            arrows = listOf(arrow("A", 3, 1, Direction.NORTH)),
            magnets = listOf(
                magnet("M1", 3, 3, Polarity.PULL),
                magnet("M2", 5, 5, Polarity.PULL),
            ),
        )

        val result = engine.resolve(state, PlayerAction("A"))

        assertTrue(result.success)
        assertEquals(Direction.EAST, result.effectiveDirection)
        assertEquals(listOf(Position(3, 2), Position(3, 3)), result.traversedCells)
        assertEquals(TerminalEvent.PullCapture("M1", Position(3, 3)), result.terminalEvent)
        assertEquals(PolarityChange("M1", Polarity.PULL, Polarity.PUSH), result.polarityChange)
        assertEquals(Polarity.PUSH, result.resultingState.magnet("M1")?.polarity)
        assertEquals(Polarity.PULL, result.resultingState.magnet("M2")?.polarity)
        assertTrue(result.resultingState.arrows.isEmpty())
        assertTrue(result.isWin)
        assertFalse(result.isDeadlocked)
    }

    @Test
    fun `Push sends the arrow away and flips the controller`() {
        val state = board(
            arrows = listOf(arrow("A", 3, 1, Direction.NORTH)),
            magnets = listOf(magnet("M1", 3, 3, Polarity.PUSH)),
        )

        val result = engine.resolve(state, PlayerAction("A"))

        assertTrue(result.success)
        assertEquals(Direction.WEST, result.effectiveDirection)
        assertTrue(result.terminalEvent is TerminalEvent.Exit)
        assertEquals(Polarity.PULL, result.resultingState.magnet("M1")?.polarity)
    }

    @Test
    fun `unaffected exit removes the arrow without flipping a magnet`() {
        val state = board(
            width = 4,
            height = 4,
            arrows = listOf(arrow("A", 2, 2, Direction.EAST)),
            magnets = listOf(magnet("M1", 4, 4, Polarity.PULL)),
        )

        val result = engine.resolve(state, PlayerAction("A"))

        assertTrue(result.success)
        assertNull(result.controllingMagnetId)
        assertNull(result.polarityChange)
        assertEquals(Polarity.PULL, result.resultingState.magnet("M1")?.polarity)
        assertEquals(listOf(Position(2, 3), Position(2, 4)), result.traversedCells)
    }

    @Test
    fun `collision with another arrow fails and preserves the exact state`() {
        val state = board(
            arrows = listOf(
                arrow("A", 2, 1, Direction.EAST),
                arrow("B", 2, 2, Direction.NORTH),
            ),
        )

        val result = engine.resolve(state, PlayerAction("A"))

        assertFalse(result.success)
        assertEquals(CollisionTarget(CollisionTargetType.ARROW, Position(2, 2), "B"), result.collisionTarget)
        assertSame(state, result.resultingState)
        assertNull(result.polarityChange)
    }

    @Test
    fun `collision with a wall fails`() {
        val state = board(
            arrows = listOf(arrow("A", 2, 1, Direction.EAST)),
            walls = listOf(wall(2, 3)),
        )

        val result = engine.resolve(state, PlayerAction("A"))

        assertFalse(result.success)
        assertEquals(CollisionTargetType.WALL, result.collisionTarget?.type)
        assertEquals(Position(2, 3), result.collisionTarget?.position)
        assertSame(state, result.resultingState)
    }

    @Test
    fun `collision with a non-controlling magnet fails without a flip`() {
        val state = board(
            arrows = listOf(arrow("A", 3, 3, Direction.NORTH)),
            magnets = listOf(
                magnet("M1", 3, 2, Polarity.PUSH),
                magnet("M2", 3, 5, Polarity.PULL),
            ),
        )

        val result = engine.resolve(state, PlayerAction("A"))

        assertFalse(result.success)
        assertEquals("M1", result.controllingMagnetId)
        assertEquals(CollisionTarget(CollisionTargetType.MAGNET, Position(3, 5), "M2"), result.collisionTarget)
        assertNull(result.polarityChange)
        assertSame(state, result.resultingState)
    }

    @Test
    fun `invalid Pull exit leaves state unchanged`() {
        val selected = arrow("A", 1, 1, Direction.NORTH)
        val controller = magnet("M1", 1, 3, Polarity.PULL)
        val state = board(arrows = listOf(selected), magnets = listOf(controller))
        val invalidPullTracer = RouteTracer { _, _ ->
            RouteTrace(
                arrowId = selected.id,
                printedDirection = selected.printedDirection,
                effectiveDirection = Direction.NORTH,
                controllingMagnet = controller,
                traversedCells = emptyList(),
                terminalEvent = TerminalEvent.InvalidPullExit("M1", selected.position, Direction.NORTH),
                success = false,
            )
        }

        val result = DefaultGameEngine(invalidPullTracer).resolve(state, PlayerAction("A"))

        assertFalse(result.success)
        assertTrue(result.terminalEvent is TerminalEvent.InvalidPullExit)
        assertSame(state, result.resultingState)
        assertNull(result.polarityChange)
    }

    @Test
    fun `deadlock is reported when no remaining arrow can launch`() {
        val state = board(
            arrows = listOf(arrow("A", 2, 2, Direction.EAST)),
            walls = listOf(wall(2, 3)),
        )

        assertTrue(engine.isDeadlocked(state))
        assertTrue(engine.validActions(state).isEmpty())
        assertTrue(engine.resolve(state, PlayerAction("A")).isDeadlocked)
    }

    @Test
    fun `resolution is deterministic and does not mutate its input`() {
        val state = board(
            arrows = listOf(arrow("A", 3, 1, Direction.NORTH)),
            magnets = listOf(magnet("M1", 3, 3, Polarity.PULL)),
        )
        val originalSnapshot = state.copy(
            arrows = state.arrows.toList(),
            magnets = state.magnets.toList(),
            walls = state.walls.toList(),
        )

        val first = engine.resolve(state, PlayerAction("A"))
        val second = engine.resolve(state, PlayerAction("A"))

        assertEquals(first, second)
        assertEquals(originalSnapshot, state)
        assertEquals(Polarity.PULL, state.magnet("M1")?.polarity)
        assertEquals(1, state.arrows.size)
    }
}
