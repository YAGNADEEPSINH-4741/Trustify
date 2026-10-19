package com.safecallai.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.safecallai.app.SafeCallApplication
import com.safecallai.app.domain.engine.ContactLookupHelper
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.ContactInfo
import com.safecallai.app.domain.models.CallerIdentityResult
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence
import com.safecallai.app.ui.call.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import java.util.Collections

enum class CallState {
    IDLE,
    RINGING,
    DIALING,
    ACTIVE,
    HOLDING,
    DISCONNECTED
}

/**
 * State representing the readiness, in-flight progress, or availability of real Telecom call merging.
 */
enum class MergeButtonState {
    READY,
    MERGING,
    MERGED,
    UNAVAILABLE,
    FAILED
}

/**
 * Individual call model representing a distinct participant in single, multi-call, or conference setups.
 */
data class IndividualCallState(
    val callId: String,
    val rawNumber: String = "",
    val normalizedNumber: String = "",
    val displayNumber: String = "",
    val contactInfo: ContactInfo = ContactInfo(),
    val classificationResult: ClassificationResult? = null,
    val callerIdentity: CallerIdentityResult? = null,
    val callState: CallState = CallState.IDLE,
    val durationSeconds: Long = 0L,
    val isOnHold: Boolean = false,
    val canHold: Boolean = true,
    val connectedElapsedRealtime: Long? = null,
    val isConferenceMember: Boolean = false
) {
    val callerName: String?
        get() = callerIdentity?.displayName?.ifBlank { null }
            ?: contactInfo.name?.ifBlank { null }
            ?: displayNumber.ifEmpty { rawNumber }.ifBlank { null }
}

/**
 * State flow emitted to the active in-call interface.
 * Backwards-compatible with single-call UI while supporting multi-call swapping and conference merging.
 */
data class CallUiState(
    val rawNumber: String = "",
    val normalizedNumber: String = "",
    val displayNumber: String = "",
    val contactInfo: ContactInfo = ContactInfo(),
    val classificationResult: ClassificationResult? = null,
    val callerIdentity: CallerIdentityResult? = null,
    val callState: CallState = CallState.IDLE,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val canHold: Boolean = false,
    val isSimulated: Boolean = false,

    // Multi-Call & Conference Extensions
    val primaryCall: IndividualCallState? = null,
    val secondaryCall: IndividualCallState? = null,
    val isMultiCall: Boolean = false,
    val isConference: Boolean = false,
    val conferenceParticipants: List<IndividualCallState> = emptyList(),
    val canSwap: Boolean = false,
    val canMerge: Boolean = false,
    val mergeState: MergeButtonState = MergeButtonState.UNAVAILABLE,
    val mergeFailureReason: String? = null,
    val canAddCall: Boolean = false,
    val isAddingCall: Boolean = false,
    val multiCallMessage: String? = null
)

/**
 * Singleton state controller managing both real Android Telecom Calls and
 * interactive on-device call simulations for testing and preview.
 *
 * Implements:
 * 1. Multi-Call tracking with separate primary and secondary calls.
 * 2. Add Call flow with contacts and manual dial pad.
 * 3. Hold & Resume preserving individual monotonic call durations.
 * 4. Swap Calls toggling between active and held calls without resetting timers.
 * 5. Real Android Telecom conference merging (`Call.conference(otherCall)`) with graceful carrier fallback.
 */
object CallManager {

    private val _callUiState = MutableStateFlow(CallUiState())
    val callUiState: StateFlow<CallUiState> = _callUiState.asStateFlow()

    var elapsedRealtimeProvider: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    private var inCallService: InCallService? = null
    private var durationJob: Job? = null
    var coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private var wakeLock: PowerManager.WakeLock? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private var appContext: Context? = null
    private var pendingOutgoingNumber: String? = null
    private var pendingOutgoingAccountHandle: PhoneAccountHandle? = null
    private var pendingHoldTimeoutJob: Job? = null

    private const val TAG = "TrustifyTelecom"
    private var isMergePending: Boolean = false
    private var lastMergeFailureReason: String? = null
    private var pendingMergeTimeoutJob: Job? = null
    internal var telecomConferenceSupportedOverride: Boolean? = null
    internal var simulateAsyncMergeForTesting: Boolean = false
    internal var simulateAsyncConferenceTerminationForTesting: Boolean = false
    private var lastRecordedDurationSeconds: Long = 0L

    private fun redact(num: String?): String {
        if (num.isNullOrBlank()) return "[none]"
        val digits = num.filter { it.isDigit() }
        return if (digits.length > 4) "***" + digits.takeLast(4) else "***"
    }

