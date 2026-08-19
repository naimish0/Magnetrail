package com.rameshta.magnetrail.core.content

import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContentFingerprintSymmetryTest {
    @Test
    fun `all D4 transforms map positions and cardinal directions correctly`() {
        val cell = Position(1, 2)
        val size = 4
        assertEquals(Position(1, 2), BoardSymmetry.IDENTITY.transform(cell, size, size))
        assertEquals(Position(2, 4), BoardSymmetry.ROTATE_90.transform(cell, size, size))
        assertEquals(Position(4, 3), BoardSymmetry.ROTATE_180.transform(cell, size, size))
        assertEquals(Position(3, 1), BoardSymmetry.ROTATE_270.transform(cell, size, size))
        assertEquals(Position(4, 2), BoardSymmetry.REFLECT_HORIZONTAL.transform(cell, size, size))
        assertEquals(Position(1, 3), BoardSymmetry.REFLECT_VERTICAL.transform(cell, size, size))
        assertEquals(Position(2, 1), BoardSymmetry.REFLECT_MAIN_DIAGONAL.transform(cell, size, size))
        assertEquals(Position(3, 4), BoardSymmetry.REFLECT_ANTI_DIAGONAL.transform(cell, size, size))

        assertEquals(Direction.EAST, BoardSymmetry.ROTATE_90.transform(Direction.NORTH))
        assertEquals(Direction.SOUTH, BoardSymmetry.ROTATE_90.transform(Direction.EAST))
        assertEquals(Direction.WEST, BoardSymmetry.ROTATE_90.transform(Direction.SOUTH))
        assertEquals(Direction.NORTH, BoardSymmetry.ROTATE_90.transform(Direction.WEST))
        assertEquals(Direction.WEST, BoardSymmetry.REFLECT_MAIN_DIAGONAL.transform(Direction.NORTH))
        assertEquals(Direction.EAST, BoardSymmetry.REFLECT_ANTI_DIAGONAL.transform(Direction.NORTH))
    }

    @Test
    fun `rotated and reflected boards normalize IDs and share one symmetry fingerprint`() {
        val source = board()
        BoardSymmetry.entries.forEach { symmetry ->
            val transformed = transform(source, symmetry)
            assertEquals(ContentFingerprint.symmetryNormalized(source), ContentFingerprint.symmetryNormalized(transformed))
        }
        assertNotEquals(ContentFingerprint.exact(source), ContentFingerprint.exact(transform(source, BoardSymmetry.ROTATE_90)))
    }

    @Test
    fun `rule relevant polarity and wall differences remain distinct`() {
        val source = board()
        assertNotEquals(
            ContentFingerprint.symmetryNormalized(source),
            ContentFingerprint.symmetryNormalized(source.copy(magnets = source.magnets.map { it.copy(polarity = Polarity.PUSH) })),
        )
        assertNotEquals(
            ContentFingerprint.symmetryNormalized(source),
            ContentFingerprint.symmetryNormalized(source.copy(walls = source.walls + Wall(Position(1, 1)))),
        )
    }

    @Test
    fun `non square boards use only dimension preserving symmetries`() {
        val source = board().copy(width = 4, height = 3)
        val reflected = transform(source, BoardSymmetry.REFLECT_VERTICAL)
        assertEquals(ContentFingerprint.symmetryNormalized(source), ContentFingerprint.symmetryNormalized(reflected))
    }

    private fun board() = LevelDefinition(
        id = "source",
        number = 1,
        title = "Source",
        width = 3,
        height = 3,
        arrows = listOf(Arrow("A", Position(1, 2), Direction.NORTH)),
        magnets = listOf(Magnet("M", Position(2, 2), Polarity.PULL)),
        walls = listOf(Wall(Position(3, 1))),
        designedSolutions = listOf(listOf("A")),
    )

    private fun transform(level: LevelDefinition, symmetry: BoardSymmetry): LevelDefinition = level.copy(
        id = "renamed",
        arrows = level.arrows.mapIndexed { index, arrow ->
            arrow.copy(
                id = "arrow-$index",
                position = symmetry.transform(arrow.position, level.width, level.height),
                printedDirection = symmetry.transform(arrow.printedDirection),
            )
        },
        magnets = level.magnets.mapIndexed { index, magnet ->
            magnet.copy(id = "magnet-$index", position = symmetry.transform(magnet.position, level.width, level.height))
        },
        walls = level.walls.map { Wall(symmetry.transform(it.position, level.width, level.height)) },
        designedSolutions = emptyList(),
        metadata = null,
    )
}
