package com.safecallai.app.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class EvidenceType {
    BUSINESS_REGISTRATION,
    DELIVERY_DISPATCH,
    RIDE_DISPATCH,
    SERVICE_APPOINTMENT,
    USER_WHITELIST,
    HISTORICAL_LEGITIMATE,
    COMMUNITY_REPUTATION,
    FALSE_SPAM_RECOVERY,
    STIR_SHAKEN_ATTESTATION
}

/**
 * Concrete piece of evidence evaluated by the classification engine.
 * [isIndependent] indicates whether the evidence was verified by a certified external
 * registry/provider vs purely user-supplied expectation.
 */
@Serializable
data class Evidence(
    val type: EvidenceType,
    val title: String,
    val description: String,
    val source: String,
    val isIndependent: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)

@Serializable
enum class WarningSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
data class Warning(
    val code: String,
    val title: String,
    val message: String,
    val severity: WarningSeverity = WarningSeverity.MEDIUM
)
