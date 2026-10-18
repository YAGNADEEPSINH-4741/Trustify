package com.safecallai.app.data.local

import android.content.Context
import android.content.SharedPreferences

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String
) {
    ENGLISH("en", "English", "English", "🇬🇧"),
    HINDI("hi", "Hindi", "हिंदी", "🇮🇳"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી", "🇮🇳");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "safecall_preferences"
        private const val KEY_PROTECTION_ENABLED = "key_protection_enabled"
        private const val KEY_AGGRESSIVE_BLOCKING = "key_aggressive_blocking"
        private const val KEY_DELIVERY_VERIFICATION = "key_delivery_verification"
        private const val KEY_USER_EXPECTING_DELIVERY = "key_user_expecting_delivery"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_OFFLINE_MODE = "key_offline_mode"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_APP_LANGUAGE = "key_app_language"
        private const val KEY_HAS_SELECTED_LANGUAGE = "key_has_selected_language"
        private const val KEY_SUPABASE_URL = "key_supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "key_supabase_anon_key"
        private const val KEY_DEVICE_AUTH_TOKEN = "key_device_auth_token"
        private const val KEY_DEVICE_AUTH_EMAIL = "key_device_auth_email"
        private const val KEY_DEVICE_AUTH_PASSWORD = "key_device_auth_password"
        private const val KEY_CONTACTS_PROMPT_DISMISSED = "key_contacts_prompt_dismissed"

        const val DEFAULT_SUPABASE_URL = "https://lulpzwyzlgxzqltzxawj.supabase.co"
        const val DEFAULT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx1bHB6d3l6bGd4enFsdHp4YXdqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIxNDYzNzksImV4cCI6MjEwNzcyMjM3OX0.HXVvEc1L1GRxgrTeU1N226hKNsEu7eyfOkrumD283mY"
    }

    enum class ThemeMode {
        LIGHT,
        DARK,
        SYSTEM_DEFAULT
    }

    var appLanguage: AppLanguage
        get() {
            val code = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.ENGLISH.code)
            return AppLanguage.fromCode(code)
        }
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value.code).apply()

    var hasSelectedLanguage: Boolean
        get() = prefs.getBoolean(KEY_HAS_SELECTED_LANGUAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SELECTED_LANGUAGE, value).apply()

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

    var isOfflineModeForced: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()

    var supabaseUrl: String
        get() = prefs.getString(KEY_SUPABASE_URL, DEFAULT_SUPABASE_URL)?.ifBlank { DEFAULT_SUPABASE_URL } ?: DEFAULT_SUPABASE_URL
        set(value) = prefs.edit().putString(KEY_SUPABASE_URL, value.trim()).apply()

    var supabaseAnonKey: String
        get() = prefs.getString(KEY_SUPABASE_ANON_KEY, DEFAULT_SUPABASE_ANON_KEY)?.ifBlank { DEFAULT_SUPABASE_ANON_KEY } ?: DEFAULT_SUPABASE_ANON_KEY
        set(value) = prefs.edit().putString(KEY_SUPABASE_ANON_KEY, value.trim()).apply()

    var deviceAuthToken: String
        get() = prefs.getString(KEY_DEVICE_AUTH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_AUTH_TOKEN, value.trim()).apply()

    var deviceAuthEmail: String
        get() = prefs.getString(KEY_DEVICE_AUTH_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_AUTH_EMAIL, value.trim()).apply()

    var deviceAuthPassword: String
        get() = prefs.getString(KEY_DEVICE_AUTH_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_AUTH_PASSWORD, value.trim()).apply()

    var hasDismissedContactsPrompt: Boolean
        get() = prefs.getBoolean(KEY_CONTACTS_PROMPT_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONTACTS_PROMPT_DISMISSED, value).apply()

    fun clearAllPreferences() {
        prefs.edit().clear().apply()
    }
}
