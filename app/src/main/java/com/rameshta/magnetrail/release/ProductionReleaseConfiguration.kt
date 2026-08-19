package com.rameshta.magnetrail.release

data class ProductionReleaseConfiguration(
    val adMobAppId: String,
    val rewardedAdUnitId: String,
    val interstitialAdUnitId: String,
    val privacyPolicyUrl: String,
    val targetAudience: String,
    val liveAdsEnabled: Boolean,
    val firebaseConfigured: Boolean,
    val uploadSigningConfigured: Boolean,
)

object ProductionReleaseConfigurationValidator {
    private val appIdPattern = Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}$")
    private val unitIdPattern = Regex("^ca-app-pub-[0-9]{16}/[0-9]{10}$")
    private const val GOOGLE_SAMPLE_PUBLISHER = "3940256099942544"

    fun problems(configuration: ProductionReleaseConfiguration): List<String> = buildList {
        if (!configuration.liveAdsEnabled) add("live ads are not explicitly enabled")
        if (!appIdPattern.matches(configuration.adMobAppId) ||
            GOOGLE_SAMPLE_PUBLISHER in configuration.adMobAppId
        ) {
            add("AdMob App ID is missing, malformed, or a Google sample")
        }
        if (!validProductionUnit(configuration.rewardedAdUnitId)) {
            add("rewarded ad-unit ID is missing, malformed, or a Google test unit")
        }
        if (!validProductionUnit(configuration.interstitialAdUnitId)) {
            add("interstitial ad-unit ID is missing, malformed, or a Google test unit")
        }
        val policyUri = runCatching { java.net.URI(configuration.privacyPolicyUrl) }.getOrNull()
        if (policyUri?.scheme != "https" || policyUri.host.isNullOrBlank() || policyUri.userInfo != null) {
            add("privacy policy URL is not a safe HTTPS URL")
        }
        if (configuration.targetAudience != "general") add("target audience is not owner-reviewed general audience")
        if (!configuration.firebaseConfigured) add("Firebase production configuration is absent")
        if (!configuration.uploadSigningConfigured) add("upload signing configuration is absent")
    }

    private fun validProductionUnit(value: String): Boolean =
        unitIdPattern.matches(value) && GOOGLE_SAMPLE_PUBLISHER !in value
}
