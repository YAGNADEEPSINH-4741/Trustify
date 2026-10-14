package com.safecallai.app.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class CallerProfile(
    val normalizedPhoneNumber: String,
    val businessName: String? = null,
    val category: String? = null,
    val isVerifiedBusiness: Boolean = false,
    val verificationMethod: String? = null,
    val verifiedAt: Long? = null,
    val verificationExpiresAt: Long? = null,
    val spamReportsCount: Int = 0,
    val falseSpamReportsCount: Int = 0,
    val positiveConfirmationsCount: Int = 0,
    val isImpersonationReported: Boolean = false,
    val isNumberStale: Boolean = false
)

@Serializable
data class UserTrustedCaller(
    val id: Long = 0,
    val phoneNumber: String,
    val normalizedPhoneNumber: String,
    val customLabel: String,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ScreenedCallRecord(
    val id: Long = 0,
    val phoneNumber: String,
    val normalizedPhoneNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val classification: CallerClassification,
    val verificationStatus: VerificationStatus,
    val callerName: String? = null,
    val reason: String,
    val wasSilenced: Boolean = false,
    val falseSpamRecovered: Boolean = false
)

@Serializable
enum class ReportCategory {
    INCORRECT_SPAM_CLASSIFICATION,
    SUSPECTED_SPAM,
    SUSPECTED_IMPERSONATION,
    INCORRECT_BUSINESS_IDENTITY,
    OTHER;

    val displayName: String
        get() = when (this) {
            INCORRECT_SPAM_CLASSIFICATION -> "False Spam (Caller is Legitimate)"
            SUSPECTED_SPAM -> "Suspected Spam / Robocall"
            SUSPECTED_IMPERSONATION -> "Suspected Impersonation / Fraud"
            INCORRECT_BUSINESS_IDENTITY -> "Incorrect Business Name / Info"
            OTHER -> "Other Suspicious Activity"
        }
}
