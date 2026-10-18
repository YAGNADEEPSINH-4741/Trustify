package com.safecallai.app.service

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of attempting to send a quick reply SMS.
 */
sealed class QuickReplyResult {
    data class Success(
        val recipient: String,
        val message: String,
        val partCount: Int
    ) : QuickReplyResult()

    data class Failure(
        val reason: String,
        val canFallbackToIntent: Boolean = true
    ) : QuickReplyResult()

    data class IntentLaunched(
        val recipient: String
    ) : QuickReplyResult()
}

/**
 * Breakdown of SMS character count, multi-segment calculation, and encoding details.
 */
data class SmsSegmentInfo(
    val charCount: Int,
    val segmentCount: Int,
    val remainingInCurrentSegment: Int,
    val maxSingleSegmentChars: Int,
    val isUnicode: Boolean
)

/**
 * Coordinates quick reply messaging during incoming phone calls:
 * - Validates caller phone numbers and prevents dispatch to private or invalid numbers.
 * - Computes SMS character counting and multi-segment GSM/UCS-2 segmentation.
 * - Direct SMS dispatch using Android's SmsManager with multipart support and dispatch confirmation.
 * - Graceful fallback to Android ACTION_SENDTO messaging intents if direct SMS is denied or unavailable.
 * - 100% on-device execution with zero cloud or third-party communications.
 */
object QuickReplyManager {

    const val ACTION_SMS_SENT = "com.safecallai.app.SMS_SENT_STATUS"

    val PREDEFINED_MESSAGE_RES_IDS: List<Int> = listOf(
        com.safecallai.app.R.string.quick_reply_busy,
        com.safecallai.app.R.string.quick_reply_out_of_station,
        com.safecallai.app.R.string.quick_reply_call_5_min,
        com.safecallai.app.R.string.quick_reply_driving,
        com.safecallai.app.R.string.quick_reply_cant_talk,
        com.safecallai.app.R.string.quick_reply_in_meeting
    )

    val PREDEFINED_MESSAGES: List<String> = listOf(
        "I'm busy right now. I'll call you back later.",
        "I'm out of station at the moment.",
        "I'll call you in 5 minutes.",
        "I'm driving right now. I'll contact you when I arrive.",
        "I can't talk right now. Please message me.",
        "I'm in a meeting. I'll call you back when I'm free."
    )

    fun getPredefinedMessages(context: Context): List<String> {
        return PREDEFINED_MESSAGE_RES_IDS.map { resId ->
            context.getString(resId)
        }
    }

    private val isSendingInProgress = AtomicBoolean(false)

    /**
     * Checks if the device has granted runtime permission to send SMS directly.
     */
    fun hasSendSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Validates whether a caller's phone number can be texted via SMS.
     * Rejects empty, unknown, private, or malformed numbers.
     */
    fun isValidRecipient(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        val trimmed = phoneNumber.trim().lowercase()
        if (trimmed == "private number" || trimmed == "unknown" || trimmed == "restricted" || trimmed == "anonymous") {
            return false
        }
        return PhoneNumberNormalizer.isValid(phoneNumber)
    }

    /**
     * Calculates character and segment counts for standard SMS billing and display.
     * Standard GSM 7-bit: 160 chars single segment, 153 chars per segment when concatenated.
     * UCS-2 (Unicode / emojis / non-Latin): 70 chars single, 67 chars per segment concatenated.
     */
    fun calculateSmsSegmentInfo(text: String): SmsSegmentInfo {
        val count = text.length
        if (count == 0) {
            return SmsSegmentInfo(
                charCount = 0,
                segmentCount = 1,
                remainingInCurrentSegment = 160,
                maxSingleSegmentChars = 160,
                isUnicode = false
            )
        }

        val isUnicode = text.any { it.code > 127 && !isExtendedGsmChar(it) }
        val maxSingle = if (isUnicode) 70 else 160
        val maxConcat = if (isUnicode) 67 else 153

        val segmentCount: Int
        val remaining: Int

        if (count <= maxSingle) {
            segmentCount = 1
            remaining = maxSingle - count
        } else {
            segmentCount = ((count - 1) / maxConcat) + 1
            val charsInLast = count - ((segmentCount - 1) * maxConcat)
            remaining = maxConcat - charsInLast
        }

        return SmsSegmentInfo(
            charCount = count,
            segmentCount = segmentCount,
            remainingInCurrentSegment = remaining,
            maxSingleSegmentChars = maxSingle,
            isUnicode = isUnicode
        )
    }

    private fun isExtendedGsmChar(c: Char): Boolean {
        // Extended GSM-7 set (escaped chars)
        return c in "^|{}[]~€\\ \n\r"
    }

