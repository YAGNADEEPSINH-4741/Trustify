package com.safecallai.app.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.safecallai.app.data.local.AppLanguage
import java.util.Locale

/**
 * Utility helper managing application-wide language and locale switching for Trustify.
 * Supports on-the-fly switching between English, Hindi (हिंदी), and Gujarati (ગુજરાતી)
 * without requiring cloud dependencies or network access.
 */
object LocaleHelper {

    /**
     * Resolves Java/Android Locale object from AppLanguage enum.
     */
    fun getLocale(language: AppLanguage): Locale {
        return when (language) {
            AppLanguage.ENGLISH -> Locale("en")
            AppLanguage.HINDI -> Locale("hi", "IN")
            AppLanguage.GUJARATI -> Locale("gu", "IN")
        }
    }

    /**
     * Creates a Configuration instance configured with the specified AppLanguage locale.
     */
    fun createLocalizedConfiguration(context: Context, language: AppLanguage): Configuration {
        val locale = getLocale(language)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return config
    }

    /**
     * Creates a localized Context wrapping the specified AppLanguage locale.
     */
    fun createLocalizedContext(context: Context, language: AppLanguage): Context {
        val locale = getLocale(language)
        Locale.setDefault(locale)
        val config = createLocalizedConfiguration(context, language)
        return context.createConfigurationContext(config)
    }

    /**
     * Applies the selected AppLanguage system-wide across Application and Activity contexts.
     * Uses Android 13+ (API 33) LocaleManager when available, with backward compatibility.
     */
    fun applyLanguage(context: Context, language: AppLanguage): Context {
        val locale = getLocale(language)
        Locale.setDefault(locale)

        // Android 13+ Per-App Language Preferences
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList.forLanguageTags(language.code)
            } catch (_: Exception) {}
        }

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        return context.createConfigurationContext(config)
    }
}
