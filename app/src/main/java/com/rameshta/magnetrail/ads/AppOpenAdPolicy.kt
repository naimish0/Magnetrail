package com.rameshta.magnetrail.ads

enum class AppOpenReason {
    ELIGIBLE,
    CONSENT_BLOCKED,
    NOT_LOADED,
    STALE_AD,
    BACKGROUND,
    FULL_SCREEN_BUSY,
    RECENT_FULL_SCREEN,
    COOLDOWN,
}

data class AppOpenPolicyInput(
    val nowWallTimeMillis: Long,
    val lastShownWallTimeMillis: Long?,
    val loadedAtWallTimeMillis: Long?,
    val nowElapsedMillis: Long,
    val lastFullScreenDismissedElapsedMillis: Long?,
    val consentAllowsAds: Boolean,
    val foreground: Boolean,
    val fullScreenIdle: Boolean,
)

data class AppOpenDecision(val eligible: Boolean, val reason: AppOpenReason)

object AppOpenAdPolicy {
    const val COOLDOWN_MILLIS = 60L * 60L * 1_000L
    const val MAX_AD_AGE_MILLIS = 4L * 60L * 60L * 1_000L
    const val FULL_SCREEN_GUARD_MILLIS = 60_000L

    fun evaluate(input: AppOpenPolicyInput): AppOpenDecision {
        fun blocked(reason: AppOpenReason) = AppOpenDecision(false, reason)
        if (!input.consentAllowsAds) return blocked(AppOpenReason.CONSENT_BLOCKED)
        if (!input.foreground) return blocked(AppOpenReason.BACKGROUND)
        if (!input.fullScreenIdle) return blocked(AppOpenReason.FULL_SCREEN_BUSY)
        val loadedAt = input.loadedAtWallTimeMillis ?: return blocked(AppOpenReason.NOT_LOADED)
        val adAge = input.nowWallTimeMillis - loadedAt
        if (adAge < 0L || adAge >= MAX_AD_AGE_MILLIS) return blocked(AppOpenReason.STALE_AD)
        input.lastFullScreenDismissedElapsedMillis?.let { lastDismissed ->
            val elapsed = input.nowElapsedMillis - lastDismissed
            if (elapsed < 0L || elapsed < FULL_SCREEN_GUARD_MILLIS) {
                return blocked(AppOpenReason.RECENT_FULL_SCREEN)
            }
        }
        input.lastShownWallTimeMillis?.let { lastShown ->
            val elapsed = input.nowWallTimeMillis - lastShown
            if (elapsed < 0L || elapsed < COOLDOWN_MILLIS) return blocked(AppOpenReason.COOLDOWN)
        }
        return AppOpenDecision(true, AppOpenReason.ELIGIBLE)
    }
}
