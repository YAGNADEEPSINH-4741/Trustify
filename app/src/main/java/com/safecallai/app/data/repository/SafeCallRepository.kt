package com.safecallai.app.data.repository

import com.safecallai.app.data.local.LocalBusinessCacheRepository
import com.safecallai.app.data.local.LocalCallHistoryRepository
import com.safecallai.app.data.local.LocalReportDraft
import com.safecallai.app.data.local.LocalReportRepository
import com.safecallai.app.data.local.LocalTrustedCallerRepository
import com.safecallai.app.data.local.PreferencesManager
import com.safecallai.app.domain.engine.CallClassificationEngine
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.ReportCategory
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.verification.VerificationRegistry

/**
 * Unified Repository managing call screening, local data sources, and user preferences.
 * 100% on-device local architecture — no cloud backend or remote server dependency.
 */
class SafeCallRepository(
    private val classificationEngine: CallClassificationEngine,
    private val verificationRegistry: VerificationRegistry,
    private val localTrustedRepo: LocalTrustedCallerRepository,
    private val localHistoryRepo: LocalCallHistoryRepository,
    private val localCacheRepo: LocalBusinessCacheRepository,
    private val localReportRepo: LocalReportRepository,
    val preferences: PreferencesManager,
    val communityRepo: CommunityReputationRepository,
    val context: android.content.Context? = null,
    val callerIdentityEngine: com.safecallai.app.domain.engine.CallerIdentificationEngine = com.safecallai.app.domain.engine.CallerIdentificationEngine()
) {

    /**
     * Fully evaluates and classifies an incoming or searched phone number locally on-device.
     * Integrates local personal trust, active dispatch verification, locally cached business
     * registrations, community-powered spam reputation, and caller identification.
     */
    suspend fun screenAndClassify(
        rawNumber: String,
        recordInHistory: Boolean = true,
        telecomDetails: android.telecom.Call.Details? = null
    ): ClassificationResult {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)

        // 1. Check local user trust list (Stored in on-device SQLite)
        var userTrustedRecord = localTrustedRepo.findByNormalizedNumber(normalized)

        // 1b. Check device contacts: if in contacts, treat as trusted contact on-device
        val contactLookup = if (context != null) {
            com.safecallai.app.domain.engine.ContactLookupHelper.lookupContact(context, rawNumber)
        } else null

        if (userTrustedRecord == null && contactLookup != null && contactLookup.isSavedContact && !contactLookup.name.isNullOrBlank()) {
            userTrustedRecord = UserTrustedCaller(
                phoneNumber = rawNumber,
                normalizedPhoneNumber = normalized,
                customLabel = contactLookup.name,
                notes = "Saved in Phone Contacts"
            )
        }

        // 2. Check active dispatch / delivery booking integration
        val activeBookingEvidence = if (preferences.isDeliveryVerificationEnabled) {
            verificationRegistry.checkActiveBookingEvidence(normalized)
        } else null

        // 3. Check verified business (Local SQLite Cache first, then local partner registries)
        var profile = localCacheRepo.getCachedProfile(normalized)
        if (profile == null) {
            profile = verificationRegistry.checkVerifiedBusiness(normalized)
            profile?.let { localCacheRepo.cacheProfile(it) }
        }

        // 4. Check community reputation (non-blocking fast in-memory cache/async)
        val communityRep = communityRepo.getReputation(rawNumber)

        // 5. Run deterministic classification engine incorporating community consensus
        val result = classificationEngine.classify(
            rawNumber = rawNumber,
            profile = profile,
            activeBookingEvidence = activeBookingEvidence,
            userTrustedRecord = userTrustedRecord,
            userExpectingDelivery = preferences.isUserExpectingDelivery,
            isBackendAvailable = !preferences.isOfflineModeForced,
            communityReputation = communityRep
        )

        // 6. Record screened call event in local history if requested with resolved identity
        if (recordInHistory && PhoneNumberNormalizer.isValid(rawNumber)) {
            val identityResult = callerIdentityEngine.identifyCaller(
                rawNumber = rawNumber,
                telecomDetails = telecomDetails,
                context = context,
                allowNetworkQuery = false
            )
            val effectiveCallerName = identityResult.displayName
                ?: if (context != null) com.safecallai.app.domain.engine.ContactLookupHelper.lookupContact(context, rawNumber).name else null
                ?: result.callerName
                ?: result.businessName

            val record = ScreenedCallRecord(
                phoneNumber = rawNumber,
                normalizedPhoneNumber = normalized,
                timestamp = System.currentTimeMillis(),
                classification = result.classification,
                verificationStatus = result.verificationStatus,
                callerName = effectiveCallerName,
                reason = result.explanation,
                wasSilenced = !result.isSafeToRingNormally && preferences.isAggressiveBlockingEnabled,
                falseSpamRecovered = result.falseSpamRecovered
            )
            localHistoryRepo.recordCall(record)
        }

        return result
    }

    /**
     * Resolves caller identity using the 6-stage identification pipeline.
     */
    suspend fun identifyCaller(
        rawNumber: String,
        telecomDetails: android.telecom.Call.Details? = null,
        telecomDisplayName: String? = null,
        telecomPresentation: Int = android.telecom.TelecomManager.PRESENTATION_UNKNOWN,
        allowNetworkQuery: Boolean = false
    ): com.safecallai.app.domain.models.CallerIdentityResult {
        return callerIdentityEngine.identifyCaller(
            rawNumber = rawNumber,
            telecomDetails = telecomDetails,
            telecomDisplayName = telecomDisplayName,
            telecomPresentation = telecomPresentation,
            context = context,
            allowNetworkQuery = allowNetworkQuery
        )
    }

    // --- Trusted Numbers (100% Local On-Device) ---
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

    // --- History & Stats (100% Local On-Device) ---
    suspend fun getRecentCallHistory(limit: Int = 50): List<ScreenedCallRecord> = localHistoryRepo.getRecentCalls(limit)

    suspend fun clearHistory(): Boolean = localHistoryRepo.clearHistory()

    suspend fun clearBusinessCache(): Boolean = localCacheRepo.clearCache()

    suspend fun getDashboardStats(): Triple<Int, Int, Int> = localHistoryRepo.getStats()

    // --- Reports & Feedback (Stored Locally in SQLite) ---
    suspend fun submitReport(
        phoneNumber: String,
        category: ReportCategory,
        description: String
    ): Result<Unit> {
        val normalized = PhoneNumberNormalizer.normalize(phoneNumber)
        val rowId = localReportRepo.saveReport(
            phoneNumber = phoneNumber,
            normalizedPhoneNumber = normalized,
            category = category.name,
            description = description
        )
        return if (rowId > 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to save report locally."))
        }
    }

    suspend fun getLocalReports(): List<LocalReportDraft> = localReportRepo.getAllReports()

    suspend fun clearAllLocalData(): Boolean {
        val h = localHistoryRepo.clearHistory()
        val c = localCacheRepo.clearCache()
        val r = localReportRepo.clearReports()
        val callers = localTrustedRepo.getAllTrustedCallers()
        callers.forEach { localTrustedRepo.removeTrustedCaller(it.id) }
        return h && c && r
    }

    fun getConnectedProvidersInfo(): List<String> = verificationRegistry.getConnectedProvidersInfo()
}
