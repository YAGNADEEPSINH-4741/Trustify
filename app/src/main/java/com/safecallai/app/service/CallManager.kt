package com.safecallai.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.safecallai.app.SafeCallApplication
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.ContactInfo
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

enum class CallState {
    IDLE,
    RINGING,
    DIALING,
    ACTIVE,
    DISCONNECTED
}

data class CallUiState(
    val rawNumber: String = "",
    val normalizedNumber: String = "",
    val displayNumber: String = "",
    val contactInfo: ContactInfo = ContactInfo(),
    val classificationResult: ClassificationResult? = null,
    val callState: CallState = CallState.IDLE,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isSimulated: Boolean = false
)

/**
 * Singleton state controller managing both real Android Telecom Calls and
 * interactive on-device call simulations for testing and preview.
 *
 * Implements real Android Telecom audio routing (setAudioRoute, setMuted, onCallAudioStateChanged)
 * and keeps a CPU wake lock during active calls to ensure continuous audio streaming without dropout.
 */
object CallManager {

    private val _callUiState = MutableStateFlow(CallUiState())
    val callUiState: StateFlow<CallUiState> = _callUiState.asStateFlow()

    private var activeCall: Call? = null
    private var inCallService: InCallService? = null
    private var durationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var wakeLock: PowerManager.WakeLock? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            when (state) {
                Call.STATE_RINGING -> {
                    _callUiState.value = _callUiState.value.copy(callState = CallState.RINGING)
                }
                Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    _callUiState.value = _callUiState.value.copy(callState = CallState.DIALING)
                }
                Call.STATE_ACTIVE -> {
                    _callUiState.value = _callUiState.value.copy(callState = CallState.ACTIVE)
                    startDurationTimer()
                    // Confirm audio routing to earpiece or currently selected route when call answers
                    try {
                        val currentRoute = inCallService?.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
                        inCallService?.setAudioRoute(currentRoute)
                    } catch (_: Exception) {}
                }
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    stopDurationTimer()
                    releaseWakeLocks()
                    _callUiState.value = _callUiState.value.copy(callState = CallState.DISCONNECTED)
                }
            }
        }
    }

    /**
     * Registers the running InCallService instance to allow direct Telecom audio manipulation.
     */
    fun registerInCallService(service: InCallService) {
        inCallService = service
        service.callAudioState?.let { onAudioStateChanged(it) }
    }

    /**
     * Unregisters the InCallService when unbound or destroyed.
     */
    fun unregisterInCallService(service: InCallService) {
        if (inCallService == service) {
            inCallService = null
        }
    }

    /**
     * Telecom callback triggered when device audio state (mute or route) changes.
     */
    fun onAudioStateChanged(audioState: CallAudioState) {
        val isSpeaker = (audioState.route == CallAudioState.ROUTE_SPEAKER)
        val isMuted = audioState.isMuted
        _callUiState.value = _callUiState.value.copy(
            isSpeakerOn = isSpeaker,
            isMuted = isMuted
        )
    }

    /**
     * Acquires wake locks to prevent device sleep and proximity blackouts from dropping voice audio.
     */
    fun acquireWakeLocks(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeCallAI:CpuAudioLock").apply {
                    setReferenceCounted(false)
                    acquire(20 * 60 * 1000L) // 20 min safety timeout
                }
            }
            if (proximityWakeLock == null && powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "SafeCallAI:ProximityLock").apply {
                    setReferenceCounted(false)
                    acquire(20 * 60 * 1000L)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Releases active wake locks when a call concludes.
     */
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

    /**
     * Binds a real Android Telecom Call received by SafeCallInCallService.
     */
    fun bindRealCall(
        call: Call,
        rawNumber: String,
        contactInfo: ContactInfo,
        classificationResult: ClassificationResult?,
        context: Context? = null
    ) {
        activeCall?.unregisterCallback(callCallback)
        activeCall = call
        call.registerCallback(callCallback)

        if (context != null) {
            acquireWakeLocks(context)
        }

        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val display = PhoneNumberNormalizer.formatForDisplay(normalized)

        val initialState = when (call.state) {
            Call.STATE_ACTIVE -> CallState.ACTIVE
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallState.DIALING
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallState.DISCONNECTED
            else -> CallState.RINGING
        }

        val currentAudioState = inCallService?.callAudioState
        val isSpeaker = currentAudioState?.route == CallAudioState.ROUTE_SPEAKER
        val isMuted = currentAudioState?.isMuted ?: false

        _callUiState.value = CallUiState(
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayNumber = display,
            contactInfo = contactInfo,
            classificationResult = classificationResult,
            callState = initialState,
            durationSeconds = 0L,
            isMuted = isMuted,
            isSpeakerOn = isSpeaker,
            isSimulated = false
        )

        // Ensure voice route is initialized on call startup
        if (initialState == CallState.ACTIVE || initialState == CallState.DIALING) {
            try {
                if (!isSpeaker) {
                    inCallService?.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                }
            } catch (_: Exception) {}
        }

        if (initialState == CallState.ACTIVE) {
            startDurationTimer()
        }
    }

    /**
     * Updates classification asynchronously when background screening finishes.
     */
    fun updateClassification(classificationResult: ClassificationResult) {
        _callUiState.value = _callUiState.value.copy(
            classificationResult = classificationResult
        )
    }

    /**
     * Answers the call (real or simulated).
     */
    fun answer() {
        val current = activeCall
        if (current != null) {
            try {
                current.answer(VideoProfile.STATE_AUDIO_ONLY)
                inCallService?.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            } catch (_: Exception) {}
        } else if (_callUiState.value.isSimulated) {
            _callUiState.value = _callUiState.value.copy(callState = CallState.ACTIVE)
            startDurationTimer()
        }
    }

    /**
     * Declines the incoming call (real or simulated).
     */
    fun decline() {
        val current = activeCall
        if (current != null) {
            try {
                if (current.state == Call.STATE_RINGING) {
                    current.reject(false, null)
                } else {
                    current.disconnect()
                }
            } catch (_: Exception) {}
        } else if (_callUiState.value.isSimulated) {
            stopDurationTimer()
            releaseWakeLocks()
            _callUiState.value = _callUiState.value.copy(callState = CallState.DISCONNECTED)
        }
    }

    /**
     * Hangs up an active call.
     */
    fun hangUp() {
        decline()
    }

    /**
     * Toggles microphone mute using official Telecom InCallService.setMuted.
     */
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

    /**
     * Toggles speakerphone on/off using official Telecom InCallService.setAudioRoute.
     */
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

    /**
     * Cleans up when call ends.
     */
    fun unbindCall() {
        stopDurationTimer()
        releaseWakeLocks()
        activeCall?.unregisterCallback(callCallback)
        activeCall = null
        _callUiState.value = _callUiState.value.copy(callState = CallState.DISCONNECTED)
    }

    fun reset() {
        stopDurationTimer()
        releaseWakeLocks()
        activeCall?.unregisterCallback(callCallback)
        activeCall = null
        _callUiState.value = CallUiState()
    }

    private fun startDurationTimer() {
        stopDurationTimer()
        durationJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000L)
                seconds++
                _callUiState.value = _callUiState.value.copy(durationSeconds = seconds)
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    /**
     * Simulates an incoming call for live preview, on-device testing, and verification.
     */
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

        scope.launch(Dispatchers.IO) {
            val result = repo.screenAndClassify(rawNumber, recordInHistory = true)
            val contactInfo = ContactInfo(
                name = customContactName,
                photoUri = customPhotoUri,
                isSavedContact = !customContactName.isNullOrBlank()
            )

            scope.launch(Dispatchers.Main) {
                activeCall = null
                stopDurationTimer()
                _callUiState.value = CallUiState(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    displayNumber = display,
                    contactInfo = contactInfo,
                    classificationResult = result,
                    callState = CallState.RINGING,
                    durationSeconds = 0L,
                    isMuted = false,
                    isSpeakerOn = false,
                    isSimulated = true
                )

                onClassified?.invoke(result)

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
