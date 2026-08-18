package com.rameshta.magnetrail.core

import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall

internal fun arrow(
    id: String,
    row: Int,
    column: Int,
    direction: Direction,
): Arrow = Arrow(id, Position(row, column), direction)

internal fun magnet(
    id: String,
    row: Int,
    column: Int,
    polarity: Polarity,
): Magnet = Magnet(id, Position(row, column), polarity)

internal fun wall(row: Int, column: Int): Wall = Wall(Position(row, column))

internal fun board(
    levelId: String = "test-level",
    width: Int = 5,
    height: Int = 5,
    arrows: List<Arrow>,
    magnets: List<Magnet> = emptyList(),
    walls: List<Wall> = emptyList(),
): BoardState = BoardState(levelId, width, height, arrows, magnets, walls)

internal fun prototypeCatalog(): LevelCatalog {
    val source = checkNotNull(object {}.javaClass.getResource("/Magnetrail_Prototype_Levels_v1.json")) {
        "Prototype level JSON was not available as a test resource"
    }.readText()
    return LevelParser().parseCatalog(source)
}
