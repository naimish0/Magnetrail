package com.rameshta.magnetrail.ads

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.data.ProgressRepository
import com.rameshta.magnetrail.data.RewardedCreditGrantResult
import com.rameshta.magnetrail.game.AppDestination
import com.rameshta.magnetrail.game.GameMode
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.privacy.PrivacyManager
import com.rameshta.magnetrail.crash.CrashKey
import com.rameshta.magnetrail.crash.CrashReporter
import com.rameshta.magnetrail.crash.NoOpCrashReporter
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

enum class RewardedOfferStatus { CREDIT_READY, AVAILABLE, LOADING, UNAVAILABLE, DAILY_CAP, DATE_ROLLBACK }

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
    private val nextLevelInFlight = AtomicBoolean(false)
    fun rewardedOffer(progress: PlayerProgress): RewardedOffer {
        val state = progress.monetization
        if (state.pendingAdHintTransactionId != null) return RewardedOffer(
            RewardedOfferStatus.CREDIT_READY,
            enabled = true,
            label = "Use earned ad hint",
            supportingText = "Your earned hint is ready.",
        )
        val today = clock.localDate()
        val storedDate = state.rewardedGrantDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
        if (storedDate != null && today.isBefore(storedDate)) return RewardedOffer(
            RewardedOfferStatus.DATE_ROLLBACK,
            false,
            "Watch an ad for one hint",
            "No ad available right now",
        )
        if (storedDate == today && state.rewardedGrantsOnDate >= 5) return RewardedOffer(
            RewardedOfferStatus.DAILY_CAP,
            false,
            "Watch an ad for one hint",
            "More ad hints available tomorrow",
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
                    RewardedCreditGrantResult.DailyCapReached -> onMessage("More ad hints available tomorrow")
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

    suspend fun nextLevel(
        activity: Activity,
        uiState: GameUiState,
        navigate: () -> Unit,
    ) {
        if (!nextLevelInFlight.compareAndSet(false, true)) return
        try {
        val progress = repository.preferences.first().progress
        val storedDate = progress.monetization.lastFullScreenAdDate?.let { value ->
            runCatching { LocalDate.parse(value) }.getOrNull()
        }
        val input = InterstitialPolicyInput(
            campaign = uiState.gameMode == GameMode.CAMPAIGN,
            lifetimeCampaignCompletions = progress.completedLevelIds.size +
                if (uiState.completionWasFirstClear && uiState.currentLevel.id !in progress.completedLevelIds) 1 else 0,
            forwardProgression = uiState.completionWasFirstClear,
            eligibleCompletionsSinceLastAd = progress.monetization.interstitialEligibleCompletions +
                if (uiState.completionWasFirstClear &&
                    uiState.currentLevel.id !in progress.firstClearRewardedLevelIds
                ) 1 else 0,
            nowDate = clock.localDate(),
            storedDailyDate = storedDate,
            interstitialsShownOnStoredDate = progress.monetization.interstitialsShownOnDate,
            nowWallMillis = clock.wallTimeMillis(),
            lastFullScreenWallMillis = progress.monetization.lastFullScreenAdWallTimeMillis,
            nowElapsedMillis = clock.elapsedRealtimeMillis(),
            lastFullScreenElapsedMillis = coordinator.lastDismissedElapsedMillis,
            lastRewardedElapsedMillis = coordinator.lastRewardedElapsedMillis,
            consentAllowsAds = privacyManager.state.value.canRequestAds,
            loaded = interstitialAdService.state.value == InterstitialAdState.READY,
            foreground = activity.isResumed(),
            expectedCompletionScreen = uiState.destination == AppDestination.GAME && uiState.isComplete,
            fullScreenIdle = coordinator.isIdle(),
        )
        val decision = InterstitialPolicy.evaluate(input)
        crashReporter.setKey(CrashKey.LAST_AD_POLICY_REASON, decision.reason.name.lowercase())
        crashReporter.setKey(CrashKey.AD_STATE, interstitialAdService.state.value.name.lowercase())
        analytics.track(AnalyticsEvent.InterstitialEligible(decision.reason.name.lowercase(), if (decision.eligible) "show" else "skip"))
        if (decision.eligible) {
            when (interstitialAdService.showAtBoundary(activity)) {
                InterstitialOutcome.Dismissed -> repository.recordFullScreenAdDismissal(
                    clock.localDate(),
                    clock.wallTimeMillis(),
                    interstitialShown = true,
                )
                is InterstitialOutcome.Failed, is InterstitialOutcome.Unavailable -> Unit
            }
        }
        navigate()
        } finally {
            nextLevelInFlight.set(false)
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
