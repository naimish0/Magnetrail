package com.rameshta.magnetrail.privacy

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.rameshta.magnetrail.ads.FullScreenAdCoordinator
import com.rameshta.magnetrail.ads.FullScreenOwner
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.StateFlow

class UmpPrivacyManager(
    context: Context,
    private val fullScreenCoordinator: FullScreenAdCoordinator,
    onAdsPermitted: () -> Unit,
    onResult: (ConsentFlowResult) -> Unit = {},
) : PrivacyManager {
    private val gateway = UmpConsentGateway(context.applicationContext, fullScreenCoordinator)
    private val orchestrator = ConsentOrchestrator(gateway, onAdsPermitted, onResult)
    override val state: StateFlow<PrivacyState> = orchestrator.state

    override fun refresh(activity: Activity) {
        gateway.setActivity(activity)
        orchestrator.refresh()
    }

    override fun showPrivacyOptions(activity: Activity) {
        gateway.setActivity(activity)
        orchestrator.showPrivacyOptions()
    }
}

private class UmpConsentGateway(
    context: Context,
    private val fullScreenCoordinator: FullScreenAdCoordinator,
) : ConsentGateway {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)
    private var activity = WeakReference<Activity>(null)

    fun setActivity(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    override fun requestUpdate(onSuccess: () -> Unit, onFailure: () -> Unit) {
        val host = activity.get() ?: return onFailure()
        consentInformation.requestConsentInfoUpdate(
            host,
            ConsentRequestParameters.Builder().build(),
            onSuccess,
            { onFailure() },
        )
    }

    override fun loadAndShowIfRequired(onComplete: (failed: Boolean) -> Unit) {
        val host = activity.get() ?: return onComplete(true)
        val couldShow = consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
        if (couldShow && !fullScreenCoordinator.tryAcquire(FullScreenOwner.CONSENT)) return onComplete(true)
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(host) { error ->
            if (couldShow) fullScreenCoordinator.release(FullScreenOwner.CONSENT, completed = false)
            onComplete(error != null)
        }
    }

    override fun showPrivacyOptions(onComplete: (failed: Boolean) -> Unit) {
        val host = activity.get() ?: return onComplete(true)
        if (!fullScreenCoordinator.tryAcquire(FullScreenOwner.CONSENT)) return onComplete(true)
        UserMessagingPlatform.showPrivacyOptionsForm(host) { error ->
            fullScreenCoordinator.release(FullScreenOwner.CONSENT, completed = false)
            onComplete(error != null)
        }
    }

    override fun snapshot(): ConsentSnapshot {
        val category = when (consentInformation.consentStatus) {
            ConsentInformation.ConsentStatus.REQUIRED -> ConsentFlowResult.REQUIRED
            ConsentInformation.ConsentStatus.OBTAINED -> ConsentFlowResult.OBTAINED
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentFlowResult.NOT_REQUIRED
            else -> if (consentInformation.canRequestAds()) ConsentFlowResult.OBTAINED else ConsentFlowResult.DENIED
        }
        return ConsentSnapshot(
            canRequestAds = consentInformation.canRequestAds(),
            privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            category = category,
        )
    }
}
