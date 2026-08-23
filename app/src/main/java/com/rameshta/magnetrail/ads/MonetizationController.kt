package com.rameshta.magnetrail.ads

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.data.ProgressRepository
import com.rameshta.magnetrail.data.RewardedCreditGrantResult
import com.rameshta.magnetrail.data.RewardedSkipResult
import com.rameshta.magnetrail.data.RewardedSkipTarget
import com.rameshta.magnetrail.game.AppDestination
import com.rameshta.magnetrail.game.GameMode
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.privacy.PrivacyManager
import com.rameshta.magnetrail.crash.CrashKey
import com.rameshta.magnetrail.crash.CrashReporter
import com.rameshta.magnetrail.crash.NoOpCrashReporter
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

enum class RewardedOfferStatus { CREDIT_READY, AVAILABLE, LOADING, UNAVAILABLE }

data class RewardedOffer(
    val status: RewardedOfferStatus,
    val enabled: Boolean,
    val label: String,
    val supportingText: String,
)

class MonetizationController(
    private val repository: ProgressRepository,
    private val privacyManager: PrivacyManager,
    private val rewardedAdService: RewardedAdService,
    private val interstitialAdService: InterstitialAdService,
    private val coordinator: FullScreenAdCoordinator,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter = NoOpCrashReporter,
    private val clock: AdClock = SystemAdClock,
) {
    private val completionInterstitialInFlight = AtomicBoolean(false)
    private val rewardedSkipInFlight = AtomicBoolean(false)
    fun rewardedOffer(progress: PlayerProgress): RewardedOffer {
        val state = progress.monetization
        if (state.pendingAdHintTransactionId != null) return RewardedOffer(
            RewardedOfferStatus.CREDIT_READY,
            enabled = true,
            label = "Use earned ad hint",
            supportingText = "Your earned hint is ready.",
        )
        if (!privacyManager.state.value.canRequestAds) return unavailableOffer()
        return when (rewardedAdService.state.value) {
            RewardedAdState.READY -> RewardedOffer(
                RewardedOfferStatus.AVAILABLE,
                true,
                "Watch an ad for one hint",
                "Watch an ad to reveal one safe move.",
            )
            RewardedAdState.LOADING -> RewardedOffer(
                RewardedOfferStatus.LOADING,
                false,
                "Watch an ad for one hint",
                "No ad available right now",
            )
            else -> unavailableOffer()
        }
    }

    fun rewardedSkipOffer(): RewardedOffer {
        if (!privacyManager.state.value.canRequestAds) return RewardedOffer(
            RewardedOfferStatus.UNAVAILABLE,
            false,
            "Skip level with an ad",
            "No ad available right now",
        )
        return when (rewardedAdService.state.value) {
            RewardedAdState.READY -> RewardedOffer(
                RewardedOfferStatus.AVAILABLE,
                true,
                "Skip level with an ad",
                "Watch an ad to skip this level and receive 10 coins.",
            )
            RewardedAdState.LOADING -> RewardedOffer(
                RewardedOfferStatus.LOADING,
                false,
                "Skip level with an ad",
                "Ad is loading",
            )
            else -> RewardedOffer(
                RewardedOfferStatus.UNAVAILABLE,
                false,
                "Skip level with an ad",
                "No ad available right now",
            )
        }
    }

    suspend fun requestRewardedHint(
        activity: Activity,
        uiState: GameUiState,
        onCreditReady: (String) -> Unit,
        onMessage: (String) -> Unit,
    ) {
        val offer = rewardedOffer(uiState.progress)
        analytics.track(AnalyticsEvent.RewardedOffer(offer.status.name.lowercase()))
        val pending = uiState.progress.monetization.pendingAdHintTransactionId
        if (offer.status == RewardedOfferStatus.CREDIT_READY && pending != null) {
            onCreditReady(pending)
            return
        }
        if (!offer.enabled || !activity.isResumed() || uiState.destination != AppDestination.GAME || uiState.isComplete) {
            onMessage(offer.supportingText)
            return
        }
        when (val outcome = rewardedAdService.showForHint(activity)) {
            is RewardedOutcome.Earned -> {
                repository.recordFullScreenAdDismissal(clock.localDate(), clock.wallTimeMillis(), interstitialShown = false)
                when (repository.grantRewardedHintCredit(outcome.transactionId, clock.localDate())) {
                    RewardedCreditGrantResult.Granted, RewardedCreditGrantResult.Duplicate -> onCreditReady(outcome.transactionId)
                    else -> onMessage("No ad available right now")
                }
            }
            RewardedOutcome.DismissedWithoutReward -> {
                repository.recordFullScreenAdDismissal(clock.localDate(), clock.wallTimeMillis(), interstitialShown = false)
                onMessage("No ad reward was earned")
            }
            is RewardedOutcome.Failed, is RewardedOutcome.Unavailable -> onMessage("No ad available right now")
        }
    }

    suspend fun requestRewardedSkip(
        activity: Activity,
        uiState: GameUiState,
        onGranted: (RewardedSkipResult.Applied) -> Unit,
        onMessage: (String) -> Unit,
    ) {
        if (!rewardedSkipInFlight.compareAndSet(false, true)) return
        try {
            val offer = rewardedSkipOffer()
            val mode = uiState.gameMode.name.lowercase()
            analytics.track(AnalyticsEvent.RewardedSkip("requested_${offer.status.name.lowercase()}", mode))
            if (!offer.enabled || !activity.isResumed() || uiState.destination != AppDestination.GAME ||
                uiState.gameMode !in setOf(GameMode.CAMPAIGN, GameMode.INFINITE) ||
                uiState.isComplete || uiState.inFlightResult != null
            ) {
                analytics.track(AnalyticsEvent.RewardedSkip("denied", mode))
                onMessage(offer.supportingText)
                return
            }
            when (val outcome = rewardedAdService.showForSkip(activity)) {
                is RewardedOutcome.Earned -> {
                    repository.recordFullScreenAdDismissal(
                        clock.localDate(),
                        clock.wallTimeMillis(),
                        interstitialShown = false,
                    )
                    val target = when (uiState.gameMode) {
                        GameMode.CAMPAIGN -> RewardedSkipTarget.Campaign(uiState.currentLevel.id)
                        GameMode.INFINITE -> RewardedSkipTarget.Infinite(
                            requireNotNull(uiState.infinitePuzzleId) { "Infinite skip requires a puzzle ID" },
                        )
                        GameMode.DAILY -> error("Daily Challenge cannot be skipped")
                        GameMode.PLAYTEST -> error("Human playtest boards cannot be skipped with ads")
                    }
                    when (val result = repository.recordRewardedSkip(outcome.transactionId, target)) {
                        is RewardedSkipResult.Applied -> {
                            analytics.track(AnalyticsEvent.RewardedSkip("granted", mode))
                            onGranted(result)
                        }
                        RewardedSkipResult.Duplicate -> {
                            analytics.track(AnalyticsEvent.RewardedSkip("duplicate", mode))
                            onMessage("This skip reward was already applied")
                        }
                    }
                }
                RewardedOutcome.DismissedWithoutReward -> {
                    repository.recordFullScreenAdDismissal(
                        clock.localDate(),
                        clock.wallTimeMillis(),
                        interstitialShown = false,
                    )
                    analytics.track(AnalyticsEvent.RewardedSkip("dismissed", mode))
                    onMessage("Finish the ad to skip this level")
                }
                is RewardedOutcome.Failed, is RewardedOutcome.Unavailable -> {
                    analytics.track(AnalyticsEvent.RewardedSkip("unavailable", mode))
                    onMessage("No ad available right now")
                }
            }
        } finally {
            rewardedSkipInFlight.set(false)
        }
    }

    suspend fun showInterstitialForCompletion(
        activity: Activity,
        uiState: GameUiState,
    ) {
        if (!completionInterstitialInFlight.compareAndSet(false, true)) return
        try {
            val progress = repository.preferences.first().progress
            val input = InterstitialPolicyInput(
                campaign = uiState.gameMode == GameMode.CAMPAIGN,
                infinite = uiState.gameMode == GameMode.INFINITE && !uiState.isAutoJourney,
                forwardProgression = uiState.completionWasFirstClear,
                eligibleCompletionsSinceLastAd = progress.monetization.interstitialEligibleCompletions,
                nowElapsedMillis = clock.elapsedRealtimeMillis(),
                lastRewardedElapsedMillis = coordinator.lastRewardedElapsedMillis,
                consentAllowsAds = privacyManager.state.value.canRequestAds,
                loaded = interstitialAdService.state.value == InterstitialAdState.READY,
                foreground = activity.isResumed(),
                expectedCompletionScreen = uiState.destination == AppDestination.GAME && uiState.isComplete,
                fullScreenIdle = coordinator.isIdle(),
                autoJourney = uiState.isAutoJourney,
            )
            val decision = InterstitialPolicy.evaluate(input)
            crashReporter.setKey(CrashKey.LAST_AD_POLICY_REASON, decision.reason.name.lowercase())
            crashReporter.setKey(CrashKey.AD_STATE, interstitialAdService.state.value.name.lowercase())
            analytics.track(
                AnalyticsEvent.InterstitialEligible(
                    decision.reason.name.lowercase(),
                    if (decision.eligible) "show" else "skip",
                ),
            )
            val claimed = if (
                progress.monetization.interstitialEligibleCompletions >= InterstitialPolicy.COMPLETION_GAP
            ) {
                repository.claimInterstitialOpportunity()
            } else {
                false
            }
            if (decision.eligible && claimed) {
                when (interstitialAdService.showAtBoundary(activity)) {
                    InterstitialOutcome.Dismissed -> repository.recordFullScreenAdDismissal(
                        clock.localDate(),
                        clock.wallTimeMillis(),
                        interstitialShown = true,
                    )
                    is InterstitialOutcome.Failed, is InterstitialOutcome.Unavailable -> Unit
                }
            }
        } finally {
            completionInterstitialInFlight.set(false)
        }
    }

    private fun unavailableOffer() = RewardedOffer(
        RewardedOfferStatus.UNAVAILABLE,
        false,
        "Watch an ad for one hint",
        "No ad available right now",
    )

    private fun Activity.isResumed(): Boolean =
        (this as? ComponentActivity)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
}
