package com.safecallai.app.ui.call

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.safecallai.app.SafeCallApplication
import com.safecallai.app.service.CallManager
import com.safecallai.app.service.CallState
import com.safecallai.app.service.QuickReplyManager
import com.safecallai.app.service.QuickReplyResult
import com.safecallai.app.ui.theme.TrustifyTheme
import kotlinx.coroutines.delay

/**
 * Fullscreen calling activity displayed when an incoming, outgoing, or active call occurs.
 *
 * Configured with flags to show over the device lock screen and illuminate the display,
 * acquiring wake locks to ensure continuous audio streaming and prevent sleep during calls.
 *
 * Supports Quick Reply SMS during incoming calls with direct SmsManager dispatch,
 * character counting, multipart SMS, permission handling, and messaging app fallback.
 */
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import com.safecallai.app.R
import com.safecallai.app.data.local.AppLanguage
import com.safecallai.app.data.local.PreferencesManager
import com.safecallai.app.util.LocaleHelper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CommunityReputation
import kotlinx.coroutines.launch

class IncomingCallActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as? SafeCallApplication
        val lang = app?.repository?.preferences?.appLanguage ?: AppLanguage.ENGLISH
        val localizedContext = LocaleHelper.createLocalizedContext(newBase, lang)
        super.attachBaseContext(localizedContext)
    }

    private var pendingQuickReplyMessage: String? = null
    private var isSendingSmsState by mutableStateOf(false)

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val message = pendingQuickReplyMessage
            val recipient = CallManager.callUiState.value.rawNumber
            if (message != null && QuickReplyManager.isValidRecipient(recipient)) {
                sendDirectSmsAndDecline(recipient, message)
            }
        } else {
            Toast.makeText(
                this,
                getString(R.string.toast_sms_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
        pendingQuickReplyMessage = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenFlags()
        CallManager.acquireWakeLocks(this)

        val app = application as SafeCallApplication
        val preferences = app.repository.preferences

        setContent {
            val uiState by CallManager.callUiState.collectAsState()
            val scope = rememberCoroutineScope()

            var isSubmittingVote by remember { mutableStateOf(false) }
            var submittedVoteResult by remember { mutableStateOf<CommunityReputation?>(null) }
            var communityReputation by remember { mutableStateOf<CommunityReputation?>(null) }
            var hasDismissedFeedback by remember { mutableStateOf(false) }
            var userAlreadyVoted by remember { mutableStateOf(false) }
            var savedUserVote by remember { mutableStateOf<String?>(null) }
            var voteErrorMessage by remember { mutableStateOf<String?>(null) }
            var lastAttemptedVote by remember { mutableStateOf<String?>(null) }

            // Fetch caller identity if not yet resolved
            LaunchedEffect(uiState.rawNumber) {
                val targetNumber = uiState.rawNumber
                if (uiState.callerIdentity == null && PhoneNumberNormalizer.isValid(targetNumber)) {
                    try {
                        val resolvedIdentity = app.repository.identifyCaller(targetNumber, allowNetworkQuery = true)
                        CallManager.updateCallerIdentity(targetNumber, resolvedIdentity)
                    } catch (_: Exception) {}
                }
            }

            // Fetch current reputation and check if user already voted when call transitions to DISCONNECTED
            LaunchedEffect(uiState.callState, uiState.rawNumber) {
                if (uiState.callState == CallState.DISCONNECTED && PhoneNumberNormalizer.isValid(uiState.rawNumber)) {
                    userAlreadyVoted = app.repository.communityRepo.hasUserVoted(uiState.rawNumber)
                    savedUserVote = app.repository.communityRepo.getSavedUserVote(uiState.rawNumber)
                    communityReputation = app.repository.communityRepo.getReputation(uiState.rawNumber)
                }
            }

            // Auto-finish only when ALL calls have genuinely ended (both primary and secondary are null)
            LaunchedEffect(uiState.callState, uiState.primaryCall, uiState.secondaryCall, hasDismissedFeedback, submittedVoteResult, userAlreadyVoted) {
                val hasAnyCall = uiState.primaryCall != null || uiState.secondaryCall != null
                if (uiState.callState == CallState.DISCONNECTED && !hasAnyCall) {
                    if (!PhoneNumberNormalizer.isValid(uiState.rawNumber) || hasDismissedFeedback) {
                        delay(1000L)
                        CallManager.reset()
                        finish()
                    } else if (submittedVoteResult != null) {
                        delay(2500L)
                        CallManager.reset()
                        finish()
                    } else if (userAlreadyVoted) {
                        delay(2000L)
                        CallManager.reset()
                        finish()
                    }
                }
            }

            val localizedContext = remember(preferences.appLanguage) {
                LocaleHelper.createLocalizedContext(this@IncomingCallActivity, preferences.appLanguage)
            }
            val localizedConfig = remember(preferences.appLanguage) {
                LocaleHelper.createLocalizedConfiguration(this@IncomingCallActivity, preferences.appLanguage)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalActivityResultRegistryOwner provides this@IncomingCallActivity
            ) {
                TrustifyTheme(themeMode = preferences.themeMode) {
                    IncomingCallScreen(
                        uiState = uiState,
                        onAnswer = { CallManager.answer() },
                        onDecline = { CallManager.decline() },
                        onHangUp = { CallManager.hangUp() },
                        onToggleMute = { CallManager.toggleMute(this@IncomingCallActivity) },
                        onToggleSpeaker = { CallManager.toggleSpeaker(this@IncomingCallActivity) },
                        onToggleHold = { CallManager.toggleHold(this@IncomingCallActivity) },
                        hasSmsPermission = QuickReplyManager.hasSendSmsPermission(this@IncomingCallActivity),
                        isSendingSms = isSendingSmsState,
                        onRequestSmsPermission = { message ->
                            pendingQuickReplyMessage = message
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        },
                        onSendQuickReply = { recipient, message ->
                            if (QuickReplyManager.hasSendSmsPermission(this@IncomingCallActivity)) {
                                sendDirectSmsAndDecline(recipient, message)
                            } else {
                                pendingQuickReplyMessage = message
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        },
                        onLaunchMessagingFallback = { recipient, message ->
                            launchMessagingFallbackAndDecline(recipient, message)
                        },
                        communityReputation = communityReputation,
                        isSubmittingVote = isSubmittingVote,
                        submittedVoteResult = submittedVoteResult,
                        userAlreadyVoted = userAlreadyVoted,
                        savedUserVote = savedUserVote,
                        voteErrorMessage = voteErrorMessage,
                        onVoteSpam = { phone ->
                            lastAttemptedVote = "spam"
                            voteErrorMessage = null
                            scope.launch {
                                isSubmittingVote = true
                                val res = app.repository.communityRepo.submitVote(phone, "spam")
                                isSubmittingVote = false
                                if (res.isSuccess) {
                                    submittedVoteResult = res.getOrNull()
                                    userAlreadyVoted = true
                                    savedUserVote = "spam"
                                    voteErrorMessage = null
                                } else {
                                    voteErrorMessage = res.exceptionOrNull()?.message ?: getString(R.string.community_vote_failed)
                                }
                            }
                        },
                        onVoteNotSpam = { phone ->
                            lastAttemptedVote = "not_spam"
                            voteErrorMessage = null
                            scope.launch {
                                isSubmittingVote = true
                                val res = app.repository.communityRepo.submitVote(phone, "not_spam")
                                isSubmittingVote = false
                                if (res.isSuccess) {
                                    submittedVoteResult = res.getOrNull()
                                    userAlreadyVoted = true
                                    savedUserVote = "not_spam"
                                    voteErrorMessage = null
                                } else {
                                    voteErrorMessage = res.exceptionOrNull()?.message ?: getString(R.string.community_vote_failed)
                                }
                            }
                        },
                        onRetryVote = {
                            val voteType = lastAttemptedVote ?: "spam"
                            val phone = uiState.rawNumber
                            voteErrorMessage = null
                            scope.launch {
                                isSubmittingVote = true
                                val res = app.repository.communityRepo.submitVote(phone, voteType)
                                isSubmittingVote = false
                                if (res.isSuccess) {
                                    submittedVoteResult = res.getOrNull()
                                    userAlreadyVoted = true
                                    savedUserVote = voteType
                                    voteErrorMessage = null
                                } else {
                                    voteErrorMessage = res.exceptionOrNull()?.message ?: getString(R.string.community_vote_failed)
                                }
                            }
                        },
                        onDismissPostCallFeedback = {
                            hasDismissedFeedback = true
                            CallManager.reset()
                            finish()
                        },
                        onAddCall = { CallManager.showAddCall(true) },
                        onSwapCalls = { CallManager.swapCalls() },
                        onMergeCalls = { CallManager.mergeCalls() },
                        onEndSecondaryCall = { CallManager.hangUpSecondaryCall() },
                        onCloseAddCall = { CallManager.showAddCall(false) },
                        onCallSelectedNumber = { number ->
                            CallManager.addCall(this@IncomingCallActivity, number)
                        },
                        onDismissMultiCallMessage = { CallManager.dismissMultiCallMessage() }
                    )
                }
            }
        }
    }

    private fun sendDirectSmsAndDecline(recipient: String, message: String) {
        isSendingSmsState = true
        QuickReplyManager.sendDirectSms(this, recipient, message) { result ->
            runOnUiThread {
                isSendingSmsState = false
                when (result) {
                    is QuickReplyResult.Success -> {
                        val partNotice = if (result.partCount > 1) " (${result.partCount} SMS)" else ""
                        Toast.makeText(
                            this,
                            getString(R.string.toast_quick_reply_sent, recipient, partNotice),
                            Toast.LENGTH_SHORT
                        ).show()
                        CallManager.decline()
                        finish()
                    }
                    is QuickReplyResult.Failure -> {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_failed_send_sms, result.reason),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is QuickReplyResult.IntentLaunched -> {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_opened_messaging_app),
                            Toast.LENGTH_SHORT
                        ).show()
                        CallManager.decline()
                        finish()
                    }
                }
            }
        }
    }

    private fun launchMessagingFallbackAndDecline(recipient: String, message: String) {
        val launched = QuickReplyManager.launchMessagingAppFallback(this, recipient, message)
        if (launched) {
            Toast.makeText(
                this,
                getString(R.string.toast_opened_messaging_app),
                Toast.LENGTH_SHORT
            ).show()
            CallManager.decline()
            finish()
        } else {
            Toast.makeText(
                this,
                getString(R.string.toast_unable_open_messaging),
                Toast.LENGTH_LONG
            ).show()
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

    override fun onResume() {
        super.onResume()
        CallManager.reconcileStateWithTelecom()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        CallManager.acquireWakeLocks(this)
        CallManager.reconcileStateWithTelecom()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val uiState = CallManager.callUiState.value
        if (uiState.isAddingCall) {
            CallManager.showAddCall(false)
            return
        }
        val currentState = uiState.callState
        if (currentState == CallState.RINGING) {
            CallManager.decline()
        } else if (currentState == CallState.DIALING && !uiState.isMultiCall && uiState.secondaryCall == null) {
            CallManager.decline()
        } else if (uiState.isMultiCall && uiState.secondaryCall?.callState == CallState.DIALING) {
            CallManager.hangUpSecondaryCall()
        } else {
            super.onBackPressed()
        }
    }
}
