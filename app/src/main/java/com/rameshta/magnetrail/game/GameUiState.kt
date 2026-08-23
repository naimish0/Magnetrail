package com.rameshta.magnetrail.game

import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.data.PlayerSettings
import com.rameshta.magnetrail.data.CompletionReceipt
import com.rameshta.magnetrail.daily.DailyLoadSource
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.playtest.HumanPlaytestAssignment
import com.rameshta.magnetrail.playtest.HumanPlaytestOutcomeDraft
import com.rameshta.magnetrail.playtest.HumanPlaytestFeedbackDraft
import com.rameshta.magnetrail.playtest.HumanPlaytestUiState

enum class GameMode {
    CAMPAIGN,
    DAILY,
    INFINITE,
    PLAYTEST,
}

enum class AppDestination {
    HOME,
    LEVELS,
    INFINITE,
    GAME,
    SETTINGS,
    PRIVACY_POLICY,
    HUMAN_PLAYTEST,
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
    val infinitePuzzleId: String? = null,
    val infiniteDifficulty: InfiniteDifficulty? = null,
    val infiniteFallbackUsed: Boolean = false,
    val infiniteSelectionReason: String? = null,
    val infiniteCatalogSize: Int = 0,
    val isAutoJourney: Boolean = false,
    val autoJourneyOrdinal: Int? = null,
    val autoJourneyInternalId: String? = null,
    val isAutoJourneyLoading: Boolean = false,
    val autoJourneyPreparationMessage: String? = null,
    val playDifficultyLabel: String = "Easy",
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
    val isHintPurchaseInProgress: Boolean = false,
    val completionReceipt: CompletionReceipt? = null,
    val completionWasFirstClear: Boolean = false,
    val completionPersisted: Boolean = false,
    val humanPlaytest: HumanPlaytestUiState = HumanPlaytestUiState(),
    val humanPlaytestAssignment: HumanPlaytestAssignment? = null,
    val humanPlaytestOutcome: HumanPlaytestOutcomeDraft? = null,
    val humanPlaytestFeedback: HumanPlaytestFeedbackDraft = HumanPlaytestFeedbackDraft(),
    val humanPlaytestTotalActions: Int = 0,
    val humanPlaytestTotalOverloads: Int = 0,
    val humanPlaytestSuccessfulWrongActions: Int = 0,
    val humanPlaytestCurrentAttemptSuccessfulActions: Int = 0,
    val humanPlaytestDeadlocks: Int = 0,
    val humanPlaytestTotalHints: Int = 0,
    val humanPlaytestRestarts: Int = 0,
) {
    val remainingArrowCount: Int get() = boardState.arrows.size
    val initialArrowCount: Int get() = initialState.arrows.size
    val canRestart: Boolean get() = inFlightResult == null
    val canRequestHint: Boolean
        get() = inputEnabled && !isComplete && !isDeadlocked && !isHintLoading &&
            !isHintPurchaseInProgress && suggestedArrowId == null
    val canRequestSkip: Boolean
        get() = gameMode != GameMode.DAILY && gameMode != GameMode.PLAYTEST && !isAutoJourney && inputEnabled &&
            !isComplete && inFlightResult == null &&
            !isHintPurchaseInProgress
    val hasNextLevel: Boolean
        get() = gameMode == GameMode.INFINITE ||
            (gameMode == GameMode.CAMPAIGN && currentLevelIndex < levels.lastIndex)
    val hasProgress: Boolean
        get() = progress.completedLevelIds.isNotEmpty() || progress.highestUnlockedLevel > 1

    fun isLevelUnlocked(index: Int, debugUnlockAll: Boolean): Boolean =
        debugUnlockAll || index < progress.highestUnlockedLevel

    val totalStars: Int get() = progress.recordsByLevel.values.sumOf { it.bestStars }
    val todayDailyCompleted: Boolean get() = dailyId?.let { it in progress.completedDailyIds } == true
    val infiniteUnlocked: Boolean
        get() = infiniteCatalogSize > 0
}
