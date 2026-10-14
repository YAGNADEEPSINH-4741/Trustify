package com.safecallai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver enabling on-device and automated ADB testing of incoming call screening.
 *
 * Example ADB execution:
 * `adb shell am broadcast -a com.safecallai.app.SIMULATE_CALL --es phone "+919876543210"`
 */
class CallScreeningBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SIMULATE_CALL) {
            val phone = intent.getStringExtra(EXTRA_PHONE) ?: "+919876543210"
            ScreeningNotificationHelper.simulateScreeningCall(context, phone)
        }
    }

    companion object {
        const val ACTION_SIMULATE_CALL = "com.safecallai.app.SIMULATE_CALL"
        const val EXTRA_PHONE = "phone"
    }
}
