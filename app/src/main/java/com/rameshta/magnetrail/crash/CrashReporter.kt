package com.rameshta.magnetrail.crash

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
