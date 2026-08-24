package com.rameshta.magnetrail

import android.app.Application
import com.rameshta.magnetrail.ads.AdConfiguration
import com.rameshta.magnetrail.ads.AppOpenAdService
import com.rameshta.magnetrail.ads.ForegroundAdClock
import com.rameshta.magnetrail.ads.FullScreenAdCoordinator
import com.rameshta.magnetrail.ads.InterstitialAdService
import com.rameshta.magnetrail.ads.RewardedAdService
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.analytics.FirebaseAnalyticsTracker
import com.rameshta.magnetrail.analytics.NoOpAnalyticsTracker
import com.rameshta.magnetrail.crash.CrashKey
import com.rameshta.magnetrail.crash.CrashReporter
import com.rameshta.magnetrail.crash.FirebaseCrashReporter
import com.rameshta.magnetrail.crash.NoOpCrashReporter
import com.rameshta.magnetrail.privacy.ObservabilityController
import com.rameshta.magnetrail.privacy.PrivacyManager
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.generation.v5.CAMPAIGN_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.release.ProductionReleaseConfiguration
import com.rameshta.magnetrail.release.ProductionReleaseConfigurationValidator

class MagnetrailApplication : Application() {
    lateinit var m4Services: M4Services
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.PRODUCTION_RELEASE_REQUESTED) {
            val problems = ProductionReleaseConfigurationValidator.problems(
                ProductionReleaseConfiguration(
                    adMobAppId = BuildConfig.ADMOB_APP_ID,
                    rewardedAdUnitId = BuildConfig.REWARDED_AD_UNIT_ID,
                    interstitialAdUnitId = BuildConfig.INTERSTITIAL_AD_UNIT_ID,
                    appOpenAdUnitId = BuildConfig.APP_OPEN_AD_UNIT_ID,
                    privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL,
                    targetAudience = BuildConfig.TARGET_AUDIENCE,
                    liveAdsEnabled = BuildConfig.MONETIZATION_ENABLED,
                    firebaseConfigured = BuildConfig.FIREBASE_CONFIGURED,
                    uploadSigningConfigured = BuildConfig.UPLOAD_SIGNING_CONFIGURED,
                ),
            )
            check(problems.isEmpty()) { "Unsafe production release configuration" }
        }
        val automatedTest = isRunningInstrumentedTest()
        val configuration = AdConfiguration.fromBuild().let { config ->
            if (automatedTest) config.copy(enabled = false, mode = "automated_test") else config
        }
        val analytics = if (automatedTest) NoOpAnalyticsTracker else FirebaseAnalyticsTracker.createOrNoOp(this)
        val crashReporter = if (automatedTest) NoOpCrashReporter else FirebaseCrashReporter.createOrNoOp(this)
        val clock = ForegroundAdClock()
        val coordinator = FullScreenAdCoordinator(clock)
        val monetization = createVariantMonetizationServices(
            context = this,
            configuration = configuration,
            automatedTest = automatedTest,
            coordinator = coordinator,
            analytics = analytics,
            clock = clock,
        )
        crashReporter.setKey(CrashKey.APP_VERSION, BuildConfig.VERSION_NAME)
        crashReporter.setKey(CrashKey.ENGINE_VERSION, "magnetrail-core-1")
        crashReporter.setKey(CrashKey.CONTENT_VERSION, CAMPAIGN_CONTENT_VERSION.toString())
        crashReporter.setKey(CrashKey.GENERATOR_VERSION, GENERATOR_VERSION_V5.toString())
        crashReporter.setKey(CrashKey.ECONOMY_VERSION, EconomyConfig.VERSION.toString())
        m4Services = M4Services(
            configuration = configuration,
            analytics = analytics,
            crashReporter = crashReporter,
            observability = ObservabilityController(analytics, crashReporter),
            coordinator = coordinator,
            privacyManager = monetization.privacyManager,
            rewardedAdService = monetization.rewarded,
            interstitialAdService = monetization.interstitial,
            appOpenAdService = monetization.appOpen,
            clock = clock,
        )
    }

    private fun isRunningInstrumentedTest(): Boolean = runCatching {
        val registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        registry.getMethod("getInstrumentation").invoke(null) != null
    }.getOrDefault(false)
}

internal data class VariantMonetizationServices(
    val privacyManager: PrivacyManager,
    val rewarded: RewardedAdService,
    val interstitial: InterstitialAdService,
    val appOpen: AppOpenAdService,
)

data class M4Services(
    val configuration: AdConfiguration,
    val analytics: AnalyticsTracker,
    val crashReporter: CrashReporter,
    val observability: ObservabilityController,
    val coordinator: FullScreenAdCoordinator,
    val privacyManager: PrivacyManager,
    val rewardedAdService: RewardedAdService,
    val interstitialAdService: InterstitialAdService,
    val appOpenAdService: AppOpenAdService,
    val clock: ForegroundAdClock,
)
