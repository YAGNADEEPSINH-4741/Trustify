package com.safecallai.app.verification

import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.Evidence

/**
 * Coordinates delivery, ride, and business verification providers.
 * Supports hot-plugging official production providers and development mocks.
 */
class VerificationRegistry(
    private val deliveryProviders: List<DeliveryVerificationProvider> = listOf(MockDeliveryVerificationProvider()),
    private val rideProviders: List<RideVerificationProvider> = listOf(MockRideVerificationProvider()),
    private val businessProviders: List<BusinessVerificationProvider> = listOf(MockBusinessVerificationProvider())
) {

    suspend fun checkActiveBookingEvidence(normalizedPhoneNumber: String): Evidence? {
        // 1. Check Delivery providers
        for (provider in deliveryProviders) {
            val result = provider.checkActiveDelivery(normalizedPhoneNumber)
            if (result.hasActiveDelivery && result.evidence != null) {
                return result.evidence
            }
        }

        // 2. Check Ride providers
        for (provider in rideProviders) {
            val result = provider.checkActiveRide(normalizedPhoneNumber)
            if (result.hasActiveRide && result.evidence != null) {
                return result.evidence
            }
        }

        return null
    }

    suspend fun checkVerifiedBusiness(normalizedPhoneNumber: String): CallerProfile? {
        for (provider in businessProviders) {
            val profile = provider.getVerifiedBusinessInformation(normalizedPhoneNumber)
            if (profile != null) return profile
        }
        return null
    }

    fun getConnectedProvidersInfo(): List<String> {
        val list = mutableListOf<String>()
        deliveryProviders.forEach { list.add("${it.providerDisplayName} (${if (it.isMockProvider) "Mock" else "Live"})") }
        rideProviders.forEach { list.add("${it.providerDisplayName} (${if (it.isMockProvider) "Mock" else "Live"})") }
        businessProviders.forEach { list.add("${it.providerDisplayName} (${if (it.isMockProvider) "Mock" else "Live"})") }
        return list
    }
}
