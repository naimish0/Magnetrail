package com.rameshta.magnetrail.core.engine

import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position

data class PolarityChange(
    val magnetId: String,
    val from: Polarity,
    val to: Polarity,
)

data class ResolutionResult(
    val success: Boolean,
    val originalState: BoardState,
    val resultingState: BoardState,
    val selectedArrowId: String,
    val printedDirection: Direction,
    val effectiveDirection: Direction,
    val controllingMagnetId: String?,
    val traversedCells: List<Position>,
    val terminalEvent: TerminalEvent,
    val collisionTarget: CollisionTarget?,
    val polarityChange: PolarityChange?,
    val isWin: Boolean,
    val isDeadlocked: Boolean,
)
