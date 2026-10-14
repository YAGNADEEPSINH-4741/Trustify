package com.safecallai.app.verification

import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.Evidence

data class DeliveryCheckResult(
    val hasActiveDelivery: Boolean,
    val providerName: String? = null,
    val trackingOrOrderId: String? = null,
    val courierName: String? = null,
    val evidence: Evidence? = null
)

data class RideCheckResult(
    val hasActiveRide: Boolean,
    val rideProviderName: String? = null,
    val vehicleNumber: String? = null,
    val driverName: String? = null,
    val evidence: Evidence? = null
)

data class BusinessVerificationResult(
    val isVerified: Boolean,
    val profile: CallerProfile? = null,
    val evidence: Evidence? = null
)

/**
 * Interface for delivery logistics verification partners (Amazon, Flipkart, Swiggy, etc.)
 */
interface DeliveryVerificationProvider {
    val providerId: String
    val providerDisplayName: String
    val isMockProvider: Boolean

    suspend fun checkActiveDelivery(normalizedPhoneNumber: String): DeliveryCheckResult
    suspend fun verifyCallerAssociation(normalizedPhoneNumber: String, orderId: String?): Evidence?
}

/**
 * Interface for ride-hailing verification partners (Uber, Ola, etc.)
 */
interface RideVerificationProvider {
    val providerId: String
    val providerDisplayName: String
    val isMockProvider: Boolean

    suspend fun checkActiveRide(normalizedPhoneNumber: String): RideCheckResult
    suspend fun verifyDriverAssociation(normalizedPhoneNumber: String): Evidence?
}

/**
 * Interface for registered enterprise and banking directory verification
 */
interface BusinessVerificationProvider {
    val providerId: String
    val providerDisplayName: String
    val isMockProvider: Boolean

    suspend fun verifyBusinessNumber(normalizedPhoneNumber: String): BusinessVerificationResult
    suspend fun getVerifiedBusinessInformation(normalizedPhoneNumber: String): CallerProfile?
}
