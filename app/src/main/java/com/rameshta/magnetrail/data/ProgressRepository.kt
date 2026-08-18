package com.rameshta.magnetrail.data

import com.rameshta.magnetrail.core.economy.RewardBreakdown
import com.rameshta.magnetrail.core.grading.AttemptGrade
import kotlinx.coroutines.flow.Flow

data class AttemptSummary(
    val actions: Int,
    val overloads: Int,
    val hintsUsed: Int,
)

data class CompletionReceipt(
    val grade: AttemptGrade,
    val bestRecord: LevelRecord,
    val rewards: RewardBreakdown,
)

data class DailyCompletionReceipt(
    val rewards: RewardBreakdown,
    val currentStreak: Int,
    val bestStreak: Int,
    val firstCompletion: Boolean,
)

sealed interface HintSpendResult {
    data class Approved(val resultingBalance: Int) : HintSpendResult
    data class InsufficientBalance(val balance: Int, val required: Int) : HintSpendResult
}

interface ProgressRepository {
    val preferences: Flow<PlayerPreferences>

    suspend fun updateSetting(key: SettingKey, enabled: Boolean)

    suspend fun selectLevel(levelId: String)

    suspend fun recordCompletion(levelId: String, moves: Int)

    suspend fun recordCampaignCompletion(levelId: String, attempt: AttemptSummary): CompletionReceipt

    suspend fun recordDailyCompletion(dailyId: String): DailyCompletionReceipt

    suspend fun spendHintCoins(): HintSpendResult

    suspend fun cacheDailyChallenge(cache: DailyCache)
}
