package com.rameshta.magnetrail.data

import com.rameshta.magnetrail.core.economy.RewardBreakdown
import com.rameshta.magnetrail.core.grading.AttemptGrade
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

sealed interface RewardedCreditGrantResult {
    data object Granted : RewardedCreditGrantResult
    data object Duplicate : RewardedCreditGrantResult
    data object InventoryFull : RewardedCreditGrantResult
    data object DailyCapReached : RewardedCreditGrantResult
    data object DateRollback : RewardedCreditGrantResult
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

    suspend fun grantRewardedHintCredit(transactionId: String, localDate: LocalDate): RewardedCreditGrantResult =
        RewardedCreditGrantResult.InventoryFull

    suspend fun consumeRewardedHintCredit(transactionId: String): Boolean = false

    suspend fun recordFullScreenAdDismissal(
        localDate: LocalDate,
        wallTimeMillis: Long,
        interstitialShown: Boolean,
    ) = Unit
}
