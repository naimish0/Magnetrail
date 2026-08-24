package com.rameshta.magnetrail.analytics

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    fun setCollectionEnabled(enabled: Boolean)
}

object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit
    override fun setCollectionEnabled(enabled: Boolean) = Unit
}
