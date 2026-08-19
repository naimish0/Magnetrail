package com.rameshta.magnetrail.privacy

import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.crash.CrashReporter

object DiagnosticsPolicy {
    fun isCollectionAllowed(localOptIn: Boolean, consentState: PrivacyState): Boolean =
        localOptIn && consentState.canRequestAds && consentState.flowResult !in setOf(
            ConsentFlowResult.DENIED,
            ConsentFlowResult.UNKNOWN,
        )
}

class ObservabilityController(
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) {
    fun apply(localOptIn: Boolean, consentState: PrivacyState) {
        val enabled = DiagnosticsPolicy.isCollectionAllowed(localOptIn, consentState)
        analytics.setCollectionEnabled(enabled)
        crashReporter.setCollectionEnabled(enabled)
    }
}
