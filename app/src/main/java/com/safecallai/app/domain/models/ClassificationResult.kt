package com.safecallai.app.domain.models

import kotlinx.serialization.Serializable

/**
 * Result produced by the CallClassificationEngine.
 * Contains transparent, human-readable explanations and explicit evidence
 * without fabricated probability percentages.
 */
@Serializable
data class ClassificationResult(
    val rawNumber: String,
    val normalizedNumber: String,
    val classification: CallerClassification,
    val verificationStatus: VerificationStatus,
    val callerName: String? = null,
    val businessName: String? = null,
    val category: String? = null,
    val evidenceList: List<Evidence> = emptyList(),
    val warnings: List<Warning> = emptyList(),
    val dataSource: String = "SafeCall AI Local Engine",
    val lastVerifiedTimestamp: Long? = null,
    val explanation: String,
    val isSafeToRingNormally: Boolean = true,
    val falseSpamRecovered: Boolean = false
)
