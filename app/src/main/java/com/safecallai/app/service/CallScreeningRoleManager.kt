package com.safecallai.app.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

class CallScreeningRoleManager(
    private val context: Context? = null,
    private val roleManagerProvider: (() -> RoleManager?)? = null
) {

    private fun getRoleManager(): RoleManager? {
        if (roleManagerProvider != null) {
            return roleManagerProvider.invoke()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context?.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Determines whether SafeCall AI is actively holding the CALL_SCREENING role on this device.
     */
    fun isCallScreeningRoleHeld(): Boolean {
        return try {
            getRoleManager()?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks whether the CALL_SCREENING role is available on this Android device.
     */
    fun isCallScreeningRoleAvailable(): Boolean {
        return try {
            getRoleManager()?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Creates the system intent to request the CALL_SCREENING role from the user.
     */
    fun createRequestRoleIntent(): Intent? {
        val roleManager = getRoleManager() ?: return null
        return try {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
