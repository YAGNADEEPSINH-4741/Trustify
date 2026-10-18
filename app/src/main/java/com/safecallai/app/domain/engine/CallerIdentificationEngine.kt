package com.safecallai.app.domain.engine

import android.content.Context
import android.telecom.Call
import android.telecom.TelecomManager
import com.safecallai.app.data.local.CallerIdentityCache
import com.safecallai.app.domain.models.CallerIdentityResult
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Unified Caller Identification Engine for Trustify.
 *
 * Implements a strict 6-stage cascading identification pipeline:
 * 1. Saved Contacts (Local ContactsContract) - Always takes top precedence
 * 2. Network-Provided Calling Name Presentation (Android Telecom Call.Details)
 * 3. Verified Indian Regulatory Series (TRAI 140, 1600, 1601, 1800, emergency short codes)
 * 4. Local Identity Cache (SQLite caller_identity_cache)
 * 5. Public Business Directory (OpenStreetMap POI directory)
 * 6. Fallback: Formatted number + "No reliable name found"
 *
 * Requirements:
 * - 100% Non-blocking: Executes within call-screening latency limits.
 * - Privacy-First: Never uploads contact address books or transmits personal identities.
 * - Identity strictly decoupled from spam reputation.
 */
class CallerIdentificationEngine(
    private val identityCache: CallerIdentityCache? = null,
    private val osmDirectoryProvider: OsmBusinessDirectoryProvider = OsmBusinessDirectoryProvider()
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Resolves caller identity using available local context and telecom signals.
     */
    suspend fun identifyCaller(
        rawNumber: String,
        telecomDetails: Call.Details? = null,
        telecomDisplayName: String? = null,
        telecomPresentation: Int = TelecomManager.PRESENTATION_UNKNOWN,
        context: Context? = null,
        allowNetworkQuery: Boolean = false
    ): CallerIdentityResult {
        if (rawNumber.isBlank()) {
            return createUnknownResult(rawNumber, "Empty number")
        }

        val normalized = PhoneNumberNormalizer.normalize(rawNumber)

        // =========================================================================
        // STAGE 1: Saved Contacts (Local ContactsContract) - Always Highest Precedence
        // =========================================================================
        if (context != null) {
            val contactInfo = ContactLookupHelper.lookupContact(context, rawNumber)
            if (contactInfo.isSavedContact && !contactInfo.name.isNullOrBlank()) {
                return CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    displayName = contactInfo.name,
                    source = CallerIdentitySource.SAVED_CONTACT,
                    identityType = CallerIdentityType.SAVED_CONTACT,
                    confidence = IdentityConfidence.VERIFIED,
                    sourceLabel = "Saved contact",
                    isVerified = true,
                    organizationName = null,
                    category = "Personal Contacts",
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        // =========================================================================
        // STAGE 2: Android Telecom Network Calling Name Presentation (CNAP)
        // =========================================================================
        val networkCnap = if (telecomDetails != null) {
            TelecomCnapExtractor.extract(telecomDetails, rawNumber)
        } else if (!telecomDisplayName.isNullOrBlank()) {
            TelecomCnapExtractor.extract(telecomDisplayName, telecomPresentation, rawNumber)
        } else {
            null
        }

        if (networkCnap != null && networkCnap.hasReliableName) {
            cacheAsync(networkCnap)
            return networkCnap
        }

        // =========================================================================
        // STAGE 3: Verified Indian Telecom Series Classification (TRAI 140 / 160 / 1800)
        // =========================================================================
        val traiClassification = TraiNumberSeriesClassifier.classify(rawNumber)
        if (traiClassification != null && traiClassification.hasReliableName) {
            cacheAsync(traiClassification)
            return traiClassification
        }

        // =========================================================================
        // STAGE 4: Local On-Device Identity Cache (SQLite)
        // =========================================================================
        if (identityCache != null) {
            val cachedResult = identityCache.get(normalized)
            if (cachedResult != null && cachedResult.hasReliableName) {
                return cachedResult
            }
        }

        // =========================================================================
        // STAGE 5: OpenStreetMap Public Business Directory (ODbL)
        // =========================================================================
        // 5a. Local instant POI directory
        val localOsmMatch = osmDirectoryProvider.lookupLocal(rawNumber)
        if (localOsmMatch != null && localOsmMatch.hasReliableName) {
            cacheAsync(localOsmMatch)
            return localOsmMatch
        }

        // 5b. Optional async network lookup if permitted
        if (allowNetworkQuery) {
            val overpassMatch = osmDirectoryProvider.queryOverpass(rawNumber)
            if (overpassMatch != null && overpassMatch.hasReliableName) {
                cacheAsync(overpassMatch)
                return overpassMatch
            }
        }

        // =========================================================================
        // STAGE 6: Fallback (No Reliable Name Available)
        // =========================================================================
        return createUnknownResult(rawNumber, "No reliable name found")
    }

    private fun cacheAsync(result: CallerIdentityResult) {
        if (identityCache != null && result.hasReliableName && result.source != CallerIdentitySource.SAVED_CONTACT) {
            engineScope.launch {
                try {
                    identityCache.put(result)
                } catch (_: Exception) {
                    // Safe non-fatal failure
                }
            }
        }
    }

    private fun createUnknownResult(rawNumber: String, reason: String): CallerIdentityResult {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        return CallerIdentityResult(
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayName = "Unknown Caller",
            source = CallerIdentitySource.NONE,
            identityType = CallerIdentityType.UNKNOWN,
            confidence = IdentityConfidence.NONE,
            sourceLabel = reason,
            isVerified = false,
            timestamp = System.currentTimeMillis()
        )
    }
}
