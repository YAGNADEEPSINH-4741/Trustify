package com.safecallai.app

import com.safecallai.app.service.CallScreeningRoleManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CallScreeningRoleManagerTest {

    @Test
    fun testRoleManager_whenRoleServiceNull_returnsFalseAndNullIntent() {
        val manager = CallScreeningRoleManager(context = null, roleManagerProvider = { null })
        assertFalse("When ROLE_SERVICE is null, isCallScreeningRoleHeld must be false", manager.isCallScreeningRoleHeld())
        assertFalse("When ROLE_SERVICE is null, isCallScreeningRoleAvailable must be false", manager.isCallScreeningRoleAvailable())
        assertNull("When ROLE_SERVICE is null, createRequestRoleIntent must be null", manager.createRequestRoleIntent())
    }

    @Test
    fun testRoleManager_withoutProvider_safeDefaults() {
        val manager = CallScreeningRoleManager(context = null)
        assertFalse("Default without provider must report not held", manager.isCallScreeningRoleHeld())
        assertFalse("Default without provider must report unavailable", manager.isCallScreeningRoleAvailable())
        assertNull("Default without provider must return null intent", manager.createRequestRoleIntent())
    }
}
