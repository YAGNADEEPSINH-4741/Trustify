package com.safecallai.app

import com.safecallai.app.data.local.PreferencesManager.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeModeTest {

    @Test
    fun testThemeModeEnumValues() {
        assertEquals(3, ThemeMode.values().size)
        assertNotNull(ThemeMode.valueOf("LIGHT"))
        assertNotNull(ThemeMode.valueOf("DARK"))
        assertNotNull(ThemeMode.valueOf("SYSTEM_DEFAULT"))
    }

    @Test
    fun testThemeModeFallback() {
        val parsed = try {
            ThemeMode.valueOf("INVALID_THEME")
        } catch (_: Exception) {
            ThemeMode.SYSTEM_DEFAULT
        }
        assertEquals(ThemeMode.SYSTEM_DEFAULT, parsed)
    }

    @Test
    fun testThemeModeStringPersistenceFormat() {
        assertEquals("LIGHT", ThemeMode.LIGHT.name)
        assertEquals("DARK", ThemeMode.DARK.name)
        assertEquals("SYSTEM_DEFAULT", ThemeMode.SYSTEM_DEFAULT.name)
    }
}
