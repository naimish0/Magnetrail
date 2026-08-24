package com.rameshta.magnetrail

import com.rameshta.magnetrail.ads.AppOpenAdPolicy
import com.rameshta.magnetrail.ads.AppOpenPolicyInput
import com.rameshta.magnetrail.ads.AppOpenReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenAdPolicyTest {
    @Test
    fun `one hour cooldown ends at the exact boundary`() {
        assertReason(
            base().copy(
                nowWallTimeMillis = AppOpenAdPolicy.COOLDOWN_MILLIS - 1L,
                lastShownWallTimeMillis = 0L,
            ),
            AppOpenReason.COOLDOWN,
        )
        assertTrue(
            AppOpenAdPolicy.evaluate(
                base().copy(
                    nowWallTimeMillis = AppOpenAdPolicy.COOLDOWN_MILLIS,
                    lastShownWallTimeMillis = 0L,
                ),
            ).eligible,
        )
    }

    @Test
    fun `wall clock rollback fails closed`() {
        assertReason(
            base().copy(
                nowWallTimeMillis = 999L,
                lastShownWallTimeMillis = 1_000L,
                loadedAtWallTimeMillis = 0L,
            ),
            AppOpenReason.COOLDOWN,
        )
    }

    @Test
    fun `loaded app open ad expires after four hours`() {
        assertReason(
            base().copy(
                nowWallTimeMillis = AppOpenAdPolicy.MAX_AD_AGE_MILLIS,
                loadedAtWallTimeMillis = 0L,
            ),
            AppOpenReason.STALE_AD,
        )
    }

    @Test
    fun `consent lifecycle availability and overlap fail closed`() {
        assertReason(base().copy(consentAllowsAds = false), AppOpenReason.CONSENT_BLOCKED)
        assertReason(base().copy(foreground = false), AppOpenReason.BACKGROUND)
        assertReason(base().copy(fullScreenIdle = false), AppOpenReason.FULL_SCREEN_BUSY)
        assertReason(base().copy(loadedAtWallTimeMillis = null), AppOpenReason.NOT_LOADED)
    }

    @Test
    fun `recent full screen dismissal prevents a back to back app open ad`() {
        assertReason(
            base().copy(
                nowElapsedMillis = AppOpenAdPolicy.FULL_SCREEN_GUARD_MILLIS - 1L,
                lastFullScreenDismissedElapsedMillis = 0L,
            ),
            AppOpenReason.RECENT_FULL_SCREEN,
        )
        assertTrue(
            AppOpenAdPolicy.evaluate(
                base().copy(
                    nowElapsedMillis = AppOpenAdPolicy.FULL_SCREEN_GUARD_MILLIS,
                    lastFullScreenDismissedElapsedMillis = 0L,
                ),
            ).eligible,
        )
    }

    private fun assertReason(input: AppOpenPolicyInput, expected: AppOpenReason) {
        assertEquals(expected, AppOpenAdPolicy.evaluate(input).reason)
    }

    private fun base() = AppOpenPolicyInput(
        nowWallTimeMillis = 10_000L,
        lastShownWallTimeMillis = null,
        loadedAtWallTimeMillis = 1_000L,
        nowElapsedMillis = 100_000L,
        lastFullScreenDismissedElapsedMillis = null,
        consentAllowsAds = true,
        foreground = true,
        fullScreenIdle = true,
    )
}
