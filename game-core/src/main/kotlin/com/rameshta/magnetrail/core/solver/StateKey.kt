package com.rameshta.magnetrail.core.solver

import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position

data class ArrowStateKey(
    val id: String,
    val position: Position,
    val printedDirection: Direction,
)

data class MagnetStateKey(
    val id: String,
    val position: Position,
    val polarity: Polarity,
)

data class StateKey(
    val levelId: String,
    val width: Int,
    val height: Int,
    val remainingArrows: List<ArrowStateKey>,
    val magnets: List<MagnetStateKey>,
    val walls: List<Position>,
) {
    companion object {
        fun from(state: BoardState): StateKey = StateKey(
            levelId = state.levelId,
            width = state.width,
            height = state.height,
            remainingArrows = state.arrows
                .map { ArrowStateKey(it.id, it.position, it.printedDirection) }
                .sortedBy { it.id },
            magnets = state.magnets
                .map { MagnetStateKey(it.id, it.position, it.polarity) }
                .sortedBy { it.id },
            walls = state.walls.map { it.position }.sortedWith(compareBy(Position::row, Position::column)),
        )
    }
}
