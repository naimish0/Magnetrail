package com.rameshta.magnetrail.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.atomic.AtomicBoolean

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    fun setCollectionEnabled(enabled: Boolean)
}

object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit
    override fun setCollectionEnabled(enabled: Boolean) = Unit
}

class FirebaseAnalyticsTracker private constructor(
    private val analytics: FirebaseAnalytics,
) : AnalyticsTracker {
    private val enabled = AtomicBoolean(false)

    override fun track(event: AnalyticsEvent) {
        if (!enabled.get()) return
        AnalyticsPrivacyGuard.requireSafe(event)
        val bundle = Bundle().apply {
            event.parameters.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Boolean -> putLong(key, if (value) 1L else 0L)
                    is Double -> putDouble(key, value)
                    else -> error("Unsupported analytics parameter type")
                }
            }
        }
        analytics.logEvent(event.name, bundle)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled.set(enabled)
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    companion object {
        fun createOrNoOp(context: Context): AnalyticsTracker = runCatching {
            check(FirebaseApp.getApps(context.applicationContext).isNotEmpty())
            FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(context.applicationContext))
        }.getOrElse { NoOpAnalyticsTracker }
    }
}
