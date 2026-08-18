package com.rameshta.magnetrail.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Position
import kotlin.math.floor
import kotlin.math.min

data class BoardGeometry(
    val boardWidth: Int,
    val boardHeight: Int,
    val boardBounds: Rect,
    val cellSize: Float,
) {
    fun cellCenter(position: Position): Offset {
        require(position.row in 1..boardHeight && position.column in 1..boardWidth) {
            "Position $position is outside ${boardWidth}x$boardHeight geometry"
        }
        return Offset(
            x = boardBounds.left + (position.column - 0.5f) * cellSize,
            y = boardBounds.top + (position.row - 0.5f) * cellSize,
        )
    }

    fun positionAt(pointer: Offset): Position? {
        if (!boardBounds.contains(pointer)) return null
        val column = floor((pointer.x - boardBounds.left) / cellSize).toInt() + 1
        val row = floor((pointer.y - boardBounds.top) / cellSize).toInt() + 1
        return Position(row, column).takeIf {
            it.row in 1..boardHeight && it.column in 1..boardWidth
        }
    }

    fun exitPoint(lastBoardPosition: Position, direction: Direction): Offset {
        val center = cellCenter(lastBoardPosition)
        val distance = cellSize * 0.72f
        return when (direction) {
            Direction.NORTH -> Offset(center.x, boardBounds.top - distance)
            Direction.EAST -> Offset(boardBounds.right + distance, center.y)
            Direction.SOUTH -> Offset(center.x, boardBounds.bottom + distance)
            Direction.WEST -> Offset(boardBounds.left - distance, center.y)
        }
    }

    fun routePoints(result: ResolutionResult): List<Offset> {
        val selectedArrow = requireNotNull(result.originalState.arrow(result.selectedArrowId)) {
            "Resolution result references missing arrow '${result.selectedArrowId}'"
        }
        return buildList {
            add(cellCenter(selectedArrow.position))
            result.traversedCells.forEach { add(cellCenter(it)) }
            when (val terminal = result.terminalEvent) {
                is TerminalEvent.Exit -> add(exitPoint(terminal.lastBoardPosition, terminal.direction))
                is TerminalEvent.InvalidPullExit -> add(
                    exitPoint(terminal.lastBoardPosition, terminal.direction),
                )
                is TerminalEvent.Collision,
                is TerminalEvent.PullCapture,
                -> Unit
            }
        }
    }

    fun pointAlongRoute(points: List<Offset>, progress: Float): Offset {
        require(points.isNotEmpty()) { "A route requires at least one point" }
        if (points.size == 1) return points.first()

        val clamped = progress.coerceIn(0f, 1f)
        val scaled = clamped * (points.size - 1)
        val segment = floor(scaled).toInt().coerceAtMost(points.size - 2)
        val segmentProgress = if (clamped == 1f) 1f else scaled - segment
        return lerp(points[segment], points[segment + 1], segmentProgress)
    }

    fun trailPoints(points: List<Offset>, progress: Float): List<Offset> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return points
        val clamped = progress.coerceIn(0f, 1f)
        val scaled = clamped * (points.size - 1)
        val completedSegments = floor(scaled).toInt().coerceAtMost(points.size - 1)
        return buildList {
            addAll(points.take(completedSegments + 1))
            if (completedSegments < points.lastIndex) {
                add(pointAlongRoute(points, clamped))
            }
        }
    }

    companion object {
        fun create(
            canvasSize: Size,
            boardWidth: Int,
            boardHeight: Int,
            exitGutterPx: Float,
        ): BoardGeometry {
            require(boardWidth > 0 && boardHeight > 0) { "Board dimensions must be positive" }
            val availableWidth = (canvasSize.width - exitGutterPx * 2).coerceAtLeast(1f)
            val availableHeight = (canvasSize.height - exitGutterPx * 2).coerceAtLeast(1f)
            val cellSize = min(availableWidth / boardWidth, availableHeight / boardHeight)
            val width = cellSize * boardWidth
            val height = cellSize * boardHeight
            val left = (canvasSize.width - width) / 2f
            val top = (canvasSize.height - height) / 2f
            return BoardGeometry(
                boardWidth = boardWidth,
                boardHeight = boardHeight,
                boardBounds = Rect(left, top, left + width, top + height),
                cellSize = cellSize,
            )
        }

        private fun lerp(start: Offset, end: Offset, fraction: Float): Offset = Offset(
            x = start.x + (end.x - start.x) * fraction,
            y = start.y + (end.y - start.y) * fraction,
        )
    }
}
