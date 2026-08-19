package com.rameshta.magnetrail.game

import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.data.PlayerSettings
import com.rameshta.magnetrail.data.CompletionReceipt
import com.rameshta.magnetrail.daily.DailyLoadSource

enum class GameMode {
    CAMPAIGN,
    DAILY,
}

enum class AppDestination {
    HOME,
    LEVELS,
    GAME,
    SETTINGS,
}

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
    val destination: AppDestination = AppDestination.HOME,
    val returnDestination: AppDestination = AppDestination.HOME,
    val settings: PlayerSettings = PlayerSettings(),
    val progress: PlayerProgress = PlayerProgress(lastSelectedLevelId = currentLevel.id),
    val preferencesLoaded: Boolean = false,
    val gameMode: GameMode = GameMode.CAMPAIGN,
    val dailyId: String? = null,
    val dailyDateLabel: String? = null,
    val dailyLoadSource: DailyLoadSource? = null,
    val isDailyLoading: Boolean = false,
    val dailyError: String? = null,
    val undoHistory: List<BoardState> = emptyList(),
    val inFlightResult: ResolutionResult? = null,
    val animationPhase: TurnAnimationPhase = TurnAnimationPhase.IDLE,
    val inputEnabled: Boolean = true,
    val isComplete: Boolean = false,
    val isDeadlocked: Boolean = false,
    val moves: Int = 0,
    val overloads: Int = 0,
    val hintsUsed: Int = 0,
    val isHintLoading: Boolean = false,
    val suggestedArrowId: String? = null,
    val hintMessage: String? = null,
    val hintPreviewResult: ResolutionResult? = null,
    val hintConfirmationPending: Boolean = false,
    val hintChoiceOpen: Boolean = false,
    val isHintPurchaseInProgress: Boolean = false,
    val completionReceipt: CompletionReceipt? = null,
    val completionWasFirstClear: Boolean = false,
) {
    val remainingArrowCount: Int get() = boardState.arrows.size
    val initialArrowCount: Int get() = initialState.arrows.size
    val canUndo: Boolean get() = undoHistory.isNotEmpty() && inFlightResult == null
    val canRestart: Boolean get() = inFlightResult == null
    val canRequestHint: Boolean
        get() = inputEnabled && !isComplete && !isHintLoading && !isHintPurchaseInProgress && suggestedArrowId == null
    val hasNextLevel: Boolean get() = gameMode == GameMode.CAMPAIGN && currentLevelIndex < levels.lastIndex
    val hasProgress: Boolean
        get() = progress.completedLevelIds.isNotEmpty() || progress.highestUnlockedLevel > 1

    fun isLevelUnlocked(index: Int, debugUnlockAll: Boolean): Boolean =
        debugUnlockAll || index < progress.highestUnlockedLevel

    val totalStars: Int get() = progress.recordsByLevel.values.sumOf { it.bestStars }
    val todayDailyCompleted: Boolean get() = dailyId?.let { it in progress.completedDailyIds } == true
}
