package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall

object HandcraftedBank {
    fun expanded(prototype: List<LevelDefinition>): List<LevelDefinition> {
        val byId = prototype.associateBy { it.id }
        val tuned = listOf(
            Tune("proto-003", 13, "Quiet attraction", 5, 0, 0, listOf(Position(5, 5))),
            Tune("proto-004", 14, "Amber release", 5, 0, 0, listOf(Position(1, 5))),
            Tune("proto-005", 15, "Offset field", 6, 1, 1),
            Tune("proto-006", 16, "Side gate", 6, 1, 0, listOf(Position(6, 6))),
            Tune("proto-007", 17, "Hidden alignment", 6, 0, 1),
            Tune("proto-008", 18, "Long shield", 6, 1, 0, listOf(Position(6, 6))),
            Tune("proto-009", 19, "Three-beat gate", 6, 0, 0, listOf(Position(6, 6))),
            Tune("proto-010", 20, "Raised relay", 7, 1, 0),
            Tune("proto-011", 21, "Push opening", 6, 0, 1, listOf(Position(6, 6))),
            Tune("proto-012", 22, "Corner rhythm", 7, 0, 0, listOf(Position(7, 7))),
            Tune("proto-005", 23, "Wide alternation", 7, 1, 1, listOf(Position(7, 7))),
            Tune("proto-006", 24, "Deep gate", 7, 1, 1, listOf(Position(1, 7))),
            Tune("proto-007", 25, "Layered reveal", 7, 1, 1, listOf(Position(7, 1))),
        ).map { tune -> applyTune(requireNotNull(byId[tune.baseId]), tune) }
        return prototype + tuned + listOf(
            dualFieldLevel(26, "Twin cadence", 2, 5, 4, Polarity.PULL, Polarity.PUSH),
            dualFieldLevel(27, "Crossed cadence", 3, 6, 4, Polarity.PUSH, Polarity.PULL),
            dualFieldLevel(28, "Left rail duet", 2, 5, 3, Polarity.PULL, Polarity.PULL),
            dualFieldLevel(29, "Right rail duet", 3, 6, 5, Polarity.PUSH, Polarity.PUSH),
            cancellationLevel(),
        )
    }

    private fun applyTune(base: LevelDefinition, tune: Tune): LevelDefinition {
        fun move(position: Position) = Position(position.row + tune.rowOffset, position.column + tune.columnOffset)
        val baseWalls = base.walls.map { Wall(move(it.position)) }
        return LevelDefinition(
            id = "campaign-${tune.number.toString().padStart(3, '0')}",
            number = tune.number,
            title = tune.title,
            width = tune.boardSize,
            height = tune.boardSize,
            arrows = base.arrows.map { it.copy(position = move(it.position)) },
            magnets = base.magnets.map { it.copy(position = move(it.position)) },
            walls = baseWalls + tune.extraWalls.map(::Wall),
            designedSolutions = base.designedSolutions,
        )
    }

    private fun dualFieldLevel(
        number: Int,
        title: String,
        firstRow: Int,
        secondRow: Int,
        magnetColumn: Int,
        firstPolarity: Polarity,
        secondPolarity: Polarity,
    ): LevelDefinition {
        val leftColumn = (magnetColumn - 2).coerceAtLeast(1)
        val rightColumn = (magnetColumn + 2).coerceAtMost(7)
        return LevelDefinition(
            id = "campaign-${number.toString().padStart(3, '0')}",
            number = number,
            title = title,
            width = 7,
            height = 7,
            arrows = listOf(
                Arrow("A", Position(firstRow, leftColumn), Direction.NORTH),
                Arrow("B", Position(firstRow, rightColumn), Direction.SOUTH),
                Arrow("C", Position(secondRow, leftColumn), Direction.SOUTH),
                Arrow("D", Position(secondRow, rightColumn), Direction.NORTH),
                Arrow("E", Position(1, 1), Direction.NORTH),
                Arrow("F", Position(7, 7), Direction.SOUTH),
            ),
            magnets = listOf(
                Magnet("M1", Position(firstRow, magnetColumn), firstPolarity),
                Magnet("M2", Position(secondRow, magnetColumn), secondPolarity),
            ),
            walls = listOf(Wall(Position(4, 7))),
            designedSolutions = listOf(listOf("A", "B", "C", "D", "E", "F")),
        )
    }

    private fun cancellationLevel(): LevelDefinition = LevelDefinition(
        id = "campaign-030",
        number = 30,
        title = "Balanced fields",
        width = 7,
        height = 7,
        arrows = listOf(
            Arrow("A", Position(4, 4), Direction.NORTH),
            Arrow("B", Position(7, 4), Direction.NORTH),
            Arrow("C", Position(2, 6), Direction.EAST),
            Arrow("D", Position(1, 1), Direction.NORTH),
            Arrow("E", Position(7, 7), Direction.SOUTH),
        ),
        magnets = listOf(
            Magnet("M1", Position(4, 2), Polarity.PULL),
            Magnet("M2", Position(4, 6), Polarity.PULL),
        ),
        walls = listOf(Wall(Position(1, 7))),
        designedSolutions = listOf(listOf("A", "B", "C", "D", "E")),
    )

    private data class Tune(
        val baseId: String,
        val number: Int,
        val title: String,
        val boardSize: Int,
        val rowOffset: Int,
        val columnOffset: Int,
        val extraWalls: List<Position> = emptyList(),
    )
}
