package com.rameshta.magnetrail.data

import com.rameshta.magnetrail.core.daily.DailySeed
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.generation.CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.GENERATOR_VERSION

const val PLAYER_PREFERENCES_SCHEMA_VERSION = 4

data class PlayerSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val highContrastFields: Boolean = false,
    val pathPreviewAssistance: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
)

data class AdMonetizationState(
    val interstitialEligibleCompletions: Int = 0,
    val lastFullScreenAdWallTimeMillis: Long? = null,
    val lastFullScreenAdDate: String? = null,
    val interstitialsShownOnDate: Int = 0,
    val rewardedGrantDate: String? = null,
    val rewardedGrantsOnDate: Int = 0,
    val pendingAdHintTransactionId: String? = null,
    val processedRewardTransactionIds: Set<String> = emptySet(),
)

data class PlayerProgress(
    val highestUnlockedLevel: Int = 1,
    val completedLevelIds: Set<String> = emptySet(),
    val lastSelectedLevelId: String,
    val bestMovesByLevel: Map<String, Int> = emptyMap(),
    val recordsByLevel: Map<String, LevelRecord> = emptyMap(),
    val firstClearRewardedLevelIds: Set<String> = emptySet(),
    val coinBalance: Int = EconomyConfig.STARTING_BALANCE,
    val economyVersion: Int = EconomyConfig.VERSION,
    val completedDailyIds: Set<String> = emptySet(),
    val rewardedDailyIds: Set<String> = emptySet(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastTrustedDailyDate: String? = null,
    val dailyCache: DailyCache? = null,
    val contentVersion: Int = CONTENT_VERSION,
    val generatorVersion: Int = GENERATOR_VERSION,
    val dailyGeneratorVersion: Int = DailySeed.GENERATOR_VERSION,
    val monetization: AdMonetizationState = AdMonetizationState(),
)

data class LevelRecord(
    val bestStars: Int = 0,
    val lowestActions: Int? = null,
    val lowestOverloads: Int? = null,
    val lowestHints: Int? = null,
)

data class DailyCache(
    val dailyId: String,
    val contentFingerprint: String,
    val catalogJson: String,
)

data class PlayerPreferences(
    val schemaVersion: Int = PLAYER_PREFERENCES_SCHEMA_VERSION,
    val settings: PlayerSettings,
    val progress: PlayerProgress,
)

enum class SettingKey {
    SOUND,
    HAPTICS,
    REDUCED_MOTION,
    HIGH_CONTRAST_FIELDS,
    PATH_PREVIEW_ASSISTANCE,
    DIAGNOSTICS,
}

fun PlayerSettings.withValue(key: SettingKey, enabled: Boolean): PlayerSettings = when (key) {
    SettingKey.SOUND -> copy(soundEnabled = enabled)
    SettingKey.HAPTICS -> copy(hapticsEnabled = enabled)
    SettingKey.REDUCED_MOTION -> copy(reducedMotion = enabled)
    SettingKey.HIGH_CONTRAST_FIELDS -> copy(highContrastFields = enabled)
    SettingKey.PATH_PREVIEW_ASSISTANCE -> copy(pathPreviewAssistance = enabled)
    SettingKey.DIAGNOSTICS -> copy(diagnosticsEnabled = enabled)
}
