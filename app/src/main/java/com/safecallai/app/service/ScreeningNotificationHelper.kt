package com.safecallai.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.safecallai.app.MainActivity
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.ClassificationResult

object ScreeningNotificationHelper {

    private const val CHANNEL_ID = "safecall_screening_channel"
    private const val NOTIFICATION_ID_BASE = 2000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Call Screening Alerts"
            val descriptionText = "Displays real-time false spam recovery and caller verification alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
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
        val title = if (result.falseSpamRecovered) {
            "✓ False Spam Recovered: ${result.callerName ?: displayPhone}"
        } else {
            "SafeCall: ${result.classification.displayName} (${result.callerName ?: displayPhone})"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(result.explanation)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${result.explanation}\n\nEvidence: ${result.evidenceList.firstOrNull()?.description ?: "Analyzed via SafeCall AI rules"}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = NOTIFICATION_ID_BASE + (result.normalizedNumber.hashCode() % 1000)
        notificationManager.notify(id, notification)
    }
}
