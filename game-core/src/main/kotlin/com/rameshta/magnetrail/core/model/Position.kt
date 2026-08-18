package com.rameshta.magnetrail.core.model

/** One-based board coordinate matching the authored level format. */
data class Position(
    val row: Int,
    val column: Int,
) {
    fun move(direction: Direction): Position = Position(
        row = row + direction.rowDelta,
        column = column + direction.columnDelta,
    )
}