    private fun logDebug(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("DEBUG: [$tag] $msg")
        }
    }

    private fun logWarn(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            println("WARN: [$tag] $msg")
        }
    }

    private fun logError(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            println("ERROR: [$tag] $msg ${tr?.message ?: ""}")
        }
    }

    // Internal tracker for a single managed call
    internal class CallTracker(
        var call: Call?,
        val callId: String,
        val rawNumber: String,
        val normalizedNumber: String,
        val displayNumber: String,
        var contactInfo: ContactInfo,
        var classificationResult: ClassificationResult?,
        var callerIdentity: CallerIdentityResult?,
        var callState: CallState,
        var connectedElapsedRealtime: Long? = null,
        var durationSeconds: Long = 0L,
        var isOnHold: Boolean = false,
        var isConferenceMember: Boolean = false,
        var callback: Call.Callback? = null
    ) {
        fun toIndividualCallState(): IndividualCallState {
            val liveState = try { call?.state } catch (_: Exception) { null }
            val actualCallState = if (liveState != null) {
                mapTelecomStateToCallState(liveState)
            } else {
                callState
            }
            val actualIsOnHold = if (liveState != null) {
                liveState == Call.STATE_HOLDING
            } else {
                isOnHold || actualCallState == CallState.HOLDING
            }
            return IndividualCallState(
                callId = callId,
                rawNumber = rawNumber,
                normalizedNumber = normalizedNumber,
                displayNumber = displayNumber,
                contactInfo = contactInfo,
                classificationResult = classificationResult,
                callerIdentity = callerIdentity,
                callState = actualCallState,
                durationSeconds = durationSeconds,
                isOnHold = actualIsOnHold,
                canHold = true,
                connectedElapsedRealtime = connectedElapsedRealtime,
                isConferenceMember = isConferenceMember
            )
        }
    }

    private var primaryTracker: CallTracker? = null
    private var secondaryTracker: CallTracker? = null
    private var conferenceTracker: CallTracker? = null

    // Persistent conference participants tracking
    private val conferenceParticipants = Collections.synchronizedMap(LinkedHashMap<String, CallTracker>())

    // Centralized conference call termination state
    @Volatile
    private var isConferenceTerminating = false
    private val terminationRequestedCallIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var conferenceTerminationSafetyJob: Job? = null

    /**
     * Backwards-compatible accessor for tests and single-call management.
     */
    val activeCall: Call?
        get() = primaryTracker?.call ?: secondaryTracker?.call

    // =========================================================================
    // REGISTRATION & SYSTEM LIFECYCLE
    // =========================================================================

    fun registerInCallService(service: InCallService) {
        inCallService = service
        appContext = service.applicationContext
        service.callAudioState?.let { onAudioStateChanged(it) }
    }

    fun unregisterInCallService(service: InCallService) {
        if (inCallService == service) {
            inCallService = null
        }
    }

    fun onAudioStateChanged(audioState: CallAudioState) {
        val isSpeaker = (audioState.route == CallAudioState.ROUTE_SPEAKER)
        val isMuted = audioState.isMuted
        _callUiState.value = _callUiState.value.copy(
            isSpeakerOn = isSpeaker,
            isMuted = isMuted
        )
    }

    fun acquireWakeLocks(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Trustify:CpuAudioLock").apply {
                    setReferenceCounted(false)
                    acquire(20 * 60 * 1000L) // 20 min safety timeout
                }
            }
            if (proximityWakeLock == null && powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Trustify:ProximityLock").apply {
                    setReferenceCounted(false)
                    acquire(20 * 60 * 1000L)
                }
            }
        } catch (_: Exception) {}
    }

    fun releaseWakeLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (_: Exception) {}
        try {
            proximityWakeLock?.let { if (it.isHeld) it.release() }
            proximityWakeLock = null
        } catch (_: Exception) {}
    }

    // =========================================================================
    // CALL BINDING & TELECOM CALLBACKS
    // =========================================================================

    /**
     * Binds a real Android Telecom call received from SafeCallInCallService.
     */
    fun bindRealCall(
        call: Call,
        rawNumber: String,
        contactInfo: ContactInfo,
        classificationResult: ClassificationResult?,
        callerIdentity: CallerIdentityResult? = null,
        context: Context? = null
    ) {
        if (context != null) {
            appContext = context.applicationContext
            acquireWakeLocks(context)
        }

        // If conference parent, track as conference
        val isLikelyConference = call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true ||
                call.children.isNotEmpty() ||
                (isMergePending && primaryTracker != null && secondaryTracker != null) ||
                (conferenceTracker != null && call == conferenceTracker?.call)

        if (isLikelyConference) {
            logDebug(TAG, "bindRealCall: detected conference call (isMergePending=$isMergePending, confProp=${call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE)}, children=${call.children.size})")
            bindConferenceCall(call, context)
            return
        }

        // If conference is active and this call is attached to the conference, track as conference child
        if (conferenceTracker != null && (call.parent == conferenceTracker?.call || conferenceTracker?.call?.children?.contains(call) == true)) {
            registerConferenceChildCall(call)
            recomputeUiState()
            return
        }

        // If primary call already exists and is not this call, bind as secondary call
        if (primaryTracker != null && primaryTracker?.call != null && primaryTracker?.call != call && primaryTracker?.callState != CallState.DISCONNECTED) {
            bindSecondCall(call, rawNumber, contactInfo, classificationResult, callerIdentity, context)
            return
        }

        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)
        val initialCallState = mapTelecomStateToCallState(call.state)

        val initialIdentity = resolveInitialIdentity(call, rawNumber, normalized, contactInfo, callerIdentity)

        val tracker = CallTracker(
            call = call,
            callId = "call_1_${System.currentTimeMillis()}",
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayNumber = display,
            contactInfo = contactInfo,
            classificationResult = classificationResult,
            callerIdentity = initialIdentity,
            callState = initialCallState,
            isOnHold = (call.state == Call.STATE_HOLDING)
        )

        val callback = createCallCallback(tracker)
        tracker.callback = callback
        call.registerCallback(callback)

        primaryTracker?.callback?.let { primaryTracker?.call?.unregisterCallback(it) }
        primaryTracker = tracker

        if (initialCallState == CallState.ACTIVE) {
            tracker.connectedElapsedRealtime = elapsedRealtimeProvider()
            ensureDurationTimerRunning()
            initVoiceAudioRoute()
        }

        recomputeUiState()
    }

    /**
     * Binds a secondary call (Person B).
     */
    fun bindSecondCall(
        call: Call,
        rawNumber: String,
        contactInfo: ContactInfo,
        classificationResult: ClassificationResult?,
        callerIdentity: CallerIdentityResult? = null,
        context: Context? = null
    ) {
        if (context != null) {
            appContext = context.applicationContext
            acquireWakeLocks(context)
        }

        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)
        val initialCallState = mapTelecomStateToCallState(call.state)
        val initialIdentity = resolveInitialIdentity(call, rawNumber, normalized, contactInfo, callerIdentity)

        val tracker = CallTracker(
            call = call,
            callId = "call_2_${System.currentTimeMillis()}",
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayNumber = display,
            contactInfo = contactInfo,
            classificationResult = classificationResult,
            callerIdentity = initialIdentity,
            callState = initialCallState,
            isOnHold = (call.state == Call.STATE_HOLDING)
        )

        val callback = createCallCallback(tracker)
        tracker.callback = callback
        call.registerCallback(callback)

        secondaryTracker?.callback?.let { secondaryTracker?.call?.unregisterCallback(it) }
        secondaryTracker = tracker

        if (initialCallState == CallState.ACTIVE) {
            tracker.connectedElapsedRealtime = elapsedRealtimeProvider()
            ensureDurationTimerRunning()
            initVoiceAudioRoute()
        }

        recomputeUiState()
    }

    /**
     * Registers a conference child call discovered from Telecom conference.children or InCallService.
     */
    private fun registerConferenceChildCall(child: Call) {
        if (child == primaryTracker?.call) {
            primaryTracker?.isConferenceMember = true
            primaryTracker?.let { conferenceParticipants[it.callId] = it }
            return
        }
        if (child == secondaryTracker?.call) {
            secondaryTracker?.isConferenceMember = true
            secondaryTracker?.let { conferenceParticipants[it.callId] = it }
            return
        }
        if (conferenceParticipants.values.any { it.call == child }) {
            return
        }
        val rawNumber = try { child.details?.handle?.schemeSpecificPart ?: "Participant" } catch (_: Exception) { "Participant" }
        val contactInfo = appContext?.let { ContactLookupHelper.lookupContact(it, rawNumber) } ?: ContactInfo(name = rawNumber)
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)
        val tracker = CallTracker(
            call = child,
            callId = "conf_child_${System.identityHashCode(child)}",
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayNumber = display,
            contactInfo = contactInfo,
            classificationResult = null,
            callerIdentity = null,
            callState = mapTelecomStateToCallState(child.state),
            connectedElapsedRealtime = elapsedRealtimeProvider(),
            isConferenceMember = true
        )
        val cb = createCallCallback(tracker)
        tracker.callback = cb
        try { child.registerCallback(cb) } catch (_: Exception) {}
        conferenceParticipants[tracker.callId] = tracker
        logDebug(TAG, "registerConferenceChildCall: tracked conference child ${redact(rawNumber)} (${tracker.callId})")
    }

    /**
     * Binds a Telecom-created conference parent call.
     */
    private fun bindConferenceCall(call: Call, context: Context?) {
        val confTracker = CallTracker(
            call = call,
            callId = "conf_${System.currentTimeMillis()}",
            rawNumber = "Conference Call",
            normalizedNumber = "",
            displayNumber = "Conference Call",
            contactInfo = ContactInfo(name = "Conference Call"),
            classificationResult = null,
            callerIdentity = null,
            callState = CallState.ACTIVE,
            connectedElapsedRealtime = conferenceTracker?.connectedElapsedRealtime ?: elapsedRealtimeProvider(),
            isConferenceMember = true
        )
        conferenceTracker = confTracker

        isMergePending = false
        pendingMergeTimeoutJob?.cancel()
        pendingMergeTimeoutJob = null
        lastMergeFailureReason = null
        logDebug(TAG, "bindConferenceCall: conference confirmed by Telecom, tracking conference parent")

        primaryTracker?.isConferenceMember = true
        secondaryTracker?.isConferenceMember = true
        primaryTracker?.let { conferenceParticipants[it.callId] = it }
        secondaryTracker?.let { conferenceParticipants[it.callId] = it }

        try {
            val children = call.children ?: emptyList()
            for (child in children) {
                registerConferenceChildCall(child)
            }
        } catch (_: Exception) {}

        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                logDebug(TAG, "conferenceCallback.onStateChanged: state=$state")
                if (state == Call.STATE_DISCONNECTED) {
                    conferenceTracker?.callback?.let { c.unregisterCallback(it) }
                    conferenceTracker = null
                    if (isConferenceTerminating) {
                        reconcileConferenceTermination()
                    } else {
                        primaryTracker?.isConferenceMember = false
                        secondaryTracker?.isConferenceMember = false
                        if (primaryTracker == null && secondaryTracker == null && conferenceParticipants.isEmpty()) {
                            unbindCall()
                        } else {
                            reconcileStateWithTelecom()
                        }
                    }
                }
            }

            override fun onChildrenChanged(c: Call, children: List<Call>) {
                logDebug(TAG, "conferenceCallback.onChildrenChanged: childrenCount=${children.size}")
                for (child in children) {
                    registerConferenceChildCall(child)
                }
                // Detect departed children from Telecom conference
                val childSet = children.toSet()
                for ((id, tracker) in conferenceParticipants.toList()) {
                    val trackerCall = tracker.call
                    if (trackerCall != null && trackerCall != c && !childSet.contains(trackerCall)) {
                        val st = try { trackerCall.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
                        if (st == Call.STATE_DISCONNECTED || st == Call.STATE_DISCONNECTING) {
                            logDebug(TAG, "conferenceCallback.onChildrenChanged: child ${redact(tracker.rawNumber)} ($id) departed conference")
                            handleCallDisconnected(tracker)
                        }
                    }
                }
                if (isConferenceTerminating) {
                    reconcileConferenceTermination()
                } else {
                    recomputeUiState()
                }
            }
        }
        confTracker.callback = callback
        call.registerCallback(callback)
        recomputeUiState()
    }

    private fun createCallCallback(tracker: CallTracker): Call.Callback {
        return object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                when (state) {
                    Call.STATE_RINGING -> {
                        tracker.callState = CallState.RINGING
                        tracker.isOnHold = false
                    }
                    Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                        tracker.callState = CallState.DIALING
                        tracker.isOnHold = false
                    }
                    Call.STATE_HOLDING -> {
                        tracker.callState = CallState.HOLDING
                        tracker.isOnHold = true
                        ensureDurationTimerRunning()
                        if (tracker == primaryTracker) {
                            triggerPendingOutgoingCallIfAny()
                        }
                    }
                    Call.STATE_ACTIVE -> {
                        tracker.callState = CallState.ACTIVE
                        tracker.isOnHold = false
                        if (tracker.connectedElapsedRealtime == null) {
                            tracker.connectedElapsedRealtime = elapsedRealtimeProvider()
                        }
                        ensureDurationTimerRunning()
                        initVoiceAudioRoute()
                    }
                    Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                        if (tracker == primaryTracker && pendingOutgoingNumber != null) {
                            pendingOutgoingNumber = null
                            pendingOutgoingAccountHandle = null
                            pendingHoldTimeoutJob?.cancel()
                            pendingHoldTimeoutJob = null
                        }
                        // During active merge transition, Telecom temporarily fires state changes while reparenting
                        if (isMergePending && !isConferenceTerminating) {
                            logDebug(TAG, "createCallCallback: child call state=$state while merge is pending; preserving in tracking")
                            return
                        }
                        tracker.callState = CallState.DISCONNECTED
                        tracker.isOnHold = false
                        handleCallDisconnected(tracker)
                    }
                }
                recomputeUiState()
            }

            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) {
                logDebug(TAG, "onConferenceableCallsChanged: call=${redact(tracker.rawNumber)}, confCount=${conferenceableCalls.size}")
                recomputeUiState()
            }

            override fun onParentChanged(call: Call, parent: Call?) {
                logDebug(TAG, "onParentChanged: call=${redact(tracker.rawNumber)}, hasParent=${parent != null}")
                if (parent != null) {
                    tracker.isConferenceMember = true
                    conferenceParticipants[tracker.callId] = tracker
                    isMergePending = false
                    pendingMergeTimeoutJob?.cancel()
                    pendingMergeTimeoutJob = null
                    lastMergeFailureReason = null
                    if (conferenceTracker == null || conferenceTracker?.call != parent) {
                        bindConferenceCall(parent, appContext)
                    }
                    recomputeUiState()
                }
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                val currentIdentity = tracker.callerIdentity
                val updatedIdentity = if (currentIdentity == null || currentIdentity.source != CallerIdentitySource.SAVED_CONTACT) {
                    com.safecallai.app.domain.engine.TelecomCnapExtractor.extract(details, tracker.rawNumber) ?: currentIdentity
                } else {
                    currentIdentity
                }
                tracker.callerIdentity = updatedIdentity

                // Check conference property on details change with confirmed conference
                if (details.hasProperty(Call.Details.PROPERTY_CONFERENCE) && (call.children.size >= 2 || call.parent != null)) {
                    isMergePending = false
                    pendingMergeTimeoutJob?.cancel()
                    pendingMergeTimeoutJob = null
                    lastMergeFailureReason = null
                    tracker.isConferenceMember = true
                }
                recomputeUiState()
            }

            override fun onChildrenChanged(call: Call, children: List<Call>) {
                logDebug(TAG, "onChildrenChanged: call=${redact(tracker.rawNumber)}, childrenCount=${children.size}")
                if (children.size >= 2) {
                    isMergePending = false
                    pendingMergeTimeoutJob?.cancel()
                    pendingMergeTimeoutJob = null
                    lastMergeFailureReason = null
                    primaryTracker?.isConferenceMember = true
                    secondaryTracker?.isConferenceMember = true
                    recomputeUiState()
                }
            }
        }
    }

    private fun handleCallDisconnected(disconnectedTracker: CallTracker) {
        val finalSec = disconnectedTracker.connectedElapsedRealtime?.let { start ->
            ((elapsedRealtimeProvider() - start) / 1000L).coerceAtLeast(0L)
        } ?: disconnectedTracker.durationSeconds
        disconnectedTracker.durationSeconds = finalSec
        lastRecordedDurationSeconds = maxOf(lastRecordedDurationSeconds, finalSec)
        disconnectedTracker.callState = CallState.DISCONNECTED
        disconnectedTracker.isOnHold = false

        logDebug(
            TAG,
            "handleCallDisconnected: tracker=${disconnectedTracker.callId}, num=${redact(disconnectedTracker.rawNumber)}, " +
                    "isTerminating=$isConferenceTerminating"
        )

        if (isConferenceTerminating) {
            conferenceParticipants.remove(disconnectedTracker.callId)
            if (disconnectedTracker == secondaryTracker) {
                secondaryTracker?.callback?.let { secondaryTracker?.call?.unregisterCallback(it) }
                secondaryTracker = null
            } else if (disconnectedTracker == primaryTracker) {
                primaryTracker?.callback?.let { primaryTracker?.call?.unregisterCallback(it) }
                primaryTracker = null
            } else if (disconnectedTracker == conferenceTracker) {
                conferenceTracker?.callback?.let { conferenceTracker?.call?.unregisterCallback(it) }
                conferenceTracker = null
            } else {
                disconnectedTracker.callback?.let { disconnectedTracker.call?.unregisterCallback(it) }
            }
            reconcileConferenceTermination()
            return
        }

        val wasInConference = _callUiState.value.isConference ||
                conferenceTracker != null ||
                disconnectedTracker.isConferenceMember ||
                conferenceParticipants.containsKey(disconnectedTracker.callId)

        if (wasInConference) {
            logDebug(TAG, "handleCallDisconnected: conference participant ${redact(disconnectedTracker.rawNumber)} (${disconnectedTracker.callId}) departed")
            disconnectedTracker.callback?.let { disconnectedTracker.call?.unregisterCallback(it) }
            conferenceParticipants.remove(disconnectedTracker.callId)

            if (disconnectedTracker == secondaryTracker) {
                secondaryTracker = null
            } else if (disconnectedTracker == primaryTracker) {
                primaryTracker = null
                val secondary = secondaryTracker
                if (secondary != null && secondary.callState != CallState.DISCONNECTED) {
                    primaryTracker = secondary
                    secondaryTracker = null
                } else {
                    val nextActive = conferenceParticipants.values.firstOrNull {
                        it.callState != CallState.DISCONNECTED && it != disconnectedTracker
                    }
                    if (nextActive != null) {
                        primaryTracker = nextActive
                    }
                }
            }

            val remainingActiveParticipants = conferenceParticipants.values.filter {
                it.callState != CallState.DISCONNECTED && (it.call == null || try {
                    val st = it.call!!.state
                    st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
                } catch (_: Exception) { false })
            }
            val pActive = primaryTracker != null && primaryTracker?.callState != CallState.DISCONNECTED && (primaryTracker?.call == null || try {
                val st = primaryTracker?.call!!.state
                st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
            } catch (_: Exception) { false })
            val sActive = secondaryTracker != null && secondaryTracker?.callState != CallState.DISCONNECTED && (secondaryTracker?.call == null || try {
                val st = secondaryTracker?.call!!.state
                st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
            } catch (_: Exception) { false })
            val confActive = conferenceTracker != null && (conferenceTracker?.call == null || try {
                val st = conferenceTracker?.call!!.state
                st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
            } catch (_: Exception) { false })

            if (remainingActiveParticipants.isNotEmpty() || pActive || sActive || confActive) {
                logDebug(
                    TAG,
                    "handleCallDisconnected: conference remains active with remaining participants " +
                            "(participants=${remainingActiveParticipants.size}, pActive=$pActive, sActive=$sActive, confActive=$confActive)"
                )
                ensureDurationTimerRunning()
                recomputeUiState()
                return
            } else {
                logDebug(TAG, "handleCallDisconnected: all conference participants have departed; cleaning up")
                unbindCall()
                return
            }
        }

        if (disconnectedTracker == secondaryTracker) {
            secondaryTracker?.callback?.let { secondaryTracker?.call?.unregisterCallback(it) }
            secondaryTracker = null

            // If primary call remains and was holding, resume it
            val primary = primaryTracker
            if (primary != null && primary.callState != CallState.DISCONNECTED) {
                if (primary.callState == CallState.HOLDING || primary.isOnHold) {
                    try {
                        primary.call?.unhold()
                    } catch (_: Exception) {}
                    primary.callState = CallState.ACTIVE
                    primary.isOnHold = false
                }
                ensureDurationTimerRunning()
                recomputeUiState()
            } else {
                primaryTracker = null
                stopDurationTimer()
                releaseWakeLocks()
                _callUiState.value = _callUiState.value.copy(
                    callState = CallState.DISCONNECTED,
                    durationSeconds = finalSec,
                    primaryCall = null,
                    secondaryCall = null,
                    isMultiCall = false,
                    isConference = false
                )
            }
        } else if (disconnectedTracker == primaryTracker) {
            primaryTracker?.callback?.let { primaryTracker?.call?.unregisterCallback(it) }

            // Promote secondary to primary if alive
            val secondary = secondaryTracker
            if (secondary != null && secondary.callState != CallState.DISCONNECTED) {
                primaryTracker = secondary
                secondaryTracker = null
                if (primaryTracker?.callState == CallState.HOLDING || primaryTracker?.isOnHold == true) {
                    try {
                        primaryTracker?.call?.unhold()
                    } catch (_: Exception) {}
                    primaryTracker?.callState = CallState.ACTIVE
                    primaryTracker?.isOnHold = false
                }
                ensureDurationTimerRunning()
                recomputeUiState()
            } else {
                primaryTracker = null
                secondaryTracker = null
                stopDurationTimer()
                releaseWakeLocks()
                _callUiState.value = _callUiState.value.copy(
                    callState = CallState.DISCONNECTED,
                    durationSeconds = finalSec,
                    primaryCall = null,
                    secondaryCall = null,
                    isMultiCall = false,
                    isConference = false
                )
            }
        } else {
            recomputeUiState()
        }
    }

    private fun mapTelecomStateToCallState(state: Int): CallState {
        return when (state) {
            Call.STATE_ACTIVE -> CallState.ACTIVE
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallState.DIALING
            Call.STATE_HOLDING -> CallState.HOLDING
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallState.DISCONNECTED
            else -> CallState.RINGING
        }
    }

    private fun resolveInitialIdentity(
        call: Call?,
        rawNumber: String,
        normalized: String,
        contactInfo: ContactInfo,
        callerIdentity: CallerIdentityResult?
    ): CallerIdentityResult? {
        return when {
            callerIdentity != null -> callerIdentity
            contactInfo.isSavedContact && !contactInfo.name.isNullOrBlank() -> {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    displayName = contactInfo.name,
                    source = CallerIdentitySource.SAVED_CONTACT,
                    identityType = CallerIdentityType.SAVED_CONTACT,
                    confidence = IdentityConfidence.VERIFIED,
                    sourceLabel = "Saved contact",
                    isVerified = true
                )
            }
            else -> {
                if (call != null) {
                    com.safecallai.app.domain.engine.TelecomCnapExtractor.extract(call.details, rawNumber)
                        ?: com.safecallai.app.domain.engine.TraiNumberSeriesClassifier.classify(rawNumber)
                } else {
                    com.safecallai.app.domain.engine.TraiNumberSeriesClassifier.classify(rawNumber)
                }
            }
        }
    }

    private fun initVoiceAudioRoute() {
        try {
            val isSpeaker = inCallService?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER
            if (!isSpeaker) {
                inCallService?.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // MULTI-CALL ACTIONS: ADD CALL, HOLD, SWAP, MERGE
    // =========================================================================

    fun showAddCall(show: Boolean) {
        _callUiState.value = _callUiState.value.copy(isAddingCall = show)
    }

    fun dismissMultiCallMessage() {
        _callUiState.value = _callUiState.value.copy(multiCallMessage = null)
    }

    fun showMergeUnavailableReason(customReason: String? = null) {
        val reason = customReason
            ?: _callUiState.value.mergeFailureReason
            ?: "Both calls must be connected to merge."
        _callUiState.value = _callUiState.value.copy(multiCallMessage = reason)
    }

    /**
     * Initiates placing a second call while preserving the existing active call.
     * Puts the current call on hold if supported.
     */
    fun addCall(context: Context, rawNumber: String) {
        val cleanNumber = rawNumber.trim()
        if (cleanNumber.isBlank()) return

        appContext = context.applicationContext
        _callUiState.value = _callUiState.value.copy(isAddingCall = false)

        // Prevent adding a 3rd call if both slots are full
        if (primaryTracker != null && secondaryTracker != null &&
            primaryTracker?.callState != CallState.DISCONNECTED &&
            secondaryTracker?.callState != CallState.DISCONNECTED
        ) {
            _callUiState.value = _callUiState.value.copy(
                multiCallMessage = "Maximum number of simultaneous calls reached."
            )
            return
        }

        val currentPrimary = primaryTracker
        // Simulated mode or unit tests without real Call
        if (_callUiState.value.isSimulated || currentPrimary?.call == null) {
            currentPrimary?.callState = CallState.HOLDING
            currentPrimary?.isOnHold = true

            val contactInfo = ContactLookupHelper.lookupContact(context, cleanNumber)
            val normalized = PhoneNumberNormalizer.normalize(cleanNumber)
            val display = PhoneNumberNormalizer.formatForDisplay(normalized)

            val secTracker = CallTracker(
                call = null,
                callId = "call_sim_2_${System.currentTimeMillis()}",
                rawNumber = cleanNumber,
                normalizedNumber = normalized,
                displayNumber = display,
                contactInfo = contactInfo,
                classificationResult = null,
                callerIdentity = resolveInitialIdentity(
                    null, cleanNumber, normalized, contactInfo, null
                ),
                callState = CallState.DIALING,
                isOnHold = false
            )
            secondaryTracker = secTracker
            recomputeUiState()

            // Simulate call answering after 1.5 seconds
            coroutineScope.launch {
                delay(1500L)
                if (secondaryTracker == secTracker) {
                    secTracker.callState = CallState.ACTIVE
                    secTracker.connectedElapsedRealtime = elapsedRealtimeProvider()
                    ensureDurationTimerRunning()
                    recomputeUiState()
                }
            }
            return
        }

        // Real Android Telecom Call:
        val realCall = currentPrimary.call
        val canHold = realCall?.details?.can(Call.Details.CAPABILITY_HOLD) ?: true
        if (!canHold) {
            _callUiState.value = _callUiState.value.copy(
                multiCallMessage = "Call hold is not supported by your network carrier for this call."
            )
            return
        }

        val accountHandle = realCall?.details?.accountHandle

        // If primary call is ALREADY in STATE_HOLDING, place the outgoing call immediately
        if (realCall?.state == Call.STATE_HOLDING || currentPrimary.callState == CallState.HOLDING) {
            placeOutgoingCall(context, cleanNumber, accountHandle)
            return
        }

        // Otherwise, place call into pending and request hold asynchronously
        pendingOutgoingNumber = cleanNumber
        pendingOutgoingAccountHandle = accountHandle

        try {
            realCall?.hold()
        } catch (e: Exception) {
            pendingOutgoingNumber = null
            pendingOutgoingAccountHandle = null
            _callUiState.value = _callUiState.value.copy(
                multiCallMessage = "Failed to put current call on hold: ${e.message ?: "Network error"}"
            )
            return
        }

        // Safety timeout: if Telecom / carrier doesn't reach STATE_HOLDING within 4.5 seconds
        pendingHoldTimeoutJob?.cancel()
        pendingHoldTimeoutJob = coroutineScope.launch {
            delay(4500L)
            if (pendingOutgoingNumber != null) {
                pendingOutgoingNumber = null
                pendingOutgoingAccountHandle = null
                _callUiState.value = _callUiState.value.copy(
                    multiCallMessage = "Your carrier took too long to hold the current call. Your existing call remains active."
                )
                currentPrimary.let { p ->
                    p.isOnHold = (p.call?.state == Call.STATE_HOLDING)
                    p.callState = mapTelecomStateToCallState(p.call?.state ?: Call.STATE_ACTIVE)
                }
                recomputeUiState()
            }
        }
    }

    private fun triggerPendingOutgoingCallIfAny() {
        val number = pendingOutgoingNumber ?: return
        val handle = pendingOutgoingAccountHandle
        pendingOutgoingNumber = null
        pendingOutgoingAccountHandle = null
        pendingHoldTimeoutJob?.cancel()
        pendingHoldTimeoutJob = null

        val ctx = appContext ?: inCallService
        if (ctx != null) {
            placeOutgoingCall(ctx, number, handle)
        }
    }

    private fun placeOutgoingCall(
        context: Context,
        phoneNumber: String,
        accountHandle: PhoneAccountHandle? = null
    ) {
        val uri = Uri.fromParts("tel", phoneNumber, null)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        val extras = Bundle().apply {
            if (accountHandle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
            }
        }

        if (telecomManager != null && ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                telecomManager.placeCall(uri, extras)
                return
            } catch (e: Exception) {
                // If SecurityException or Telecom error, fallback
            }
        }

        // Fallback: Intent.ACTION_CALL
        try {
            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (accountHandle != null) {
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
                }
            }
            context.startActivity(callIntent)
        } catch (e: Exception) {
            // Final fallback: Intent.ACTION_DIAL
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            } catch (_: Exception) {
                _callUiState.value = _callUiState.value.copy(
                    multiCallMessage = "Unable to place call to $phoneNumber."
                )
            }
        }
    }

    /**
     * Swaps between active and held calls.
     * Preserves both calls' connection times, identities, and individual duration counters.
     */
    fun swapCalls() {
        val p = primaryTracker
        val s = secondaryTracker
        if (p == null || s == null) return

        val pState = p.call?.state ?: if (p.callState == CallState.ACTIVE) Call.STATE_ACTIVE else Call.STATE_HOLDING
        val sState = s.call?.state ?: if (s.callState == CallState.ACTIVE) Call.STATE_ACTIVE else Call.STATE_HOLDING

        val active = if (pState == Call.STATE_ACTIVE) p else s
        val held = if (active == p) s else p

        // In real Telecom mode:
        if (active.call != null && held.call != null) {
            try {
                active.call?.hold()
                held.call?.unhold()
            } catch (_: Exception) {}
        }

        // Update state models: swap active and holding states without swapping tracker references
        active.callState = CallState.HOLDING
        active.isOnHold = true

        held.callState = CallState.ACTIVE
        held.isOnHold = false
        if (held.connectedElapsedRealtime == null) {
            held.connectedElapsedRealtime = elapsedRealtimeProvider()
        }

        ensureDurationTimerRunning()
        recomputeUiState()
    }

    /**
     * Merges the two active/held calls into an official cellular conference call.
     * Validates both real Telecom Call objects, inspects capabilities including
     * conferenceableCalls and mergeConference, and gracefully handles carrier limitations.
     * Prevents duplicate requests while merge is pending and awaits Telecom confirmation.
     */
    fun mergeCalls() {
        logDebug(TAG, "mergeCalls: Merge button tapped. isMergePending=$isMergePending, currentMergeState=${_callUiState.value.mergeState}")

        // Prevent accidental duplicate requests while a merge is pending
        if (isMergePending) {
            logDebug(TAG, "mergeCalls: request ignored because merge is already pending")
            return
        }

        val p = primaryTracker
        val s = secondaryTracker
        if (p == null || s == null) {
            logWarn(TAG, "mergeCalls: cannot merge, primary or secondary call tracker is null")
            val reason = "Cannot merge calls: two calls are not present."
            lastMergeFailureReason = reason
            _callUiState.value = _callUiState.value.copy(
                multiCallMessage = reason,
                mergeState = MergeButtonState.UNAVAILABLE
            )
            return
        }

        // Real Telecom mode
        if (p.call != null && s.call != null) {
            val pCall = p.call!!
            val sCall = s.call!!
            val pState = try { pCall.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
            val sState = try { sCall.state } catch (_: Exception) { Call.STATE_DISCONNECTED }

            logDebug(
                TAG,
                "mergeCalls: real Telecom calls found. pState=$pState, sState=$sState, " +
                        "pNum=${redact(p.rawNumber)}, sNum=${redact(s.rawNumber)}"
            )

            // Verify both calls are still connected
            val pConnected = (pState == Call.STATE_ACTIVE || pState == Call.STATE_HOLDING)
            val sConnected = (sState == Call.STATE_ACTIVE || sState == Call.STATE_HOLDING)
            if (!pConnected || !sConnected) {
                val reason = "Both calls must be connected to merge."
                lastMergeFailureReason = reason
                logWarn(TAG, "mergeCalls: aborting merge because calls not both connected: pConnected=$pConnected, sConnected=$sConnected")
                _callUiState.value = _callUiState.value.copy(multiCallMessage = reason)
                recomputeUiState()
                return
            }

            // Inspect capabilities and phone accounts
            val pConfList = try { pCall.conferenceableCalls } catch (_: Exception) { emptyList<Call>() }
            val sConfList = try { sCall.conferenceableCalls } catch (_: Exception) { emptyList<Call>() }
            val pCanMergeConf = try { pCall.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true } catch (_: Exception) { false }
            val sCanMergeConf = try { sCall.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true } catch (_: Exception) { false }
            val pAccount = try { pCall.details?.accountHandle } catch (_: Exception) { null }
            val sAccount = try { sCall.details?.accountHandle } catch (_: Exception) { null }

            logDebug(
                TAG,
                "mergeCalls: pConfCount=${pConfList.size}, sConfCount=${sConfList.size}, " +
                        "pCanMerge=$pCanMergeConf, sCanMerge=$sCanMergeConf, " +
                        "pAccount=${pAccount?.id}, sAccount=${sAccount?.id}"
            )

            if (pAccount != null && sAccount != null && pAccount != sAccount) {
                val reason = "Calls on different SIM cards or phone accounts cannot be merged."
                lastMergeFailureReason = reason
                logWarn(TAG, "mergeCalls: aborting merge because phone accounts differ")
                _callUiState.value = _callUiState.value.copy(multiCallMessage = reason)
                recomputeUiState()
                return
            }

            // Enter MERGING state
            isMergePending = true
            lastMergeFailureReason = null
            recomputeUiState()

            var mergeIssued = false
            try {
                when {
                    pConfList.contains(sCall) -> {
                        logDebug(TAG, "mergeCalls: invoking pCall.conference(sCall)")
                        pCall.conference(sCall)
                        mergeIssued = true
                    }
                    sConfList.contains(pCall) -> {
                        logDebug(TAG, "mergeCalls: invoking sCall.conference(pCall)")
                        sCall.conference(pCall)
                        mergeIssued = true
                    }
                    pCanMergeConf -> {
                        logDebug(TAG, "mergeCalls: invoking pCall.mergeConference()")
                        pCall.mergeConference()
                        mergeIssued = true
                    }
                    sCanMergeConf -> {
                        logDebug(TAG, "mergeCalls: invoking sCall.mergeConference()")
                        sCall.mergeConference()
                        mergeIssued = true
                    }
                    else -> {
                        // Standard GSM / VoLTE / IMS fallback: active call conferences held call
                        val activeCall = if (pState == Call.STATE_ACTIVE) pCall else sCall
                        val heldCall = if (activeCall == pCall) sCall else pCall
                        logDebug(TAG, "mergeCalls: standard cellular fallback invoking activeCall.conference(heldCall)")
                        try {
                            activeCall.conference(heldCall)
                            mergeIssued = true
                        } catch (eActive: Exception) {
                            logWarn(TAG, "mergeCalls: activeCall.conference threw: ${eActive.message}. Trying heldCall.conference")
                            heldCall.conference(activeCall)
                            mergeIssued = true
                        }
                    }
                }
            } catch (e: Exception) {
                logError(TAG, "mergeCalls: exception during conference invocation", e)
                isMergePending = false
                val reason = "Conference call failed: ${e.localizedMessage ?: "Carrier rejected merge"}"
                lastMergeFailureReason = reason
                _callUiState.value = _callUiState.value.copy(multiCallMessage = reason)
                recomputeUiState()
                return
            }

            if (mergeIssued) {
                logDebug(TAG, "mergeCalls: conference command dispatched, awaiting Telecom confirmation")
                pendingMergeTimeoutJob?.cancel()
                pendingMergeTimeoutJob = coroutineScope.launch {
                    delay(6000L)
                    if (isMergePending && !_callUiState.value.isConference) {
                        logWarn(TAG, "mergeCalls: timed out waiting for Telecom conference confirmation")
                        isMergePending = false
                        reconcileStateWithTelecom()
                        if (!_callUiState.value.isConference) {
                            val reason = "Conference call was not confirmed by your network carrier. Your calls remain connected."
                            lastMergeFailureReason = reason
                            _callUiState.value = _callUiState.value.copy(multiCallMessage = reason)
                        }
                        recomputeUiState()
                    }
                }
            }
            return
        }

        // In simulated mode:
        if (_callUiState.value.isSimulated) {
            if (telecomConferenceSupportedOverride == false) {
                val reason = "Conference call is not supported by your network carrier for these calls."
                lastMergeFailureReason = reason
                _callUiState.value = _callUiState.value.copy(multiCallMessage = reason)
                recomputeUiState()
                return
            }

            if (simulateAsyncMergeForTesting) {
                isMergePending = true
                lastMergeFailureReason = null
                recomputeUiState()
                return
            }

            isMergePending = false
            lastMergeFailureReason = null
            p.isConferenceMember = true
            s.isConferenceMember = true
            p.callState = CallState.ACTIVE
            p.isOnHold = false
            s.callState = CallState.ACTIVE
            s.isOnHold = false
            conferenceParticipants[p.callId] = p
            conferenceParticipants[s.callId] = s
            if (p.connectedElapsedRealtime == null) {
                p.connectedElapsedRealtime = elapsedRealtimeProvider()
            }
            ensureDurationTimerRunning()
            recomputeUiState()
        }
    }

    /**
     * Disconnects a specific call (e.g. secondary call).
     */
    fun hangUpSecondaryCall() {
        val sec = secondaryTracker ?: return
        if (sec.call != null) {
            try {
                sec.call?.disconnect()
            } catch (_: Exception) {}
        } else {
            handleCallDisconnected(sec)
        }
    }

    // =========================================================================
    // STANDARD CONTROLS (ANSWER, DECLINE, MUTE, SPEAKER, HOLD)
    // =========================================================================

    fun answer() {
        val current = primaryTracker?.call
        if (current != null) {
            try {
                current.answer(VideoProfile.STATE_AUDIO_ONLY)
                inCallService?.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            } catch (_: Exception) {}
        } else if (_callUiState.value.isSimulated) {
            primaryTracker?.let {
                if (it.connectedElapsedRealtime == null) {
                    it.connectedElapsedRealtime = elapsedRealtimeProvider()
                }
                it.callState = CallState.ACTIVE
                it.isOnHold = false
            }
            ensureDurationTimerRunning()
            recomputeUiState()
        }
    }

    fun decline() {
        val current = primaryTracker?.call
        if (current != null) {
            try {
                if (current.state == Call.STATE_RINGING) {
                    current.reject(false, null)
                } else {
                    current.disconnect()
                }
            } catch (_: Exception) {}
        } else if (_callUiState.value.isSimulated) {
            unbindCall()
        }
    }

    /**
     * Disconnects a real or simulated Telecom call.
     * Prevents duplicate disconnect requests on the same call instance unless forced during reconciliation retry.
     */
    private fun disconnectCallSafely(call: Call?, trackerId: String? = null, forceRetry: Boolean = false) {
        if (call == null) return
        val key = trackerId ?: "call_${System.identityHashCode(call)}"
        if (!forceRetry && !terminationRequestedCallIds.add(key)) {
            logDebug(TAG, "disconnectCallSafely: disconnect already requested for $key, skipping duplicate")
            return
        }
        val state = try { call.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            logDebug(TAG, "disconnectCallSafely: call $key already disconnected/disconnecting (state=$state)")
            return
        }
        try {
            logDebug(TAG, "disconnectCallSafely: sending disconnect() to call $key (state=$state, forceRetry=$forceRetry)")
            if (state == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call.disconnect()
            }
        } catch (e: Exception) {
            logError(TAG, "disconnectCallSafely: error disconnecting call $key", e)
        }
    }

    /**
     * Centralized conference call termination flow.
     * Ensures all conference participants (conference parent, child calls, and any independently
     * tracked conference members) are disconnected properly without leaving any participant abandoned or active.
     */
    fun endConference() {
        val conf = conferenceTracker
        val primary = primaryTracker
        val secondary = secondaryTracker

        logDebug(
            TAG,
            "endConference: requested. isConference=${_callUiState.value.isConference}, " +
                    "confTracker=${conf?.callId}, " +
                    "pTracker=${primary?.callId} (isConf=${primary?.isConferenceMember}), " +
                    "sTracker=${secondary?.callId} (isConf=${secondary?.isConferenceMember}), " +
                    "confParticipantsCount=${conferenceParticipants.size}"
        )

        if (isConferenceTerminating) {
            logDebug(TAG, "endConference: already terminating, reconciling remaining active calls")
            reconcileConferenceTermination()
            return
        }

        isConferenceTerminating = true
        terminationRequestedCallIds.clear()

        // In simulated mode or when no real Telecom Call objects exist:
        val hasRealCalls = (conf?.call != null || primary?.call != null || secondary?.call != null ||
                conferenceParticipants.values.any { it.call != null })
        if (!hasRealCalls && !simulateAsyncConferenceTerminationForTesting) {
            logDebug(TAG, "endConference: simulated mode, disconnecting all trackers")
            val finalDuration = maxOf(
                primary?.durationSeconds ?: 0L,
                secondary?.durationSeconds ?: 0L,
                conf?.durationSeconds ?: 0L,
                conferenceParticipants.values.maxOfOrNull { it.durationSeconds } ?: 0L,
                lastRecordedDurationSeconds,
                _callUiState.value.durationSeconds
            )
            lastRecordedDurationSeconds = finalDuration
            primary?.callState = CallState.DISCONNECTED
            secondary?.callState = CallState.DISCONNECTED
            conf?.callState = CallState.DISCONNECTED
            conferenceParticipants.values.forEach { it.callState = CallState.DISCONNECTED }
            conferenceParticipants.clear()
            primaryTracker = null
            secondaryTracker = null
            conferenceTracker = null
            isConferenceTerminating = false
            unbindCall()
            return
        }

        // 1. Locate the actual Telecom conference parent if present
        val confCall = conf?.call
            ?: primary?.call?.parent
            ?: secondary?.call?.parent
            ?: conferenceParticipants.values.firstNotNullOfOrNull { it.call?.parent }
            ?: findConferenceParentInTelecom()

        val confChildren = try {
            confCall?.children ?: emptyList()
        } catch (_: Exception) {
            emptyList<Call>()
        }

        logDebug(
            TAG,
            "endConference: resolved confCall=${confCall?.let { "Call@${it.hashCode()}" } ?: "none"}, " +
                    "childrenCount=${confChildren.size}"
        )

        // 2. Disconnect the conference parent first if available
        if (confCall != null) {
            val confState = try { confCall.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
            if (confState != Call.STATE_DISCONNECTED && confState != Call.STATE_DISCONNECTING) {
                disconnectCallSafely(confCall, conf?.callId ?: "conf_${System.identityHashCode(confCall)}")
            }
        }

        // 3. Disconnect EVERY child and participant call!
        // On cellular networks (VoLTE/VoNR/Samsung IMS), disconnecting confCall alone does NOT
        // reliably disconnect participants on hold (e.g. Phone B).
        // Disconnecting every participant call instructs the modem to terminate all underlying call legs.
        val targetCalls = mutableListOf<Pair<Call, String>>()

        for (child in confChildren) {
            targetCalls.add(child to "child_${System.identityHashCode(child)}")
        }

        for ((id, tracker) in conferenceParticipants) {
            val c = tracker.call
            if (c != null) {
                targetCalls.add(c to id)
            }
        }

        if (primary != null && primary.call != null) {
            targetCalls.add(primary.call!! to primary.callId)
        }
        if (secondary != null && secondary.call != null) {
            targetCalls.add(secondary.call!! to secondary.callId)
        }

        val liveCalls = try { inCallService?.calls ?: emptyList() } catch (_: Exception) { emptyList() }
        for (live in liveCalls) {
            if (live == confCall || confChildren.contains(live) || live.parent == confCall) {
                targetCalls.add(live to "live_${System.identityHashCode(live)}")
            }
        }

        val distinctTargets = targetCalls.distinctBy { it.first }
        for ((call, id) in distinctTargets) {
            if (call != confCall) {
                disconnectCallSafely(call, id)
            }
        }

        // 4. Launch asynchronous watcher to handle asynchronous Telecom callback ordering,
        // reconcile stubborn held calls, and ensure cleanup only when Telecom confirms.
        startConferenceTerminationWatcher()

        recomputeUiState()
    }

    /**
     * Reconciles conference call termination state against live Telecom calls.
     * Ensures that every participant of the conference is disconnected and cleanup occurs
     * only when Telecom confirms all calls have disconnected.
     */
    private fun reconcileConferenceTermination() {
        if (!isConferenceTerminating) return

        val p = primaryTracker
        val s = secondaryTracker
        val conf = conferenceTracker

        val liveCalls = try { inCallService?.calls ?: emptyList() } catch (_: Exception) { emptyList() }
        val confCall = conf?.call
            ?: p?.call?.parent
            ?: s?.call?.parent
            ?: conferenceParticipants.values.firstNotNullOfOrNull { it.call?.parent }
            ?: findConferenceParentInTelecom()

        val confChildren = try {
            confCall?.children ?: emptyList()
        } catch (_: Exception) {
            emptyList<Call>()
        }

        // Reconcile conference parent
        if (confCall != null) {
            val state = try { confCall.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
            if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                logWarn(TAG, "reconcileConferenceTermination: confCall still active ($state). Disconnecting.")
                disconnectCallSafely(confCall, conf?.callId ?: "conf_${System.identityHashCode(confCall)}", forceRetry = true)
            } else if (state == Call.STATE_DISCONNECTED && conf != null) {
                handleCallDisconnected(conf)
            }
        }

        // Reconcile conference participants
        for ((id, tracker) in conferenceParticipants) {
            val c = tracker.call
            if (c != null) {
                val state = try { c.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
                if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                    logWarn(TAG, "reconcileConferenceTermination: participant ${redact(tracker.rawNumber)} ($id) still active (state=$state). Disconnecting.")
                    disconnectCallSafely(c, id, forceRetry = true)
                } else if (state == Call.STATE_DISCONNECTED) {
                    tracker.callState = CallState.DISCONNECTED
                }
            } else if (_callUiState.value.isSimulated && !simulateAsyncConferenceTerminationForTesting) {
                tracker.callState = CallState.DISCONNECTED
            }
        }

        // Reconcile primary call
        if (p != null) {
            val pCall = p.call
            if (pCall != null) {
                val state = try { pCall.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
                if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                    logWarn(TAG, "reconcileConferenceTermination: primary ${redact(p.rawNumber)} still active (state=$state). Disconnecting.")
                    disconnectCallSafely(pCall, p.callId, forceRetry = true)
                } else if (state == Call.STATE_DISCONNECTED) {
                    handleCallDisconnected(p)
                }
            } else if (_callUiState.value.isSimulated && !simulateAsyncConferenceTerminationForTesting) {
                handleCallDisconnected(p)
            }
        }

        // Reconcile secondary call
        if (s != null) {
            val sCall = s.call
            if (sCall != null) {
                val state = try { sCall.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
                if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                    logWarn(TAG, "reconcileConferenceTermination: secondary ${redact(s.rawNumber)} still active (state=$state). Disconnecting.")
                    disconnectCallSafely(sCall, s.callId, forceRetry = true)
                } else if (state == Call.STATE_DISCONNECTED) {
                    handleCallDisconnected(s)
                }
            } else if (_callUiState.value.isSimulated && !simulateAsyncConferenceTerminationForTesting) {
                handleCallDisconnected(s)
            }
        }

        // Reconcile live calls belonging to the conference
        for (live in liveCalls) {
            val isConfRelated = live == confCall ||
                    confChildren.contains(live) ||
                    live.parent == confCall ||
                    live == p?.call ||
                    live == s?.call ||
                    conferenceParticipants.values.any { it.call == live }
            if (isConfRelated) {
                val state = try { live.state } catch (_: Exception) { Call.STATE_DISCONNECTED }
                if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                    logWarn(TAG, "reconcileConferenceTermination: live conference call still active (state=$state). Disconnecting.")
                    disconnectCallSafely(live, "live_${System.identityHashCode(live)}", forceRetry = true)
                }
            }
        }

        val hasActiveConfCall = confCall != null && try {
            val st = confCall.state
            st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
        } catch (_: Exception) { false }

        val hasActiveChildren = confChildren.any { child ->
            try {
                val st = child.state
                st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
            } catch (_: Exception) { false }
        }

        val hasActiveParticipant = conferenceParticipants.values.any { tracker ->
            val c = tracker.call
            if (c != null) {
                try {
                    val st = c.state
                    st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
                } catch (_: Exception) { false }
            } else {
                tracker.callState != CallState.DISCONNECTED
            }
        }

        val hasActivePrimary = p != null && p.callState != CallState.DISCONNECTED && (p.call == null || try {
            val st = p.call!!.state
            st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
        } catch (_: Exception) { false })

        val hasActiveSecondary = s != null && s.callState != CallState.DISCONNECTED && (s.call == null || try {
            val st = s.call!!.state
            st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
        } catch (_: Exception) { false })

        val hasActiveLiveConfCalls = liveCalls.any { live ->
            val isConfRelated = live == confCall ||
                    confChildren.contains(live) ||
                    live.parent == confCall ||
                    live == p?.call ||
                    live == s?.call ||
                    conferenceParticipants.values.any { it.call == live }
            if (isConfRelated) {
                try {
                    val st = live.state
                    st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING
                } catch (_: Exception) { false }
            } else {
                false
            }
        }

        val remainingActive = hasActiveConfCall || hasActiveChildren || hasActiveParticipant ||
                hasActivePrimary || hasActiveSecondary || hasActiveLiveConfCalls

        logDebug(
            TAG,
            "reconcileConferenceTermination: remainingActive=$remainingActive " +
                    "(conf=$hasActiveConfCall, children=$hasActiveChildren, participants=$hasActiveParticipant, " +
                    "p=$hasActivePrimary, s=$hasActiveSecondary, live=$hasActiveLiveConfCalls)"
        )

        if (!remainingActive) {
            logDebug(TAG, "reconcileConferenceTermination: all conference calls confirmed disconnected. Performing final cleanup.")
            isConferenceTerminating = false
            conferenceTerminationSafetyJob?.cancel()
            conferenceTerminationSafetyJob = null
            conferenceParticipants.clear()
            unbindCall()
        } else {
            recomputeUiState()
        }
    }

    private fun startConferenceTerminationWatcher() {
        conferenceTerminationSafetyJob?.cancel()
        conferenceTerminationSafetyJob = coroutineScope.launch {
            val startTime = elapsedRealtimeProvider()
            var tick = 0
            while (isActive && isConferenceTerminating) {
                delay(400L)
                tick++
                reconcileConferenceTermination()
                if (!isConferenceTerminating) break

                val elapsed = elapsedRealtimeProvider() - startTime
                if (tick % 3 == 0) {
                    logDebug(TAG, "startConferenceTerminationWatcher: tick=$tick, elapsed=${elapsed}ms, retrying disconnect on stubborn calls")
                    reconcileConferenceTermination()
                }

                if (elapsed >= 8000L) {
                    logWarn(TAG, "startConferenceTerminationWatcher: timeout (8s) reached, performing final forced cleanup")
                    val liveCalls = try { inCallService?.calls ?: emptyList() } catch (_: Exception) { emptyList() }
                    val remainingConfCalls = liveCalls.filter { live ->
                        live == conferenceTracker?.call ||
                                live == primaryTracker?.call ||
                                live == secondaryTracker?.call ||
                                conferenceParticipants.values.any { it.call == live } ||
                                conferenceTracker?.call?.children?.contains(live) == true ||
                                live.parent == conferenceTracker?.call
                    }
                    if (remainingConfCalls.isEmpty()) {
                        isConferenceTerminating = false
                        unbindCall()
                    } else {
                        logWarn(TAG, "startConferenceTerminationWatcher: forcing disconnect on ${remainingConfCalls.size} remaining calls")
                        remainingConfCalls.forEach { disconnectCallSafely(it, forceRetry = true) }
                    }
                    break
                }
            }
        }
    }

    private fun findConferenceParentInTelecom(): Call? {
        val service = inCallService ?: return null
        return try {
            service.calls.firstOrNull { call ->
                call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true ||
                        call.children.isNotEmpty()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hangUp() {
        val isConf = _callUiState.value.isConference ||
                conferenceTracker != null ||
                conferenceParticipants.isNotEmpty() ||
                (primaryTracker?.isConferenceMember == true && secondaryTracker?.isConferenceMember == true)

        if (isConf) {
            endConference()
            return
        }

        // Disconnect the currently active call if multiple calls exist, preserving any held call
        val targetTracker = when {
            secondaryTracker?.callState == CallState.ACTIVE -> secondaryTracker
            primaryTracker?.callState == CallState.ACTIVE -> primaryTracker
            primaryTracker != null -> primaryTracker
            secondaryTracker != null -> secondaryTracker
            else -> null
        }
        val targetCall = targetTracker?.call
        if (targetCall != null) {
            try {
                if (targetCall.state == Call.STATE_RINGING) {
                    targetCall.reject(false, null)
                } else {
                    targetCall.disconnect()
                }
            } catch (_: Exception) {}
        } else if (_callUiState.value.isSimulated) {
            if (targetTracker != null && (primaryTracker != null && secondaryTracker != null)) {
                handleCallDisconnected(targetTracker)
            } else {
                unbindCall()
            }
        }
    }

    fun toggleMute(context: Context) {
        val service = inCallService
        if (service != null) {
            val currentMuted = service.callAudioState?.isMuted ?: _callUiState.value.isMuted
            val newMute = !currentMuted
            service.setMuted(newMute)
            _callUiState.value = _callUiState.value.copy(isMuted = newMute)
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val newMute = !_callUiState.value.isMuted
            audioManager.isMicrophoneMute = newMute
            _callUiState.value = _callUiState.value.copy(isMuted = newMute)
        }
    }

    fun toggleSpeaker(context: Context) {
        val service = inCallService
        if (service != null) {
            val audioState = service.callAudioState
            val currentRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
            val newRoute = if (currentRoute == CallAudioState.ROUTE_SPEAKER) {
                CallAudioState.ROUTE_EARPIECE
            } else {
                CallAudioState.ROUTE_SPEAKER
            }
            service.setAudioRoute(newRoute)
            _callUiState.value = _callUiState.value.copy(
                isSpeakerOn = (newRoute == CallAudioState.ROUTE_SPEAKER)
            )
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val newSpeaker = !_callUiState.value.isSpeakerOn
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = newSpeaker
            _callUiState.value = _callUiState.value.copy(isSpeakerOn = newSpeaker)
        }
    }

    fun toggleHold(context: Context) {
        val active = if (primaryTracker?.callState == CallState.ACTIVE || primaryTracker?.callState == CallState.HOLDING) primaryTracker else secondaryTracker
        if (active?.call != null) {
            try {
                if (active.callState == CallState.HOLDING || active.isOnHold) {
                    active.call?.unhold()
                    active.callState = CallState.ACTIVE
                    active.isOnHold = false
                } else if (active.callState == CallState.ACTIVE) {
                    active.call?.hold()
                    active.callState = CallState.HOLDING
                    active.isOnHold = true
                }
            } catch (_: Exception) {}
        } else if (_callUiState.value.isSimulated && active != null) {
            val newHold = !active.isOnHold
            active.isOnHold = newHold
            active.callState = if (newHold) CallState.HOLDING else CallState.ACTIVE
        }
        ensureDurationTimerRunning()
        recomputeUiState()
    }

    /**
     * Handles removal of an individual call by Android Telecom.
     * Preserves the remaining call if one is still connected or held.
     * Accurately distinguishes between a participant Call being restructured into a conference
     * versus an actual participant disconnection.
     */
    fun onCallRemoved(call: Call) {
        val callNum = try { call.details?.handle?.schemeSpecificPart } catch (_: Throwable) { null }
        val callState = try { call.state } catch (_: Exception) { Call.STATE_DISCONNECTED }

        logDebug(
            TAG,
            "onCallRemoved: call=${callNum?.let { redact(it) } ?: "[call]"}, state=$callState, " +
                    "isTerminating=$isConferenceTerminating, isMergePending=$isMergePending"
        )

        when {
            call == conferenceTracker?.call -> {
                conferenceTracker?.callback?.let { call.unregisterCallback(it) }
                conferenceTracker = null
                if (isConferenceTerminating) {
                    reconcileConferenceTermination()
                } else {
                    primaryTracker?.isConferenceMember = false
                    secondaryTracker?.isConferenceMember = false
                    if (primaryTracker == null && secondaryTracker == null && conferenceParticipants.isEmpty()) {
                        unbindCall()
                    } else {
                        reconcileStateWithTelecom()
                    }
                }
            }
            call == secondaryTracker?.call -> {
                if (isMergePending && !isConferenceTerminating) {
                    logDebug(TAG, "onCallRemoved: secondary call removed during merge transition; preserving")
                } else {
                    secondaryTracker?.let { handleCallDisconnected(it) }
                }
            }
            call == primaryTracker?.call -> {
                if (isMergePending && !isConferenceTerminating) {
                    logDebug(TAG, "onCallRemoved: primary call removed during merge transition; preserving")
                } else {
                    primaryTracker?.let { handleCallDisconnected(it) }
                }
            }
            else -> {
                val matchingTracker = conferenceParticipants.values.firstOrNull { it.call == call }
                if (matchingTracker != null) {
                    if (isMergePending && !isConferenceTerminating) {
                        logDebug(TAG, "onCallRemoved: participant call removed during merge transition; preserving")
                    } else {
                        handleCallDisconnected(matchingTracker)
                    }
                } else if (isConferenceTerminating) {
                    reconcileConferenceTermination()
                }
            }
        }
    }

    /**
     * Reconciles current trackers with the real, authoritative calls reported by Telecom.
     * Safe to invoke on resume or returning from system screens.
     */
    fun reconcileStateWithTelecom() {
        if (isConferenceTerminating) {
            reconcileConferenceTermination()
            return
        }
        val service = inCallService ?: return
        val currentCalls = try {
            service.calls
        } catch (_: Exception) {
            emptyList<Call>()
        }

        // Check conference parent if any
        val confCall = currentCalls.firstOrNull {
            it.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true || it.children.size >= 2
        }
        if (confCall != null && conferenceTracker == null) {
            bindConferenceCall(confCall, appContext)
        }

        // 1. If primaryTracker has a real call, verify it is still in service.calls or attached to conference
        val p = primaryTracker
        if (p?.call != null) {
            val isPresent = currentCalls.contains(p.call) || p.call!!.parent != null || conferenceTracker?.call?.children?.contains(p.call) == true
            if (!isPresent) {
                handleCallDisconnected(p)
            } else {
                val actualState = mapTelecomStateToCallState(p.call!!.state)
                p.callState = actualState
                p.isOnHold = (p.call!!.state == Call.STATE_HOLDING)
            }
        }

        // 2. If secondaryTracker has a real call, verify it is still in service.calls or attached to conference
        val s = secondaryTracker
        if (s?.call != null) {
            val isPresent = currentCalls.contains(s.call) || s.call!!.parent != null || conferenceTracker?.call?.children?.contains(s.call) == true
            if (!isPresent) {
                handleCallDisconnected(s)
            } else {
                val actualState = mapTelecomStateToCallState(s.call!!.state)
                s.callState = actualState
                s.isOnHold = (s.call!!.state == Call.STATE_HOLDING)
            }
        }

        recomputeUiState()
    }

    fun unbindCall() {
        logDebug(TAG, "unbindCall: cleaning up all calls and state")
        pendingOutgoingNumber = null
        pendingOutgoingAccountHandle = null
        pendingHoldTimeoutJob?.cancel()
        pendingHoldTimeoutJob = null
        pendingMergeTimeoutJob?.cancel()
        pendingMergeTimeoutJob = null
        isMergePending = false
        lastMergeFailureReason = null
        isConferenceTerminating = false
        conferenceTerminationSafetyJob?.cancel()
        conferenceTerminationSafetyJob = null
        terminationRequestedCallIds.clear()
        stopDurationTimer()
        releaseWakeLocks()

        primaryTracker?.callback?.let { primaryTracker?.call?.unregisterCallback(it) }
        secondaryTracker?.callback?.let { secondaryTracker?.call?.unregisterCallback(it) }
        conferenceTracker?.callback?.let { conferenceTracker?.call?.unregisterCallback(it) }
        conferenceParticipants.values.forEach { participant ->
            participant.callback?.let { participant.call?.unregisterCallback(it) }
        }
        conferenceParticipants.clear()

        val finalDuration = maxOf(
            primaryTracker?.durationSeconds ?: 0L,
            secondaryTracker?.durationSeconds ?: 0L,
            conferenceTracker?.durationSeconds ?: 0L,
            lastRecordedDurationSeconds,
            _callUiState.value.durationSeconds
        )
        primaryTracker = null
        secondaryTracker = null
        conferenceTracker = null

        _callUiState.value = CallUiState(
            callState = CallState.DISCONNECTED,
            durationSeconds = finalDuration
        )
    }

    fun reset() {
        pendingOutgoingNumber = null
        pendingOutgoingAccountHandle = null
        pendingHoldTimeoutJob?.cancel()
        pendingHoldTimeoutJob = null
        pendingMergeTimeoutJob?.cancel()
        pendingMergeTimeoutJob = null
        isMergePending = false
        lastMergeFailureReason = null
        isConferenceTerminating = false
        conferenceTerminationSafetyJob?.cancel()
        conferenceTerminationSafetyJob = null
        terminationRequestedCallIds.clear()
        telecomConferenceSupportedOverride = null
        simulateAsyncMergeForTesting = false
        simulateAsyncConferenceTerminationForTesting = false
        lastRecordedDurationSeconds = 0L
        stopDurationTimer()
        releaseWakeLocks()
        primaryTracker?.callback?.let { primaryTracker?.call?.unregisterCallback(it) }
        secondaryTracker?.callback?.let { secondaryTracker?.call?.unregisterCallback(it) }
        conferenceTracker?.callback?.let { conferenceTracker?.call?.unregisterCallback(it) }
        conferenceParticipants.values.forEach { participant ->
            participant.callback?.let { participant.call?.unregisterCallback(it) }
        }
        conferenceParticipants.clear()
        primaryTracker = null
        secondaryTracker = null
        conferenceTracker = null
        _callUiState.value = CallUiState()
    }

    // =========================================================================
    // DURATION TIMERS & STATE RECOMPUTATION
    // =========================================================================

    fun ensureDurationTimerRunning() {
        val p = primaryTracker
        val s = secondaryTracker
        val conf = conferenceTracker
        val hasAnyConnected = p?.connectedElapsedRealtime != null ||
                s?.connectedElapsedRealtime != null ||
                conf?.connectedElapsedRealtime != null ||
                conferenceParticipants.values.any { it.connectedElapsedRealtime != null }
        if (!hasAnyConnected) {
            return
        }

        updateCalculatedDurations()

        if (durationJob?.isActive == true) return

        durationJob = coroutineScope.launch {
            while (isActive) {
                delay(500L)
                updateCalculatedDurations()
            }
        }
    }

    private fun updateCalculatedDurations() {
        val now = elapsedRealtimeProvider()
        primaryTracker?.let {
            if (it.connectedElapsedRealtime != null && (it.callState == CallState.ACTIVE || it.callState == CallState.HOLDING)) {
                it.durationSeconds = ((now - it.connectedElapsedRealtime!!) / 1000L).coerceAtLeast(0L)
            }
        }
        secondaryTracker?.let {
            if (it.connectedElapsedRealtime != null && (it.callState == CallState.ACTIVE || it.callState == CallState.HOLDING)) {
                it.durationSeconds = ((now - it.connectedElapsedRealtime!!) / 1000L).coerceAtLeast(0L)
            }
        }
        conferenceTracker?.let {
            if (it.connectedElapsedRealtime != null && it.callState == CallState.ACTIVE) {
                it.durationSeconds = ((now - it.connectedElapsedRealtime!!) / 1000L).coerceAtLeast(0L)
            }
        }
        for (part in conferenceParticipants.values) {
            if (part.connectedElapsedRealtime != null && (part.callState == CallState.ACTIVE || part.callState == CallState.HOLDING)) {
                part.durationSeconds = ((now - part.connectedElapsedRealtime!!) / 1000L).coerceAtLeast(0L)
            }
        }
        recomputeUiState()
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    /**
     * Synchronizes and emits the unified CallUiState from active call trackers.
     */
    private fun recomputeUiState() {
        val p = primaryTracker
        val s = secondaryTracker
        val conf = conferenceTracker

        val hasMulti = (p != null && s != null && s.callState != CallState.DISCONNECTED && p.callState != CallState.DISCONNECTED)
        val isConf = conf != null ||
                (p?.isConferenceMember == true && s?.isConferenceMember == true) ||
                conferenceParticipants.size >= 2 ||
                (conferenceParticipants.isNotEmpty() && (p?.isConferenceMember == true || s?.isConferenceMember == true)) ||
                (isConferenceTerminating && (conf != null || p != null || s != null || conferenceParticipants.isNotEmpty()))

        val active = when {
            conf != null && conf.callState == CallState.ACTIVE -> conf
            p?.callState == CallState.ACTIVE -> p
            s?.callState == CallState.ACTIVE -> s
            s?.callState == CallState.DIALING -> s
            p != null -> p
            s != null -> s
            conferenceParticipants.isNotEmpty() -> conferenceParticipants.values.first()
            else -> null
        }

        val primaryState = p?.toIndividualCallState()
        val secondaryState = s?.toIndividualCallState()

        val canSwap = hasMulti && !isConf && (
                (p?.callState == CallState.ACTIVE && s?.callState == CallState.HOLDING) ||
                        (s?.callState == CallState.ACTIVE && p?.callState == CallState.HOLDING)
                )

        val bothConnected = hasMulti && (
                (p?.callState == CallState.ACTIVE || p?.callState == CallState.HOLDING) &&
                        (s?.callState == CallState.ACTIVE || s?.callState == CallState.HOLDING)
                )

        val pCall = p?.call
        val sCall = s?.call
        val isRealTelecom = (pCall != null || sCall != null)

        val telecomCompatible = when {
            telecomConferenceSupportedOverride != null -> telecomConferenceSupportedOverride == true
            isRealTelecom -> {
                if (pCall != null && sCall != null) {
                    val pConfList = try { pCall.conferenceableCalls } catch (_: Exception) { emptyList<Call>() }
                    val sConfList = try { sCall.conferenceableCalls } catch (_: Exception) { emptyList<Call>() }
                    val pCanMergeConf = try { pCall.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true } catch (_: Exception) { false }
                    val sCanMergeConf = try { sCall.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true } catch (_: Exception) { false }
                    val pAccount = try { pCall.details?.accountHandle } catch (_: Exception) { null }
                    val sAccount = try { sCall.details?.accountHandle } catch (_: Exception) { null }
                    val sameAccount = (pAccount == null || sAccount == null || pAccount == sAccount)

                    val hasExplicitCap = pConfList.contains(sCall) || sConfList.contains(pCall) || pCanMergeConf || sCanMergeConf
                    // In cellular telephony (GSM, VoLTE, VoNR), two calls on same SIM are mergeable when both connected
                    sameAccount && (hasExplicitCap || bothConnected)
                } else {
                    false
                }
            }
            else -> true // In simulated mode allow merge
        }

        val computedMergeState = when {
            isConf -> MergeButtonState.MERGED
            isMergePending -> MergeButtonState.MERGING
            lastMergeFailureReason != null && hasMulti -> MergeButtonState.FAILED
            hasMulti && bothConnected && telecomCompatible -> MergeButtonState.READY
            else -> MergeButtonState.UNAVAILABLE
        }

        val canMerge = (computedMergeState == MergeButtonState.READY)

        val canAddCall = !isMergePending && (
            (!hasMulti && !isConf && (active?.callState == CallState.ACTIVE || active?.callState == CallState.HOLDING)) ||
            (isConf && secondaryTracker == null && (active?.callState == CallState.ACTIVE || active?.callState == CallState.HOLDING))
        )

        val participants = if (isConf) {
            val allList = mutableListOf<IndividualCallState>()
            if (primaryState != null && primaryState.callState != CallState.DISCONNECTED) {
                allList.add(primaryState)
            }
            if (secondaryState != null && secondaryState.callState != CallState.DISCONNECTED) {
                allList.add(secondaryState)
            }
            for (tracker in conferenceParticipants.values) {
                if (tracker.callState != CallState.DISCONNECTED) {
                    val state = tracker.toIndividualCallState()
                    if (allList.none { it.callId == state.callId || (it.rawNumber.isNotBlank() && it.rawNumber == state.rawNumber) }) {
                        allList.add(state)
                    }
                }
            }
            allList
        } else {
            emptyList()
        }

        val effectiveDurationSeconds = if (isConf) {
            conf?.durationSeconds ?: maxOf(p?.durationSeconds ?: 0L, s?.durationSeconds ?: 0L, conferenceParticipants.values.maxOfOrNull { it.durationSeconds } ?: 0L)
        } else {
            active?.durationSeconds ?: 0L
        }

        _callUiState.value = _callUiState.value.copy(
            rawNumber = active?.rawNumber ?: "",
            normalizedNumber = active?.normalizedNumber ?: "",
            displayNumber = active?.displayNumber ?: "",
            contactInfo = active?.contactInfo ?: ContactInfo(),
            classificationResult = active?.classificationResult,
            callerIdentity = active?.callerIdentity,
            callState = active?.callState ?: if (p == null && s == null) _callUiState.value.callState else CallState.IDLE,
            durationSeconds = effectiveDurationSeconds,
            isOnHold = active?.isOnHold ?: false,
            canHold = (active != null),
            isSimulated = (active?.call == null && active != null),

            // Multi-Call
            primaryCall = primaryState,
            secondaryCall = secondaryState,
            isMultiCall = hasMulti,
            isConference = isConf,
            conferenceParticipants = participants,
            canSwap = canSwap,
            canMerge = canMerge,
            mergeState = computedMergeState,
            mergeFailureReason = lastMergeFailureReason,
            canAddCall = canAddCall
        )
    }

    // =========================================================================
    // TESTING & SIMULATION HELPERS
    // =========================================================================

    fun getPendingOutgoingNumberForTesting(): String? = pendingOutgoingNumber

    fun triggerPendingOutgoingCallForTesting() {
        triggerPendingOutgoingCallIfAny()
    }

    fun setMergePendingForTesting(pending: Boolean) {
        isMergePending = pending
        recomputeUiState()
    }

    fun setMergeFailureForTesting(reason: String?) {
        lastMergeFailureReason = reason
        recomputeUiState()
    }

    fun getIsMergePending(): Boolean = isMergePending

    fun confirmConferenceForTesting() {
        isMergePending = false
        pendingMergeTimeoutJob?.cancel()
        pendingMergeTimeoutJob = null
        lastMergeFailureReason = null
        primaryTracker?.isConferenceMember = true
        secondaryTracker?.isConferenceMember = true
        primaryTracker?.callState = CallState.ACTIVE
        primaryTracker?.isOnHold = false
        secondaryTracker?.callState = CallState.ACTIVE
        secondaryTracker?.isOnHold = false
        primaryTracker?.let { conferenceParticipants[it.callId] = it }
        secondaryTracker?.let { conferenceParticipants[it.callId] = it }
        recomputeUiState()
    }

    fun isConferenceTerminatingForTesting(): Boolean = isConferenceTerminating

    internal fun getConferenceTrackerForTesting(): CallTracker? = conferenceTracker

    internal fun getPrimaryTrackerForTesting(): CallTracker? = primaryTracker

    internal fun getSecondaryTrackerForTesting(): CallTracker? = secondaryTracker

    internal fun getConferenceParticipantsForTesting(): Map<String, CallTracker> = conferenceParticipants.toMap()

    fun getTerminationRequestedCallIdsForTesting(): Set<String> = terminationRequestedCallIds.toSet()

    fun setConferenceCallForTesting(
        call: Call? = null,
        callId: String = "test_conf",
        callState: CallState = CallState.ACTIVE
    ) {
        conferenceTracker = CallTracker(
            call = call,
            callId = callId,
            rawNumber = "Conference Call",
            normalizedNumber = "",
            displayNumber = "Conference Call",
            contactInfo = ContactInfo(name = "Conference Call"),
            classificationResult = null,
            callerIdentity = null,
            callState = callState,
            connectedElapsedRealtime = elapsedRealtimeProvider(),
            isConferenceMember = true
        )
        primaryTracker?.isConferenceMember = true
        secondaryTracker?.isConferenceMember = true
        primaryTracker?.let { conferenceParticipants[it.callId] = it }
        secondaryTracker?.let { conferenceParticipants[it.callId] = it }
        recomputeUiState()
    }

    fun triggerConferenceParentDisconnectedForTesting() {
        val conf = conferenceTracker ?: return
        handleCallDisconnected(conf)
    }

    internal fun triggerConferenceChildDisconnectedForTesting(tracker: CallTracker) {
        conferenceParticipants.remove(tracker.callId)
        handleCallDisconnected(tracker)
    }

    internal fun addConferenceParticipantForTesting(
        callId: String,
        rawNumber: String,
        callerName: String?,
        callState: CallState = CallState.ACTIVE,
        isOnHold: Boolean = false,
        origin: Long? = 100_000L
    ): CallTracker {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)
        val tracker = CallTracker(
            call = null,
            callId = callId,
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayNumber = display,
            contactInfo = ContactInfo(name = callerName, isSavedContact = callerName != null),
            classificationResult = null,
            callerIdentity = callerName?.let {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    displayName = it,
                    source = CallerIdentitySource.SAVED_CONTACT,
                    identityType = CallerIdentityType.SAVED_CONTACT,
                    confidence = IdentityConfidence.VERIFIED,
                    sourceLabel = "Saved contact",
                    isVerified = true
                )
            },
            callState = callState,
            isOnHold = isOnHold,
            connectedElapsedRealtime = origin,
            isConferenceMember = true
        )
        conferenceParticipants[callId] = tracker
        recomputeUiState()
        return tracker
    }

    fun triggerReconcileConferenceTerminationForTesting() {
        reconcileConferenceTermination()
    }

    fun setTelecomConferenceSupportedForTesting(supported: Boolean) {
        telecomConferenceSupportedOverride = supported
        recomputeUiState()
    }

    fun getCallConnectedElapsedRealtime(): Long? = primaryTracker?.connectedElapsedRealtime

    fun setCallConnectedElapsedRealtimeForTesting(origin: Long?) {
        if (primaryTracker == null) {
            primaryTracker = CallTracker(
                call = null,
                callId = "test_1",
                rawNumber = "+919876543210",
                normalizedNumber = "+919876543210",
                displayNumber = "+91 98765 43210",
                contactInfo = ContactInfo(),
                classificationResult = null,
                callerIdentity = null,
                callState = CallState.ACTIVE
            )
        }
        primaryTracker?.connectedElapsedRealtime = origin
        if (origin != null) {
            primaryTracker?.callState = CallState.ACTIVE
        }
        recomputeUiState()
    }

    fun setSecondaryConnectedElapsedRealtimeForTesting(origin: Long?) {
        if (secondaryTracker == null) {
            secondaryTracker = CallTracker(
                call = null,
                callId = "test_2",
                rawNumber = "+919876511111",
                normalizedNumber = "+919876511111",
                displayNumber = "+91 98765 11111",
                contactInfo = ContactInfo(),
                classificationResult = null,
                callerIdentity = null,
                callState = CallState.ACTIVE
            )
        }
        secondaryTracker?.connectedElapsedRealtime = origin
        recomputeUiState()
    }

    fun setPrimaryCallForTesting(
        callId: String = "test_primary",
        rawNumber: String = "+919876543210",
        callerName: String? = "Person A",
        callState: CallState = CallState.ACTIVE,
        isOnHold: Boolean = false,
        origin: Long? = 100_000L
    ) {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)
        primaryTracker = CallTracker(
            call = null,
            callId = callId,
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayNumber = display,
            contactInfo = ContactInfo(name = callerName, isSavedContact = callerName != null),
            classificationResult = null,
            callerIdentity = callerName?.let {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    displayName = it,
                    source = CallerIdentitySource.SAVED_CONTACT,
                    identityType = CallerIdentityType.SAVED_CONTACT,
                    confidence = IdentityConfidence.VERIFIED,
                    sourceLabel = "Saved contact",
                    isVerified = true
                )
            },
            callState = callState,
            isOnHold = isOnHold,
            connectedElapsedRealtime = origin
        )
        recomputeUiState()
    }

    fun setSecondaryCallForTesting(
        callId: String = "test_secondary",
        rawNumber: String = "+919876511111",
        callerName: String? = "Person B",
        callState: CallState = CallState.DIALING,
        isOnHold: Boolean = false,
        origin: Long? = null
    ) {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)
        secondaryTracker = CallTracker(
            call = null,
            callId = callId,
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayNumber = display,
            contactInfo = ContactInfo(name = callerName, isSavedContact = callerName != null),
            classificationResult = null,
            callerIdentity = callerName?.let {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    displayName = it,
                    source = CallerIdentitySource.SAVED_CONTACT,
                    identityType = CallerIdentityType.SAVED_CONTACT,
                    confidence = IdentityConfidence.VERIFIED,
                    sourceLabel = "Saved contact",
                    isVerified = true
                )
            },
            callState = callState,
            isOnHold = isOnHold,
            connectedElapsedRealtime = origin
        )
        recomputeUiState()
    }

    fun removeSecondaryCallForTesting() {
        val s = secondaryTracker ?: return
        handleCallDisconnected(s)
    }

    fun removePrimaryCallForTesting() {
        val p = primaryTracker ?: return
        handleCallDisconnected(p)
    }

    internal fun findTrackerByNumber(number: String): CallTracker? {
        val clean = PhoneNumberNormalizer.normalize(number)
        if (clean.isNotBlank()) {
            if (primaryTracker?.normalizedNumber == clean || primaryTracker?.rawNumber == number) return primaryTracker
            if (secondaryTracker?.normalizedNumber == clean || secondaryTracker?.rawNumber == number) return secondaryTracker
        }
        return null
    }

    fun updateCallerIdentity(phoneNumber: String, callerIdentity: CallerIdentityResult) {
        val target = findTrackerByNumber(phoneNumber) ?: when {
            secondaryTracker != null && PhoneNumberNormalizer.normalize(phoneNumber) == secondaryTracker?.normalizedNumber -> secondaryTracker
            primaryTracker != null && secondaryTracker == null -> primaryTracker
            else -> primaryTracker
        }
        target?.let { t ->
            val current = t.callerIdentity
            if (current?.source == CallerIdentitySource.SAVED_CONTACT &&
                callerIdentity.source != CallerIdentitySource.SAVED_CONTACT
            ) {
                return
            }
            t.callerIdentity = callerIdentity
        }
        recomputeUiState()
    }

    fun updateCallerIdentity(callerIdentity: CallerIdentityResult) {
        val num = callerIdentity.normalizedNumber.ifBlank { callerIdentity.rawNumber }
        updateCallerIdentity(num, callerIdentity)
    }

    fun updateClassification(phoneNumber: String, classificationResult: ClassificationResult) {
        val target = findTrackerByNumber(phoneNumber) ?: when {
            secondaryTracker != null && PhoneNumberNormalizer.normalize(phoneNumber) == secondaryTracker?.normalizedNumber -> secondaryTracker
            primaryTracker != null && secondaryTracker == null -> primaryTracker
            else -> primaryTracker
        }
        target?.classificationResult = classificationResult
        recomputeUiState()
    }

    fun updateClassification(classificationResult: ClassificationResult) {
        val num = primaryTracker?.rawNumber ?: ""
        updateClassification(num, classificationResult)
    }

    fun simulateIncomingCall(
        context: Context,
        rawNumber: String,
        customContactName: String? = null,
        customPhotoUri: Uri? = null,
        onClassified: ((ClassificationResult) -> Unit)? = null
    ) {
        val app = context.applicationContext as? SafeCallApplication ?: return
        val repo = app.repository
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)

        coroutineScope.launch(Dispatchers.IO) {
            val result = repo.screenAndClassify(rawNumber, recordInHistory = true)
            val contactInfo = ContactInfo(
                name = customContactName,
                photoUri = customPhotoUri,
                isSavedContact = !customContactName.isNullOrBlank()
            )

            coroutineScope.launch(Dispatchers.Default) {
                reset()
                primaryTracker = CallTracker(
                    call = null,
                    callId = "sim_1_${System.currentTimeMillis()}",
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    displayNumber = display,
                    contactInfo = contactInfo,
                    classificationResult = result,
                    callerIdentity = null,
                    callState = CallState.RINGING,
                    isOnHold = false
                )

                onClassified?.invoke(result)
                recomputeUiState()

                val intent = Intent(context, IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            }
        }
    }
}
