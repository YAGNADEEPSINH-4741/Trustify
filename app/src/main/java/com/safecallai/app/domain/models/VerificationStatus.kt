package com.safecallai.app.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class VerificationStatus {
    /** Verified enterprise, government, or registered corporate entity */
    VERIFIED_BUSINESS,

    /** Active authorized dispatch linked to e-commerce, courier, food or ride booking */
    ACTIVE_BOOKING_ASSOCIATED,

    /** Personally marked as trusted by the device user (kept strictly separate from public verification) */
    USER_TRUSTED,

    /** Public reputation derived from community consensus or telecom registry */
    COMMUNITY_REPUTATION,

    /** No independent verification records found */
    UNVERIFIED,

    /** Previously verified, but validity period or booking dispatch has expired */
    EXPIRED,

    /** Conflicting evidence detected (e.g. claims to be bank, but carrier reports spoofing) */
    CONFLICTING_SIGNALS
}
