package com.rameshta.magnetrail

import android.app.Application
import com.rameshta.magnetrail.ads.AdConfiguration
import com.rameshta.magnetrail.ads.FullScreenAdCoordinator
import com.rameshta.magnetrail.ads.GoogleAdInitializer
import com.rameshta.magnetrail.ads.GoogleInterstitialAdService
import com.rameshta.magnetrail.ads.GoogleRewardedAdService
import com.rameshta.magnetrail.ads.InterstitialAdService
import com.rameshta.magnetrail.ads.NoOpInterstitialAdService
import com.rameshta.magnetrail.ads.NoOpRewardedAdService
import com.rameshta.magnetrail.ads.RewardedAdService
import com.rameshta.magnetrail.ads.ForegroundAdClock
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.analytics.FirebaseAnalyticsTracker
import com.rameshta.magnetrail.analytics.NoOpAnalyticsTracker
import com.rameshta.magnetrail.crash.CrashKey
import com.rameshta.magnetrail.crash.CrashReporter
import com.rameshta.magnetrail.crash.FirebaseCrashReporter
import com.rameshta.magnetrail.crash.NoOpCrashReporter
import com.rameshta.magnetrail.privacy.ObservabilityController
import com.rameshta.magnetrail.privacy.PrivacyManager
import com.rameshta.magnetrail.privacy.UmpPrivacyManager
import com.rameshta.magnetrail.privacy.NoOpPrivacyManager
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.generation.CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.GENERATOR_VERSION

class MagnetrailApplication : Application() {
    lateinit var m4Services: M4Services
        private set

    override fun onCreate() {
        super.onCreate()
        val automatedTest = isRunningInstrumentedTest()
        val configuration = AdConfiguration.fromBuild().let { config ->
            if (automatedTest) config.copy(enabled = false, mode = "automated_test") else config
        }
        val analytics = if (automatedTest) NoOpAnalyticsTracker else FirebaseAnalyticsTracker.createOrNoOp(this)
        val crashReporter = if (automatedTest) NoOpCrashReporter else FirebaseCrashReporter.createOrNoOp(this)
        val clock = ForegroundAdClock()
        val coordinator = FullScreenAdCoordinator(clock)
        lateinit var privacyManager: PrivacyManager
        val rewarded: RewardedAdService
        val interstitial: InterstitialAdService
        if (configuration.enabled) {
            rewarded = GoogleRewardedAdService(
                this,
                configuration,
                canRequestAds = { privacyManager.state.value.canRequestAds },
                coordinator = coordinator,
                analytics = analytics,
            )
            interstitial = GoogleInterstitialAdService(
                this,
                configuration,
                canRequestAds = { privacyManager.state.value.canRequestAds },
                coordinator = coordinator,
                analytics = analytics,
            )
        } else {
            rewarded = NoOpRewardedAdService()
            interstitial = NoOpInterstitialAdService()
        }
        val initializer = GoogleAdInitializer(this, configuration) {
            rewarded.preloadIfAllowed()
            interstitial.preloadIfAllowed()
        }
        privacyManager = if (automatedTest) {
            NoOpPrivacyManager()
        } else {
            UmpPrivacyManager(
                context = this,
                fullScreenCoordinator = coordinator,
                onAdsPermitted = initializer::initializeOnce,
                onResult = { analytics.track(AnalyticsEvent.ConsentFlowResult(it.name.lowercase())) },
            )
        }
        crashReporter.setKey(CrashKey.APP_VERSION, BuildConfig.VERSION_NAME)
        crashReporter.setKey(CrashKey.ENGINE_VERSION, "magnetrail-core-1")
        crashReporter.setKey(CrashKey.CONTENT_VERSION, CONTENT_VERSION.toString())
        crashReporter.setKey(CrashKey.GENERATOR_VERSION, GENERATOR_VERSION.toString())
        crashReporter.setKey(CrashKey.ECONOMY_VERSION, EconomyConfig.VERSION.toString())
        m4Services = M4Services(
            configuration = configuration,
            analytics = analytics,
            crashReporter = crashReporter,
            observability = ObservabilityController(analytics, crashReporter),
            coordinator = coordinator,
            privacyManager = privacyManager,
            rewardedAdService = rewarded,
            interstitialAdService = interstitial,
            clock = clock,
        )
    }

    private fun isRunningInstrumentedTest(): Boolean = runCatching {
        val registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        registry.getMethod("getInstrumentation").invoke(null) != null
    }.getOrDefault(false)
}

data class M4Services(
    val configuration: AdConfiguration,
    val analytics: AnalyticsTracker,
    val crashReporter: CrashReporter,
    val observability: ObservabilityController,
    val coordinator: FullScreenAdCoordinator,
    val privacyManager: PrivacyManager,
    val rewardedAdService: RewardedAdService,
    val interstitialAdService: InterstitialAdService,
    val clock: ForegroundAdClock,
)
