package com.safecallai.app

import android.app.Application
import com.safecallai.app.data.local.LocalBusinessCacheRepository
import com.safecallai.app.data.local.LocalCallHistoryRepository
import com.safecallai.app.data.local.LocalTrustedCallerRepository
import com.safecallai.app.data.local.PreferencesManager
import com.safecallai.app.data.local.SafeCallDatabaseHelper
import com.safecallai.app.data.remote.SupabaseClient
import com.safecallai.app.data.repository.SafeCallRepository
import com.safecallai.app.domain.engine.CallClassificationEngine
import com.safecallai.app.service.ScreeningNotificationHelper
import com.safecallai.app.verification.VerificationRegistry

class SafeCallApplication : Application() {

    lateinit var repository: SafeCallRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize notification channel
        ScreeningNotificationHelper.createNotificationChannel(this)

        // 2. Initialize local storage & preferences
        val dbHelper = SafeCallDatabaseHelper(this)
        val preferences = PreferencesManager(this)
        val localTrusted = LocalTrustedCallerRepository(dbHelper)
        val localHistory = LocalCallHistoryRepository(dbHelper)
        val localCache = LocalBusinessCacheRepository(dbHelper)

        // 3. Initialize remote client
        val supabaseClient = SupabaseClient(
            getBaseUrl = { preferences.supabaseUrl },
            getAnonKey = { preferences.supabaseAnonKey }
        )

        // 4. Initialize engine & verification providers
        val classificationEngine = CallClassificationEngine()
        val verificationRegistry = VerificationRegistry()

        // 5. Construct unified repository
        repository = SafeCallRepository(
            classificationEngine = classificationEngine,
            verificationRegistry = verificationRegistry,
            localTrustedRepo = localTrusted,
            localHistoryRepo = localHistory,
            localCacheRepo = localCache,
            supabaseClient = supabaseClient,
            preferences = preferences
        )
    }
}
