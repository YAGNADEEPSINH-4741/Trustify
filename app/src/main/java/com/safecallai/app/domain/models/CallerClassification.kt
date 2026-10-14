package com.safecallai.app.domain.models

import kotlinx.serialization.Serializable

/**
 * Caller classifications backed by verifiable evidence.
 * As per core principle:
 * A spam report does not automatically mean a caller is malicious,
 * and an unknown number is not automatically safe.
 */
@Serializable
enum class CallerClassification {
    /**
     * Reliable verification evidence exists (e.g. registered business, authorized partner).
     */
    VERIFIED_LEGITIMATE,

    /**
     * Multiple credible signals support legitimacy, but identity is not conclusively verified.
     */
    LIKELY_LEGITIMATE,

    /**
     * Insufficient evidence available. Caution advised.
     */
    UNKNOWN,

    /**
     * Credible indicators suggest suspicious or unwanted behavior.
     */
    POTENTIAL_SPAM,

    /**
     * Strong evidence supports malicious behavior, spoofing, or fraudulent scamming.
     */
    HIGH_RISK;

    val displayName: String
        get() = when (this) {
            VERIFIED_LEGITIMATE -> "Verified Legitimate"
            LIKELY_LEGITIMATE -> "Likely Legitimate"
            UNKNOWN -> "Unknown Caller"
            POTENTIAL_SPAM -> "Potential Spam"
            HIGH_RISK -> "High-Risk Alert"
        }
}
