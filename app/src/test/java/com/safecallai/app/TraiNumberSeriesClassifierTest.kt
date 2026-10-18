package com.safecallai.app

import com.safecallai.app.domain.engine.TraiNumberSeriesClassifier
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for verified Indian TRAI / DoT number-series classification.
 *
 * Verifies:
 * - Requirement 6: Verified number-series rules produce the appropriate categories.
 * - Requirement 7: Unverified number-series rules are not applied (e.g., standard mobile, international).
 */
class TraiNumberSeriesClassifierTest {

    @Test
    fun testTrai140Series_PromotionalTelemarketing() {
        val numbers = listOf(
            "+911401234567",
            "1401234567",
            "01401234567",
            "+91 140 987 6543",
            "00911409999999"
        )

        for (num in numbers) {
            val result = TraiNumberSeriesClassifier.classify(num)
            assertNotNull("Expected classification for $num", result)
            assertEquals(CallerIdentitySource.TRAI_SERIES, result?.source)
            assertEquals(CallerIdentityType.PROMOTIONAL_TELEMARKETING, result?.identityType)
            assertEquals("Commercial Telemarketer", result?.displayName)
            assertEquals("Registered telemarketing series", result?.sourceLabel)
            assertEquals(IdentityConfidence.HIGH, result?.confidence)
            assertTrue(result?.isVerified == true)
        }
    }

    @Test
    fun testTrai1600Series_BankingFinancialServicesInsurance() {
        val numbers = listOf(
            "+911600123456",
            "1600987654",
            "+91 1600 555 123",
            "01600112233"
        )

        for (num in numbers) {
            val result = TraiNumberSeriesClassifier.classify(num)
            assertNotNull("Expected classification for $num", result)
            assertEquals(CallerIdentitySource.TRAI_SERIES, result?.source)
            assertEquals(CallerIdentityType.REGULATED_BFSI, result?.identityType)
            assertEquals("Banking / Financial Services", result?.displayName)
            assertEquals("Banking & Financial Services (BFSI)", result?.sourceLabel)
            assertEquals(IdentityConfidence.HIGH, result?.confidence)
            assertTrue(result?.isVerified == true)
        }
    }

    @Test
    fun testTrai1601Series_UtilitiesAndCourierLogistics() {
        val numbers = listOf(
            "+911601123456",
            "1601999888",
            "+91 1601 333 444",
            "01601887766"
        )

        for (num in numbers) {
            val result = TraiNumberSeriesClassifier.classify(num)
            assertNotNull("Expected classification for $num", result)
            assertEquals(CallerIdentitySource.TRAI_SERIES, result?.source)
            assertEquals(CallerIdentityType.REGULATED_LOGISTICS, result?.identityType)
            assertEquals("Utilities & Logistics Service", result?.displayName)
            assertEquals("Utilities & Logistics Service", result?.sourceLabel)
            assertEquals(IdentityConfidence.HIGH, result?.confidence)
            assertTrue(result?.isVerified == true)
        }
    }

    @Test
    fun testTrai160GenericSeries_RegulatedEnterprise() {
        val result = TraiNumberSeriesClassifier.classify("+911609123456")
        assertNotNull(result)
        assertEquals(CallerIdentitySource.TRAI_SERIES, result?.source)
        assertEquals("Regulated Enterprise Service", result?.displayName)
        assertEquals("Regulated service series (160)", result?.sourceLabel)
    }

    @Test
    fun testNationalTollFree1800Series() {
        val numbers = listOf(
            "+9118002026161",
            "1800112233",
            "18001802222",
            "+91 1800 1234"
        )

        for (num in numbers) {
            val result = TraiNumberSeriesClassifier.classify(num)
            assertNotNull("Expected classification for $num", result)
            assertEquals(CallerIdentitySource.TRAI_SERIES, result?.source)
            assertEquals(CallerIdentityType.TOLL_FREE, result?.identityType)
            assertEquals("Toll-Free Helpline", result?.displayName)
            assertEquals("National toll-free service", result?.sourceLabel)
        }
    }

    @Test
    fun testSharedCostSupport1860Series() {
        val numbers = listOf(
            "+911860123456",
            "18602666868",
            "+91 1860 500 1234"
        )

        for (num in numbers) {
            val result = TraiNumberSeriesClassifier.classify(num)
            assertNotNull("Expected classification for $num", result)
            assertEquals(CallerIdentitySource.TRAI_SERIES, result?.source)
            assertEquals(CallerIdentityType.SHARED_COST_SUPPORT, result?.identityType)
            assertEquals("Customer Support Line", result?.displayName)
            assertEquals("Shared-cost enterprise support", result?.sourceLabel)
        }
    }

    @Test
    fun testEmergencyShortCodes() {
        val emergencyCodes = mapOf(
            "112" to "National Emergency Helpline",
            "100" to "Police Emergency",
            "101" to "Fire Emergency",
            "102" to "Ambulance Emergency",
            "108" to "Medical Emergency Helpline",
            "1091" to "Women Helpline",
            "1098" to "Childline Support"
        )

        for ((code, expectedName) in emergencyCodes) {
            val result = TraiNumberSeriesClassifier.classify(code)
            assertNotNull("Expected classification for code $code", result)
            assertEquals(CallerIdentitySource.TRAI_SERIES, result?.source)
            assertEquals(CallerIdentityType.PUBLIC_SERVICE, result?.identityType)
            assertEquals(expectedName, result?.displayName)
            assertEquals("National emergency helpline", result?.sourceLabel)
        }
    }

    @Test
    fun testStandardMobileNumbers_NeverClassifiedAsBusinessSeries() {
        // Standard personal mobile numbers (6, 7, 8, 9) must return null
        val mobileNumbers = listOf(
            "+919876543210",
            "+91 81234 56789",
            "+917001122334",
            "+916300112233",
            "9876543210",
            "8123456789",
            "7001122334",
            "6300112233"
        )

        for (mobile in mobileNumbers) {
            val result = TraiNumberSeriesClassifier.classify(mobile)
            assertNull("Standard mobile number $mobile must NOT be classified as business series", result)
        }
    }

    @Test
    fun testInvalidOrUnsupportedPrefixes_ReturnNull() {
        val unsupportedNumbers = listOf(
            "",
            "   ",
            "abc",
            "+14155552671",      // US number
            "+442071838750",     // UK number
            "+911500123456",     // 1500 is not a recognized TRAI category
            "+911700123456",     // 1700 is not a recognized TRAI category
            "+911900123456",     // 1900 is unverified/non-allocated in this engine
            "123"                // Unknown short code
        )

        for (num in unsupportedNumbers) {
            val result = TraiNumberSeriesClassifier.classify(num)
            assertNull("Number $num should return null", result)
        }
    }
}
