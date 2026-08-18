package com.rameshta.magnetrail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.game.BoardGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGeometryTest {
    @Test
    fun `cell centers and pointer hit testing share one transform`() {
        val geometry = BoardGeometry.create(
            canvasSize = Size(360f, 360f),
            boardWidth = 7,
            boardHeight = 7,
            exitGutterPx = 12f,
        )

        for (row in 1..7) {
            for (column in 1..7) {
                val position = Position(row, column)
                assertEquals(position, geometry.positionAt(geometry.cellCenter(position)))
            }
        }
        assertNull(geometry.positionAt(Offset(0f, 0f)))
        assertTrue(geometry.cellSize >= 48f)
    }

    @Test
    fun `exit route follows the exact engine result beyond the correct edge`() {
        val catalogSource = checkNotNull(javaClass.getResource("/Magnetrail_Prototype_Levels_v1.json")).readText()
        val level = LevelParser().parseCatalog(catalogSource).level("proto-001")
        val result = DefaultGameEngine().resolve(level.initialState(), PlayerAction("A"))
        val geometry = BoardGeometry.create(Size(360f, 360f), 4, 4, 12f)

        val route = geometry.routePoints(result)

        assertEquals(geometry.cellCenter(Position(2, 2)), route.first())
        assertTrue(route.last().x > geometry.boardBounds.right)
        assertEquals(geometry.cellCenter(Position(2, 2)).y, route.last().y, 0.001f)
    }
}
