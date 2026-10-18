package com.safecallai.app

import android.telecom.TelecomManager
import com.safecallai.app.domain.engine.TelecomCnapExtractor
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for network-provided Calling Name Presentation (CNAP) extraction.
 *
 * Verifies:
 * - Requirement 3: Android caller-name information is used when legitimately available.
 * - Requirement 4: Missing or restricted caller-name information is handled correctly.
 * - Requirement 5: Unsupported devices / null details do not crash.
 */
class TelecomCnapExtractorTest {

    @Test
    fun testValidNetworkName_PresentationAllowed_ReturnsNetworkProvidedIdentity() {
        val rawNumber = "+91 98765 43210"
        val networkName = "Tata Consultancy Services"

        val result = TelecomCnapExtractor.extract(
            displayName = networkName,
            presentation = TelecomManager.PRESENTATION_ALLOWED,
            rawNumber = rawNumber
        )

        assertNotNull(result)
        assertEquals("Tata Consultancy Services", result?.displayName)
        assertEquals(CallerIdentitySource.NETWORK_CNAP, result?.source)
        assertEquals(CallerIdentityType.NETWORK_PROVIDED, result?.identityType)
        assertEquals(IdentityConfidence.HIGH, result?.confidence)
        assertEquals("Network-provided name", result?.sourceLabel)
        assertTrue(result?.isVerified == true)
        assertEquals("+919876543210", result?.normalizedNumber)
    }

    @Test
    fun testRestrictedPresentation_ReturnsNull() {
        val result = TelecomCnapExtractor.extract(
            displayName = "Secret Caller Name",
            presentation = TelecomManager.PRESENTATION_RESTRICTED,
            rawNumber = "+919876543210"
        )

        assertNull("Restricted caller identity presentation must return null", result)
    }

    @Test
    fun testUnknownOrPayphonePresentation_ReturnsNull() {
        val unknownResult = TelecomCnapExtractor.extract(
            displayName = "Some Name",
            presentation = TelecomManager.PRESENTATION_UNKNOWN,
            rawNumber = "+919876543210"
        )
        assertNull(unknownResult)

        val payphoneResult = TelecomCnapExtractor.extract(
            displayName = "Some Name",
            presentation = TelecomManager.PRESENTATION_PAYPHONE,
            rawNumber = "+919876543210"
        )
        assertNull(payphoneResult)
    }

    @Test
    fun testBlankOrEmptyDisplayName_ReturnsNull() {
        val blankResult = TelecomCnapExtractor.extract(
            displayName = "   ",
            presentation = TelecomManager.PRESENTATION_ALLOWED,
            rawNumber = "+919876543210"
        )
        assertNull(blankResult)

        val singleCharResult = TelecomCnapExtractor.extract(
            displayName = "A",
            presentation = TelecomManager.PRESENTATION_ALLOWED,
            rawNumber = "+919876543210"
        )
        assertNull(singleCharResult)

        val nullResult = TelecomCnapExtractor.extract(
            displayName = null,
            presentation = TelecomManager.PRESENTATION_ALLOWED,
            rawNumber = "+919876543210"
        )
        assertNull(nullResult)
    }

    @Test
    fun testGenericNetworkPlaceholders_AreRejected() {
        val placeholders = listOf(
            "Private",
            "private number",
            "Private Caller",
            "RESTRICTED",
            "Unknown",
            "unknown caller",
            "Anonymous",
            "Unavailable",
            "Withheld",
            "No Caller ID",
            "None",
            "null"
        )

        for (placeholder in placeholders) {
            val result = TelecomCnapExtractor.extract(
                displayName = placeholder,
                presentation = TelecomManager.PRESENTATION_ALLOWED,
                rawNumber = "+919876543210"
            )
            assertNull("Placeholder '$placeholder' must be rejected", result)
        }
    }

    @Test
    fun testNullDetails_DoesNotCrash() {
        val result = TelecomCnapExtractor.extract(
            details = null,
            rawNumber = "+919876543210"
        )
        assertNull(result)
    }
}
