package com.safecallai.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.safecallai.app.MainActivity
import com.safecallai.app.R
import com.safecallai.app.SafeCallApplication
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ClassificationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles real-time incoming call caller-identification notifications.
 *
 * Produces heads-up notifications with verification badges, evidence summaries,
 * and quick action intents to inspect evidence or report spam.
 */
object ScreeningNotificationHelper {

    const val CHANNEL_ID = "safecall_screening_channel"
    private const val NOTIFICATION_ID_BASE = 2000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Call Screening & Caller ID"
            val descriptionText = "Displays real-time legitimate caller identification and false-spam recovery alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lightColor = Color.BLUE
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showCallScreenedNotification(context: Context, result: ClassificationResult) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PHONE_NUMBER", result.rawNumber)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            result.rawNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayPhone = PhoneNumberNormalizer.formatForDisplay(result.normalizedNumber)
        val callerName = result.callerName ?: displayPhone

        // Distinctive title and color based on transparent classification
        val pair = when (result.classification) {
            CallerClassification.VERIFIED_LEGITIMATE -> {
                val prefix = if (result.falseSpamRecovered) "✓ False Spam Recovered" else "✓ Verified Caller"
                Pair(prefix, Color.parseColor("#10B981"))
            }
            CallerClassification.LIKELY_LEGITIMATE -> {
                Pair("Likely Legitimate", Color.parseColor("#06B6D4"))
            }
            CallerClassification.POTENTIAL_SPAM -> {
                Pair("⚠️ Suspected Spam", Color.parseColor("#F59E0B"))
            }
            CallerClassification.HIGH_RISK -> {
                Pair("🚨 High-Risk Scammer", Color.parseColor("#EF4444"))
            }
            CallerClassification.UNKNOWN -> {
                Pair("ℹ️ Unverified Caller", Color.parseColor("#64748B"))
            }
        }
        val titlePrefix = pair.first
        val colorArgb = pair.second

        val title = "$titlePrefix: $callerName"
        val evidenceSummary = result.evidenceList.firstOrNull()?.description
            ?: "Evaluated via transparent deterministic rules engine"

        val bigText = buildString {
            append(result.explanation)
            append("\n\n")
            append("• Number: ").append(displayPhone).append("\n")
            append("• Status: ").append(result.verificationStatus.displayName).append("\n")
            append("• Primary Evidence: ").append(evidenceSummary)
            if (!result.isSafeToRingNormally) {
                append("\n• Ringtone: Silenced by Aggressive Scam Filter")
            } else {
                append("\n• Ringtone: Ringing normally")
            }
        }

        val reportIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PHONE_NUMBER", result.rawNumber)
            putExtra("EXTRA_NAV_TARGET", "REPORT")
        }
        val reportPendingIntent = PendingIntent.getActivity(
            context,
            (result.rawNumber + "_report").hashCode(),
            reportIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_safecall_shield)
            .setContentTitle(title)
            .setContentText(result.explanation)
            .setSubText(displayPhone)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(colorArgb)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_safecall_shield, "View Evidence", pendingIntent)
            .addAction(android.R.drawable.ic_menu_report_image, "Report Flag", reportPendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = NOTIFICATION_ID_BASE + Math.abs(result.normalizedNumber.hashCode() % 1000)
        notificationManager.notify(id, notification)
    }

    /**
     * Simulates an incoming call screening event for live on-device testing.
     * Evaluates the number using the full engine and displays the live notification.
     */
    fun simulateScreeningCall(context: Context, rawNumber: String, onComplete: ((ClassificationResult) -> Unit)? = null) {
        val app = context.applicationContext as? SafeCallApplication ?: return
        val repository = app.repository
        CoroutineScope(Dispatchers.IO).launch {
            val result = repository.screenAndClassify(rawNumber, recordInHistory = true)
            showCallScreenedNotification(context, result)
            onComplete?.invoke(result)
        }
    }
}
