package com.safecallai.app

import com.safecallai.app.domain.engine.CallClassificationEngine
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.Evidence
import com.safecallai.app.domain.models.EvidenceType
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.domain.models.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallClassificationEngineTest {

    private lateinit var engine: CallClassificationEngine
    private val now = 1776000000000L // Constant mock epoch time

    @Before
    fun setUp() {
        engine = CallClassificationEngine()
    }

    @Test
    fun testCase1_VerifiedBusinessWithNoSuspiciousReports() {
        val profile = CallerProfile(
            normalizedPhoneNumber = "+9118002026161",
            businessName = "HDFC Bank Official Support",
            category = "Banking",
            isVerifiedBusiness = true,
            verificationMethod = "Regulatory Registry",
            verifiedAt = now - 1000000L,
            verificationExpiresAt = now + 10000000L,
            spamReportsCount = 0,
            positiveConfirmationsCount = 150
        )

        val result = engine.classify(
            rawNumber = "+91 1800 202 6161",
            profile = profile,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.VERIFIED_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.VERIFIED_BUSINESS, result.verificationStatus)
        assertEquals("HDFC Bank Official Support", result.businessName)
        assertTrue(result.isSafeToRingNormally)
        assertFalse(result.falseSpamRecovered)
        assertTrue(result.evidenceList.any { it.type == EvidenceType.BUSINESS_REGISTRATION })
    }

    @Test
    fun testCase2_LegitimateBusinessWithOutdatedSpamReport_FalseSpamRecovery() {
        // High volume customer support number that received spam flags from non-customers
        val profile = CallerProfile(
            normalizedPhoneNumber = "+9118001088888",
            businessName = "Flipkart Customer Helpdesk",
            category = "E-Commerce",
            isVerifiedBusiness = true,
            verificationMethod = "Telecom Directory",
            verifiedAt = now - 1000000L,
            verificationExpiresAt = now + 10000000L,
            spamReportsCount = 18,
            falseSpamReportsCount = 20,
            positiveConfirmationsCount = 300
        )

        val result = engine.classify(
            rawNumber = "18001088888",
            profile = profile,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.VERIFIED_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.VERIFIED_BUSINESS, result.verificationStatus)
        assertTrue(result.falseSpamRecovered)
        assertTrue(result.isSafeToRingNormally)
        assertTrue(result.evidenceList.any { it.type == EvidenceType.FALSE_SPAM_RECOVERY })
    }

    @Test
    fun testCase3_UnknownCaller() {
        val result = engine.classify(
            rawNumber = "+919876543219",
            profile = null,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.UNKNOWN, result.classification)
        assertEquals(VerificationStatus.UNVERIFIED, result.verificationStatus)
        assertTrue(result.isSafeToRingNormally) // Phone must ring normally for unknown callers!
        assertFalse(result.falseSpamRecovered)
    }

    @Test
    fun testCase4_CallerWithManyCredibleSpamReports() {
        val profile = CallerProfile(
            normalizedPhoneNumber = "+919999999999",
            isVerifiedBusiness = false,
            spamReportsCount = 45,
            falseSpamReportsCount = 0
        )

        val result = engine.classify(
            rawNumber = "+91 99999 99999",
            profile = profile,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.HIGH_RISK, result.classification)
        assertEquals(VerificationStatus.COMMUNITY_REPUTATION, result.verificationStatus)
        assertFalse(result.isSafeToRingNormally)
        assertTrue(result.warnings.any { it.code == "HIGH_VOLUME_SPAM" })
    }

    @Test
    fun testCase5_UserTrustedNumberWithContradictoryEvidence() {
        val trusted = UserTrustedCaller(
            id = 1L,
            phoneNumber = "+919876512345",
            normalizedPhoneNumber = "+919876512345",
            customLabel = "Local Plumber",
            createdAt = now - 50000L
        )

        // Contradictory evidence: number recently received high spam reports
        val profile = CallerProfile(
            normalizedPhoneNumber = "+919876512345",
            spamReportsCount = 22,
            isVerifiedBusiness = false
        )

        val result = engine.classify(
            rawNumber = "+919876512345",
            profile = profile,
            userTrustedRecord = trusted,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.LIKELY_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.CONFLICTING_SIGNALS, result.verificationStatus)
        assertTrue(result.warnings.any { it.code == "CONFLICTING_TRUST" })
        assertTrue(result.isSafeToRingNormally)
    }

    @Test
    fun testCase6_DeliveryClaimWithoutIndependentVerification() {
        // User expects delivery, but no partner dispatch exists
        val result = engine.classify(
            rawNumber = "+919876543220",
            profile = null,
            activeBookingEvidence = null,
            userExpectingDelivery = true,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.UNKNOWN, result.classification)
        assertEquals(VerificationStatus.UNVERIFIED, result.verificationStatus)
        assertTrue(result.warnings.any { it.code == "UNVERIFIED_DELIVERY_CLAIM" })
        assertFalse(result.falseSpamRecovered)
    }

    @Test
    fun testCase7_ActiveDeliveryVerifiedPartner_FalseSpamRecovery() {
        val dispatchEvidence = Evidence(
            type = EvidenceType.DELIVERY_DISPATCH,
            title = "Active Delivery Dispatch",
            description = "Associated with active order AMZ-84920-IND",
            source = "Amazon Logistics",
            isIndependent = true,
            timestamp = now
        )

        // Number has external spam reports on other apps
        val profile = CallerProfile(
            normalizedPhoneNumber = "+919876543210",
            businessName = "Delivery Associate",
            spamReportsCount = 8
        )

        val result = engine.classify(
            rawNumber = "+919876543210",
            profile = profile,
            activeBookingEvidence = dispatchEvidence,
            userExpectingDelivery = true,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.VERIFIED_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.ACTIVE_BOOKING_ASSOCIATED, result.verificationStatus)
        assertTrue(result.falseSpamRecovered)
        assertTrue(result.isSafeToRingNormally)
        assertTrue(result.evidenceList.any { it.type == EvidenceType.FALSE_SPAM_RECOVERY })
    }

    @Test
    fun testCase8_UnavailableBackend_FailsSafely() {
        val result = engine.classify(
            rawNumber = "+919876543211",
            profile = null,
            isBackendAvailable = false,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.UNKNOWN, result.classification)
        assertEquals(VerificationStatus.UNVERIFIED, result.verificationStatus)
        assertTrue(result.warnings.any { it.code == "OFFLINE_MODE" })
        assertTrue(result.isSafeToRingNormally)
    }

    @Test
    fun testCase9_InvalidOrIncompletePhoneNumber() {
        val result = engine.classify(
            rawNumber = "123", // Too short
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.UNKNOWN, result.classification)
        assertEquals(VerificationStatus.UNVERIFIED, result.verificationStatus)
        assertTrue(result.warnings.any { it.code == "INVALID_NUMBER_FORMAT" })
    }

    @Test
    fun testCase10_StaleOrExpiredVerificationInformation() {
        val profile = CallerProfile(
            normalizedPhoneNumber = "+919876500000",
            businessName = "Lapsed Vendor Tech Services",
            isVerifiedBusiness = true,
            verificationMethod = "Corporate Registry",
            verifiedAt = now - 200000000L,
            verificationExpiresAt = now - 50000L, // Expired!
            spamReportsCount = 2
        )

        val result = engine.classify(
            rawNumber = "+919876500000",
            profile = profile,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.LIKELY_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.EXPIRED, result.verificationStatus)
        assertTrue(result.warnings.any { it.code == "VERIFICATION_EXPIRED" })
    }

    @Test
    fun testCase11_ReportedImpersonationOverridesBusinessListing() {
        val profile = CallerProfile(
            normalizedPhoneNumber = "+9118002020000",
            businessName = "National Bank Helpline",
            isVerifiedBusiness = true,
            isImpersonationReported = true // Critical fraud report!
        )

        val result = engine.classify(
            rawNumber = "+9118002020000",
            profile = profile,
            currentTimeMillis = now
        )

        assertEquals(CallerClassification.HIGH_RISK, result.classification)
        assertEquals(VerificationStatus.CONFLICTING_SIGNALS, result.verificationStatus)
        assertFalse(result.isSafeToRingNormally)
        assertTrue(result.warnings.any { it.code == "SUSPECTED_IMPERSONATION" })
    }
}