    /**
     * Obtains the appropriate SmsManager instance for current Android platform version.
     */
    fun getSmsManager(context: Context): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sends a real SMS directly to the recipient using Android's SmsManager.
     *
     * @param context Application or Activity context
     * @param recipient Phone number to receive the message
     * @param message Text body to transmit
     * @param onResult Callback delivering Success or Failure
     */
    fun sendDirectSms(
        context: Context,
        recipient: String,
        message: String,
        onResult: (QuickReplyResult) -> Unit
    ) {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) {
            onResult(QuickReplyResult.Failure("Message cannot be empty", canFallbackToIntent = false))
            return
        }

        if (!isValidRecipient(recipient)) {
            onResult(QuickReplyResult.Failure("Caller's phone number is invalid or unavailable", canFallbackToIntent = false))
            return
        }

        if (!hasSendSmsPermission(context)) {
            onResult(QuickReplyResult.Failure("SMS permission not granted", canFallbackToIntent = true))
            return
        }

        // Prevent duplicate concurrent dispatches
        if (!isSendingInProgress.compareAndSet(false, true)) {
            return
        }

        val smsManager = getSmsManager(context)
        if (smsManager == null) {
            isSendingInProgress.set(false)
            onResult(QuickReplyResult.Failure("SMS service unavailable on this device", canFallbackToIntent = true))
            return
        }

        val appContext = context.applicationContext

        try {
            val parts = smsManager.divideMessage(cleanMessage)
            val partCount = parts.size
            val uniqueAction = "$ACTION_SMS_SENT.${System.currentTimeMillis()}"

            val receiver = object : BroadcastReceiver() {
                private var receivedCount = 0
                private var hasReportedError = false

                override fun onReceive(recvContext: Context, intent: Intent) {
                    receivedCount++
                    val code = resultCode

                    if (code != Activity.RESULT_OK && !hasReportedError) {
                        hasReportedError = true
                        isSendingInProgress.set(false)
                        try {
                            appContext.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        val errorDesc = when (code) {
                            SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service available"
                            SmsManager.RESULT_ERROR_RADIO_OFF -> "Cellular radio / airplane mode is off"
                            SmsManager.RESULT_ERROR_NULL_PDU -> "SMS protocol error (Null PDU)"
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Carrier network transmission failure"
                            else -> "SMS failed to transmit (code: $code)"
                        }
                        onResult(QuickReplyResult.Failure(errorDesc, canFallbackToIntent = true))
                        return
                    }

                    if (receivedCount >= partCount && !hasReportedError) {
                        isSendingInProgress.set(false)
                        try {
                            appContext.unregisterReceiver(this)
                        } catch (_: Exception) {}
                        onResult(QuickReplyResult.Success(recipient, cleanMessage, partCount))
                    }
                }
            }

            // Register receiver with appropriate export flags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    receiver,
                    IntentFilter(uniqueAction),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                appContext.registerReceiver(
                    receiver,
                    IntentFilter(uniqueAction)
                )
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }

            if (partCount <= 1) {
                val sentIntent = PendingIntent.getBroadcast(
                    appContext,
                    0,
                    Intent(uniqueAction).setPackage(appContext.packageName),
                    flags
                )
                smsManager.sendTextMessage(recipient, null, cleanMessage, sentIntent, null)
            } else {
                val sentIntents = ArrayList<PendingIntent>(partCount)
                for (i in 0 until partCount) {
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            appContext,
                            i,
                            Intent(uniqueAction).setPackage(appContext.packageName),
                            flags
                        )
                    )
                }
                smsManager.sendMultipartTextMessage(recipient, null, parts, sentIntents, null)
            }

        } catch (e: SecurityException) {
            isSendingInProgress.set(false)
            onResult(QuickReplyResult.Failure("Permission denied: ${e.message}", canFallbackToIntent = true))
        } catch (e: Exception) {
            isSendingInProgress.set(false)
            onResult(QuickReplyResult.Failure("Failed to send SMS: ${e.message ?: "Unknown error"}", canFallbackToIntent = true))
        }
    }

    /**
     * Builds standard smsto: URI string for messaging intents.
     */
    fun buildSmstoUriString(recipient: String): String {
        return "smsto:${recipient.trim()}"
    }

    /**
     * Builds an Android ACTION_SENDTO intent to open the user's default messaging app
     * with the recipient and message pre-populated.
     */
    fun createMessagingIntent(recipient: String, message: String): Intent {
        val uri = Uri.parse(buildSmstoUriString(recipient))
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches the default messaging app when direct SMS permission is denied or unavailable.
     */
    fun launchMessagingAppFallback(context: Context, recipient: String, message: String): Boolean {
        return try {
            val intent = createMessagingIntent(recipient, message)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
