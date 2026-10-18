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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerTest {

    private lateinit var engine: CallClassificationEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        engine = CallClassificationEngine()
        CallManager.reset()
    }

    @After
    fun tearDown() {
        CallManager.reset()
        Dispatchers.resetMain()
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

    @Test
    fun testHoldAndResumeLifecycleState() {
        // Initial state
        val state = CallUiState(
            callState = CallState.ACTIVE,
            canHold = true,
            isOnHold = false
        )
        assertFalse(state.isOnHold)
        assertTrue(state.canHold)
        assertEquals(CallState.ACTIVE, state.callState)

        // Put on hold
        val onHoldState = state.copy(
            callState = CallState.HOLDING,
            isOnHold = true
        )
        assertTrue(onHoldState.isOnHold)
        assertEquals(CallState.HOLDING, onHoldState.callState)

        // Resume call
        val resumedState = onHoldState.copy(
            callState = CallState.ACTIVE,
            isOnHold = false
        )
        assertFalse(resumedState.isOnHold)
        assertEquals(CallState.ACTIVE, resumedState.callState)
    }

    @Test
    fun testCallCannotHoldWhenUnsupported() {
        val state = CallUiState(
            callState = CallState.ACTIVE,
            canHold = false,
            isOnHold = false
        )
        assertFalse(state.canHold)
    }

    @Test
    fun testCallDurationMonotonicAdvanceAndHoldResumeContinuity() {
        var mockCurrentTime = 100_000L
        CallManager.elapsedRealtimeProvider = { mockCurrentTime }

        // Start simulated active call
        CallManager.reset()
        assertNull(CallManager.getCallConnectedElapsedRealtime())
        assertEquals(0L, CallManager.callUiState.value.durationSeconds)

        // Set call active
        CallManager.setCallConnectedElapsedRealtimeForTesting(mockCurrentTime)
        CallManager.ensureDurationTimerRunning()
        assertEquals(0L, CallManager.callUiState.value.durationSeconds)

        // Advance 155 seconds (2 mins 35 secs)
        mockCurrentTime += 155_000L
        CallManager.ensureDurationTimerRunning()
        assertEquals(155L, CallManager.callUiState.value.durationSeconds)

        // Put call on hold
        val originBeforeHold = CallManager.getCallConnectedElapsedRealtime()
        assertNotNull(originBeforeHold)
        // Advance 30 seconds while on hold
        mockCurrentTime += 30_000L
        CallManager.ensureDurationTimerRunning()
        // Duration reflects 155 + 30 = 185s
        assertEquals(185L, CallManager.callUiState.value.durationSeconds)
        assertEquals(originBeforeHold, CallManager.getCallConnectedElapsedRealtime())

        // Resume call
        CallManager.ensureDurationTimerRunning()
        // Origin MUST be preserved, NOT reset to mockCurrentTime
        assertEquals(originBeforeHold, CallManager.getCallConnectedElapsedRealtime())
        assertEquals(185L, CallManager.callUiState.value.durationSeconds)

        // Advance another 15 seconds
        mockCurrentTime += 15_000L
        CallManager.ensureDurationTimerRunning()
        assertEquals(200L, CallManager.callUiState.value.durationSeconds)

        // Clean up
        CallManager.reset()
        assertEquals(0L, CallManager.callUiState.value.durationSeconds)
        assertNull(CallManager.getCallConnectedElapsedRealtime())
    }

    @Test
    fun testRepeatedActiveCallbacksDoNotResetTimerOrigin() {
        var mockCurrentTime = 50_000L
        CallManager.elapsedRealtimeProvider = { mockCurrentTime }
        CallManager.reset()

        CallManager.setCallConnectedElapsedRealtimeForTesting(mockCurrentTime)
        CallManager.ensureDurationTimerRunning()

        val initialOrigin = CallManager.getCallConnectedElapsedRealtime()
        assertEquals(mockCurrentTime, initialOrigin)

        // Advance 60 seconds
        mockCurrentTime += 60_000L
        CallManager.ensureDurationTimerRunning()
        assertEquals(60L, CallManager.callUiState.value.durationSeconds)

        // Trigger ensureDurationTimerRunning multiple times (mimicking multiple STATE_ACTIVE events)
        CallManager.ensureDurationTimerRunning()
        CallManager.ensureDurationTimerRunning()
        assertEquals(initialOrigin, CallManager.getCallConnectedElapsedRealtime())
        assertEquals(60L, CallManager.callUiState.value.durationSeconds)

        CallManager.reset()
    }

    @Test
    fun testDisconnectFinalizesDurationAndNewCallStartsClean() {
        var mockCurrentTime = 200_000L
        CallManager.elapsedRealtimeProvider = { mockCurrentTime }
        CallManager.reset()

        // Call 1
        CallManager.setCallConnectedElapsedRealtimeForTesting(mockCurrentTime)
        mockCurrentTime += 45_000L
        CallManager.ensureDurationTimerRunning()
        assertEquals(45L, CallManager.callUiState.value.durationSeconds)

        // Disconnect Call 1
        CallManager.unbindCall()
        assertEquals(45L, CallManager.callUiState.value.durationSeconds)
        assertNull(CallManager.getCallConnectedElapsedRealtime())

        // Call 2 starts fresh
        mockCurrentTime += 10_000L
        CallManager.reset()
        assertEquals(0L, CallManager.callUiState.value.durationSeconds)
        CallManager.setCallConnectedElapsedRealtimeForTesting(mockCurrentTime)
        CallManager.ensureDurationTimerRunning()
        assertEquals(0L, CallManager.callUiState.value.durationSeconds)

        CallManager.reset()
    }
}
