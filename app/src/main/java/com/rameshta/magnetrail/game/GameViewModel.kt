package com.rameshta.magnetrail.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.data.PlayerPreferences
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.data.ProgressRepository
import com.rameshta.magnetrail.data.SettingKey
import com.rameshta.magnetrail.data.withValue
import com.rameshta.magnetrail.feedback.FeedbackEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class GameViewModel(
    private val catalog: LevelCatalog,
    private val engine: GameEngine = DefaultGameEngine(),
    private val progressRepository: ProgressRepository? = null,
    private val hintProvider: HintProvider = SolverHintProvider(),
    val debugUnlockAll: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow(createLevelState(levelIndex = 0))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<FeedbackEvent>(extraBufferCapacity = 16)
    val feedbackEvents: SharedFlow<FeedbackEvent> = _feedbackEvents.asSharedFlow()

    private var hintJob: Job? = null
    private var hintGeneration = 0L

    init {
        progressRepository?.let { repository ->
            viewModelScope.launch {
                repository.preferences.collect(::applyPreferences)
            }
        }
    }

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.LaunchArrow -> launchArrow(action.arrowId)
            is GameAction.AnimationPhaseChanged -> updateAnimationPhase(action.phase)
            GameAction.AnimationCompleted -> completeAnimation()
            GameAction.Undo -> undo()
            GameAction.Restart, GameAction.Replay -> restart()
            GameAction.NavigateHome -> navigateHome()
            GameAction.Play -> play()
            GameAction.OpenLevelSelection -> openLevelSelection()
            GameAction.CloseLevelSelection -> closeOverlay(AppDestination.LEVELS)
            GameAction.OpenSettings -> openSettings()
            GameAction.CloseSettings -> closeOverlay(AppDestination.SETTINGS)
            is GameAction.SelectLevel -> selectLevel(action.index)
            is GameAction.UpdateSetting -> updateSetting(action.key, action.enabled)
            GameAction.RequestHint -> requestHint()
            GameAction.NextLevel -> nextLevel()
        }
    }

    private fun launchArrow(arrowId: String) {
        val current = _uiState.value
        if (!current.inputEnabled || current.isComplete || current.inFlightResult != null) return
        if (current.boardState.arrow(arrowId) == null) return

        cancelHint(current)
        val state = _uiState.value
        val resolution = engine.resolve(state.boardState, PlayerAction(arrowId))
        _uiState.value = state.copy(
            inFlightResult = resolution,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = false,
        )
        emit(FeedbackEvent.SELECT)
    }

    private fun updateAnimationPhase(phase: TurnAnimationPhase) {
        val state = _uiState.value
        if (state.inFlightResult == null || state.animationPhase == phase) return
        _uiState.value = state.copy(animationPhase = phase)
        when (phase) {
            TurnAnimationPhase.ROUTE -> emit(FeedbackEvent.ARROW_TRAVEL)
            TurnAnimationPhase.IMPACT -> emit(FeedbackEvent.COLLISION)
            TurnAnimationPhase.POLARITY_FLIP -> emit(FeedbackEvent.POLARITY_FLIP)
            TurnAnimationPhase.IDLE, TurnAnimationPhase.REWIND -> Unit
        }
    }

    private fun completeAnimation() {
        val state = _uiState.value
        val result = state.inFlightResult ?: return
        val history = if (result.success) state.undoHistory + result.originalState else state.undoHistory
        val nextMoves = state.moves + if (result.success) 1 else 0
        val completedIds = if (result.isWin) {
            state.progress.completedLevelIds + state.currentLevel.id
        } else {
            state.progress.completedLevelIds
        }
        val highestUnlocked = if (result.isWin) {
            maxOf(
                state.progress.highestUnlockedLevel,
                (state.currentLevelIndex + 2).coerceAtMost(state.levels.size),
            )
        } else {
            state.progress.highestUnlockedLevel
        }
        val bestMoves = if (result.isWin) {
            state.progress.bestMovesByLevel + (
                state.currentLevel.id to minOf(
                    state.progress.bestMovesByLevel[state.currentLevel.id] ?: Int.MAX_VALUE,
                    nextMoves,
                )
            )
        } else {
            state.progress.bestMovesByLevel
        }

        _uiState.value = state.copy(
            boardState = result.resultingState,
            undoHistory = history,
            inFlightResult = null,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = !result.isWin,
            isComplete = result.isWin,
            isDeadlocked = result.isDeadlocked,
            moves = nextMoves,
            progress = state.progress.copy(
                highestUnlockedLevel = highestUnlocked,
                completedLevelIds = completedIds,
                bestMovesByLevel = bestMoves,
            ),
        )
        emitTerminalFeedback(result)
        if (result.isWin) {
            emit(FeedbackEvent.BOARD_COMPLETION)
            progressRepository?.let { repository ->
                viewModelScope.launch {
                    repository.recordCompletion(state.currentLevel.id, nextMoves)
                }
            }
        }
    }

    private fun undo() {
        val current = _uiState.value
        if (!current.canUndo) return
        cancelHint(current)
        val state = _uiState.value
        val restoredState = state.undoHistory.last()
        _uiState.value = state.copy(
            boardState = restoredState,
            undoHistory = state.undoHistory.dropLast(1),
            isComplete = false,
            isDeadlocked = engine.isDeadlocked(restoredState),
            inputEnabled = true,
        )
        emit(FeedbackEvent.UNDO)
    }

    private fun restart() {
        val current = _uiState.value
        if (!current.canRestart) return
        cancelHint(current)
        val state = _uiState.value
        _uiState.value = state.copy(
            boardState = state.initialState,
            undoHistory = emptyList(),
            inFlightResult = null,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = true,
            isComplete = false,
            isDeadlocked = engine.isDeadlocked(state.initialState),
            moves = 0,
            hintsUsed = 0,
        )
        emit(FeedbackEvent.RESTART)
    }

    private fun navigateHome() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        cancelHint(state)
        val current = _uiState.value
        if (current.isComplete && current.hasNextLevel) {
            val nextIndex = current.currentLevelIndex + 1
            _uiState.value = createLevelState(
                levelIndex = nextIndex,
                previous = current,
                destination = AppDestination.HOME,
            )
            progressRepository?.let { repository ->
                viewModelScope.launch { repository.selectLevel(catalog.levels[nextIndex].id) }
            }
        } else if (current.isComplete) {
            _uiState.value = createLevelState(
                levelIndex = current.currentLevelIndex,
                previous = current,
                destination = AppDestination.HOME,
            )
        } else {
            _uiState.value = current.copy(destination = AppDestination.HOME)
        }
    }

    private fun play() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        _uiState.value = state.copy(destination = AppDestination.GAME)
    }

    private fun openLevelSelection() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        cancelHint(state)
        _uiState.value = _uiState.value.copy(
            destination = AppDestination.LEVELS,
            returnDestination = state.destination.takeIf { it != AppDestination.LEVELS }
                ?: AppDestination.HOME,
        )
    }

    private fun openSettings() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        _uiState.value = state.copy(
            destination = AppDestination.SETTINGS,
            returnDestination = state.destination.takeIf { it != AppDestination.SETTINGS }
                ?: AppDestination.HOME,
        )
    }

    private fun closeOverlay(overlay: AppDestination) {
        val state = _uiState.value
        if (state.destination != overlay) return
        _uiState.value = state.copy(destination = state.returnDestination)
    }

    private fun selectLevel(index: Int) {
        val state = _uiState.value
        if (index !in catalog.levels.indices || !state.isLevelUnlocked(index, debugUnlockAll)) return
        cancelHint(state)
        _uiState.value = createLevelState(
            levelIndex = index,
            previous = _uiState.value,
            destination = AppDestination.GAME,
        )
        progressRepository?.let { repository ->
            viewModelScope.launch { repository.selectLevel(catalog.levels[index].id) }
        }
    }

    private fun nextLevel() {
        val state = _uiState.value
        if (state.inFlightResult != null || !state.isComplete) return
        if (state.hasNextLevel) {
            selectLevel(state.currentLevelIndex + 1)
        } else {
            _uiState.value = state.copy(
                destination = AppDestination.LEVELS,
                returnDestination = AppDestination.HOME,
            )
        }
    }

    private fun updateSetting(key: SettingKey, enabled: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(settings = state.settings.withValue(key, enabled))
        progressRepository?.let { repository ->
            viewModelScope.launch { repository.updateSetting(key, enabled) }
        }
    }

    private fun requestHint() {
        val state = _uiState.value
        if (!state.canRequestHint || hintJob?.isActive == true) return
        if (state.isDeadlocked) {
            _uiState.value = state.copy(hintMessage = "No clean route remains. Undo or restart.")
            return
        }
        val requestedBoard = state.boardState
        val requestedLevelId = state.currentLevel.id
        val generation = ++hintGeneration
        hintJob = viewModelScope.launch {
            val loadingJob = launch {
                delay(HINT_LOADING_DELAY_MILLIS)
                if (hintGeneration == generation) {
                    _uiState.value = _uiState.value.copy(isHintLoading = true, hintMessage = "Finding a clean move")
                }
            }
            val outcome = withTimeoutOrNull(HINT_TIMEOUT_MILLIS) {
                hintProvider.hintFor(requestedBoard)
            }
            loadingJob.cancel()
            val current = _uiState.value
            val stale = hintGeneration != generation ||
                current.currentLevel.id != requestedLevelId ||
                current.boardState != requestedBoard ||
                current.inFlightResult != null
            if (stale) return@launch

            when (outcome) {
                is HintOutcome.SuggestedArrow -> {
                    val valid = engine.validActions(current.boardState)
                        .any { it.arrowId == outcome.arrowId }
                    if (!valid) {
                        showHintFallback(current)
                        return@launch
                    }
                    val preview = if (current.settings.pathPreviewAssistance) {
                        engine.resolve(current.boardState, PlayerAction(outcome.arrowId))
                    } else {
                        null
                    }
                    _uiState.value = current.copy(
                        isHintLoading = false,
                        suggestedArrowId = outcome.arrowId,
                        hintMessage = "Hint: Try arrow ${outcome.arrowId}",
                        hintPreviewResult = preview,
                        hintsUsed = current.hintsUsed + 1,
                    )
                }
                HintOutcome.NoSolution, null -> showHintFallback(current)
            }
        }
    }

    private fun showHintFallback(state: GameUiState) {
        _uiState.value = state.copy(
            isHintLoading = false,
            hintMessage = "No hint is available. Undo or restart to keep exploring.",
        )
    }

    private fun cancelHint(state: GameUiState) {
        hintGeneration += 1
        hintJob?.cancel()
        hintJob = null
        if (state.isHintLoading || state.suggestedArrowId != null || state.hintMessage != null) {
            _uiState.value = state.copy(
                isHintLoading = false,
                suggestedArrowId = null,
                hintMessage = null,
                hintPreviewResult = null,
            )
        }
    }

    private fun applyPreferences(preferences: PlayerPreferences) {
        val state = _uiState.value
        val selectedIndex = catalog.levels.indexOfFirst {
            it.id == preferences.progress.lastSelectedLevelId
        }.coerceAtLeast(0)
        val canRestoreSelection = state.destination == AppDestination.HOME &&
            state.inFlightResult == null && state.undoHistory.isEmpty() && state.moves == 0 &&
            state.boardState == state.initialState
        _uiState.value = if (canRestoreSelection && selectedIndex != state.currentLevelIndex) {
            createLevelState(
                levelIndex = selectedIndex,
                previous = state.copy(
                    settings = preferences.settings,
                    progress = preferences.progress,
                    preferencesLoaded = true,
                ),
                destination = AppDestination.HOME,
            )
        } else {
            state.copy(
                settings = preferences.settings,
                progress = preferences.progress,
                preferencesLoaded = true,
            )
        }
    }

    private fun emitTerminalFeedback(result: com.rameshta.magnetrail.core.engine.ResolutionResult) {
        val event = when (result.terminalEvent) {
            is TerminalEvent.PullCapture -> FeedbackEvent.PULL_CAPTURE
            is TerminalEvent.Exit -> if (result.polarityChange?.from == com.rameshta.magnetrail.core.model.Polarity.PUSH) {
                FeedbackEvent.PUSH_EXIT
            } else {
                FeedbackEvent.ARROW_EXIT
            }
            is TerminalEvent.InvalidPullExit -> FeedbackEvent.INVALID_MOVE
            is TerminalEvent.Collision -> null
        }
        event?.let(::emit)
    }

    private fun emit(event: FeedbackEvent) {
        _feedbackEvents.tryEmit(event)
    }

    private fun createLevelState(
        levelIndex: Int,
        previous: GameUiState? = null,
        destination: AppDestination = AppDestination.HOME,
    ): GameUiState {
        require(catalog.levels.isNotEmpty()) { "Magnetrail requires at least one validated level" }
        val level = catalog.levels[levelIndex]
        val initialState = level.initialState()
        val existingProgress = previous?.progress ?: PlayerProgress(
            lastSelectedLevelId = catalog.levels.first().id,
        )
        return GameUiState(
            levels = catalog.levels.toList(),
            currentLevelIndex = levelIndex,
            currentLevel = level,
            initialState = initialState,
            boardState = initialState,
            destination = destination,
            returnDestination = previous?.returnDestination ?: AppDestination.HOME,
            settings = previous?.settings ?: com.rameshta.magnetrail.data.PlayerSettings(),
            progress = existingProgress.copy(lastSelectedLevelId = level.id),
            preferencesLoaded = previous?.preferencesLoaded ?: false,
            isDeadlocked = engine.isDeadlocked(initialState),
        )
    }

    companion object {
        private const val HINT_LOADING_DELAY_MILLIS = 120L
        private const val HINT_TIMEOUT_MILLIS = 2_000L

        fun factory(
            catalog: LevelCatalog,
            repository: ProgressRepository,
            debugUnlockAll: Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(GameViewModel::class.java)) {
                    "Unsupported ViewModel class: ${modelClass.name}"
                }
                return GameViewModel(
                    catalog = catalog,
                    progressRepository = repository,
                    debugUnlockAll = debugUnlockAll,
                ) as T
            }
        }
    }
}
