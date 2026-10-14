package com.safecallai.app.data.repository

import com.safecallai.app.data.local.LocalBusinessCacheRepository
import com.safecallai.app.data.local.LocalCallHistoryRepository
import com.safecallai.app.data.local.LocalTrustedCallerRepository
import com.safecallai.app.data.local.PreferencesManager
import com.safecallai.app.data.remote.SupabaseClient
import com.safecallai.app.domain.engine.CallClassificationEngine
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.ReportCategory
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.verification.VerificationRegistry

/**
 * Unified Repository managing call screening, data sources, and user preferences.
 */
class SafeCallRepository(
    private val classificationEngine: CallClassificationEngine,
    private val verificationRegistry: VerificationRegistry,
    private val localTrustedRepo: LocalTrustedCallerRepository,
    private val localHistoryRepo: LocalCallHistoryRepository,
    private val localCacheRepo: LocalBusinessCacheRepository,
    private val supabaseClient: SupabaseClient,
    val preferences: PreferencesManager
) {

    /**
     * Fully evaluates and classifies an incoming or searched phone number.
     * Integrates local trust, active booking dispatches, cached businesses,
     * cloud reputation, and offline rules.
     */
    suspend fun screenAndClassify(
        rawNumber: String,
        recordInHistory: Boolean = true
    ): ClassificationResult {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)

        // 1. Check local user trust list
        val userTrustedRecord = localTrustedRepo.findByNormalizedNumber(normalized)

        // 2. Check active dispatch / delivery booking integration
        val activeBookingEvidence = if (preferences.isDeliveryVerificationEnabled) {
            verificationRegistry.checkActiveBookingEvidence(normalized)
        } else null

        // 3. Check verified business (Local Cache first, then Mock/Partner Providers, then Supabase)
        var profile = localCacheRepo.getCachedProfile(normalized)
        if (profile == null) {
            profile = verificationRegistry.checkVerifiedBusiness(normalized)
        }
        if (profile == null && !preferences.isOfflineModeForced && supabaseClient.isConfigured) {
            profile = supabaseClient.fetchVerifiedBusiness(normalized)
            profile?.let { localCacheRepo.cacheProfile(it) }
        }

        val isBackendReachable = !preferences.isOfflineModeForced

        // 4. Run deterministic classification engine
        val result = classificationEngine.classify(
            rawNumber = rawNumber,
            profile = profile,
            activeBookingEvidence = activeBookingEvidence,
            userTrustedRecord = userTrustedRecord,
            userExpectingDelivery = preferences.isUserExpectingDelivery,
            isBackendAvailable = isBackendReachable
        )

        // 5. Record screened call event in history if requested
        if (recordInHistory && PhoneNumberNormalizer.isValid(rawNumber)) {
            val record = ScreenedCallRecord(
                phoneNumber = rawNumber,
                normalizedPhoneNumber = normalized,
                timestamp = System.currentTimeMillis(),
                classification = result.classification,
                verificationStatus = result.verificationStatus,
                callerName = result.callerName ?: result.businessName,
                reason = result.explanation,
                wasSilenced = !result.isSafeToRingNormally && preferences.isAggressiveBlockingEnabled,
                falseSpamRecovered = result.falseSpamRecovered
            )
            localHistoryRepo.recordCall(record)
        }

        return result
    }

    // --- Trusted Numbers ---
    suspend fun getTrustedCallers(): List<UserTrustedCaller> = localTrustedRepo.getAllTrustedCallers()

    suspend fun addTrustedCaller(phoneNumber: String, customLabel: String, notes: String? = null): Long {
        val normalized = PhoneNumberNormalizer.normalize(phoneNumber)
        return localTrustedRepo.addTrustedCaller(
            UserTrustedCaller(
                phoneNumber = phoneNumber,
                normalizedPhoneNumber = normalized,
                customLabel = customLabel,
                notes = notes
            )
        )
    }

    suspend fun removeTrustedCaller(id: Long): Boolean = localTrustedRepo.removeTrustedCaller(id)

    suspend fun isNumberTrusted(phoneNumber: String): Boolean {
        val normalized = PhoneNumberNormalizer.normalize(phoneNumber)
        return localTrustedRepo.findByNormalizedNumber(normalized) != null
    }

    // --- History & Stats ---
    suspend fun getRecentCallHistory(limit: Int = 50): List<ScreenedCallRecord> = localHistoryRepo.getRecentCalls(limit)

    suspend fun clearHistory(): Boolean = localHistoryRepo.clearHistory()

    suspend fun getDashboardStats(): Triple<Int, Int, Int> = localHistoryRepo.getStats()

    // --- Reports ---
    suspend fun submitReport(
        phoneNumber: String,
        category: ReportCategory,
        description: String
    ): Result<Unit> {
        val normalized = PhoneNumberNormalizer.normalize(phoneNumber)
        return supabaseClient.submitReport(normalized, category, description)
    }

    fun getConnectedProvidersInfo(): List<String> = verificationRegistry.getConnectedProvidersInfo()
}
