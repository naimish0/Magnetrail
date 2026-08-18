package com.rameshta.magnetrail.game

import androidx.compose.runtime.Composable
import com.rameshta.magnetrail.home.HomeScreen
import com.rameshta.magnetrail.levels.LevelSelectionScreen
import com.rameshta.magnetrail.settings.SettingsScreen

@Composable
fun MagnetrailApp(
    uiState: GameUiState,
    debugUnlockAll: Boolean,
    onAction: (GameAction) -> Unit,
) {
    when (uiState.destination) {
        AppDestination.HOME -> HomeScreen(
            uiState = uiState,
            onPlay = { onAction(GameAction.Play) },
            onOpenLevels = { onAction(GameAction.OpenLevelSelection) },
            onOpenSettings = { onAction(GameAction.OpenSettings) },
        )
        AppDestination.LEVELS -> LevelSelectionScreen(
            levels = uiState.levels,
            currentLevelIndex = uiState.currentLevelIndex,
            highestUnlockedLevel = uiState.progress.highestUnlockedLevel,
            completedLevelIds = uiState.progress.completedLevelIds,
            debugUnlockAll = debugUnlockAll,
            onBack = { onAction(GameAction.CloseLevelSelection) },
            onLevelSelected = { onAction(GameAction.SelectLevel(it)) },
        )
        AppDestination.GAME -> GameScreen(uiState = uiState, onAction = onAction)
        AppDestination.SETTINGS -> SettingsScreen(
            settings = uiState.settings,
            onBack = { onAction(GameAction.CloseSettings) },
            onSettingChanged = { key, enabled ->
                onAction(GameAction.UpdateSetting(key, enabled))
            },
        )
    }
}
