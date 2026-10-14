package com.safecallai.app.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

class CallScreeningRoleManager(private val context: Context) {

    /**
     * Determines whether SafeCall AI is actively holding the CALL_SCREENING role on this device.
     */
    fun isCallScreeningRoleHeld(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            // Pre-Android 10: CallScreeningService does not require RoleManager, but requires OEM service binding
            true
        }
    }

    /**
     * Creates the system intent to request the CALL_SCREENING role from the user.
     */
    fun createRequestRoleIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        } else {
            null
        }
    }
}
