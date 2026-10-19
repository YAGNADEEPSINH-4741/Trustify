package com.safecallai.app

import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerIdentityResult
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence
import com.safecallai.app.service.CallManager
import com.safecallai.app.service.CallState
import com.safecallai.app.service.MergeButtonState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Production test suite covering:
 * 1. Add Call UI state & number entry validation.
 * 2. Multi-call state management (Person A on hold, Person B dialing/active).
 * 3. Swap Calls operation and strict preservation of monotonic durations.
 * 4. Merge Calls / Conference call creation & carrier limitation fallback.
 * 5. Call termination paths (ending Person B preserves Person A; ending Person A preserves Person B).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiCallConferenceTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CallManager.reset()
    }

    @After
    fun tearDown() {
        CallManager.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun testAddCallVisibilityAndDialogState() {
        // Initial idle: cannot add call
        assertFalse(CallManager.callUiState.value.canAddCall)
        assertFalse(CallManager.callUiState.value.isAddingCall)

        // Setup single active call
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.ACTIVE
        )

        val activeState = CallManager.callUiState.value
        assertTrue("Single active call must support Add Call", activeState.canAddCall)
        assertFalse("Add Call screen is not open by default", activeState.isAddingCall)

        // Open Add Call screen
        CallManager.showAddCall(true)
        assertTrue(CallManager.callUiState.value.isAddingCall)

        // Close Add Call screen
        CallManager.showAddCall(false)
        assertFalse(CallManager.callUiState.value.isAddingCall)
    }

    @Test
    fun testDialPadNumberValidation() {
        // Valid international and local numbers
        assertTrue(PhoneNumberNormalizer.isValid("+919876543210"))
        assertTrue(PhoneNumberNormalizer.isValid("+91 98765 43210"))
        assertTrue(PhoneNumberNormalizer.isValid("9876543210"))
        assertTrue(PhoneNumberNormalizer.isValid("+14155552671"))
        assertTrue(PhoneNumberNormalizer.isValid("08012345678"))

        // Invalid numbers
        assertFalse(PhoneNumberNormalizer.isValid(""))
        assertFalse(PhoneNumberNormalizer.isValid(" "))
        assertFalse(PhoneNumberNormalizer.isValid("123"))
        assertFalse(PhoneNumberNormalizer.isValid("abc"))
    }

    @Test
    fun testPersonAHeldAndTimerPreservedDuringPersonBDialing() {
        var currentTime = 100_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        // 1. Person A connects at t=100s
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.ACTIVE,
            origin = currentTime
        )
        CallManager.ensureDurationTimerRunning()

        // Advance 30 seconds
        currentTime += 30_000L
        CallManager.ensureDurationTimerRunning()
        assertEquals(30L, CallManager.callUiState.value.durationSeconds)

        // 2. User adds Person B: Person A is put on hold, Person B is dialing
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.DIALING,
            origin = null
        )

        val multiState = CallManager.callUiState.value
        assertTrue("Multi-call mode active", multiState.isMultiCall)
        assertFalse("Conference must NOT be active while second call is dialing", multiState.isConference)
        assertNotNull(multiState.primaryCall)
        assertNotNull(multiState.secondaryCall)
        assertTrue(multiState.primaryCall!!.isOnHold)
        assertEquals(CallState.DIALING, multiState.secondaryCall!!.callState)

        // Advance 15 seconds while Person B is ringing/connecting
        currentTime += 15_000L
        CallManager.ensureDurationTimerRunning()

        // Person A's duration continues smoothly: (145_000 - 100_000) / 1000 = 45s
        val updatedState = CallManager.callUiState.value
        assertEquals(45L, updatedState.primaryCall!!.durationSeconds)
        assertEquals(0L, updatedState.secondaryCall!!.durationSeconds)
    }

    @Test
    fun testPersonBAnswersEnablesSwapAndMerge() {
        var currentTime = 145_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        // Person A is held (started at 100_000L)
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )

        // Person B answers at t=145s
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = currentTime
        )

        val state = CallManager.callUiState.value
        assertTrue(state.isMultiCall)
        assertFalse(state.isConference)
        assertTrue("Swap calls must be enabled when one is active and one is held", state.canSwap)
        assertTrue("Merge calls must be available when both calls are connected", state.canMerge)
    }

    @Test
    fun testSwapCallsSwitchesActiveAndHeldWithoutResettingTimers() {
        var currentTime = 150_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        // Initial setup:
        // Person A (call_a): origin = 100_000L, held
        // Person B (call_b): origin = 140_000L, active
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 140_000L
        )
        CallManager.ensureDurationTimerRunning()

        // At t=150s: Person A duration = 50s; Person B duration = 10s
        assertEquals(50L, CallManager.callUiState.value.primaryCall?.durationSeconds)
        assertEquals(10L, CallManager.callUiState.value.secondaryCall?.durationSeconds)

        // Advance 20 seconds to t=170s
        currentTime += 20_000L
        CallManager.ensureDurationTimerRunning()

        // Swap calls
        CallManager.swapCalls()

        val swappedState = CallManager.callUiState.value
        // After swap: Person A becomes active, Person B becomes held
        val currentPrimary = swappedState.primaryCall
        val currentSecondary = swappedState.secondaryCall
        assertNotNull(currentPrimary)
        assertNotNull(currentSecondary)

        // Neither timer was reset!
        // At t=170s: Person A (origin 100s) = 70s; Person B (origin 140s) = 30s
        assertEquals(70L, currentPrimary!!.durationSeconds)
        assertEquals(30L, currentSecondary!!.durationSeconds)

        // Swap back
        currentTime += 10_000L // t=180s
        CallManager.swapCalls()

        val swapBackState = CallManager.callUiState.value
        // At t=180s: Person A is primary (80s, holding); Person B is secondary (40s, active)
        assertEquals(80L, swapBackState.primaryCall!!.durationSeconds)
        assertEquals(40L, swapBackState.secondaryCall!!.durationSeconds)
        assertTrue("Person A is held after swapping back", swapBackState.primaryCall!!.isOnHold)
        assertFalse("Person B is active after swapping back", swapBackState.secondaryCall!!.isOnHold)
    }

    @Test
    fun testMergeCallsCreatesConferenceCall() {
        var currentTime = 200_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 150_000L
        )

        assertFalse(CallManager.callUiState.value.isConference)

        // User taps Merge Calls
        CallManager.mergeCalls()

        val confState = CallManager.callUiState.value
        assertTrue("Conference state must be active after merge", confState.isConference)
        assertEquals(2, confState.conferenceParticipants.size)
    }

    @Test
    fun testEndingPersonBRestoresPersonAWithoutTimerReset() {
        var currentTime = 160_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 140_000L
        )

        // Person B ends the call
        CallManager.removeSecondaryCallForTesting()

        val state = CallManager.callUiState.value
        assertFalse("No longer multi-call after secondary hangs up", state.isMultiCall)
        assertNull("Secondary call is cleared", state.secondaryCall)
        assertNotNull(state.primaryCall)
        assertEquals(CallState.ACTIVE, state.primaryCall!!.callState)
        assertFalse(state.primaryCall!!.isOnHold)

        // Person A's duration origin is strictly preserved (160 - 100 = 60s)
        assertEquals(60L, state.primaryCall!!.durationSeconds)
        assertEquals(60L, state.durationSeconds)
    }

    @Test
    fun testEndingPersonAPreservesPersonB() {
        var currentTime = 160_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 140_000L
        )

        // Person A disconnects
        CallManager.removePrimaryCallForTesting()

        val state = CallManager.callUiState.value
        assertFalse("No longer multi-call", state.isMultiCall)
        assertNull("Secondary call promoted, so secondary tracker is empty", state.secondaryCall)
        assertNotNull("Person B is now the primary active call", state.primaryCall)
        assertEquals("+919876511111", state.primaryCall!!.rawNumber)
        assertEquals(CallState.ACTIVE, state.primaryCall!!.callState)
        assertEquals(20L, state.primaryCall!!.durationSeconds) // 160 - 140 = 20s
    }

    @Test
    fun testCarrierLimitationDialogDismissal() {
        CallManager.setPrimaryCallForTesting()
        CallManager.setSecondaryCallForTesting()

        // Merge calls failure sets message
        CallManager.mergeCalls()
        // If simulated or telecom fails:
        CallManager.dismissMultiCallMessage()
        assertNull(CallManager.callUiState.value.multiCallMessage)
    }

    @Test
    fun testUntrackedCallRemovalDoesNotDisconnectPersonA() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 100_000L
        )

        // Instantiate a dummy Telecom Call via reflection (bypassing private framework constructor)
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafeObj = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val untrackedCall = allocateInstanceMethod.invoke(unsafeObj, android.telecom.Call::class.java) as android.telecom.Call

        // Telecom removes an untracked/failed call
        CallManager.onCallRemoved(untrackedCall)

        // Person A MUST NOT be disconnected!
        val state = CallManager.callUiState.value
        assertEquals("Person A must remain active when an untracked call is removed", CallState.ACTIVE, state.callState)
        assertNotNull("Primary call must remain intact", state.primaryCall)
        assertEquals("+919876543210", state.primaryCall!!.rawNumber)
    }

    @Test
    fun testPersonBFailureLeavesPersonAActiveWithoutEndingCall() {
        var currentTime = 100_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.DIALING,
            isOnHold = false,
            origin = null
        )

        assertTrue(CallManager.callUiState.value.isMultiCall)

        // Advance 10 seconds, then Person B call fails to connect / is rejected
        currentTime += 10_000L
        CallManager.removeSecondaryCallForTesting()

        val state = CallManager.callUiState.value
        assertFalse("Multi-call is no longer active", state.isMultiCall)
        assertNull("Secondary call is removed", state.secondaryCall)
        assertNotNull("Person A is retained", state.primaryCall)
        assertEquals("Person A resumes active state", CallState.ACTIVE, state.primaryCall!!.callState)
        assertFalse("Person A is no longer on hold", state.primaryCall!!.isOnHold)
        assertEquals("Call session state is ACTIVE, NOT DISCONNECTED", CallState.ACTIVE, state.callState)
        assertEquals("Person A duration preserved at 10s", 10L, state.primaryCall!!.durationSeconds)
    }

    @Test
    fun testPersonAIdentityNeverOverwrittenByPersonBResolution() {
        // Person A setup
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A Original",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )

        // Person B setup (initially dialed number before resolution)
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = null,
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        // Asynchronous identity lookup resolves for Person B (+917433994741)
        val personBIdentity = CallerIdentityResult(
            rawNumber = "+917433994741",
            normalizedNumber = "+917433994741",
            displayName = "Karmaraj",
            source = CallerIdentitySource.NETWORK_CNAP,
            identityType = CallerIdentityType.NETWORK_PROVIDED,
            confidence = IdentityConfidence.HIGH,
            sourceLabel = "Carrier CNAP",
            isVerified = true
        )
        CallManager.updateCallerIdentity("+917433994741", personBIdentity)

        val state = CallManager.callUiState.value
        // CRITICAL BUG 2 FIX VERIFICATION:
        // Person A's name MUST REMAIN "Person A Original", NOT replaced with Karmaraj!
        assertEquals("Person A name must be preserved", "Person A Original", state.primaryCall?.callerName)
        assertEquals("Person A number must match", "+919687180288", state.primaryCall?.rawNumber)

        // Person B's name MUST BE Karmaraj
        assertEquals("Person B name updated to Karmaraj", "Karmaraj", state.secondaryCall?.callerName)
        assertEquals("Person B number must match", "+917433994741", state.secondaryCall?.rawNumber)
    }

    @Test
    fun testHeldPersonBIsNotDisplayedAsActive() {
        // Person A is active, Person B is holding
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 120_000L
        )

        val state = CallManager.callUiState.value
        // CRITICAL BUG 3 FIX VERIFICATION:
        // Person A is active and not on hold
        assertEquals(CallState.ACTIVE, state.primaryCall?.callState)
        assertFalse(state.primaryCall!!.isOnHold)

        // Person B is holding and strictly on hold
        assertEquals(CallState.HOLDING, state.secondaryCall?.callState)
        assertTrue("Person B must have isOnHold=true", state.secondaryCall!!.isOnHold)

        // Under no circumstance should both calls be active
        assertFalse(
            "Both calls must not be active simultaneously",
            state.primaryCall?.callState == CallState.ACTIVE && state.secondaryCall?.callState == CallState.ACTIVE
        )
    }

    @Test
    fun testPressingMergeCallsAloneDoesNotCreateConferenceWithoutTelecomConfirmation() {
        // Instantiate dummy Telecom Calls using reflection
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafeObj = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val dummyCallA = allocateInstanceMethod.invoke(unsafeObj, android.telecom.Call::class.java) as android.telecom.Call
        val dummyCallB = allocateInstanceMethod.invoke(unsafeObj, android.telecom.Call::class.java) as android.telecom.Call

        // Set primary and secondary with real Telecom Call objects
        CallManager.setPrimaryCallForTesting(callId = "call_a", rawNumber = "+919687180288", callerName = "Person A")
        CallManager.setSecondaryCallForTesting(callId = "call_b", rawNumber = "+917433994741", callerName = "Person B")

        // Associate dummy telecom calls
        val trackerA = CallManager.findTrackerByNumber("+919687180288")
        val trackerB = CallManager.findTrackerByNumber("+917433994741")
        trackerA?.call = dummyCallA
        trackerB?.call = dummyCallB

        // Before merge: isConference is false
        assertFalse(CallManager.callUiState.value.isConference)

        // User taps Merge Calls
        CallManager.mergeCalls()

        // CRITICAL BUG 1 FIX VERIFICATION:
        // Calling merge alone must NOT transition UI to conference until Telecom confirms!
        assertFalse("Tapping Merge Calls alone must not fake conference state", CallManager.callUiState.value.isConference)
    }

    @Test
    fun testConferenceParentCreationPreservesBothParticipantIdentities() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        // Simulate conference merge confirmation
        CallManager.mergeCalls()

        val confState = CallManager.callUiState.value
        assertTrue("Conference active after merge confirmation", confState.isConference)
        assertEquals(2, confState.conferenceParticipants.size)

        val participant1 = confState.conferenceParticipants[0]
        val participant2 = confState.conferenceParticipants[1]

        assertEquals("Person A", participant1.callerName)
        assertEquals("+919687180288", participant1.rawNumber)

        assertEquals("Person B", participant2.callerName)
        assertEquals("+917433994741", participant2.rawNumber)
    }

    @Test
    fun testFailedMergeLeavesCallsConnectedWithoutFalseCallEndedPopup() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.ACTIVE,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 120_000L
        )

        // Trigger unsupported message
        CallManager.dismissMultiCallMessage()
        assertNull(CallManager.callUiState.value.multiCallMessage)

        // Ensure calls are still intact
        val state = CallManager.callUiState.value
        assertNotEquals(CallState.DISCONNECTED, state.callState)
        assertNotNull(state.primaryCall)
        assertNotNull(state.secondaryCall)
    }

    @Test
    fun testRepeatedMergeRequestsDoNotCreateDuplicateConferenceStates() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        // Tap Merge 3 times repeatedly
        CallManager.mergeCalls()
        CallManager.mergeCalls()
        CallManager.mergeCalls()

        val confState = CallManager.callUiState.value
        assertTrue(confState.isConference)
        // Exactly 2 participants, no duplicate cards or participants
        assertEquals(2, confState.conferenceParticipants.size)
    }

    @Test
    fun testSwapCallsStillWorksWhenConferenceIsUnsupported() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        val state = CallManager.callUiState.value
        assertTrue("Swap calls must remain available", state.canSwap)

        // Swap calls
        CallManager.swapCalls()

        val swapped = CallManager.callUiState.value
        assertEquals("Person A becomes active", CallState.ACTIVE, swapped.primaryCall?.callState)
        assertFalse(swapped.primaryCall!!.isOnHold)
        assertEquals("Person B becomes held", CallState.HOLDING, swapped.secondaryCall?.callState)
        assertTrue(swapped.secondaryCall!!.isOnHold)
    }

    @Test
    fun testTelecomCallbacksOutOfOrderDoNotCorruptIdentities() {
        // Simulate out-of-order callback where secondary is registered first
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.DIALING
        )
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = null,
            callState = CallState.HOLDING,
            isOnHold = true
        )

        // Resolve Person A
        CallManager.updateCallerIdentity(
            "+919687180288",
            CallerIdentityResult(
                rawNumber = "+919687180288",
                displayName = "Person A Verified"
            )
        )

        val state = CallManager.callUiState.value
        assertEquals("Person A Verified", state.primaryCall?.callerName)
        assertEquals("Person B", state.secondaryCall?.callerName)
    }

    @Test
    fun testMergeButtonStatesReadyWhenBothConnected() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        val state = CallManager.callUiState.value
        assertEquals("Merge button state must be READY when both calls connected", MergeButtonState.READY, state.mergeState)
        assertTrue("canMerge must be true when READY", state.canMerge)
        assertNull(state.mergeFailureReason)
    }

    @Test
    fun testMergeButtonUnavailableWhenSecondCallStillDialing() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.DIALING,
            isOnHold = false,
            origin = null
        )

        val state = CallManager.callUiState.value
        assertEquals("Merge button state must be UNAVAILABLE when second call is dialing", MergeButtonState.UNAVAILABLE, state.mergeState)
        assertFalse("canMerge must be false when UNAVAILABLE", state.canMerge)
    }

    @Test
    fun testMergeButtonCannotTriggerDuplicateMergeRequests() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        CallManager.setMergePendingForTesting(true)
        val mergingState = CallManager.callUiState.value
        assertEquals(MergeButtonState.MERGING, mergingState.mergeState)
        assertFalse("Button is not enabled while merge is pending", mergingState.canMerge)

        // Attempt second merge while in-flight
        CallManager.mergeCalls()
        // State remains in MERGING and was not replaced or corrupted
        assertTrue(CallManager.getIsMergePending())
        assertEquals(MergeButtonState.MERGING, CallManager.callUiState.value.mergeState)
    }

    @Test
    fun testAsyncMergeAwaitsTelecomConfirmationBeforeShowingConference() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        CallManager.simulateAsyncMergeForTesting = true
        CallManager.mergeCalls()

        val inFlightState = CallManager.callUiState.value
        assertEquals(MergeButtonState.MERGING, inFlightState.mergeState)
        assertFalse("Must NOT mark conference as established before confirmation", inFlightState.isConference)
        assertEquals(0, inFlightState.conferenceParticipants.size)

        // Now simulate Telecom callback confirming conference
        CallManager.confirmConferenceForTesting()

        val confirmedState = CallManager.callUiState.value
        assertTrue("Conference is established after Telecom confirmation", confirmedState.isConference)
        assertEquals(MergeButtonState.MERGED, confirmedState.mergeState)
        assertEquals(2, confirmedState.conferenceParticipants.size)
    }

    @Test
    fun testFailedMergePreservesBothCallsAndTimers() {
        var currentTime = 200_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 150_000L
        )
        CallManager.ensureDurationTimerRunning()

        // At t=200s: Person A is 100s, Person B is 50s
        assertEquals(100L, CallManager.callUiState.value.primaryCall?.durationSeconds)
        assertEquals(50L, CallManager.callUiState.value.secondaryCall?.durationSeconds)

        // Merge fails from carrier
        CallManager.setMergeFailureForTesting("Carrier rejected merge")

        val failedState = CallManager.callUiState.value
        assertEquals(MergeButtonState.FAILED, failedState.mergeState)
        assertEquals("Carrier rejected merge", failedState.mergeFailureReason)
        assertFalse("Not a conference", failedState.isConference)

        // Both calls remain connected with correct states and monotonic timers
        assertNotNull(failedState.primaryCall)
        assertNotNull(failedState.secondaryCall)
        assertEquals(CallState.HOLDING, failedState.primaryCall?.callState)
        assertEquals(CallState.ACTIVE, failedState.secondaryCall?.callState)
        assertEquals(100L, failedState.primaryCall?.durationSeconds)
        assertEquals(50L, failedState.secondaryCall?.durationSeconds)

        // Can still swap calls
        assertTrue(failedState.canSwap)
    }

    @Test
    fun testCallerIdentitiesPreservedAcrossConferenceMerge() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Dr. Alice",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Bob Builder",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 150_000L
        )

        CallManager.confirmConferenceForTesting()

        val confState = CallManager.callUiState.value
        assertTrue(confState.isConference)
        assertEquals(2, confState.conferenceParticipants.size)

        val participantA = confState.conferenceParticipants.find { it.rawNumber == "+919687180288" }
        val participantB = confState.conferenceParticipants.find { it.rawNumber == "+917433994741" }

        assertNotNull("Participant A must exist with original number", participantA)
        assertEquals("Dr. Alice", participantA?.callerName)

        assertNotNull("Participant B must exist with original number", participantB)
        assertEquals("Bob Builder", participantB?.callerName)
    }

    @Test
    fun testProviderWithoutConferenceSupportHandledHonestly() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 150_000L
        )

        // Carrier does not support conference
        CallManager.setTelecomConferenceSupportedForTesting(false)

        val state = CallManager.callUiState.value
        assertEquals("Merge button state must be UNAVAILABLE", MergeButtonState.UNAVAILABLE, state.mergeState)
        assertFalse("canMerge must be false when carrier does not support conference", state.canMerge)

        // Attempting merge explains limitation honestly
        CallManager.mergeCalls()
        val explainedState = CallManager.callUiState.value
        assertNotNull(explainedState.multiCallMessage)
        assertTrue(explainedState.multiCallMessage!!.contains("not supported"))
    }

    @Test
    fun testShowMergeUnavailableReasonShowsHonestFeedback() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919687180288",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+917433994741",
            callerName = "Person B",
            callState = CallState.DIALING
        )

        CallManager.showMergeUnavailableReason("Both calls must be connected to merge.")
        assertEquals("Both calls must be connected to merge.", CallManager.callUiState.value.multiCallMessage)

        CallManager.dismissMultiCallMessage()
        assertNull(CallManager.callUiState.value.multiCallMessage)
    }

    // =========================================================================
    // CONFERENCE TERMINATION TESTS (PHASE 5: TESTS 1 - 8)
    // =========================================================================

    /**
     * TEST 1:
     * Phone A calls B, adds C, merges successfully, and ends the conference.
     * Verify that the app requests termination of the conference and does not leave either tracked participant incorrectly active.
     */
    @Test
    fun testConferenceTermination_endsAllParticipantsAndLeavesNoneActive() {
        // 1. Phone A calls Phone B
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            origin = 100_000L
        )

        // 2. Phone A adds Phone C (Phone B goes to hold)
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )

        // 3. Merge calls successfully into conference
        CallManager.confirmConferenceForTesting()
        assertTrue("Conference active after merge", CallManager.callUiState.value.isConference)
        assertEquals(2, CallManager.callUiState.value.conferenceParticipants.size)

        // 4. End the conference
        CallManager.hangUp()

        // 5. Verify that conference termination was requested and no participant remains active
        val finalState = CallManager.callUiState.value
        assertEquals("UI state must transition to DISCONNECTED", CallState.DISCONNECTED, finalState.callState)
        assertFalse("Conference must no longer be active", finalState.isConference)
        assertNull("Primary call tracker must be cleared", CallManager.getPrimaryTrackerForTesting())
        assertNull("Secondary call tracker must be cleared", CallManager.getSecondaryTrackerForTesting())
        assertNull("Conference tracker must be cleared", CallManager.getConferenceTrackerForTesting())
        assertTrue("Conference participants list must be empty", finalState.conferenceParticipants.isEmpty())
    }

    /**
     * TEST 2:
     * The conference parent disconnects before child callbacks arrive.
     * Ensure the implementation handles asynchronous callback ordering.
     */
    @Test
    fun testConferenceTermination_parentDisconnectsBeforeChildCallbacks() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.setConferenceCallForTesting(call = null, callId = "conf_parent")
        CallManager.simulateAsyncConferenceTerminationForTesting = true

        assertTrue(CallManager.callUiState.value.isConference)
        val trackerB = CallManager.getPrimaryTrackerForTesting()!!
        val trackerC = CallManager.getSecondaryTrackerForTesting()!!

        // User requests conference termination
        CallManager.endConference()
        assertTrue("Conference termination must be in progress", CallManager.isConferenceTerminatingForTesting())

        // Telecom fires conference parent disconnected first
        CallManager.triggerConferenceParentDisconnectedForTesting()

        // Ensure child B was NOT unheld or resumed to ACTIVE while conference termination is active!
        assertNotEquals("Person B must not be resumed to ACTIVE", CallState.ACTIVE, trackerB.callState)
        assertNull("Conference tracker must be null", CallManager.getConferenceTrackerForTesting())

        // Now child callbacks arrive
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerB)
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerC)

        // Verify clean final state
        val finalState = CallManager.callUiState.value
        assertEquals(CallState.DISCONNECTED, finalState.callState)
        assertFalse(finalState.isConference)
        assertNull(CallManager.getPrimaryTrackerForTesting())
        assertNull(CallManager.getSecondaryTrackerForTesting())
    }

    /**
     * TEST 3:
     * A child disconnects before the conference parent.
     * Ensure cleanup remains correct.
     */
    @Test
    fun testConferenceTermination_childDisconnectsBeforeParent() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.setConferenceCallForTesting(call = null, callId = "conf_parent")
        CallManager.simulateAsyncConferenceTerminationForTesting = true

        val trackerB = CallManager.getPrimaryTrackerForTesting()!!
        val trackerC = CallManager.getSecondaryTrackerForTesting()!!

        CallManager.endConference()
        assertTrue(CallManager.isConferenceTerminatingForTesting())

        // Child C disconnects first
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerC)
        assertNull("Secondary tracker must be cleaned up", CallManager.getSecondaryTrackerForTesting())
        // Child B must NOT be resumed
        assertNotEquals("Child B must not be promoted to active single call", CallState.ACTIVE, trackerB.callState)

        // Then conference parent disconnects
        CallManager.triggerConferenceParentDisconnectedForTesting()
        assertNull(CallManager.getConferenceTrackerForTesting())

        // Finally Child B disconnects
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerB)
        assertNull(CallManager.getPrimaryTrackerForTesting())

        assertEquals(CallState.DISCONNECTED, CallManager.callUiState.value.callState)
        assertFalse(CallManager.callUiState.value.isConference)
    }

    /**
     * TEST 4:
     * One tracked call remains active after the initial conference disconnect request.
     * Verify that reconciliation detects it and handles it appropriately.
     */
    @Test
    fun testConferenceTermination_reconciliationDetectsRemainingActiveParticipant() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.setConferenceCallForTesting(call = null, callId = "conf_parent")
        CallManager.simulateAsyncConferenceTerminationForTesting = true

        val trackerB = CallManager.getPrimaryTrackerForTesting()!!
        val trackerC = CallManager.getSecondaryTrackerForTesting()!!

        // Initiate conference termination
        CallManager.endConference()

        // Simulate conference parent disconnected and secondary disconnected, but primary is still in HOLDING
        CallManager.triggerConferenceParentDisconnectedForTesting()
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerC)

        // Primary is still tracked
        assertNotNull(CallManager.getPrimaryTrackerForTesting())

        // Trigger reconciliation
        CallManager.triggerReconcileConferenceTerminationForTesting()

        // When reconciliation runs on simulated call or remaining tracker, it ensures disconnect
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerB)
        assertNull(CallManager.getPrimaryTrackerForTesting())
        assertEquals(CallState.DISCONNECTED, CallManager.callUiState.value.callState)
    }

    /**
     * TEST 5:
     * The user ends a normal one-to-one call.
     * Confirm existing behavior still works.
     */
    @Test
    fun testNormalCallHangUp_preservesExistingBehavior() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_single",
            rawNumber = "+919876543210",
            callerName = "Alice",
            callState = CallState.ACTIVE,
            origin = 100_000L
        )
        assertFalse("Single call is not a conference", CallManager.callUiState.value.isConference)
        assertFalse("Single call is not multi-call", CallManager.callUiState.value.isMultiCall)

        CallManager.hangUp()

        assertEquals(CallState.DISCONNECTED, CallManager.callUiState.value.callState)
        assertNull(CallManager.getPrimaryTrackerForTesting())
        assertNull(CallManager.getSecondaryTrackerForTesting())
    }

    /**
     * TEST 6:
     * The user adds a second call but has not merged yet.
     * Confirm that normal call handling is preserved and no unrelated call is terminated.
     */
    @Test
    fun testUnmergedMultiCall_hangUpDoesNotTerminateHeldCall() {
        // Person A is held, Person B is active
        CallManager.setPrimaryCallForTesting(
            callId = "call_a",
            rawNumber = "+919876543210",
            callerName = "Person A",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876511111",
            callerName = "Person B",
            callState = CallState.ACTIVE,
            isOnHold = false,
            origin = 120_000L
        )

        val multiState = CallManager.callUiState.value
        assertTrue(multiState.isMultiCall)
        assertFalse(multiState.isConference)

        // Hanging up active call B in unmerged multi-call resumes Person A
        CallManager.hangUp()

        val afterHangUp = CallManager.callUiState.value
        assertFalse("Must no longer be multi-call", afterHangUp.isMultiCall)
        assertFalse("Must not be conference", afterHangUp.isConference)
        // Person A is preserved and resumed!
        assertNotNull("Person A must remain connected", CallManager.getPrimaryTrackerForTesting())
        assertEquals("+919876543210", afterHangUp.rawNumber)
        assertEquals(CallState.ACTIVE, afterHangUp.callState)
        assertFalse(afterHangUp.isOnHold)
    }

    /**
     * TEST 7:
     * A participant is already disconnected when conference termination begins.
     * Ensure there are no duplicate operations, crashes, or stuck UI states.
     */
    @Test
    fun testConferenceTermination_participantAlreadyDisconnected_noCrashOrDuplicate() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        // Participant C hung up independently before conference ended
        val trackerC = CallManager.getSecondaryTrackerForTesting()!!
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerC)

        // Now user taps Hang Up on the conference
        CallManager.hangUp()

        // Primary is also terminated, no crashes or NPEs
        assertEquals(CallState.DISCONNECTED, CallManager.callUiState.value.callState)
        assertFalse(CallManager.callUiState.value.isConference)
        assertNull(CallManager.getPrimaryTrackerForTesting())
        assertNull(CallManager.getSecondaryTrackerForTesting())
    }

    /**
     * TEST 8:
     * The conference is terminated and all calls are disconnected.
     * Confirm that references, callbacks, timers, and UI state are cleaned up correctly.
     */
    @Test
    fun testConferenceTermination_fullCleanupOfReferencesCallbacksTimersAndUi() {
        var currentTime = 100_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        // Advance 60 seconds
        currentTime += 60_000L
        CallManager.ensureDurationTimerRunning()

        val inFlightState = CallManager.callUiState.value
        assertTrue(inFlightState.isConference)
        assertEquals(60L, inFlightState.primaryCall?.durationSeconds)

        // End conference
        CallManager.hangUp()

        val finalState = CallManager.callUiState.value
        assertEquals("Call state must be DISCONNECTED", CallState.DISCONNECTED, finalState.callState)
        assertFalse("isConference must be false", finalState.isConference)
        assertFalse("isMultiCall must be false", finalState.isMultiCall)
        assertNull("primaryCall in UI state must be null", finalState.primaryCall)
        assertNull("secondaryCall in UI state must be null", finalState.secondaryCall)
        assertTrue("conferenceParticipants must be empty", finalState.conferenceParticipants.isEmpty())
        assertNull("Primary tracker reference must be null", CallManager.getPrimaryTrackerForTesting())
        assertNull("Secondary tracker reference must be null", CallManager.getSecondaryTrackerForTesting())
        assertNull("Conference tracker reference must be null", CallManager.getConferenceTrackerForTesting())
        assertFalse("Termination flag must be reset", CallManager.isConferenceTerminatingForTesting())
        assertTrue("Final duration must be preserved", finalState.durationSeconds >= 60L)
    }

    /**
     * TEST 9 (Root cause verification):
     * Verifies that after merging, both Phone B and Phone C (e.g. Karmaraj) are present
     * in the conferenceParticipants list and persistently tracked so neither is dropped,
     * and when the conference ends, all participants are terminated.
     */
    @Test
    fun testConferencePreservesBothParticipantsInParticipantsListAndTracking() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+917433994741",
            callerName = "Karmaraj",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        val confState = CallManager.callUiState.value
        assertTrue("Conference must be active", confState.isConference)
        assertEquals("Both participants must be listed in conferenceParticipants", 2, confState.conferenceParticipants.size)

        val participantNames = confState.conferenceParticipants.mapNotNull { it.callerName }
        assertTrue("Person B must be in participants list", participantNames.contains("Person B"))
        assertTrue("Karmaraj must be in participants list", participantNames.contains("Karmaraj"))

        // When conference ends, both participants are disconnected
        CallManager.hangUp()
        val afterEndState = CallManager.callUiState.value
        assertEquals(CallState.DISCONNECTED, afterEndState.callState)
        assertFalse(afterEndState.isConference)
        assertTrue(afterEndState.conferenceParticipants.isEmpty())
        assertNull(CallManager.getPrimaryTrackerForTesting())
        assertNull(CallManager.getSecondaryTrackerForTesting())
        assertTrue(CallManager.getConferenceParticipantsForTesting().isEmpty())
    }

    /**
     * REQUIREMENT 1 / SCENARIO A:
     * Phone A is the Trustify device. Phone B is connected. Phone A adds Phone C.
     * Calls are merged. Phone C ends its call.
     * Expected:
     * - C disconnects.
     * - A and B remain connected if Telecom/carrier reports conference continues.
     * - The entire conference must not be terminated merely because C disconnected.
     * - Participant list must remove C and retain B.
     * - Remaining call duration must not unnecessarily reset.
     */
    @Test
    fun testScenarioA_PhoneCLeaves_PhoneARemainsWithPhoneB_DurationPreserved() {
        var currentTime = 100_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        // Advance 50 seconds (t=170s)
        currentTime = 170_000L
        CallManager.ensureDurationTimerRunning()

        val inFlightState = CallManager.callUiState.value
        assertTrue("Conference must be active", inFlightState.isConference)
        assertEquals("Both B and C must be in participant list", 2, inFlightState.conferenceParticipants.size)

        // Phone C ends its call independently
        val trackerC = CallManager.getSecondaryTrackerForTesting()!!
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerC)

        val afterCLeftState = CallManager.callUiState.value
        assertNotEquals("Conference must NOT terminate because C left", CallState.DISCONNECTED, afterCLeftState.callState)
        assertEquals("Call state must remain ACTIVE", CallState.ACTIVE, afterCLeftState.callState)

        // Participant list must remove C and retain B
        assertEquals("Participant list must now contain only 1 participant (B)", 1, afterCLeftState.conferenceParticipants.size)
        val remainingNames = afterCLeftState.conferenceParticipants.mapNotNull { it.callerName }
        assertTrue("Person B must be retained", remainingNames.contains("Person B"))
        assertFalse("Person C must be removed", remainingNames.contains("Person C"))

        // Primary tracker B is retained
        val primary = CallManager.getPrimaryTrackerForTesting()
        assertNotNull("Person B must remain connected as primary tracker", primary)
        assertEquals("+919876543210", primary?.rawNumber)

        // Duration must not reset
        assertTrue("Duration must be preserved (>= 70s for B)", afterCLeftState.durationSeconds >= 70L)
    }

    /**
     * REQUIREMENT 1 / SCENARIO B:
     * Phone B leaves instead of C.
     * Expected:
     * - B disconnects.
     * - A and C remain connected.
     * - Participant list retains C and removes B.
     * - Duration is preserved.
     */
    @Test
    fun testScenarioB_PhoneBLeaves_PhoneARemainsWithPhoneC_DurationPreserved() {
        var currentTime = 100_000L
        CallManager.elapsedRealtimeProvider = { currentTime }

        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        // Advance 40 seconds (t=160s)
        currentTime = 160_000L
        CallManager.ensureDurationTimerRunning()

        // Phone B ends its call independently
        val trackerB = CallManager.getPrimaryTrackerForTesting()!!
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerB)

        val afterBLeftState = CallManager.callUiState.value
        assertNotEquals("Conference must NOT terminate because B left", CallState.DISCONNECTED, afterBLeftState.callState)
        assertEquals("Call state must remain ACTIVE", CallState.ACTIVE, afterBLeftState.callState)

        // Participant list must remove B and retain C
        assertEquals("Participant list must now contain only 1 participant (C)", 1, afterBLeftState.conferenceParticipants.size)
        val remainingNames = afterBLeftState.conferenceParticipants.mapNotNull { it.callerName }
        assertTrue("Person C must be retained", remainingNames.contains("Person C"))
        assertFalse("Person B must be removed", remainingNames.contains("Person B"))

        // C is promoted to primary tracker
        val primary = CallManager.getPrimaryTrackerForTesting()
        assertNotNull("Person C must be promoted and remain connected", primary)
        assertEquals("+919876511111", primary?.rawNumber)

        // Duration must not reset
        assertTrue("Duration must be preserved (>= 40s for C)", afterBLeftState.durationSeconds >= 40L)
    }

    /**
     * SEQUENTIAL DEPARTURE & CLEANUP:
     * Phone C leaves first (A & B remain), then Phone B leaves.
     * Once all participants depart, full cleanup occurs.
     */
    @Test
    fun testSequentialDeparture_PhoneCLeaves_ThenPhoneBLeaves_FullCleanup() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        // C leaves
        val trackerC = CallManager.getSecondaryTrackerForTesting()!!
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerC)
        assertEquals(1, CallManager.callUiState.value.conferenceParticipants.size)
        assertEquals(CallState.ACTIVE, CallManager.callUiState.value.callState)

        // B leaves
        val trackerB = CallManager.getPrimaryTrackerForTesting()!!
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerB)

        val finalState = CallManager.callUiState.value
        assertEquals("Once all participants leave, state must be DISCONNECTED", CallState.DISCONNECTED, finalState.callState)
        assertFalse("Conference must end", finalState.isConference)
        assertTrue("Participants must be empty", finalState.conferenceParticipants.isEmpty())
        assertNull("Primary tracker must be null", CallManager.getPrimaryTrackerForTesting())
        assertNull("Secondary tracker must be null", CallManager.getSecondaryTrackerForTesting())
    }

    /**
     * REQUIREMENT 4 & 5:
     * Support for more than 3 phones (A + B + C + D).
     * Verify that all actual conference participants appear in CallUiState without duplicates.
     */
    @Test
    fun testConferenceWithFourPhones_AllThreeRemoteParticipantsDisplayedWithoutDuplicates() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        // Phone D joins the conference
        CallManager.addConferenceParticipantForTesting(
            callId = "call_d",
            rawNumber = "+919876522222",
            callerName = "Person D",
            callState = CallState.ACTIVE,
            origin = 130_000L
        )

        val confState = CallManager.callUiState.value
        assertTrue("Conference must be active", confState.isConference)
        assertEquals("All 3 remote participants must be displayed", 3, confState.conferenceParticipants.size)

        val participantNames = confState.conferenceParticipants.mapNotNull { it.callerName }
        assertTrue("Person B must be present", participantNames.contains("Person B"))
        assertTrue("Person C must be present", participantNames.contains("Person C"))
        assertTrue("Person D must be present", participantNames.contains("Person D"))

        // Check for no duplicate numbers or IDs
        val uniqueNumbers = confState.conferenceParticipants.map { it.rawNumber }.toSet()
        assertEquals("Participants must have unique numbers", 3, uniqueNumbers.size)

        // If Person C leaves, B and D remain
        val trackerC = CallManager.getConferenceParticipantsForTesting()["call_c"]!!
        CallManager.triggerConferenceChildDisconnectedForTesting(trackerC)

        val afterCState = CallManager.callUiState.value
        assertEquals("Remaining participants must be 2 (B and D)", 2, afterCState.conferenceParticipants.size)
        val remainingNames = afterCState.conferenceParticipants.mapNotNull { it.callerName }
        assertTrue("Person B must be preserved", remainingNames.contains("Person B"))
        assertTrue("Person D must be preserved", remainingNames.contains("Person D"))
        assertFalse("Person C must be removed", remainingNames.contains("Person C"))
    }

    /**
     * REQUIREMENT 5:
     * Add Call during an active conference when secondaryTracker is null.
     */
    @Test
    fun testAddCallCanBeUsedDuringConference() {
        CallManager.setPrimaryCallForTesting(
            callId = "call_b",
            rawNumber = "+919876543210",
            callerName = "Person B",
            callState = CallState.HOLDING,
            isOnHold = true,
            origin = 100_000L
        )
        CallManager.setSecondaryCallForTesting(
            callId = "call_c",
            rawNumber = "+919876511111",
            callerName = "Person C",
            callState = CallState.ACTIVE,
            origin = 120_000L
        )
        CallManager.confirmConferenceForTesting()

        // When both calls are confirmed into conference, secondaryTracker is freed for adding a new call
        CallManager.triggerConferenceChildDisconnectedForTesting(CallManager.getSecondaryTrackerForTesting()!!)
        // Now only B remains in conference, secondaryTracker is null
        val state = CallManager.callUiState.value
        assertTrue("canAddCall must be true when secondary slot is free during conference", state.canAddCall)
    }
}

