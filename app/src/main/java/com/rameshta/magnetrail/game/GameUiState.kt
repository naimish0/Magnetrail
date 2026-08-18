package com.rameshta.magnetrail.game

import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition

enum class TurnAnimationPhase {
    IDLE,
    ROUTE,
    IMPACT,
    REWIND,
    POLARITY_FLIP,
}

data class GameUiState(
    val levels: List<LevelDefinition>,
    val currentLevelIndex: Int,
    val currentLevel: LevelDefinition,
    val initialState: BoardState,
    val boardState: BoardState,
    val undoHistory: List<BoardState> = emptyList(),
    val inFlightResult: ResolutionResult? = null,
    val animationPhase: TurnAnimationPhase = TurnAnimationPhase.IDLE,
    val inputEnabled: Boolean = true,
    val isComplete: Boolean = false,
    val isDeadlocked: Boolean = false,
    val isLevelSelectionVisible: Boolean = false,
) {
    val remainingArrowCount: Int get() = boardState.arrows.size
    val initialArrowCount: Int get() = initialState.arrows.size
    val canUndo: Boolean get() = undoHistory.isNotEmpty() && inFlightResult == null
    val canRestart: Boolean get() = inFlightResult == null
    val hasNextLevel: Boolean get() = currentLevelIndex < levels.lastIndex
}
