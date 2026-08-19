package com.rameshta.magnetrail.ads

import com.rameshta.magnetrail.BuildConfig

data class AdConfiguration(
    val enabled: Boolean,
    val mode: String,
    val rewardedAdUnitId: String,
    val interstitialAdUnitId: String,
) {
    val isTestConfiguration: Boolean get() = mode == "google_test"

    companion object {
        fun fromBuild(): AdConfiguration = AdConfiguration(
            enabled = BuildConfig.MONETIZATION_ENABLED,
            mode = BuildConfig.AD_CONFIGURATION_MODE,
            rewardedAdUnitId = BuildConfig.REWARDED_AD_UNIT_ID,
            interstitialAdUnitId = BuildConfig.INTERSTITIAL_AD_UNIT_ID,
        )
    }
}
