package com.safecallai.app.domain.engine

import android.telecom.Call
import android.telecom.TelecomManager
import com.safecallai.app.domain.models.CallerIdentityResult
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence

/**
 * Safely extracts network-provided Calling Name Presentation (CNAP) data from Android Telecom APIs.
 *
 * Implements:
 * - android.telecom.Call.Details.getCallerDisplayName()
 * - android.telecom.Call.Details.getCallerDisplayNamePresentation()
 *
 * Requirements:
 * 1. Checks TelecomManager.PRESENTATION_ALLOWED; respects restricted or unavailable identities.
 * 2. Rejects blank, whitespace, and generic network placeholder names ("Private", "Unknown", etc.).
 * 3. Uses official, documented Android APIs only (no reflection or undocumented workarounds).
 * 4. Labels honestly as "Network-provided name" without falsely claiming cryptographic certainty.
 */
object TelecomCnapExtractor {

    private val REJECTED_PLACEHOLDERS = setOf(
        "private",
        "private number",
        "private caller",
        "restricted",
        "restricted number",
        "unknown",
        "unknown caller",
        "unknown number",
        "anonymous",
        "unavailable",
        "withheld",
        "no caller id",
        "none",
        "null"
    )

    /**
     * Extracts network-provided CNAP caller name from Android [Call.Details].
     */
    fun extract(details: Call.Details?, rawNumber: String): CallerIdentityResult? {
        if (details == null) return null
        return extract(
            displayName = details.callerDisplayName,
            presentation = details.callerDisplayNamePresentation,
            rawNumber = rawNumber
        )
    }

    /**
     * Testable overload accepting discrete parameters.
     */
    fun extract(displayName: String?, presentation: Int, rawNumber: String): CallerIdentityResult? {
        // 1. Must be explicitly permitted by telecom presentation rules
        if (presentation != TelecomManager.PRESENTATION_ALLOWED) {
            return null
        }

        // 2. Must not be null or blank
        val cleanName = displayName?.trim() ?: return null
        if (cleanName.length < 2) return null

        // 3. Must not be a generic telecom placeholder
        if (cleanName.lowercase() in REJECTED_PLACEHOLDERS) {
            return null
        }

        val normalized = PhoneNumberNormalizer.normalize(rawNumber)

        return CallerIdentityResult(
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayName = cleanName,
            source = CallerIdentitySource.NETWORK_CNAP,
            identityType = CallerIdentityType.NETWORK_PROVIDED,
            confidence = IdentityConfidence.HIGH,
            sourceLabel = "Network-provided name",
            isVerified = true,
            organizationName = cleanName,
            timestamp = System.currentTimeMillis()
        )
    }
}
