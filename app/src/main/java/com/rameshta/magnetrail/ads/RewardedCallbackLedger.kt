package com.rameshta.magnetrail.ads

import java.util.concurrent.atomic.AtomicBoolean

class RewardedCallbackLedger(private val transactionId: String) {
    private val rewarded = AtomicBoolean(false)

    fun rewardCallback(): Boolean = rewarded.compareAndSet(false, true)

    fun dismiss(): RewardedOutcome = if (rewarded.get()) {
        RewardedOutcome.Earned(transactionId)
    } else {
        RewardedOutcome.DismissedWithoutReward
    }
}
