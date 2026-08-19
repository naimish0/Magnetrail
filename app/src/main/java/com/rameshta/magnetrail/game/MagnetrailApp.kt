package com.rameshta.magnetrail.game

import androidx.compose.runtime.Composable
import com.rameshta.magnetrail.home.HomeScreen
import com.rameshta.magnetrail.levels.LevelSelectionScreen
import com.rameshta.magnetrail.settings.SettingsScreen
import com.rameshta.magnetrail.ads.RewardedOffer
import com.rameshta.magnetrail.ads.RewardedOfferStatus

@Composable
fun MagnetrailApp(
    uiState: GameUiState,
    debugUnlockAll: Boolean,
    onAction: (GameAction) -> Unit,
    rewardedOffer: RewardedOffer = RewardedOffer(
        RewardedOfferStatus.UNAVAILABLE,
        false,
        "Watch an ad for one hint",
        "No ad available right now",
    ),
    onRewardedHint: () -> Unit = {},
    onNextLevel: () -> Unit = { onAction(GameAction.NextLevel) },
    privacyOptionsRequired: Boolean = false,
    privacyPolicyUrl: String? = null,
    showPrivacyPolicyPlaceholder: Boolean = false,
    onPrivacyOptions: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
) {
    when (uiState.destination) {
        AppDestination.HOME -> HomeScreen(
            uiState = uiState,
            onPlay = { onAction(GameAction.Play) },
            onOpenDaily = { onAction(GameAction.OpenDailyChallenge) },
            onOpenLevels = { onAction(GameAction.OpenLevelSelection) },
            onOpenSettings = { onAction(GameAction.OpenSettings) },
        )
        AppDestination.LEVELS -> LevelSelectionScreen(
            levels = uiState.levels,
            currentLevelIndex = uiState.currentLevelIndex,
            highestUnlockedLevel = uiState.progress.highestUnlockedLevel,
            completedLevelIds = uiState.progress.completedLevelIds,
            recordsByLevel = uiState.progress.recordsByLevel,
            debugUnlockAll = debugUnlockAll,
            onBack = { onAction(GameAction.CloseLevelSelection) },
            onLevelSelected = { onAction(GameAction.SelectLevel(it)) },
        )
        AppDestination.GAME -> GameScreen(
            uiState = uiState,
            onAction = onAction,
            rewardedOffer = rewardedOffer,
            onRewardedHint = onRewardedHint,
            onNextLevel = onNextLevel,
        )
        AppDestination.SETTINGS -> SettingsScreen(
            settings = uiState.settings,
            onBack = { onAction(GameAction.CloseSettings) },
            onSettingChanged = { key, enabled ->
                onAction(GameAction.UpdateSetting(key, enabled))
            },
            privacyOptionsRequired = privacyOptionsRequired,
            privacyPolicyUrl = privacyPolicyUrl,
            showPrivacyPolicyPlaceholder = showPrivacyPolicyPlaceholder,
            onPrivacyOptions = onPrivacyOptions,
            onPrivacyPolicy = onPrivacyPolicy,
        )
    }
}
