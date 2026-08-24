package com.rameshta.magnetrail

import android.content.Context
import com.rameshta.magnetrail.ads.AdClock
import com.rameshta.magnetrail.ads.AdConfiguration
import com.rameshta.magnetrail.ads.AppOpenAdService
import com.rameshta.magnetrail.ads.FullScreenAdCoordinator
import com.rameshta.magnetrail.ads.GoogleAdInitializer
import com.rameshta.magnetrail.ads.GoogleAppOpenAdService
import com.rameshta.magnetrail.ads.GoogleInterstitialAdService
import com.rameshta.magnetrail.ads.GoogleRewardedAdService
import com.rameshta.magnetrail.ads.InterstitialAdService
import com.rameshta.magnetrail.ads.NoOpAppOpenAdService
import com.rameshta.magnetrail.ads.NoOpInterstitialAdService
import com.rameshta.magnetrail.ads.NoOpRewardedAdService
import com.rameshta.magnetrail.ads.RewardedAdService
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.privacy.NoOpPrivacyManager
import com.rameshta.magnetrail.privacy.PrivacyManager
import com.rameshta.magnetrail.privacy.UmpPrivacyManager

internal fun createVariantMonetizationServices(
    context: Context,
    configuration: AdConfiguration,
    automatedTest: Boolean,
    coordinator: FullScreenAdCoordinator,
    analytics: AnalyticsTracker,
    clock: AdClock,
): VariantMonetizationServices {
    lateinit var privacyManager: PrivacyManager
    val rewarded: RewardedAdService
    val interstitial: InterstitialAdService
    val appOpen: AppOpenAdService
    if (configuration.enabled) {
        rewarded = GoogleRewardedAdService(
            context,
            configuration,
            canRequestAds = { privacyManager.state.value.canRequestAds },
            coordinator = coordinator,
            analytics = analytics,
        )
        interstitial = GoogleInterstitialAdService(
            context,
            configuration,
            canRequestAds = { privacyManager.state.value.canRequestAds },
            coordinator = coordinator,
            analytics = analytics,
        )
        appOpen = GoogleAppOpenAdService(
            context,
            configuration,
            canRequestAds = { privacyManager.state.value.canRequestAds },
            coordinator = coordinator,
            analytics = analytics,
            clock = clock,
        )
    } else {
        rewarded = NoOpRewardedAdService()
        interstitial = NoOpInterstitialAdService()
        appOpen = NoOpAppOpenAdService()
    }
    val initializer = GoogleAdInitializer(context, configuration) {
        rewarded.preloadIfAllowed()
        interstitial.preloadIfAllowed()
        appOpen.preloadIfAllowed()
    }
    privacyManager = if (automatedTest || !configuration.enabled) {
        NoOpPrivacyManager()
    } else {
        UmpPrivacyManager(
            context = context,
            fullScreenCoordinator = coordinator,
            onAdsPermitted = initializer::initializeOnce,
            onResult = { analytics.track(AnalyticsEvent.ConsentFlowResult(it.name.lowercase())) },
        )
    }
    return VariantMonetizationServices(
        privacyManager = privacyManager,
        rewarded = rewarded,
        interstitial = interstitial,
        appOpen = appOpen,
    )
}
