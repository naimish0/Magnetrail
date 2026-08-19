package com.rameshta.magnetrail

import com.rameshta.magnetrail.ads.AdClock
import com.rameshta.magnetrail.ads.FullScreenAdCoordinator
import com.rameshta.magnetrail.ads.FullScreenOwner
import com.rameshta.magnetrail.ads.InterstitialPolicy
import com.rameshta.magnetrail.ads.InterstitialPolicyInput
import com.rameshta.magnetrail.ads.InterstitialReason
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M4InterstitialPolicyTest {
    @Test
    fun `first ten lifetime levels daily and replay are excluded`() {
        assertReason(base().copy(lifetimeCampaignCompletions = 9), InterstitialReason.FIRST_LEVELS)
        assertReason(base().copy(campaign = false), InterstitialReason.NOT_CAMPAIGN)
        assertReason(base().copy(forwardProgression = false), InterstitialReason.NOT_FORWARD_PROGRESS)
    }

    @Test
    fun `third eligible completion is the first eligible gap`() {
        assertReason(base().copy(eligibleCompletionsSinceLastAd = 2), InterstitialReason.COMPLETION_GAP)
        assertTrue(InterstitialPolicy.evaluate(base().copy(eligibleCompletionsSinceLastAd = 3)).eligible)
    }

    @Test
    fun `cooldown and recent rewarded use exact 120 second edge`() {
        assertReason(
            base().copy(lastFullScreenElapsedMillis = 1_000L, nowElapsedMillis = 120_999L),
            InterstitialReason.COOLDOWN,
        )
        assertTrue(
            InterstitialPolicy.evaluate(
                base().copy(lastFullScreenElapsedMillis = 1_000L, nowElapsedMillis = 121_000L),
            ).eligible,
        )
        assertReason(
            base().copy(lastRewardedElapsedMillis = 1_001L, nowElapsedMillis = 121_000L),
            InterstitialReason.RECENT_REWARDED,
        )
    }

    @Test
    fun `daily cap rollback consent load lifecycle and overlap fail closed`() {
        assertReason(base().copy(interstitialsShownOnStoredDate = 4), InterstitialReason.DAILY_CAP)
        assertReason(base().copy(nowDate = LocalDate.of(2026, 8, 18)), InterstitialReason.DATE_ROLLBACK)
        assertReason(base().copy(consentAllowsAds = false), InterstitialReason.CONSENT_BLOCKED)
        assertReason(base().copy(loaded = false), InterstitialReason.NOT_LOADED)
        assertReason(base().copy(foreground = false), InterstitialReason.BACKGROUND)
        assertReason(base().copy(expectedCompletionScreen = false), InterstitialReason.WRONG_SCREEN)
        assertReason(base().copy(fullScreenIdle = false), InterstitialReason.FULL_SCREEN_BUSY)
    }

    @Test
    fun `wall clock rollback cannot bypass persisted cooldown`() {
        assertReason(
            base().copy(
                lastFullScreenElapsedMillis = null,
                nowWallMillis = 900_000L,
                lastFullScreenWallMillis = 1_000_000L,
            ),
            InterstitialReason.COOLDOWN,
        )
    }

    @Test
    fun `one process wide full screen owner at a time`() {
        val clock = FakeClock()
        val coordinator = FullScreenAdCoordinator(clock)
        assertTrue(coordinator.tryAcquire(FullScreenOwner.REWARDED))
        assertFalse(coordinator.tryAcquire(FullScreenOwner.INTERSTITIAL))
        coordinator.release(FullScreenOwner.REWARDED, shown = true)
        assertTrue(coordinator.tryAcquire(FullScreenOwner.INTERSTITIAL))
        assertEquals(clock.elapsed, coordinator.lastRewardedElapsedMillis)
    }

    private fun assertReason(input: InterstitialPolicyInput, reason: InterstitialReason) {
        assertEquals(reason, InterstitialPolicy.evaluate(input).reason)
    }

    private fun base() = InterstitialPolicyInput(
        campaign = true,
        lifetimeCampaignCompletions = 10,
        forwardProgression = true,
        eligibleCompletionsSinceLastAd = 3,
        nowDate = LocalDate.of(2026, 8, 19),
        storedDailyDate = LocalDate.of(2026, 8, 19),
        interstitialsShownOnStoredDate = 0,
        nowWallMillis = 2_000_000L,
        lastFullScreenWallMillis = null,
        nowElapsedMillis = 500_000L,
        lastFullScreenElapsedMillis = null,
        lastRewardedElapsedMillis = null,
        consentAllowsAds = true,
        loaded = true,
        foreground = true,
        expectedCompletionScreen = true,
        fullScreenIdle = true,
    )

    private class FakeClock : AdClock {
        val elapsed = 42L
        override fun wallTimeMillis() = 100L
        override fun elapsedRealtimeMillis() = elapsed
        override fun localDate(): LocalDate = LocalDate.of(2026, 8, 19)
    }
}
