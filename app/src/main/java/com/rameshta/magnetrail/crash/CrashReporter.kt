package com.rameshta.magnetrail.crash

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

enum class CrashKey(val wireName: String) {
    APP_VERSION("app_version"), CONTENT_VERSION("content_version"), ENGINE_VERSION("engine_version"),
    GENERATOR_VERSION("generator_version"), ECONOMY_VERSION("economy_version"), SCREEN("screen"),
    CONTENT_PROFILE("content_profile"), ANIMATION_PHASE("animation_phase"), CONSENT_STATE("consent_state"),
    AD_STATE("ad_state"), LAST_AD_POLICY_REASON("last_ad_policy_reason"),
}

interface CrashReporter {
    fun setCollectionEnabled(enabled: Boolean)
    fun setKey(key: CrashKey, value: String)
    fun recordUnexpected(error: Throwable)
}

object NoOpCrashReporter : CrashReporter {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun setKey(key: CrashKey, value: String) = Unit
    override fun recordUnexpected(error: Throwable) = Unit
}

class FirebaseCrashReporter private constructor(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {
    private val enabled = AtomicBoolean(false)
    private val keys = ConcurrentHashMap<CrashKey, String>()

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled.set(enabled)
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if (enabled) keys.forEach { (key, value) -> crashlytics.setCustomKey(key.wireName, value) }
        if (!enabled) crashlytics.deleteUnsentReports()
    }

    override fun setKey(key: CrashKey, value: String) {
        val safeValue = value.take(100)
        keys[key] = safeValue
        if (enabled.get()) crashlytics.setCustomKey(key.wireName, safeValue)
    }

    override fun recordUnexpected(error: Throwable) {
        if (enabled.get()) crashlytics.recordException(error)
    }

    companion object {
        fun createOrNoOp(context: Context): CrashReporter = runCatching {
            check(FirebaseApp.getApps(context.applicationContext).isNotEmpty())
            FirebaseCrashReporter(FirebaseCrashlytics.getInstance())
        }.getOrElse { NoOpCrashReporter }
    }
}
