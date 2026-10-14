package com.safecallai.app

import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.verification.MockDeliveryVerificationProvider
import com.safecallai.app.verification.MockRideVerificationProvider
import com.safecallai.app.verification.VerificationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun testNormalization_standard10Digit() {
        val result = PhoneNumberNormalizer.normalize("9876543210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testNormalization_withSpacesAndDashes() {
        val result = PhoneNumberNormalizer.normalize("+91 98765-43210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testNormalization_withLeadingZero() {
        val result = PhoneNumberNormalizer.normalize("09876543210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun testNormalization_withParentheses() {
        val result = PhoneNumberNormalizer.normalize("+1 (800) 555-0199")
        assertEquals("+18005550199", result)
    }

    @Test
    fun testValidation() {
        assertTrue(PhoneNumberNormalizer.isValid("+919876543210"))
        assertTrue(PhoneNumberNormalizer.isValid("9876543210"))
        assertFalse(PhoneNumberNormalizer.isValid("123"))
        assertFalse(PhoneNumberNormalizer.isValid(""))
        assertFalse(PhoneNumberNormalizer.isValid(null))
    }
}

class VerificationProviderTest {

    @Test
    fun testMockDeliveryProvider_knownAmazonPartner() = runBlocking {
        val provider = MockDeliveryVerificationProvider()
        val result = provider.checkActiveDelivery("+919876543210")

        assertTrue(result.hasActiveDelivery)
        assertEquals("Amazon Logistics", result.providerName)
        assertNotNull(result.evidence)
        assertTrue(provider.isMockProvider)
    }

    @Test
    fun testMockDeliveryProvider_unknownNumber() = runBlocking {
        val provider = MockDeliveryVerificationProvider()
        val result = provider.checkActiveDelivery("+919999999999")

        assertFalse(result.hasActiveDelivery)
        assertNull(result.evidence)
    }

    @Test
    fun testVerificationRegistry_dispatchesActiveRide() = runBlocking {
        val registry = VerificationRegistry()
        val evidence = registry.checkActiveBookingEvidence("+919988776655")

        assertNotNull(evidence)
        assertTrue(evidence?.description?.contains("UBR-9021") == true)
    }
}
