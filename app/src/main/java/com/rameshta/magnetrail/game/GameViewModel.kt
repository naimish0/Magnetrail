package com.rameshta.magnetrail.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.level.LevelCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameViewModel(
    private val catalog: LevelCatalog,
    private val engine: GameEngine = DefaultGameEngine(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(createLevelState(levelIndex = 0))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.LaunchArrow -> launchArrow(action.arrowId)
            is GameAction.AnimationPhaseChanged -> updateAnimationPhase(action.phase)
            GameAction.AnimationCompleted -> completeAnimation()
            GameAction.Undo -> undo()
            GameAction.Restart, GameAction.Replay -> restart()
            GameAction.OpenLevelSelection -> openLevelSelection()
            GameAction.CloseLevelSelection -> closeLevelSelection()
            is GameAction.SelectLevel -> selectLevel(action.index)
            GameAction.NextLevel -> nextLevel()
        }
    }

    private fun launchArrow(arrowId: String) {
        val state = _uiState.value
        if (!state.inputEnabled || state.isComplete || state.inFlightResult != null) return
        if (state.boardState.arrow(arrowId) == null) return

        val resolution = engine.resolve(state.boardState, PlayerAction(arrowId))
        _uiState.value = state.copy(
            inFlightResult = resolution,
            animationPhase = TurnAnimationPhase.ROUTE,
            inputEnabled = false,
        )
    }

    private fun updateAnimationPhase(phase: TurnAnimationPhase) {
        val state = _uiState.value
        if (state.inFlightResult == null) return
        _uiState.value = state.copy(animationPhase = phase)
    }

    private fun completeAnimation() {
        val state = _uiState.value
        val result = state.inFlightResult ?: return
        val history = if (result.success) {
            state.undoHistory + result.originalState
        } else {
            state.undoHistory
        }
        val committedState = result.resultingState

        _uiState.value = state.copy(
            boardState = committedState,
            undoHistory = history,
            inFlightResult = null,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = !result.isWin,
            isComplete = result.isWin,
            isDeadlocked = result.isDeadlocked,
        )
    }

    private fun undo() {
        val state = _uiState.value
        if (!state.canUndo) return
        val restoredState = state.undoHistory.last()
        _uiState.value = state.copy(
            boardState = restoredState,
            undoHistory = state.undoHistory.dropLast(1),
            isComplete = false,
            isDeadlocked = engine.isDeadlocked(restoredState),
            inputEnabled = true,
        )
    }

    private fun restart() {
        val state = _uiState.value
        if (!state.canRestart) return
        _uiState.value = state.copy(
            boardState = state.initialState,
            undoHistory = emptyList(),
            inFlightResult = null,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = true,
            isComplete = false,
            isDeadlocked = engine.isDeadlocked(state.initialState),
        )
    }

    private fun openLevelSelection() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        _uiState.value = state.copy(isLevelSelectionVisible = true)
    }

    private fun closeLevelSelection() {
        _uiState.value = _uiState.value.copy(isLevelSelectionVisible = false)
    }

    private fun selectLevel(index: Int) {
        if (index !in catalog.levels.indices) return
        _uiState.value = createLevelState(levelIndex = index)
    }

    private fun nextLevel() {
        val state = _uiState.value
        if (state.inFlightResult != null || !state.isComplete) return
        if (state.hasNextLevel) {
            _uiState.value = createLevelState(state.currentLevelIndex + 1)
        } else {
            _uiState.value = state.copy(isLevelSelectionVisible = true)
        }
    }

    private fun createLevelState(levelIndex: Int): GameUiState {
        require(catalog.levels.isNotEmpty()) { "Magnetrail requires at least one validated level" }
        val level = catalog.levels[levelIndex]
        val initialState = level.initialState()
        return GameUiState(
            levels = catalog.levels.toList(),
            currentLevelIndex = levelIndex,
            currentLevel = level,
            initialState = initialState,
            boardState = initialState,
            isDeadlocked = engine.isDeadlocked(initialState),
        )
    }

    companion object {
        fun factory(catalog: LevelCatalog): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(GameViewModel::class.java)) {
                    "Unsupported ViewModel class: ${modelClass.name}"
                }
                return GameViewModel(catalog) as T
            }
        }
    }
}
