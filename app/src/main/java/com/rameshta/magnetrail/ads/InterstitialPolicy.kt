package com.rameshta.magnetrail.ads

enum class InterstitialReason {
    ELIGIBLE,
    NOT_CAMPAIGN,
    NOT_FORWARD_PROGRESS,
    COMPLETION_GAP,
    RECENT_REWARDED,
    CONSENT_BLOCKED,
    NOT_LOADED,
    BACKGROUND,
    WRONG_SCREEN,
    FULL_SCREEN_BUSY,
}

data class InterstitialPolicyInput(
    val campaign: Boolean,
    val infinite: Boolean = false,
    val forwardProgression: Boolean,
    val eligibleCompletionsSinceLastAd: Int,
    val nowElapsedMillis: Long,
    val lastRewardedElapsedMillis: Long?,
    val consentAllowsAds: Boolean,
    val loaded: Boolean,
    val foreground: Boolean,
    val expectedCompletionScreen: Boolean,
    val fullScreenIdle: Boolean,
    val autoJourney: Boolean = false,
)

data class InterstitialDecision(val eligible: Boolean, val reason: InterstitialReason)

object InterstitialPolicy {
    const val FIRST_CAMPAIGN_OPPORTUNITY_LEVEL = 11
    const val COMPLETION_GAP = 5
    const val REWARDED_TO_INTERSTITIAL_GUARD_MILLIS = 60_000L

    fun evaluate(input: InterstitialPolicyInput): InterstitialDecision {
        fun blocked(reason: InterstitialReason) = InterstitialDecision(false, reason)
        if (!input.campaign && !input.infinite && !input.autoJourney) {
            return blocked(InterstitialReason.NOT_CAMPAIGN)
        }
        if (!input.forwardProgression) return blocked(InterstitialReason.NOT_FORWARD_PROGRESS)
        if (input.eligibleCompletionsSinceLastAd < COMPLETION_GAP) return blocked(InterstitialReason.COMPLETION_GAP)
        input.lastRewardedElapsedMillis?.let { lastRewarded ->
            if (input.nowElapsedMillis < lastRewarded ||
                input.nowElapsedMillis - lastRewarded < REWARDED_TO_INTERSTITIAL_GUARD_MILLIS
            ) {
                return blocked(InterstitialReason.RECENT_REWARDED)
            }
        }
        if (!input.consentAllowsAds) return blocked(InterstitialReason.CONSENT_BLOCKED)
        if (!input.loaded) return blocked(InterstitialReason.NOT_LOADED)
        if (!input.foreground) return blocked(InterstitialReason.BACKGROUND)
        if (!input.expectedCompletionScreen) return blocked(InterstitialReason.WRONG_SCREEN)
        if (!input.fullScreenIdle) return blocked(InterstitialReason.FULL_SCREEN_BUSY)
        return InterstitialDecision(true, InterstitialReason.ELIGIBLE)
    }
}
