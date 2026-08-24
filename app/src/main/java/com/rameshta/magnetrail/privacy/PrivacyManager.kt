package com.rameshta.magnetrail.privacy

import android.app.Activity
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConsentFlowResult {
    UNKNOWN,
    PREVIOUS_SESSION,
    REQUIRED,
    OBTAINED,
    NOT_REQUIRED,
    DENIED,
    UPDATE_ERROR,
    FORM_ERROR,
}

data class PrivacyState(
    val refreshStarted: Boolean = false,
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val consentFormShowing: Boolean = false,
    val flowResult: ConsentFlowResult = ConsentFlowResult.UNKNOWN,
)

data class ConsentSnapshot(
    val canRequestAds: Boolean,
    val privacyOptionsRequired: Boolean,
    val category: ConsentFlowResult,
)

interface ConsentGateway {
    fun requestUpdate(onSuccess: () -> Unit, onFailure: () -> Unit)
    fun loadAndShowIfRequired(onComplete: (failed: Boolean) -> Unit)
    fun showPrivacyOptions(onComplete: (failed: Boolean) -> Unit)
    fun snapshot(): ConsentSnapshot
}

class ConsentOrchestrator(
    private val gateway: ConsentGateway,
    private val onAdsPermitted: () -> Unit,
    private val onResult: (ConsentFlowResult) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(PrivacyState())
    val state: StateFlow<PrivacyState> = mutableState.asStateFlow()
    private val refreshRequested = AtomicBoolean(false)
    private val adsNotified = AtomicBoolean(false)

    fun refresh() {
        if (!refreshRequested.compareAndSet(false, true)) return
        mutableState.value = mutableState.value.copy(refreshStarted = true)
        gateway.requestUpdate(
            onSuccess = {
                val beforeForm = gateway.snapshot()
                mutableState.value = beforeForm.toState(consentFormShowing = beforeForm.category == ConsentFlowResult.REQUIRED)
                gateway.loadAndShowIfRequired { failed ->
                    publish(if (failed) ConsentFlowResult.FORM_ERROR else null)
                }
            },
            onFailure = { publish(ConsentFlowResult.UPDATE_ERROR) },
        )
        val cached = gateway.snapshot()
        if (cached.canRequestAds) publish(ConsentFlowResult.PREVIOUS_SESSION)
    }

    fun showPrivacyOptions() {
        if (!mutableState.value.privacyOptionsRequired) return
        gateway.showPrivacyOptions { failed ->
            publish(if (failed) ConsentFlowResult.FORM_ERROR else null)
        }
    }

    private fun publish(override: ConsentFlowResult?) {
        val snapshot = gateway.snapshot()
        val result = override ?: snapshot.category
        mutableState.value = snapshot.toState(flowResult = result)
        onResult(result)
        if (snapshot.canRequestAds && adsNotified.compareAndSet(false, true)) onAdsPermitted()
    }

    private fun ConsentSnapshot.toState(
        flowResult: ConsentFlowResult = category,
        consentFormShowing: Boolean = false,
    ) = PrivacyState(
        refreshStarted = true,
        canRequestAds = canRequestAds,
        privacyOptionsRequired = privacyOptionsRequired,
        consentFormShowing = consentFormShowing,
        flowResult = flowResult,
    )
}

interface PrivacyManager {
    val state: StateFlow<PrivacyState>
    fun refresh(activity: Activity)
    fun showPrivacyOptions(activity: Activity)
}

class NoOpPrivacyManager : PrivacyManager {
    private val mutableState = MutableStateFlow(PrivacyState())
    override val state: StateFlow<PrivacyState> = mutableState.asStateFlow()
    override fun refresh(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}
