package com.safecallai.app.ui.call

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.safecallai.app.SafeCallApplication
import com.safecallai.app.service.CallManager
import com.safecallai.app.service.CallState
import com.safecallai.app.ui.theme.SafeCallAITheme
import kotlinx.coroutines.delay

/**
 * Fullscreen calling activity displayed when an incoming, outgoing, or active call occurs.
 *
 * Configured with flags to show over the device lock screen and illuminate the display,
 * acquiring wake locks to ensure continuous audio streaming and prevent sleep during calls.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenFlags()
        CallManager.acquireWakeLocks(this)

        val app = application as SafeCallApplication
        val preferences = app.repository.preferences

        setContent {
            val uiState by CallManager.callUiState.collectAsState()

            LaunchedEffect(uiState.callState) {
                if (uiState.callState == CallState.DISCONNECTED) {
                    delay(1200L)
                    CallManager.reset()
                    finish()
                }
            }

            SafeCallAITheme(themeMode = preferences.themeMode) {
                IncomingCallScreen(
                    uiState = uiState,
                    onAnswer = { CallManager.answer() },
                    onDecline = { CallManager.decline() },
                    onHangUp = { CallManager.hangUp() },
                    onToggleMute = { CallManager.toggleMute(this@IncomingCallActivity) },
                    onToggleSpeaker = { CallManager.toggleSpeaker(this@IncomingCallActivity) }
                )
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    override fun onBackPressed() {
        val currentState = CallManager.callUiState.value.callState
        if (currentState == CallState.RINGING || currentState == CallState.DIALING) {
            CallManager.decline()
        } else {
            super.onBackPressed()
        }
    }
}
