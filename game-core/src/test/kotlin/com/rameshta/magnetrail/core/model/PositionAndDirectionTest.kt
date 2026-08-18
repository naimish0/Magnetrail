package com.rameshta.magnetrail.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionAndDirectionTest {
    @Test
    fun `movement follows the one-based authored coordinate system`() {
        val origin = Position(row = 3, column = 4)

        assertEquals(Position(2, 4), origin.move(Direction.NORTH))
        assertEquals(Position(3, 5), origin.move(Direction.EAST))
        assertEquals(Position(4, 4), origin.move(Direction.SOUTH))
        assertEquals(Position(3, 3), origin.move(Direction.WEST))
    }

    @Test
    fun `direction codes and opposites are deterministic`() {
        assertEquals(Direction.NORTH, Direction.fromCode("N"))
        assertEquals(Direction.EAST, Direction.fromCode("e"))
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite())
        assertEquals(Direction.WEST, Direction.EAST.opposite())
    }

    @Test
    fun `direction between aligned cells points toward the target`() {
        val origin = Position(3, 3)

        assertEquals(Direction.NORTH, Direction.between(origin, Position(1, 3)))
        assertEquals(Direction.EAST, Direction.between(origin, Position(3, 5)))
        assertEquals(Direction.SOUTH, Direction.between(origin, Position(5, 3)))
        assertEquals(Direction.WEST, Direction.between(origin, Position(3, 1)))
    }
}
