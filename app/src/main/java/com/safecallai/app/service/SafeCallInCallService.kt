package com.safecallai.app.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.safecallai.app.SafeCallApplication
import com.safecallai.app.domain.engine.ContactLookupHelper
import com.safecallai.app.ui.call.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * System InCallService that binds to Android Telecom when SafeCall AI is
 * designated as the system's Default Phone (Dialer) application.
 *
 * Receives incoming and outgoing call events directly from the Telecom framework,
 * routes call audio, supports hardware speaker/mute control, resolves contacts,
 * and maintains continuous audio streaming.
 */
class SafeCallInCallService : InCallService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        CallManager.registerInCallService(this)
    }

    override fun onDestroy() {
        CallManager.unregisterInCallService(this)
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.registerInCallService(this)

        val handle = call.details?.handle
        val rawNumber = handle?.schemeSpecificPart ?: "Private Number"

        // Lookup saved contact name and photo from device ContactsContract
        val contactInfo = ContactLookupHelper.lookupContact(this, rawNumber)

        // 1. Immediately bind real call & launch UI so audio & telephony are never blocked by network latency
        CallManager.bindRealCall(call, rawNumber, contactInfo, null, this)

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)

        // 2. Perform background classification and update UI asynchronously when results are ready
        val app = applicationContext as? SafeCallApplication
        val repository = app?.repository
        if (repository != null) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val classificationResult = repository.screenAndClassify(rawNumber, recordInHistory = true)
                    serviceScope.launch(Dispatchers.Main) {
                        CallManager.updateClassification(classificationResult)
                    }
                } catch (_: Exception) {
                    // Safe fallback
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.unbindCall()
    }
}
