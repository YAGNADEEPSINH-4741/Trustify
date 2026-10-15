package com.safecallai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver for triggering incoming-call interface simulations via ADB or internal events.
 *
 * Usage:
 * adb shell am broadcast -a com.safecallai.app.SIMULATE_INCOMING_CALL -n com.safecallai.app/.service.IncomingCallTestReceiver --es phone "+919876543210" --es contact_name "Delivery Partner"
 */
class IncomingCallTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SIMULATE_INCOMING_CALL) {
            val phone = intent.getStringExtra("phone") ?: "+919876543210"
            val contactName = intent.getStringExtra("contact_name")
            CallManager.simulateIncomingCall(context, phone, customContactName = contactName)
        }
    }

    companion object {
        const val ACTION_SIMULATE_INCOMING_CALL = "com.safecallai.app.SIMULATE_INCOMING_CALL"
    }
}
