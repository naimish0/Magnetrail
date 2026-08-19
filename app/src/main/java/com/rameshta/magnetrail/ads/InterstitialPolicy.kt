package com.rameshta.magnetrail.ads

import java.time.LocalDate

enum class InterstitialReason {
    ELIGIBLE,
    NOT_CAMPAIGN,
    FIRST_LEVELS,
    NOT_FORWARD_PROGRESS,
    COMPLETION_GAP,
    COOLDOWN,
    RECENT_REWARDED,
    DAILY_CAP,
    DATE_ROLLBACK,
    CONSENT_BLOCKED,
    NOT_LOADED,
    BACKGROUND,
    WRONG_SCREEN,
    FULL_SCREEN_BUSY,
}

data class InterstitialPolicyInput(
    val campaign: Boolean,
    val lifetimeCampaignCompletions: Int,
    val forwardProgression: Boolean,
    val eligibleCompletionsSinceLastAd: Int,
    val nowDate: LocalDate,
    val storedDailyDate: LocalDate?,
    val interstitialsShownOnStoredDate: Int,
    val nowWallMillis: Long,
    val lastFullScreenWallMillis: Long?,
    val nowElapsedMillis: Long,
    val lastFullScreenElapsedMillis: Long?,
    val lastRewardedElapsedMillis: Long?,
    val consentAllowsAds: Boolean,
    val loaded: Boolean,
    val foreground: Boolean,
    val expectedCompletionScreen: Boolean,
    val fullScreenIdle: Boolean,
)

data class InterstitialDecision(val eligible: Boolean, val reason: InterstitialReason)

object InterstitialPolicy {
    const val MIN_LIFETIME_COMPLETIONS = 10
    const val COMPLETION_GAP = 3
    const val COOLDOWN_MILLIS = 120_000L
    const val DAILY_CAP = 4

    fun evaluate(input: InterstitialPolicyInput): InterstitialDecision {
        fun blocked(reason: InterstitialReason) = InterstitialDecision(false, reason)
        if (!input.campaign) return blocked(InterstitialReason.NOT_CAMPAIGN)
        if (input.lifetimeCampaignCompletions < MIN_LIFETIME_COMPLETIONS) return blocked(InterstitialReason.FIRST_LEVELS)
        if (!input.forwardProgression) return blocked(InterstitialReason.NOT_FORWARD_PROGRESS)
        if (input.eligibleCompletionsSinceLastAd < COMPLETION_GAP) return blocked(InterstitialReason.COMPLETION_GAP)
        if (input.storedDailyDate != null && input.nowDate.isBefore(input.storedDailyDate)) {
            return blocked(InterstitialReason.DATE_ROLLBACK)
        }
        val shownToday = if (input.storedDailyDate == input.nowDate) input.interstitialsShownOnStoredDate else 0
        if (shownToday >= DAILY_CAP) return blocked(InterstitialReason.DAILY_CAP)
        if (!cooldownPassed(input)) return blocked(InterstitialReason.COOLDOWN)
        if (input.lastRewardedElapsedMillis != null &&
            input.nowElapsedMillis - input.lastRewardedElapsedMillis < COOLDOWN_MILLIS
        ) return blocked(InterstitialReason.RECENT_REWARDED)
        if (!input.consentAllowsAds) return blocked(InterstitialReason.CONSENT_BLOCKED)
        if (!input.loaded) return blocked(InterstitialReason.NOT_LOADED)
        if (!input.foreground) return blocked(InterstitialReason.BACKGROUND)
        if (!input.expectedCompletionScreen) return blocked(InterstitialReason.WRONG_SCREEN)
        if (!input.fullScreenIdle) return blocked(InterstitialReason.FULL_SCREEN_BUSY)
        return InterstitialDecision(true, InterstitialReason.ELIGIBLE)
    }

    private fun cooldownPassed(input: InterstitialPolicyInput): Boolean {
        input.lastFullScreenElapsedMillis?.let { last ->
            return input.nowElapsedMillis >= last && input.nowElapsedMillis - last >= COOLDOWN_MILLIS
        }
        input.lastFullScreenWallMillis?.let { last ->
            return input.nowWallMillis >= last && input.nowWallMillis - last >= COOLDOWN_MILLIS &&
                input.nowElapsedMillis >= COOLDOWN_MILLIS
        }
        return true
    }
}
