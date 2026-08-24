package com.rameshta.magnetrail

import android.content.Context
import com.rameshta.magnetrail.ads.AdClock
import com.rameshta.magnetrail.ads.AdConfiguration
import com.rameshta.magnetrail.ads.FullScreenAdCoordinator
import com.rameshta.magnetrail.ads.NoOpAppOpenAdService
import com.rameshta.magnetrail.ads.NoOpInterstitialAdService
import com.rameshta.magnetrail.ads.NoOpRewardedAdService
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.privacy.NoOpPrivacyManager

@Suppress("UNUSED_PARAMETER")
internal fun createVariantMonetizationServices(
    context: Context,
    configuration: AdConfiguration,
    automatedTest: Boolean,
    coordinator: FullScreenAdCoordinator,
    analytics: AnalyticsTracker,
    clock: AdClock,
): VariantMonetizationServices = VariantMonetizationServices(
    privacyManager = NoOpPrivacyManager(),
    rewarded = NoOpRewardedAdService(),
    interstitial = NoOpInterstitialAdService(),
    appOpen = NoOpAppOpenAdService(),
)
