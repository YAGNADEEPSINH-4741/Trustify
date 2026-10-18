package com.safecallai.app.util

import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Privacy-preserving phone number identification utility.
 *
 * Core principles:
 * 1. Normalize numbers to canonical international format (E.164).
 * 2. Calculate a keyed HMAC-SHA256 identifier so raw phone numbers are never stored
 *    or queried over public endpoints.
 * 3. Never embed Supabase service-role keys or database passwords in the APK.
 */
object PhoneNumberPrivacyUtil {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    // Public domain separation salt for client-side privacy lookup key derivation
    private const val DOMAIN_SALT = "trustify.community.reputation.v1"

    /**
     * Converts a raw or formatted phone number to a privacy-preserving lookup hash.
     */
    fun computePhoneHash(rawNumber: String): String {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        return hmacSha256(normalized, DOMAIN_SALT)
    }

    private fun hmacSha256(data: String, salt: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(salt.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKey)
        val hashBytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
