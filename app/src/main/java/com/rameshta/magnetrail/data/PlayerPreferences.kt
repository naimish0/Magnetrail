package com.rameshta.magnetrail.data

const val PLAYER_PREFERENCES_SCHEMA_VERSION = 1

data class PlayerSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val highContrastFields: Boolean = false,
    val pathPreviewAssistance: Boolean = false,
)

data class PlayerProgress(
    val highestUnlockedLevel: Int = 1,
    val completedLevelIds: Set<String> = emptySet(),
    val lastSelectedLevelId: String,
    val bestMovesByLevel: Map<String, Int> = emptyMap(),
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
}

fun PlayerSettings.withValue(key: SettingKey, enabled: Boolean): PlayerSettings = when (key) {
    SettingKey.SOUND -> copy(soundEnabled = enabled)
    SettingKey.HAPTICS -> copy(hapticsEnabled = enabled)
    SettingKey.REDUCED_MOTION -> copy(reducedMotion = enabled)
    SettingKey.HIGH_CONTRAST_FIELDS -> copy(highContrastFields = enabled)
    SettingKey.PATH_PREVIEW_ASSISTANCE -> copy(pathPreviewAssistance = enabled)
}
