package com.rameshta.magnetrail.game

import androidx.compose.runtime.Composable
import com.rameshta.magnetrail.levels.LevelSelectionScreen

@Composable
fun MagnetrailApp(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    if (uiState.isLevelSelectionVisible) {
        LevelSelectionScreen(
            levels = uiState.levels,
            currentLevelIndex = uiState.currentLevelIndex,
            onBack = { onAction(GameAction.CloseLevelSelection) },
            onLevelSelected = { onAction(GameAction.SelectLevel(it)) },
        )
    } else {
        GameScreen(uiState = uiState, onAction = onAction)
    }
}
