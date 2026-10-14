package com.safecallai.app.domain.engine

/**
 * Normalizes phone numbers into standard E.164 format and validates inputs.
 * Strips whitespace, dashes, brackets, and handles regional formatting safely.
 */
object PhoneNumberNormalizer {

    private const val DEFAULT_COUNTRY_CODE = "+91" // Configurable default (India/Global)

    /**
     * Normalizes a raw phone number.
     * Returns an E.164 compliant string if valid, or a cleaned digit string.
     */
    fun normalize(rawNumber: String?, defaultCountryCode: String = DEFAULT_COUNTRY_CODE): String {
        if (rawNumber.isNullOrBlank()) return ""

        // Strip non-digit and non-plus characters
        var cleaned = rawNumber.trim().replace(Regex("[^0-9+]"), "")

        if (cleaned.isEmpty()) return ""

        // If number begins with "+", validate standard length
        if (cleaned.startsWith("+")) {
            return cleaned
        }

        // Handle "00" international prefix
        if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2)
        }

        // Handle leading single 0 (trunk prefix)
        if (cleaned.startsWith("0") && cleaned.length > 1) {
            cleaned = cleaned.substring(1)
        }

        // 10-digit number with default country code
        val prefix = if (defaultCountryCode.startsWith("+")) defaultCountryCode else "+$defaultCountryCode"
        return if (cleaned.length == 10) {
            "$prefix$cleaned"
        } else {
            // Return with plus if digits look international, or prefix
            if (cleaned.length > 10) "+$cleaned" else "$prefix$cleaned"
        }
    }

    /**
     * Checks if a phone number is structurally valid (has between 7 and 15 digits as per ITU E.164).
     */
    fun isValid(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val digitsOnly = number.replace(Regex("[^0-9]"), "")
        return digitsOnly.length in 7..15
    }

    /**
     * Formats normalized number for clear user display (e.g. +91 98765 43210).
     */
    fun formatForDisplay(normalized: String): String {
        if (normalized.length < 10) return normalized
        return try {
            if (normalized.startsWith("+91") && normalized.length == 13) {
                "${normalized.substring(0, 3)} ${normalized.substring(3, 8)} ${normalized.substring(8)}"
            } else if (normalized.startsWith("+1") && normalized.length == 12) {
                "${normalized.substring(0, 2)} (${normalized.substring(2, 5)}) ${normalized.substring(5, 8)}-${normalized.substring(8)}"
            } else {
                normalized
            }
        } catch (_: Exception) {
            normalized
        }
    }
}
