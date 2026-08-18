package com.rameshta.magnetrail.core.engine

import com.rameshta.magnetrail.core.arrow
import com.rameshta.magnetrail.core.board
import com.rameshta.magnetrail.core.magnet
import com.rameshta.magnetrail.core.wall
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Polarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTracerTest {
    private val tracer = DeterministicRouteTracer()

    @Test
    fun `another arrow blocks magnetic line of sight`() {
        val selected = arrow("A", 3, 1, Direction.NORTH)
        val state = board(
            arrows = listOf(selected, arrow("B", 3, 2, Direction.NORTH)),
            magnets = listOf(magnet("M1", 3, 4, Polarity.PULL)),
        )

        val trace = tracer.trace(state, selected)

        assertNull(trace.controllingMagnet)
        assertEquals(Direction.NORTH, trace.effectiveDirection)
    }

    @Test
    fun `a wall blocks magnetic line of sight`() {
        val selected = arrow("A", 3, 1, Direction.NORTH)
        val state = board(
            arrows = listOf(selected),
            magnets = listOf(magnet("M1", 3, 4, Polarity.PULL)),
            walls = listOf(wall(3, 3)),
        )

        val trace = tracer.trace(state, selected)

        assertNull(trace.controllingMagnet)
        assertEquals(Direction.NORTH, trace.effectiveDirection)
    }

    @Test
    fun `a nearer magnet occludes a farther magnet`() {
        val selected = arrow("A", 3, 1, Direction.NORTH)
        val nearer = magnet("M1", 3, 3, Polarity.PUSH)
        val state = board(
            width = 6,
            arrows = listOf(selected),
            magnets = listOf(nearer, magnet("M2", 3, 6, Polarity.PULL)),
        )

        assertEquals(nearer, tracer.findControllingMagnet(state, selected))
    }

    @Test
    fun `nearest visible aligned magnet controls`() {
        val selected = arrow("A", 3, 3, Direction.WEST)
        val nearest = magnet("M1", 2, 3, Polarity.PULL)
        val state = board(
            arrows = listOf(selected),
            magnets = listOf(nearest, magnet("M2", 3, 5, Polarity.PUSH)),
        )

        val trace = tracer.trace(state, selected)

        assertEquals(nearest, trace.controllingMagnet)
        assertEquals(Direction.NORTH, trace.effectiveDirection)
    }

    @Test
    fun `equal-distance magnetic influence cancels`() {
        val selected = arrow("A", 3, 3, Direction.SOUTH)
        val state = board(
            arrows = listOf(selected),
            magnets = listOf(
                magnet("M1", 3, 1, Polarity.PULL),
                magnet("M2", 3, 5, Polarity.PUSH),
            ),
        )

        val trace = tracer.trace(state, selected)

        assertNull(trace.controllingMagnet)
        assertEquals(Direction.SOUTH, trace.effectiveDirection)
    }
}
