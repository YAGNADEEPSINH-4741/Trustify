package com.safecallai.app

import android.app.Application
import com.safecallai.app.data.local.LocalBusinessCacheRepository
import com.safecallai.app.data.local.LocalCallHistoryRepository
import com.safecallai.app.data.local.LocalReportRepository
import com.safecallai.app.data.local.LocalTrustedCallerRepository
import com.safecallai.app.data.local.PreferencesManager
import com.safecallai.app.data.local.SafeCallDatabaseHelper
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

        // 2. Initialize on-device local storage & preferences
        val dbHelper = SafeCallDatabaseHelper(this)
        val preferences = PreferencesManager(this)
        val localTrusted = LocalTrustedCallerRepository(dbHelper)
        val localHistory = LocalCallHistoryRepository(dbHelper)
        val localCache = LocalBusinessCacheRepository(dbHelper)
        val localReports = LocalReportRepository(dbHelper)
        val communityRepo = com.safecallai.app.data.repository.CommunityReputationRepository(dbHelper, preferences)
        val identityCache = com.safecallai.app.data.local.CallerIdentityCache(dbHelper)

        // 3. Initialize engine & local verification providers
        val classificationEngine = CallClassificationEngine()
        val verificationRegistry = VerificationRegistry()
        val callerIdentityEngine = com.safecallai.app.domain.engine.CallerIdentificationEngine(
            identityCache = identityCache,
            osmDirectoryProvider = com.safecallai.app.domain.engine.OsmBusinessDirectoryProvider()
        )

        // 4. Construct unified repository with community-powered reputation and caller identification
        repository = SafeCallRepository(
            classificationEngine = classificationEngine,
            verificationRegistry = verificationRegistry,
            localTrustedRepo = localTrusted,
            localHistoryRepo = localHistory,
            localCacheRepo = localCache,
            localReportRepo = localReports,
            preferences = preferences,
            communityRepo = communityRepo,
            context = this,
            callerIdentityEngine = callerIdentityEngine
        )

        // 5. Apply saved language preference (English, Hindi, or Gujarati)
        com.safecallai.app.util.LocaleHelper.applyLanguage(this, preferences.appLanguage)
    }
}
