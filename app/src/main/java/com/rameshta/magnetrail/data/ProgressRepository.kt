package com.rameshta.magnetrail.data

import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    val preferences: Flow<PlayerPreferences>

    suspend fun updateSetting(key: SettingKey, enabled: Boolean)

    suspend fun selectLevel(levelId: String)

    suspend fun recordCompletion(levelId: String, moves: Int)
}
