package com.rameshta.magnetrail.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.concurrent.thread

class GoogleAdInitializer(
    context: Context,
    private val configuration: AdConfiguration,
    private val afterInitialization: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initializeOnce() {
        if (!configuration.enabled || !initialized.compareAndSet(false, true)) return
        mainHandler.post {
            // Leaving age-treatment fields unset is the SDK's UNSPECIFIED state until the owner
            // records a target-audience decision. Release live ads remain blocked in that state.
            val requestConfiguration = RequestConfiguration.Builder().build()
            MobileAds.setRequestConfiguration(requestConfiguration)
            thread(name = "magnetrail-mobile-ads-init", isDaemon = true) {
                MobileAds.initialize(appContext) { afterInitialization() }
            }
        }
    }
}

class GoogleRewardedAdService(
    context: Context,
    private val configuration: AdConfiguration,
    private val canRequestAds: () -> Boolean,
    private val coordinator: FullScreenAdCoordinator,
    private val analytics: AnalyticsTracker,
) : RewardedAdService {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(RewardedAdState.BLOCKED)
    override val state: StateFlow<RewardedAdState> = mutableState.asStateFlow()
    private var loadedAd: RewardedAd? = null
    private var loading = false

    override fun preloadIfAllowed() {
        mainHandler.post {
            if (!configuration.enabled || !canRequestAds()) {
                mutableState.value = RewardedAdState.BLOCKED
                return@post
            }
            if (loading || loadedAd != null || mutableState.value == RewardedAdState.SHOWING) return@post
            loading = true
            mutableState.value = RewardedAdState.LOADING
            RewardedAd.load(
                appContext,
                configuration.rewardedAdUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        loading = false
                        loadedAd = ad
                        mutableState.value = RewardedAdState.READY
                        analytics.track(AnalyticsEvent.RewardedLoadResult("loaded"))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loading = false
                        loadedAd = null
                        mutableState.value = RewardedAdState.UNAVAILABLE
                        analytics.track(AnalyticsEvent.RewardedLoadResult(error.coarseCategory()))
                    }
                },
            )
        }
    }

    override suspend fun showForHint(activity: Activity): RewardedOutcome = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            if (!configuration.enabled || !canRequestAds()) {
                continuation.resume(RewardedOutcome.Unavailable("consent_blocked"))
                return@post
            }
            val ad = loadedAd
            if (ad == null) {
                continuation.resume(RewardedOutcome.Unavailable("not_loaded"))
                preloadIfAllowed()
                return@post
            }
            if (!coordinator.tryAcquire(FullScreenOwner.REWARDED)) {
                continuation.resume(RewardedOutcome.Unavailable("full_screen_busy"))
                return@post
            }
            loadedAd = null
            mutableState.value = RewardedAdState.SHOWING
            val transactionId = UUID.randomUUID().toString()
            val rewardLedger = RewardedCallbackLedger(transactionId)
            val completed = AtomicBoolean(false)
            fun finish(outcome: RewardedOutcome, shown: Boolean) {
                if (!completed.compareAndSet(false, true)) return
                coordinator.release(FullScreenOwner.REWARDED, shown)
                mutableState.value = RewardedAdState.UNAVAILABLE
                if (continuation.isActive) continuation.resume(outcome)
                preloadIfAllowed()
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    analytics.track(AnalyticsEvent.RewardedShow)
                }

                override fun onAdDismissedFullScreenContent() {
                    val outcome = rewardLedger.dismiss()
                    analytics.track(AnalyticsEvent.RewardedDismiss(outcome is RewardedOutcome.Earned))
                    finish(outcome, shown = true)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    val category = error.coarseCategory()
                    analytics.track(AnalyticsEvent.AdShowFailure("rewarded", category))
                    finish(RewardedOutcome.Failed(category), shown = false)
                }
            }
            runCatching {
                ad.show(activity) {
                    if (rewardLedger.rewardCallback()) analytics.track(AnalyticsEvent.RewardedEarned)
                }
            }.onFailure { finish(RewardedOutcome.Failed("sdk_exception"), shown = false) }
        }
    }

    override fun clear() {
        mainHandler.post {
            loadedAd = null
            loading = false
            mutableState.value = RewardedAdState.BLOCKED
        }
    }
}

