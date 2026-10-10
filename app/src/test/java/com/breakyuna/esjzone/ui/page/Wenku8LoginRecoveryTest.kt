package com.breakyuna.esjzone.ui.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Wenku8LoginRecoveryTest {
    @Test fun openingWebRetriesFailedPreparation() {
        for (status in listOf(Wenku8LoginStatus.INCOMPATIBLE, Wenku8LoginStatus.NETWORK, Wenku8LoginStatus.UNKNOWN)) {
            assertTrue(status.needsWebReload(formReady = false, submitting = false, attemptStarted = false))
        }
    }

    @Test fun openingWebPreservesFormVerificationAndActiveRequests() {
        for (status in listOf(Wenku8LoginStatus.READY, Wenku8LoginStatus.MANUAL,
            Wenku8LoginStatus.PREPARING, Wenku8LoginStatus.CHECKING, Wenku8LoginStatus.SUBMITTING)) {
            assertFalse(status.needsWebReload(formReady = false, submitting = false, attemptStarted = false))
        }
        assertFalse(Wenku8LoginStatus.UNKNOWN.needsWebReload(formReady = true, submitting = false, attemptStarted = false))
        assertFalse(Wenku8LoginStatus.UNKNOWN.needsWebReload(formReady = false, submitting = true, attemptStarted = false))
    }

    @Test fun openingWebPreservesAnUncertainSubmissionResult() {
        assertFalse(Wenku8LoginStatus.UNKNOWN.needsWebReload(formReady = false, submitting = false, attemptStarted = true))
        assertFalse(Wenku8LoginStatus.INCOMPATIBLE.needsWebReload(formReady = false, submitting = false, attemptStarted = true))
    }
}
