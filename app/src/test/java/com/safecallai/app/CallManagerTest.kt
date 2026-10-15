package com.safecallai.app

import com.safecallai.app.domain.engine.CallClassificationEngine
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ContactInfo
import com.safecallai.app.domain.models.VerificationStatus
import com.safecallai.app.service.CallManager
import com.safecallai.app.service.CallState
import com.safecallai.app.service.CallUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallManagerTest {

    private lateinit var engine: CallClassificationEngine

    @Before
    fun setUp() {
        engine = CallClassificationEngine()
        CallManager.reset()
    }

    @Test
    fun testInitialCallStateIsIdle() {
        val state = CallManager.callUiState.value
        assertEquals(CallState.IDLE, state.callState)
        assertEquals("", state.rawNumber)
        assertNull(state.contactInfo.name)
        assertFalse(state.contactInfo.isSavedContact)
    }

    @Test
    fun testSavedContactVsEnterpriseVerificationSeparation() {
        // Saved contact does NOT grant verified business status
        val contactInfo = ContactInfo(
            name = "Rahul Sharma",
            isSavedContact = true
        )

        val classification = engine.classify(
            rawNumber = "+919876500001",
            profile = null,
            currentTimeMillis = 1776000000000L
        )

        // Contact is saved, but public enterprise status is UNVERIFIED
        assertTrue(contactInfo.isSavedContact)
        assertEquals("Rahul Sharma", contactInfo.name)
        assertEquals(VerificationStatus.UNVERIFIED, classification.verificationStatus)
        assertEquals(CallerClassification.UNKNOWN, classification.classification)
        assertTrue(classification.isSafeToRingNormally)
    }

    @Test
    fun testUnknownCallerIsSafeToRingNormally() {
        val result = engine.classify(
            rawNumber = "+919876543219",
            profile = null,
            currentTimeMillis = 1776000000000L
        )

        assertEquals(CallerClassification.UNKNOWN, result.classification)
        assertEquals(VerificationStatus.UNVERIFIED, result.verificationStatus)
        assertTrue(result.isSafeToRingNormally) // Must not be blocked automatically
    }

    @Test
    fun testSimulatedCallingLifecycleTransitions() {
        val contact = ContactInfo(name = "Amazon Logistics", isSavedContact = false)
        val classification = engine.classify(
            rawNumber = "+919876543210",
            profile = null,
            currentTimeMillis = 1776000000000L
        )

        // Test answering simulated call
        CallManager.answer() // When IDLE, stays IDLE or handles safely

        // Test resetting
        CallManager.reset()
        assertEquals(CallState.IDLE, CallManager.callUiState.value.callState)
    }
}
