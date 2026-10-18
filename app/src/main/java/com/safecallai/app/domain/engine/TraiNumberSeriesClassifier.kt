package com.safecallai.app.domain.engine

import com.safecallai.app.domain.models.CallerIdentityResult
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence

/**
 * Deterministic classifier for official Indian telecom numbering series based on
 * regulations established by the Telecom Regulatory Authority of India (TRAI) and
 * Department of Telecommunications (DoT).
 *
 * Applicable series:
 * - 140 series: Mandatory prefix for registered promotional telemarketing voice calls.
 * - 1600 series: Mandated by DoT & TRAI for Banking, Financial Services, and Insurance (BFSI) & Govt service calls.
 * - 1601 series: Mandated by DoT & TRAI for essential utilities, courier, express logistics & delivery services.
 * - 1800 series: National toll-free communications.
 * - 1860 series: Shared-cost enterprise customer support.
 * - Official emergency short codes (112, 100, 101, 102, 108, 1091, 1098).
 *
 * Core rule:
 * Standard Indian personal mobile numbers (+91 6XXX, 7XXX, 8XXX, 9XXX) are NOT business series
 * and return null to prevent misclassification.
 */
object TraiNumberSeriesClassifier {

    private val EMERGENCY_SHORT_CODES = mapOf(
        "112" to "National Emergency Helpline",
        "100" to "Police Emergency",
        "101" to "Fire Emergency",
        "102" to "Ambulance Emergency",
        "108" to "Medical Emergency Helpline",
        "1091" to "Women Helpline",
        "1098" to "Childline Support"
    )

    /**
     * Evaluates a phone number against verified Indian telecom series rules.
     * Returns a structured [CallerIdentityResult] if the number belongs to an official series,
     * or null if it is an ordinary mobile, landline, or non-Indian number.
     */
    fun classify(rawNumber: String): CallerIdentityResult? {
        if (rawNumber.isBlank()) return null

        val digitsOnly = rawNumber.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return null

        // 1. Check emergency short codes (e.g., 112, 100)
        if (digitsOnly in EMERGENCY_SHORT_CODES) {
            val name = EMERGENCY_SHORT_CODES[digitsOnly] ?: "Emergency Service"
            return CallerIdentityResult(
                rawNumber = rawNumber,
                normalizedNumber = digitsOnly,
                displayName = name,
                source = CallerIdentitySource.TRAI_SERIES,
                identityType = CallerIdentityType.PUBLIC_SERVICE,
                confidence = IdentityConfidence.HIGH,
                sourceLabel = "National emergency helpline",
                isVerified = true,
                category = "Emergency"
            )
        }

        // 2. Extract national Indian number without +91 or leading 0
        val nationalNumber = extractNationalNumber(rawNumber) ?: return null

        // 3. Evaluate TRAI regulated series
        return when {
            // TRAI 140 series (10 digits starting with 140) - Promotional marketing calls
            nationalNumber.startsWith("140") && nationalNumber.length == 10 -> {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = "+91$nationalNumber",
                    displayName = "Commercial Telemarketer",
                    source = CallerIdentitySource.TRAI_SERIES,
                    identityType = CallerIdentityType.PROMOTIONAL_TELEMARKETING,
                    confidence = IdentityConfidence.HIGH,
                    sourceLabel = "Registered telemarketing series",
                    isVerified = true,
                    category = "Telemarketing"
                )
            }

            // TRAI 1600 series (10 digits starting with 1600) - Regulated BFSI / Banking / Government
            nationalNumber.startsWith("1600") && nationalNumber.length == 10 -> {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = "+91$nationalNumber",
                    displayName = "Banking / Financial Services",
                    source = CallerIdentitySource.TRAI_SERIES,
                    identityType = CallerIdentityType.REGULATED_BFSI,
                    confidence = IdentityConfidence.HIGH,
                    sourceLabel = "Banking & Financial Services (BFSI)",
                    isVerified = true,
                    category = "BFSI"
                )
            }

            // TRAI 1601 series (10 digits starting with 1601) - Utilities & Delivery logistics
            nationalNumber.startsWith("1601") && nationalNumber.length == 10 -> {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = "+91$nationalNumber",
                    displayName = "Utilities & Logistics Service",
                    source = CallerIdentitySource.TRAI_SERIES,
                    identityType = CallerIdentityType.REGULATED_LOGISTICS,
                    confidence = IdentityConfidence.HIGH,
                    sourceLabel = "Utilities & Logistics Service",
                    isVerified = true,
                    category = "Utilities / Logistics"
                )
            }

            // TRAI 160 series generic (10 digits starting with 160) - Other transactional services
            nationalNumber.startsWith("160") && nationalNumber.length == 10 -> {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = "+91$nationalNumber",
                    displayName = "Regulated Enterprise Service",
                    source = CallerIdentitySource.TRAI_SERIES,
                    identityType = CallerIdentityType.PUBLIC_SERVICE,
                    confidence = IdentityConfidence.HIGH,
                    sourceLabel = "Regulated service series (160)",
                    isVerified = true,
                    category = "Enterprise Service"
                )
            }

            // National Toll-Free: 1800 (8, 10 or 11 digits, e.g., 1800 1234 or 1800 202 6161)
            nationalNumber.startsWith("1800") && nationalNumber.length in 8..11 -> {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = "+91$nationalNumber",
                    displayName = "Toll-Free Helpline",
                    source = CallerIdentitySource.TRAI_SERIES,
                    identityType = CallerIdentityType.TOLL_FREE,
                    confidence = IdentityConfidence.HIGH,
                    sourceLabel = "National toll-free service",
                    isVerified = true,
                    category = "Toll-Free"
                )
            }

            // Shared-Cost Customer Support: 1860 (10 or 11 digits)
            nationalNumber.startsWith("1860") && nationalNumber.length in 10..11 -> {
                CallerIdentityResult(
                    rawNumber = rawNumber,
                    normalizedNumber = "+91$nationalNumber",
                    displayName = "Customer Support Line",
                    source = CallerIdentitySource.TRAI_SERIES,
                    identityType = CallerIdentityType.SHARED_COST_SUPPORT,
                    confidence = IdentityConfidence.HIGH,
                    sourceLabel = "Shared-cost enterprise support",
                    isVerified = true,
                    category = "Customer Support"
                )
            }

            // Standard mobile numbers (6, 7, 8, 9) or other standard landline prefixes
            else -> null
        }
    }

    /**
     * Extracts the national number from an Indian phone number format.
     * Returns null if the number is explicitly international (non-+91) or structurally invalid.
     */
    private fun extractNationalNumber(rawNumber: String): String? {
        val trimmed = rawNumber.trim()
        val digits = trimmed.filter { it.isDigit() }

        return when {
            // Begins with +91 (10 to 13 digits total)
            trimmed.startsWith("+91") && digits.length >= 10 -> {
                val national = digits.substring(2)
                if (national.length in 8..11) national else null
            }

            // Begins with 0091 (12 to 15 digits total)
            trimmed.startsWith("0091") && digits.length >= 12 -> {
                val national = digits.substring(4)
                if (national.length in 8..11) national else null
            }

            // Begins with + followed by non-91 country code
            trimmed.startsWith("+") -> null

            // National number starting with leading 0 (e.g. 018001234 -> 18001234, 01401234567 -> 1401234567)
            digits.startsWith("0") && digits.length in 9..12 -> {
                digits.substring(1)
            }

            // Bare national number (8 to 11 digits)
            digits.length in 8..11 -> digits

            else -> null
        }
    }
}
