package com.rameshta.magnetrail

import com.rameshta.magnetrail.analytics.AnalyticsBuckets
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsPrivacyGuard
import com.rameshta.magnetrail.crash.CrashKey
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.crash.CrashReporter
import com.rameshta.magnetrail.privacy.ConsentFlowResult
import com.rameshta.magnetrail.privacy.ObservabilityController
import com.rameshta.magnetrail.privacy.PrivacyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M4AnalyticsTest {
    @Test
    fun `typed event mapping contains required stable parameters`() {
        val event = AnalyticsEvent.LevelComplete("proto-010", 3, 8, 1, 0, "1_2m")
        assertEquals("level_complete", event.name)
        assertEquals(
            setOf("level_id", "stars", "actions", "overloads", "hints", "duration_bucket"),
            event.parameters.keys,
        )
        AnalyticsPrivacyGuard.requireSafe(event)
    }

    @Test
    fun `bucketing avoids exact product telemetry where unnecessary`() {
        assertEquals("0", AnalyticsBuckets.count(0))
        assertEquals("3_5", AnalyticsBuckets.count(5))
        assertEquals("11_plus", AnalyticsBuckets.count(99))
        assertEquals("under_30s", AnalyticsBuckets.duration(29))
        assertEquals("10m_plus", AnalyticsBuckets.duration(600))
    }

    @Test
    fun `catalog events contain no forbidden raw identity fields`() {
        val events = listOf(
            AnalyticsEvent.ConsentFlowResult("obtained"),
            AnalyticsEvent.RewardedLoadResult("no_fill"),
            AnalyticsEvent.InterstitialEligible("cooldown", "skip"),
            AnalyticsEvent.DailyComplete("advanced", 2, "3_5"),
        )
        events.forEach(AnalyticsPrivacyGuard::requireSafe)
        assertFalse(events.flatMap { it.parameters.keys }.any { it in setOf("local_date", "daily_seed", "consent_string") })
    }

    @Test
    fun `crash key allowlist excludes board consent payload and identifiers`() {
        val names = CrashKey.entries.map { it.wireName }.toSet()
        assertTrue("content_profile" in names)
        assertFalse("board_state" in names)
        assertFalse("consent_string" in names)
        assertFalse("advertising_id" in names)
    }

    @Test
    fun `effective collection gates analytics and crash reporting together`() {
        val analytics = RecordingAnalytics()
        val crash = RecordingCrash()
        val controller = ObservabilityController(analytics, crash)
        controller.apply(true, PrivacyState(canRequestAds = false, flowResult = ConsentFlowResult.DENIED))
        assertFalse(analytics.enabled)
        assertFalse(crash.enabled)
        controller.apply(true, PrivacyState(canRequestAds = true, flowResult = ConsentFlowResult.OBTAINED))
        assertTrue(analytics.enabled)
        assertTrue(crash.enabled)
    }

    private class RecordingAnalytics : AnalyticsTracker {
        var enabled = false
        override fun track(event: AnalyticsEvent) = Unit
        override fun setCollectionEnabled(enabled: Boolean) { this.enabled = enabled }
    }

    private class RecordingCrash : CrashReporter {
        var enabled = false
        override fun setCollectionEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun setKey(key: CrashKey, value: String) = Unit
        override fun recordUnexpected(error: Throwable) = Unit
    }
}
