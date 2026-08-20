package com.rameshta.magnetrail.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RewardedAdState { BLOCKED, LOADING, READY, SHOWING, UNAVAILABLE }
enum class InterstitialAdState { BLOCKED, LOADING, READY, SHOWING, UNAVAILABLE }

sealed interface RewardedOutcome {
    data class Earned(val transactionId: String) : RewardedOutcome
    data object DismissedWithoutReward : RewardedOutcome
    data class Unavailable(val reason: String) : RewardedOutcome
    data class Failed(val category: String) : RewardedOutcome
}

sealed interface InterstitialOutcome {
    data object Dismissed : InterstitialOutcome
    data class Unavailable(val reason: String) : InterstitialOutcome
    data class Failed(val category: String) : InterstitialOutcome
}

interface RewardedAdService {
    val state: StateFlow<RewardedAdState>
    fun preloadIfAllowed()
    suspend fun showForHint(activity: Activity): RewardedOutcome
    suspend fun showForSkip(activity: Activity): RewardedOutcome = showForHint(activity)
    fun clear()
}

interface InterstitialAdService {
    val state: StateFlow<InterstitialAdState>
    fun preloadIfAllowed()
    suspend fun showAtBoundary(activity: Activity): InterstitialOutcome
    fun clear()
}

class NoOpRewardedAdService : RewardedAdService {
    private val mutableState = MutableStateFlow(RewardedAdState.BLOCKED)
    override val state = mutableState.asStateFlow()
    override fun preloadIfAllowed() = Unit
    override suspend fun showForHint(activity: Activity): RewardedOutcome = RewardedOutcome.Unavailable("disabled")
    override fun clear() = Unit
}

class NoOpInterstitialAdService : InterstitialAdService {
    private val mutableState = MutableStateFlow(InterstitialAdState.BLOCKED)
    override val state = mutableState.asStateFlow()
    override fun preloadIfAllowed() = Unit
    override suspend fun showAtBoundary(activity: Activity): InterstitialOutcome =
        InterstitialOutcome.Unavailable("disabled")
    override fun clear() = Unit
}
