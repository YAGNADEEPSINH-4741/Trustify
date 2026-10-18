package com.safecallai.app

import com.safecallai.app.domain.engine.OsmBusinessDirectoryProvider
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OpenStreetMap Public Business Directory provider.
 *
 * Verifies:
 * - Requirement 8: A public business match is displayed only when actual data supports it.
 * - Requirement 9: Missing business data produces Unknown Caller / null local match.
 * - Requirement 10: Network timeouts / disabled network queries do not block or crash.
 */
class OsmBusinessDirectoryProviderTest {

    private lateinit var provider: OsmBusinessDirectoryProvider

    @Before
    fun setUp() {
        provider = OsmBusinessDirectoryProvider(enableLiveOverpass = false)
    }

    @Test
    fun testVerifiedHospitalMatch_AiimsDelhi() {
        val rawNumber = "+91 11 2658 8500"
        val result = provider.lookupLocal(rawNumber)

        assertNotNull(result)
        assertEquals("AIIMS New Delhi Emergency", result?.displayName)
        assertEquals(CallerIdentitySource.PUBLIC_DIRECTORY_OSM, result?.source)
        assertEquals(CallerIdentityType.PUBLIC_BUSINESS, result?.identityType)
        assertEquals(IdentityConfidence.HIGH, result?.confidence)
        assertEquals("Public business listing", result?.sourceLabel)
        assertEquals("Identity source: OpenStreetMap", result?.attribution)
        assertEquals("Hospital", result?.category)
        assertTrue(result?.isVerified == true)
    }

    @Test
    fun testVerifiedRailwaysHelpline_IndianRailways139() {
        val result = provider.lookupLocal("+91139")

        assertNotNull(result)
        assertEquals("Indian Railways Inquiry & Support (139)", result?.displayName)
        assertEquals("Railways / Transit", result?.category)
        assertEquals("Identity source: OpenStreetMap", result?.attribution)
    }

    @Test
    fun testVerifiedBankingCentralHelpline_SbiSupport() {
        val result = provider.lookupLocal("+9118001234")

        assertNotNull(result)
        assertEquals("State Bank of India (SBI) Contact Centre", result?.displayName)
        assertEquals("Banking", result?.category)
        assertEquals("Identity source: OpenStreetMap", result?.attribution)
    }

    @Test
    fun testVerifiedPostalHelpline_SpeedPost() {
        val result = provider.lookupLocal("+91 1800 266 6868")

        assertNotNull(result)
        assertEquals("India Post Speed Post Customer Care", result?.displayName)
        assertEquals("Courier / Postal", result?.category)
        assertEquals("Identity source: OpenStreetMap", result?.attribution)
    }

    @Test
    fun testUnknownNumber_ReturnsNull() {
        val unknownNumber = "+91 98765 43210"
        val result = provider.lookupLocal(unknownNumber)

        assertNull("Unregistered number must return null from local business directory", result)
    }

    @Test
    fun testLiveOverpassDisabled_ReturnsNullSafelyWithoutNetwork() = runBlocking {
        val result = provider.queryOverpass("+919876543210")
        assertNull("When live Overpass is disabled, queryOverpass returns null immediately", result)
    }
}
