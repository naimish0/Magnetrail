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
    fun `campaign normal Infinite and Auto Journey are eligible modes while other modes and replays are excluded`() {
        assertReason(base().copy(campaign = false), InterstitialReason.NOT_CAMPAIGN)
        assertTrue(InterstitialPolicy.evaluate(base().copy(campaign = false, infinite = true)).eligible)
        assertTrue(InterstitialPolicy.evaluate(base().copy(campaign = false, autoJourney = true)).eligible)
        assertReason(base().copy(forwardProgression = false), InterstitialReason.NOT_FORWARD_PROGRESS)
    }

    @Test
    fun `fifth eligible completion is the exact opportunity`() {
        assertReason(base().copy(eligibleCompletionsSinceLastAd = 4), InterstitialReason.COMPLETION_GAP)
        assertTrue(InterstitialPolicy.evaluate(base().copy(eligibleCompletionsSinceLastAd = 5)).eligible)
    }

    @Test
    fun `rewarded ad blocks interstitial for exactly sixty seconds`() {
        assertReason(
            base().copy(nowElapsedMillis = 60_999L, lastRewardedElapsedMillis = 1_000L),
            InterstitialReason.RECENT_REWARDED,
        )
        assertTrue(
            InterstitialPolicy.evaluate(
                base().copy(nowElapsedMillis = 61_000L, lastRewardedElapsedMillis = 1_000L),
            ).eligible,
        )
        assertReason(
            base().copy(nowElapsedMillis = 999L, lastRewardedElapsedMillis = 1_000L),
            InterstitialReason.RECENT_REWARDED,
        )
    }

    @Test
    fun `consent load lifecycle and overlap fail closed`() {
        assertReason(base().copy(consentAllowsAds = false), InterstitialReason.CONSENT_BLOCKED)
        assertReason(base().copy(loaded = false), InterstitialReason.NOT_LOADED)
        assertReason(base().copy(foreground = false), InterstitialReason.BACKGROUND)
        assertReason(base().copy(expectedCompletionScreen = false), InterstitialReason.WRONG_SCREEN)
        assertReason(base().copy(fullScreenIdle = false), InterstitialReason.FULL_SCREEN_BUSY)
    }

    @Test
    fun `only a completed rewarded ad starts the rewarded guard`() {
        val clock = FakeClock()
        val coordinator = FullScreenAdCoordinator(clock)
        assertTrue(coordinator.tryAcquire(FullScreenOwner.REWARDED))
        assertFalse(coordinator.tryAcquire(FullScreenOwner.INTERSTITIAL))
        coordinator.release(FullScreenOwner.REWARDED, completed = false)
        assertEquals(null, coordinator.lastRewardedElapsedMillis)

        assertTrue(coordinator.tryAcquire(FullScreenOwner.REWARDED))
        coordinator.release(FullScreenOwner.REWARDED, completed = true)
        assertTrue(coordinator.tryAcquire(FullScreenOwner.INTERSTITIAL))
        assertEquals(clock.elapsed, coordinator.lastRewardedElapsedMillis)
    }

    @Test
    fun `app open ownership prevents an interstitial collision`() {
        val coordinator = FullScreenAdCoordinator(FakeClock())
        assertTrue(coordinator.tryAcquire(FullScreenOwner.APP_OPEN))
        assertFalse(coordinator.tryAcquire(FullScreenOwner.INTERSTITIAL))
        assertReason(base().copy(fullScreenIdle = coordinator.isIdle()), InterstitialReason.FULL_SCREEN_BUSY)
    }

    private fun assertReason(input: InterstitialPolicyInput, reason: InterstitialReason) {
        assertEquals(reason, InterstitialPolicy.evaluate(input).reason)
    }

    private fun base() = InterstitialPolicyInput(
        campaign = true,
        forwardProgression = true,
        eligibleCompletionsSinceLastAd = 5,
        nowElapsedMillis = 100_000L,
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
