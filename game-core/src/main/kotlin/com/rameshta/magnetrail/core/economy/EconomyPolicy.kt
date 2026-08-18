package com.rameshta.magnetrail.core.economy

object EconomyConfig {
    const val VERSION = 1
    const val STARTING_BALANCE = 150
    const val FIRST_CAMPAIGN_CLEAR_REWARD = 20
    const val NEW_STAR_REWARD = 5
    const val FIRST_DAILY_CLEAR_REWARD = 50
    const val HINT_COST = 30
}

data class RewardBreakdown(
    val firstClearReward: Int = 0,
    val newStarReward: Int = 0,
    val dailyReward: Int = 0,
    val resultingBalance: Int,
) {
    val total: Int get() = firstClearReward + newStarReward + dailyReward
}

object RewardPolicy {
    fun campaignCompletion(
        previousBalance: Int,
        wasFirstClearRewarded: Boolean,
        previousBestStars: Int,
        earnedStars: Int,
    ): RewardBreakdown {
        require(previousBalance >= 0) { "Coin balance cannot be negative" }
        require(previousBestStars in 0..3 && earnedStars in 1..3) { "Stars must be in range 0..3" }
        val firstClear = if (wasFirstClearRewarded) 0 else EconomyConfig.FIRST_CAMPAIGN_CLEAR_REWARD
        val incrementalStars = (earnedStars - previousBestStars).coerceAtLeast(0)
        val starReward = incrementalStars * EconomyConfig.NEW_STAR_REWARD
        return RewardBreakdown(
            firstClearReward = firstClear,
            newStarReward = starReward,
            resultingBalance = previousBalance + firstClear + starReward,
        )
    }

    fun dailyCompletion(previousBalance: Int, wasRewarded: Boolean): RewardBreakdown {
        require(previousBalance >= 0) { "Coin balance cannot be negative" }
        val daily = if (wasRewarded) 0 else EconomyConfig.FIRST_DAILY_CLEAR_REWARD
        return RewardBreakdown(dailyReward = daily, resultingBalance = previousBalance + daily)
    }
}
