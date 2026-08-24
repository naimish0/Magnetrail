package com.rameshta.magnetrail.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class GoogleAppOpenAdService(
    context: Context,
    private val configuration: AdConfiguration,
    private val canRequestAds: () -> Boolean,
    private val coordinator: FullScreenAdCoordinator,
    private val analytics: AnalyticsTracker,
    private val clock: AdClock,
    private val cooldownStore: AppOpenCooldownStore = SharedPreferencesAppOpenCooldownStore(context),
) : AppOpenAdService {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(AppOpenAdState.BLOCKED)
    override val state: StateFlow<AppOpenAdState> = mutableState.asStateFlow()
    private var loadedAd: AppOpenAd? = null
    private var loadedAtWallTimeMillis: Long? = null
    private var loading = false
    private var loadGeneration = 0L

    override fun preloadIfAllowed() {
        mainHandler.post {
            if (!configuration.enabled || !canRequestAds()) {
                clearLoadedAd(AppOpenAdState.BLOCKED)
                return@post
            }
            if (loadedAd != null && isLoadedAdFresh()) {
                mutableState.value = AppOpenAdState.READY
                return@post
            }
            if (loadedAd != null) clearLoadedAd(AppOpenAdState.UNAVAILABLE)
            if (loading || mutableState.value == AppOpenAdState.SHOWING) return@post
            loading = true
            mutableState.value = AppOpenAdState.LOADING
            val generation = ++loadGeneration
            AppOpenAd.load(
                appContext,
                configuration.appOpenAdUnitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        if (generation != loadGeneration) return
                        loading = false
                        loadedAd = ad
                        loadedAtWallTimeMillis = clock.wallTimeMillis()
                        mutableState.value = AppOpenAdState.READY
                        analytics.track(AnalyticsEvent.AppOpenLoadResult("loaded"))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (generation != loadGeneration) return
                        loading = false
                        clearLoadedAd(AppOpenAdState.UNAVAILABLE)
                        analytics.track(AnalyticsEvent.AppOpenLoadResult(error.coarseCategory()))
                    }
                },
            )
        }
    }

    override suspend fun showIfEligible(activity: Activity): AppOpenOutcome =
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                val decision = AppOpenAdPolicy.evaluate(
                    AppOpenPolicyInput(
                        nowWallTimeMillis = clock.wallTimeMillis(),
                        lastShownWallTimeMillis = cooldownStore.lastShownWallTimeMillis(),
                        loadedAtWallTimeMillis = loadedAtWallTimeMillis.takeIf { loadedAd != null },
                        nowElapsedMillis = clock.elapsedRealtimeMillis(),
                        lastFullScreenDismissedElapsedMillis = coordinator.lastDismissedElapsedMillis,
                        consentAllowsAds = configuration.enabled && canRequestAds(),
                        foreground = activity.isResumed(),
                        fullScreenIdle = coordinator.isIdle(),
                    ),
                )
                analytics.track(
                    AnalyticsEvent.AppOpenEligible(
                        reason = decision.reason.name.lowercase(),
                        outcome = if (decision.eligible) "show" else "skip",
                    ),
                )
                if (!decision.eligible) {
                    when (decision.reason) {
                        AppOpenReason.CONSENT_BLOCKED -> clearLoadedAd(AppOpenAdState.BLOCKED)
                        AppOpenReason.STALE_AD -> clearLoadedAd(AppOpenAdState.UNAVAILABLE)
                        else -> Unit
                    }
                    if (decision.reason in setOf(AppOpenReason.NOT_LOADED, AppOpenReason.STALE_AD)) {
                        preloadIfAllowed()
                    }
                    continuation.resume(AppOpenOutcome.Unavailable(decision.reason.name.lowercase()))
                    return@post
                }
                val ad = loadedAd
                if (ad == null) {
                    continuation.resume(AppOpenOutcome.Unavailable("not_loaded"))
                    preloadIfAllowed()
                    return@post
                }
                if (!coordinator.tryAcquire(FullScreenOwner.APP_OPEN)) {
                    continuation.resume(AppOpenOutcome.Unavailable("full_screen_busy"))
                    return@post
                }
                loadedAd = null
                loadedAtWallTimeMillis = null
                mutableState.value = AppOpenAdState.SHOWING
                val completed = AtomicBoolean(false)
                fun finish(outcome: AppOpenOutcome, completedSuccessfully: Boolean) {
                    if (!completed.compareAndSet(false, true)) return
                    coordinator.release(FullScreenOwner.APP_OPEN, completedSuccessfully)
                    mutableState.value = AppOpenAdState.UNAVAILABLE
                    if (continuation.isActive) continuation.resume(outcome)
                    preloadIfAllowed()
                }
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        cooldownStore.recordShown(clock.wallTimeMillis())
                        analytics.track(AnalyticsEvent.AppOpenShow)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        analytics.track(AnalyticsEvent.AppOpenDismiss)
                        finish(AppOpenOutcome.Dismissed, completedSuccessfully = true)
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        val category = error.coarseCategory()
                        analytics.track(AnalyticsEvent.AdShowFailure("app_open", category))
                        finish(AppOpenOutcome.Failed(category), completedSuccessfully = false)
                    }
                }
                runCatching { ad.show(activity) }
                    .onFailure { finish(AppOpenOutcome.Failed("sdk_exception"), completedSuccessfully = false) }
            }
        }

    override fun clear() {
        mainHandler.post {
            loading = false
            clearLoadedAd(AppOpenAdState.BLOCKED)
        }
    }

    private fun isLoadedAdFresh(): Boolean {
        val loadedAt = loadedAtWallTimeMillis ?: return false
        val age = clock.wallTimeMillis() - loadedAt
        return age in 0 until AppOpenAdPolicy.MAX_AD_AGE_MILLIS
    }

    private fun clearLoadedAd(state: AppOpenAdState) {
        loadGeneration += 1
        loading = false
        loadedAd = null
        loadedAtWallTimeMillis = null
        mutableState.value = state
    }

    private fun Activity.isResumed(): Boolean =
        (this as? ComponentActivity)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
}
