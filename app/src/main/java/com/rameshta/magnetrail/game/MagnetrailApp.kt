package com.rameshta.magnetrail.game

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import com.rameshta.magnetrail.home.HomeScreen
import com.rameshta.magnetrail.levels.LevelSelectionScreen
import com.rameshta.magnetrail.settings.SettingsScreen
import com.rameshta.magnetrail.infinite.InfiniteModeScreen
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
    rewardedSkipOffer: RewardedOffer = RewardedOffer(
        RewardedOfferStatus.UNAVAILABLE,
        false,
        "Skip level with an ad",
        "No ad available right now",
    ),
    onRewardedHint: () -> Unit = {},
    onRewardedSkip: () -> Unit = {},
    onNextLevel: () -> Unit = { onAction(GameAction.NextLevel) },
    privacyOptionsRequired: Boolean = false,
    privacyPolicyUrl: String? = null,
    showPrivacyPolicyPlaceholder: Boolean = false,
    onPrivacyOptions: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
) {
    BackHandler(enabled = uiState.destination != AppDestination.HOME) {
        onAction(
            when (uiState.destination) {
                AppDestination.LEVELS -> GameAction.CloseLevelSelection
                AppDestination.INFINITE -> GameAction.CloseInfiniteMode
                AppDestination.SETTINGS -> GameAction.CloseSettings
                AppDestination.GAME -> GameAction.NavigateHome
                AppDestination.HOME -> return@BackHandler
            },
        )
    }
    when (uiState.destination) {
        AppDestination.HOME -> HomeScreen(
            uiState = uiState,
            onPlay = { onAction(GameAction.Play) },
            onOpenDaily = { onAction(GameAction.OpenDailyChallenge) },
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
            infiniteLevelCount = uiState.infiniteCatalogSize,
            onOpenInfinite = { onAction(GameAction.OpenInfiniteMode) },
        )
        AppDestination.GAME -> GameScreen(
            uiState = uiState,
            onAction = onAction,
            rewardedOffer = rewardedOffer,
            rewardedSkipOffer = rewardedSkipOffer,
            onRewardedHint = onRewardedHint,
            onRewardedSkip = onRewardedSkip,
            onNextLevel = onNextLevel,
        )
        AppDestination.INFINITE -> InfiniteModeScreen(
            progress = uiState.progress.infinite,
            catalogSize = uiState.infiniteCatalogSize,
            onBack = { onAction(GameAction.CloseInfiniteMode) },
            onSelectDifficulty = { onAction(GameAction.SelectInfiniteDifficulty(it)) },
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
