package com.rameshta.magnetrail.core.model

enum class Direction(
    val code: String,
    val rowDelta: Int,
    val columnDelta: Int,
) {
    NORTH("N", -1, 0),
    EAST("E", 0, 1),
    SOUTH("S", 1, 0),
    WEST("W", 0, -1),
    ;

    fun opposite(): Direction = when (this) {
        NORTH -> SOUTH
        EAST -> WEST
        SOUTH -> NORTH
        WEST -> EAST
    }

    companion object {
        fun fromCode(code: String): Direction = entries.firstOrNull { it.code == code.uppercase() }
            ?: throw IllegalArgumentException("Unknown direction '$code'; expected N, E, S, or W")

        fun between(from: Position, to: Position): Direction {
            require(from != to) { "Cannot determine a direction between the same position" }
            return when {
                from.row == to.row && from.column < to.column -> EAST
                from.row == to.row -> WEST
                from.column == to.column && from.row < to.row -> SOUTH
                from.column == to.column -> NORTH
                else -> throw IllegalArgumentException("Positions $from and $to are not aligned")
            }
        }
    }
}
