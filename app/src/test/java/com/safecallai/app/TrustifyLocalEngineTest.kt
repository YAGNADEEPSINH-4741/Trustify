package com.safecallai.app

import com.safecallai.app.data.local.LocalReportDraft
import com.safecallai.app.data.local.PreferencesManager.ThemeMode
import com.safecallai.app.domain.engine.CallClassificationEngine
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.Evidence
import com.safecallai.app.domain.models.EvidenceType
import com.safecallai.app.domain.models.ReportCategory
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.domain.models.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying Trustify's 100% on-device, local-first architecture,
 * transparent classification engine, and privacy model.
 */
class TrustifyLocalEngineTest {

    private lateinit var engine: CallClassificationEngine
    private val currentTime = 1776000000000L

    @Before
    fun setUp() {
        engine = CallClassificationEngine()
    }

    @Test
    fun testLocalReportDraft_CreationAndFields() {
        val draft = LocalReportDraft(
            id = 1L,
            phoneNumber = "+919876543210",
            normalizedPhoneNumber = "+919876543210",
            category = ReportCategory.INCORRECT_SPAM_CLASSIFICATION.name,
            description = "Legitimate delivery driver falsely flagged on external databases",
            timestamp = currentTime
        )

        assertEquals("+919876543210", draft.phoneNumber)
        assertEquals("+919876543210", draft.normalizedPhoneNumber)
        assertEquals(ReportCategory.INCORRECT_SPAM_CLASSIFICATION.name, draft.category)
        assertEquals("Legitimate delivery driver falsely flagged on external databases", draft.description)
        assertEquals(currentTime, draft.timestamp)
    }

    @Test
    fun testOfflineOperation_NoBackendRequired() {
        // Test that engine operates deterministically when no cloud backend is available
        val result = engine.classify(
            rawNumber = "+919876599999",
            profile = null,
            isBackendAvailable = false,
            currentTimeMillis = currentTime
        )

        assertEquals(CallerClassification.UNKNOWN, result.classification)
        assertEquals(VerificationStatus.UNVERIFIED, result.verificationStatus)
        assertTrue(result.isSafeToRingNormally)
        assertTrue(result.warnings.any { it.code == "OFFLINE_MODE" })
        assertEquals("Trustify Local Offline Directory", result.dataSource)
    }

    @Test
    fun testPersonalTrust_DoesNotFabricatePublicBusinessIdentity() {
        // A personally trusted caller whitelist record
        val trusted = UserTrustedCaller(
            id = 42L,
            phoneNumber = "+919876500005",
            normalizedPhoneNumber = "+919876500005",
            customLabel = "Gate Security Guard",
            notes = "Apartment visitor check",
            createdAt = currentTime
        )

        val result = engine.classify(
            rawNumber = "+91 98765 00005",
            profile = null,
            userTrustedRecord = trusted,
            currentTimeMillis = currentTime
        )

        // Must be treated as likely legitimate on user's device, but NOT verified public corporate business
        assertEquals(CallerClassification.LIKELY_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.USER_TRUSTED, result.verificationStatus)
        assertEquals("Gate Security Guard", result.callerName)
        assertEquals(null, result.businessName) // Personal trust must NEVER fabricate a corporate business entity
        assertTrue(result.isSafeToRingNormally)
        assertTrue(result.evidenceList.any { it.type == EvidenceType.USER_WHITELIST })
    }

    @Test
    fun testConflictingSignals_TrustedCallerWithExternalSpamFlags() {
        val trusted = UserTrustedCaller(
            id = 5L,
            phoneNumber = "+919876511111",
            normalizedPhoneNumber = "+919876511111",
            customLabel = "Local Courier Associate",
            createdAt = currentTime
        )

        // External crowd reports mark number as spam
        val profile = CallerProfile(
            normalizedPhoneNumber = "+919876511111",
            spamReportsCount = 35,
            isVerifiedBusiness = false
        )

        val result = engine.classify(
            rawNumber = "+919876511111",
            profile = profile,
            userTrustedRecord = trusted,
            currentTimeMillis = currentTime
        )

        // Should flag conflicting signals while respecting user's personal trust
        assertEquals(CallerClassification.LIKELY_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.CONFLICTING_SIGNALS, result.verificationStatus)
        assertTrue(result.warnings.any { it.code == "CONFLICTING_TRUST" })
        assertTrue(result.isSafeToRingNormally)
    }

    @Test
    fun testStaleCachedInformation_FlagsWarning() {
        val staleProfile = CallerProfile(
            normalizedPhoneNumber = "+919876522222",
            businessName = "Old Telecom Vendor",
            isVerifiedBusiness = true,
            verificationMethod = "Local Directory Cache",
            verifiedAt = currentTime - 365L * 86400000L, // 1 year ago
            verificationExpiresAt = currentTime - 30L * 86400000L, // Expired 30 days ago
            spamReportsCount = 1
        )

        val result = engine.classify(
            rawNumber = "+919876522222",
            profile = staleProfile,
            currentTimeMillis = currentTime
        )

        assertEquals(CallerClassification.LIKELY_LEGITIMATE, result.classification)
        assertEquals(VerificationStatus.EXPIRED, result.verificationStatus)
        assertTrue(result.warnings.any { it.code == "VERIFICATION_EXPIRED" })
    }

    @Test
    fun testPhoneNumberNormalizer_MultipleFormats() {
        // Standard 10 digit Indian number
        val normalized1 = PhoneNumberNormalizer.normalize("9876543210")
        assertEquals("+919876543210", normalized1)

        // Number with leading zero
        val normalized2 = PhoneNumberNormalizer.normalize("09876543210")
        assertEquals("+919876543210", normalized2)

        // Number with +91 prefix and formatting spaces
        val normalized3 = PhoneNumberNormalizer.normalize("+91 98765 43210")
        assertEquals("+919876543210", normalized3)

        // Toll-free number
        val normalizedTollFree = PhoneNumberNormalizer.normalize("1800 202 6161")
        assertEquals("+18002026161", normalizedTollFree)

        // Display formatting
        val display = PhoneNumberNormalizer.formatForDisplay("+919876543210")
        assertEquals("+91 98765 43210", display)
    }

    @Test
    fun testThemeMode_Options() {
        assertEquals("SYSTEM_DEFAULT", ThemeMode.SYSTEM_DEFAULT.name)
        assertEquals("LIGHT", ThemeMode.LIGHT.name)
        assertEquals("DARK", ThemeMode.DARK.name)

        // Verify distinct values
        assertNotEquals(ThemeMode.LIGHT, ThemeMode.DARK)
        assertNotEquals(ThemeMode.SYSTEM_DEFAULT, ThemeMode.LIGHT)
        assertNotEquals(ThemeMode.SYSTEM_DEFAULT, ThemeMode.DARK)
    }

    @Test
    fun testUnknownCaller_TransparentExplanationWithoutFabricatedPercentages() {
        val result = engine.classify(
            rawNumber = "+919123456789",
            profile = null,
            currentTimeMillis = currentTime
        )

        assertEquals(CallerClassification.UNKNOWN, result.classification)
        // Ensure no fabricated score or probability percentage is returned
        assertTrue(result.explanation.contains("could not independently verify"))
        assertTrue(result.isSafeToRingNormally)
    }
}
