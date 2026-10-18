package com.safecallai.app.domain.models

import androidx.annotation.StringRes
import com.safecallai.app.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Community classification rules:
 * - SPAM: At least 10 unique spam votes.
 * - COMMUNITY_TRUSTED: At least 10 not_spam votes and <= 2 spam votes.
 * - UNVERIFIED: All other cases (e.g. fewer than 10 spam votes).
 */
@Serializable
enum class CommunityClassification(@StringRes val labelRes: Int) {
    @SerialName("SPAM")
    SPAM(R.string.community_reported_spam),

    @SerialName("COMMUNITY_TRUSTED")
    COMMUNITY_TRUSTED(R.string.community_trusted),

    @SerialName("UNVERIFIED")
    UNVERIFIED(R.string.community_unverified_number)
}

@Serializable
data class CommunityReputation(
    @SerialName("phone_hash") val phoneHash: String,
    val classification: CommunityClassification,
    @SerialName("spam_count") val spamCount: Int = 0,
    @SerialName("not_spam_count") val notSpamCount: Int = 0,
    val threshold: Int = 10,
    @SerialName("user_vote") val userVote: String? = null,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

object CommunityThresholdEngine {
    const val SPAM_THRESHOLD = 10
    const val TRUSTED_MIN_NOT_SPAM = 10
    const val TRUSTED_MAX_SPAM = 2

    fun classify(spamCount: Int, notSpamCount: Int): CommunityClassification {
        return when {
            spamCount >= SPAM_THRESHOLD -> CommunityClassification.SPAM
            notSpamCount >= TRUSTED_MIN_NOT_SPAM && spamCount <= TRUSTED_MAX_SPAM -> CommunityClassification.COMMUNITY_TRUSTED
            else -> CommunityClassification.UNVERIFIED
        }
    }
}
