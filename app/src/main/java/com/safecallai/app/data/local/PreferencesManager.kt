package com.safecallai.app.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "safecall_preferences"
        private const val KEY_PROTECTION_ENABLED = "key_protection_enabled"
        private const val KEY_AGGRESSIVE_BLOCKING = "key_aggressive_blocking"
        private const val KEY_DELIVERY_VERIFICATION = "key_delivery_verification"
        private const val KEY_USER_EXPECTING_DELIVERY = "key_user_expecting_delivery"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_SUPABASE_URL = "key_supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "key_supabase_anon_key"
        private const val KEY_OFFLINE_MODE = "key_offline_mode"
        private const val KEY_THEME_MODE = "key_theme_mode"
    }

    enum class ThemeMode {
        LIGHT,
        DARK,
        SYSTEM_DEFAULT
    }

    var themeMode: ThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM_DEFAULT.name)
            return try {
                ThemeMode.valueOf(name ?: ThemeMode.SYSTEM_DEFAULT.name)
            } catch (_: Exception) {
                ThemeMode.SYSTEM_DEFAULT
            }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    var isAggressiveBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AGGRESSIVE_BLOCKING, false) // Default false: let calls ring normally!
        set(value) = prefs.edit().putBoolean(KEY_AGGRESSIVE_BLOCKING, value).apply()

    var isDeliveryVerificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DELIVERY_VERIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_DELIVERY_VERIFICATION, value).apply()

    var isUserExpectingDelivery: Boolean
        get() = prefs.getBoolean(KEY_USER_EXPECTING_DELIVERY, false)
        set(value) = prefs.edit().putBoolean(KEY_USER_EXPECTING_DELIVERY, value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var supabaseUrl: String
        get() = prefs.getString(KEY_SUPABASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_URL, value).apply()

    var supabaseAnonKey: String
        get() = prefs.getString(KEY_SUPABASE_ANON_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_ANON_KEY, value).apply()

    var isOfflineModeForced: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()
}
