package com.safecallai.app.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager

/**
 * Manages detection and requests for the Android Default Dialer (Phone App) role.
 *
 * Holding the Default Dialer role is the official Android mechanism allowing an app
 * to display a custom InCallService incoming-call screen and execute answer/decline actions.
 */
class DefaultDialerManager(private val context: Context) {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
    } else {
        null
    }

    /**
     * Checks if this application is currently the system's default phone/dialer application.
     */
    fun isDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    /**
     * Returns an intent to prompt the user to make this application the default phone app.
     * Returns null if already default dialer.
     */
    fun createRequestDefaultDialerIntent(): Intent? {
        if (isDefaultDialer()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            }
        }

        try {
            @Suppress("DEPRECATION")
            return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        } catch (_: Exception) {}

        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    /**
     * Intent opening the App Info screen in System Settings, where the user can tap
     * the 3 dots (⋮) in the top right corner and choose "Allow restricted settings"
     * on Android 13+.
     */
    fun createAppInfoIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Intent opening Default Apps in System Settings, where the user can select SafeCall AI
     * directly under "Phone app".
     */
    fun createDefaultAppsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}


