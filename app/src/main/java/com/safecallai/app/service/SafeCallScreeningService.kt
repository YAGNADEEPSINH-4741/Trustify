package com.safecallai.app.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.safecallai.app.SafeCallApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android CallScreeningService implementation.
 *
 * Intercepts incoming call screening events, analyzes numbers using the
 * classification engine, and protects legitimate callers from wrongful spam blocking.
 */
@RequiresApi(Build.VERSION_CODES.N)
class SafeCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val app = application as? SafeCallApplication
        val repository = app?.repository

        if (repository == null || !repository.preferences.isProtectionEnabled) {
            // Default pass-through if disabled or repository unavailable
            val passThroughResponse = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            respondToCall(callDetails, passThroughResponse)
            return
        }

        serviceScope.launch {
            try {
                // Perform multi-signal evidence classification with a strict 4-second timeout guarantee
                val result = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                    repository.screenAndClassify(rawNumber, recordInHistory = true, telecomDetails = callDetails)
                }

                if (result == null) {
                    // Timeout fallback: pass call through safely
                    val safeTimeoutResponse = CallResponse.Builder()
                        .setDisallowCall(false)
                        .setRejectCall(false)
                        .setSkipCallLog(false)
                        .setSkipNotification(false)
                        .build()
                    respondToCall(callDetails, safeTimeoutResponse)
                    return@launch
                }

                val responseBuilder = CallResponse.Builder()

                // Rule: Let all legitimate and uncertain calls ring normally.
                // Disallow only if user explicitly turned on aggressive blocking and caller is HIGH_RISK.
                val shouldDisallow = repository.preferences.isAggressiveBlockingEnabled &&
                        !result.isSafeToRingNormally

                if (shouldDisallow) {
                    responseBuilder.setDisallowCall(true)
                    responseBuilder.setRejectCall(false)
                    responseBuilder.setSkipCallLog(false)
                    responseBuilder.setSkipNotification(true)
                } else {
                    responseBuilder.setDisallowCall(false)
                    responseBuilder.setRejectCall(false)
                    responseBuilder.setSkipCallLog(false)
                    responseBuilder.setSkipNotification(false)
                }

                respondToCall(callDetails, responseBuilder.build())

                // Send rich notification to alert user of screening decision
                ScreeningNotificationHelper.showCallScreenedNotification(applicationContext, result)
            } catch (_: Exception) {
                // Fail-safe: Always allow call to ring through on any unexpected error
                val safeFallback = CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                respondToCall(callDetails, safeFallback)
            }
        }
    }
}
