package com.rameshta.magnetrail

import com.rameshta.magnetrail.privacy.ConsentFlowResult
import com.rameshta.magnetrail.privacy.ConsentGateway
import com.rameshta.magnetrail.privacy.ConsentOrchestrator
import com.rameshta.magnetrail.privacy.ConsentSnapshot
import com.rameshta.magnetrail.privacy.DiagnosticsPolicy
import com.rameshta.magnetrail.privacy.PrivacyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M4ConsentTest {
    @Test
    fun `refresh is requested once and ads initialize only after permission`() {
        val gateway = FakeConsentGateway()
        var initializations = 0
        val orchestrator = ConsentOrchestrator(gateway, { initializations++ })

        orchestrator.refresh()
        orchestrator.refresh()
        assertEquals(1, gateway.updateRequests)
        assertEquals(0, initializations)

        gateway.snapshot = ConsentSnapshot(true, false, ConsentFlowResult.NOT_REQUIRED)
        gateway.updateSuccess()
        gateway.formComplete(false)
        gateway.formComplete(false)

        assertEquals(1, initializations)
        assertTrue(orchestrator.state.value.canRequestAds)
        assertEquals(ConsentFlowResult.NOT_REQUIRED, orchestrator.state.value.flowResult)
    }

    @Test
    fun `previous-session permission survives update error without duplicate initialization`() {
        val gateway = FakeConsentGateway(
            ConsentSnapshot(true, true, ConsentFlowResult.OBTAINED),
        )
        var initializations = 0
        val orchestrator = ConsentOrchestrator(gateway, { initializations++ })

        orchestrator.refresh()
        assertEquals(1, initializations)
        assertEquals(ConsentFlowResult.PREVIOUS_SESSION, orchestrator.state.value.flowResult)
        gateway.updateFailure()

        assertEquals(1, initializations)
        assertEquals(ConsentFlowResult.UPDATE_ERROR, orchestrator.state.value.flowResult)
        assertTrue(orchestrator.state.value.privacyOptionsRequired)
    }

    @Test
    fun `required denied and form error paths remain blocked`() {
        val gateway = FakeConsentGateway()
        var initializations = 0
        val orchestrator = ConsentOrchestrator(gateway, { initializations++ })
        orchestrator.refresh()

        gateway.snapshot = ConsentSnapshot(false, true, ConsentFlowResult.REQUIRED)
        gateway.updateSuccess()
        assertTrue(orchestrator.state.value.consentFormShowing)
        gateway.snapshot = ConsentSnapshot(false, true, ConsentFlowResult.DENIED)
        gateway.formComplete(true)

        assertEquals(0, initializations)
        assertFalse(orchestrator.state.value.canRequestAds)
        assertEquals(ConsentFlowResult.FORM_ERROR, orchestrator.state.value.flowResult)
    }

    @Test
    fun `privacy options are visible and presented only when required`() {
        val gateway = FakeConsentGateway()
        val orchestrator = ConsentOrchestrator(gateway, {})
        orchestrator.showPrivacyOptions()
        assertEquals(0, gateway.privacyRequests)

        orchestrator.refresh()
        gateway.snapshot = ConsentSnapshot(true, true, ConsentFlowResult.OBTAINED)
        gateway.updateSuccess()
        gateway.formComplete(false)
        orchestrator.showPrivacyOptions()
        assertEquals(1, gateway.privacyRequests)
    }

    @Test
    fun `diagnostics requires both local opt in and effective consent`() {
        assertFalse(DiagnosticsPolicy.isCollectionAllowed(true, PrivacyState()))
        assertFalse(
            DiagnosticsPolicy.isCollectionAllowed(
                true,
                PrivacyState(canRequestAds = false, flowResult = ConsentFlowResult.DENIED),
            ),
        )
        assertFalse(
            DiagnosticsPolicy.isCollectionAllowed(
                false,
                PrivacyState(canRequestAds = true, flowResult = ConsentFlowResult.OBTAINED),
            ),
        )
        assertTrue(
            DiagnosticsPolicy.isCollectionAllowed(
                true,
                PrivacyState(canRequestAds = true, flowResult = ConsentFlowResult.OBTAINED),
            ),
        )
    }

    private class FakeConsentGateway(
        var snapshot: ConsentSnapshot = ConsentSnapshot(false, false, ConsentFlowResult.UNKNOWN),
    ) : ConsentGateway {
        var updateRequests = 0
        var privacyRequests = 0
        private var success: (() -> Unit)? = null
        private var failure: (() -> Unit)? = null
        private var form: ((Boolean) -> Unit)? = null

        override fun requestUpdate(onSuccess: () -> Unit, onFailure: () -> Unit) {
            updateRequests++
            success = onSuccess
            failure = onFailure
        }

        override fun loadAndShowIfRequired(onComplete: (failed: Boolean) -> Unit) {
            form = onComplete
        }

        override fun showPrivacyOptions(onComplete: (failed: Boolean) -> Unit) {
            privacyRequests++
            onComplete(false)
        }

        override fun snapshot(): ConsentSnapshot = snapshot
        fun updateSuccess() = checkNotNull(success).invoke()
        fun updateFailure() = checkNotNull(failure).invoke()
        fun formComplete(failed: Boolean) = checkNotNull(form).invoke(failed)
    }
}
