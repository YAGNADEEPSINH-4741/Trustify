package com.safecallai.app.domain.models

/**
 * Enumeration of caller identity discovery sources.
 */
enum class CallerIdentitySource {
    SAVED_CONTACT,
    NETWORK_CNAP,
    TRAI_SERIES,
    PUBLIC_DIRECTORY_OSM,
    LOCAL_CACHE,
    NONE
}

/**
 * Broad category/type of the identified caller.
 */
enum class CallerIdentityType {
    SAVED_CONTACT,
    NETWORK_PROVIDED,
    REGULATED_BFSI,
    REGULATED_LOGISTICS,
    PROMOTIONAL_TELEMARKETING,
    TOLL_FREE,
    SHARED_COST_SUPPORT,
    PUBLIC_SERVICE,
    PUBLIC_BUSINESS,
    UNKNOWN
}

/**
 * Confidence level in the caller's identity.
 */
enum class IdentityConfidence {
    VERIFIED,    // Saved contacts or carrier-authenticated network CNAP
    HIGH,        // Mandated regulatory series (140, 1600, 1601) or curated public directory
    MEDIUM,      // General public directory or cached match
    LOW,         // Weak or unconfirmed match
    NONE         // No match / unknown
}

/**
 * Structured result representing caller identification for an incoming or searched phone number.
 *
 * Strictly separated from spam reputation: identity answers "Who is calling?",
 * while reputation answers "Should I trust this call?".
 */
data class CallerIdentityResult(
    val rawNumber: String = "",
    val normalizedNumber: String = "",
    val displayName: String? = null,
    val source: CallerIdentitySource = CallerIdentitySource.NONE,
    val identityType: CallerIdentityType = CallerIdentityType.UNKNOWN,
    val confidence: IdentityConfidence = IdentityConfidence.NONE,
    val sourceLabel: String = "",
    val isVerified: Boolean = false,
    val organizationName: String? = null,
    val category: String? = null,
    val attribution: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val hasReliableName: Boolean
        get() = !displayName.isNullOrBlank() && displayName != "Unknown Caller"
}