class GoogleInterstitialAdService(
    context: Context,
    private val configuration: AdConfiguration,
    private val canRequestAds: () -> Boolean,
    private val coordinator: FullScreenAdCoordinator,
    private val analytics: AnalyticsTracker,
) : InterstitialAdService {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(InterstitialAdState.BLOCKED)
    override val state: StateFlow<InterstitialAdState> = mutableState.asStateFlow()
    private var loadedAd: InterstitialAd? = null
    private var loading = false

    override fun preloadIfAllowed() {
        mainHandler.post {
            if (!configuration.enabled || !canRequestAds()) {
                mutableState.value = InterstitialAdState.BLOCKED
                return@post
            }
            if (loading || loadedAd != null || mutableState.value == InterstitialAdState.SHOWING) return@post
            loading = true
            mutableState.value = InterstitialAdState.LOADING
            InterstitialAd.load(
                appContext,
                configuration.interstitialAdUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loading = false
                        loadedAd = ad
                        mutableState.value = InterstitialAdState.READY
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loading = false
                        loadedAd = null
                        mutableState.value = InterstitialAdState.UNAVAILABLE
                    }
                },
            )
        }
    }

    override suspend fun showAtBoundary(activity: Activity): InterstitialOutcome =
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                if (!configuration.enabled || !canRequestAds()) {
                    continuation.resume(InterstitialOutcome.Unavailable("consent_blocked"))
                    return@post
                }
                val ad = loadedAd
                if (ad == null) {
                    continuation.resume(InterstitialOutcome.Unavailable("not_loaded"))
                    preloadIfAllowed()
                    return@post
                }
                if (!coordinator.tryAcquire(FullScreenOwner.INTERSTITIAL)) {
                    continuation.resume(InterstitialOutcome.Unavailable("full_screen_busy"))
                    return@post
                }
                loadedAd = null
                mutableState.value = InterstitialAdState.SHOWING
                val completed = AtomicBoolean(false)
                fun finish(outcome: InterstitialOutcome, shown: Boolean) {
                    if (!completed.compareAndSet(false, true)) return
                    coordinator.release(FullScreenOwner.INTERSTITIAL, shown)
                    mutableState.value = InterstitialAdState.UNAVAILABLE
                    if (continuation.isActive) continuation.resume(outcome)
                    preloadIfAllowed()
                }
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        analytics.track(AnalyticsEvent.InterstitialShow)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        analytics.track(AnalyticsEvent.InterstitialDismiss)
                        finish(InterstitialOutcome.Dismissed, shown = true)
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        val category = error.coarseCategory()
                        analytics.track(AnalyticsEvent.AdShowFailure("interstitial", category))
                        finish(InterstitialOutcome.Failed(category), shown = false)
                    }
                }
                runCatching { ad.show(activity) }
                    .onFailure { finish(InterstitialOutcome.Failed("sdk_exception"), shown = false) }
            }
        }

    override fun clear() {
        mainHandler.post {
            loadedAd = null
            loading = false
            mutableState.value = InterstitialAdState.BLOCKED
        }
    }
}

class RewardedCallbackLedger(private val transactionId: String) {
    private val rewarded = AtomicBoolean(false)

    fun rewardCallback(): Boolean = rewarded.compareAndSet(false, true)

    fun dismiss(): RewardedOutcome = if (rewarded.get()) {
        RewardedOutcome.Earned(transactionId)
    } else {
        RewardedOutcome.DismissedWithoutReward
    }
}

private fun AdError.coarseCategory(): String = when (code) {
    0 -> "internal"
    1 -> "invalid_request"
    2 -> "network"
    3 -> "no_fill"
    else -> "other"
}
