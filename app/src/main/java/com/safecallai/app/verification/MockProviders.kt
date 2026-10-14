package com.safecallai.app.verification

import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.Evidence
import com.safecallai.app.domain.models.EvidenceType

/**
 * Explicit test-only mock provider simulating logistics dispatches.
 * Clearly marked as test component to preserve security integrity.
 */
class MockDeliveryVerificationProvider : DeliveryVerificationProvider {
    override val providerId: String = "mock_logistics_v1"
    override val providerDisplayName: String = "Simulated Logistics Integration [DEV-MOCK]"
    override val isMockProvider: Boolean = true

    // Sample mock active dispatch associations
    private val activeDeliveries = mapOf(
        "+919876543210" to DeliveryCheckResult(
            hasActiveDelivery = true,
            providerName = "Amazon Logistics",
            trackingOrOrderId = "AMZ-84920-IND",
            courierName = "Ramesh Kumar (Delivery Associate)",
            evidence = Evidence(
                type = EvidenceType.DELIVERY_DISPATCH,
                title = "Active Delivery Dispatch [DEV MOCK]",
                description = "Associated with active order AMZ-84920-IND out for delivery today.",
                source = "Simulated Amazon Logistics Partner API",
                isIndependent = true,
                expiresAt = System.currentTimeMillis() + 86400000L
            )
        ),
        "+919123456780" to DeliveryCheckResult(
            hasActiveDelivery = true,
            providerName = "Swiggy Express",
            trackingOrOrderId = "SWG-FOOD-5510",
            courierName = "Deepak Patel (Delivery Partner)",
            evidence = Evidence(
                type = EvidenceType.DELIVERY_DISPATCH,
                title = "Active Food Delivery Partner [DEV MOCK]",
                description = "Associated with order SWG-FOOD-5510 dispatched 15 minutes ago.",
                source = "Simulated Swiggy Delivery API",
                isIndependent = true,
                expiresAt = System.currentTimeMillis() + 7200000L
            )
        )
    )

    override suspend fun checkActiveDelivery(normalizedPhoneNumber: String): DeliveryCheckResult {
        return activeDeliveries[normalizedPhoneNumber] ?: DeliveryCheckResult(hasActiveDelivery = false)
    }

    override suspend fun verifyCallerAssociation(
        normalizedPhoneNumber: String,
        orderId: String?
    ): Evidence? {
        val check = checkActiveDelivery(normalizedPhoneNumber)
        return if (check.hasActiveDelivery && (orderId == null || check.trackingOrOrderId == orderId)) {
            check.evidence
        } else null
    }
}

/**
 * Explicit test-only mock provider simulating ride dispatch dispatches.
 */
class MockRideVerificationProvider : RideVerificationProvider {
    override val providerId: String = "mock_mobility_v1"
    override val providerDisplayName: String = "Simulated Ride-Hailing Integration [DEV-MOCK]"
    override val isMockProvider: Boolean = true

    private val activeRides = mapOf(
        "+919988776655" to RideCheckResult(
            hasActiveRide = true,
            rideProviderName = "Uber Mobility",
            vehicleNumber = "MH 02 CK 4589",
            driverName = "Suresh Verma",
            evidence = Evidence(
                type = EvidenceType.RIDE_DISPATCH,
                title = "Active Cab Driver Dispatch [DEV MOCK]",
                description = "Assigned driver for active trip #UBR-9021. Vehicle: MH 02 CK 4589.",
                source = "Simulated Uber Mobility Driver API",
                isIndependent = true,
                expiresAt = System.currentTimeMillis() + 3600000L
            )
        )
    )

    override suspend fun checkActiveRide(normalizedPhoneNumber: String): RideCheckResult {
        return activeRides[normalizedPhoneNumber] ?: RideCheckResult(hasActiveRide = false)
    }

    override suspend fun verifyDriverAssociation(normalizedPhoneNumber: String): Evidence? {
        return checkActiveRide(normalizedPhoneNumber).evidence
    }
}

/**
 * Explicit test-only mock provider simulating verified enterprise directories.
 */
class MockBusinessVerificationProvider : BusinessVerificationProvider {
    override val providerId: String = "mock_business_dir_v1"
    override val providerDisplayName: String = "Enterprise Directory [DEV-MOCK]"
    override val isMockProvider: Boolean = true

    private val verifiedBusinesses = mapOf(
        "+9118002026161" to CallerProfile(
            normalizedPhoneNumber = "+9118002026161",
            businessName = "HDFC Bank Customer Support",
            category = "Banking & Financial Services",
            isVerifiedBusiness = true,
            verificationMethod = "RBI Telecom Corporate Registration",
            verifiedAt = System.currentTimeMillis() - 60000000L,
            verificationExpiresAt = System.currentTimeMillis() + 31536000000L,
            spamReportsCount = 2,
            falseSpamReportsCount = 8,
            positiveConfirmationsCount = 230
        ),
        "+9118001088888" to CallerProfile(
            normalizedPhoneNumber = "+9118001088888",
            businessName = "Flipkart Customer Helpdesk",
            category = "E-Commerce",
            isVerifiedBusiness = true,
            verificationMethod = "Telecom Regulatory Directory",
            verifiedAt = System.currentTimeMillis() - 80000000L,
            verificationExpiresAt = System.currentTimeMillis() + 31536000000L,
            spamReportsCount = 18, // High spam reports on other apps due to volume!
            falseSpamReportsCount = 25,
            positiveConfirmationsCount = 410
        ),
        "+919876500000" to CallerProfile(
            normalizedPhoneNumber = "+919876500000",
            businessName = "Lapsed Vendor Tech Services",
            category = "IT Support",
            isVerifiedBusiness = true,
            verificationMethod = "Corporate Registry",
            verifiedAt = System.currentTimeMillis() - 2000000000L,
            verificationExpiresAt = System.currentTimeMillis() - 10000000L, // Expired!
            spamReportsCount = 4
        )
    )

    override suspend fun verifyBusinessNumber(normalizedPhoneNumber: String): BusinessVerificationResult {
        val profile = verifiedBusinesses[normalizedPhoneNumber]
        return if (profile != null) {
            BusinessVerificationResult(
                isVerified = profile.isVerifiedBusiness,
                profile = profile,
                evidence = Evidence(
                    type = EvidenceType.BUSINESS_REGISTRATION,
                    title = "Verified Business [DEV MOCK]",
                    description = "Matched registered entity: ${profile.businessName}",
                    source = profile.verificationMethod ?: "Mock Business Directory",
                    isIndependent = true,
                    timestamp = profile.verifiedAt ?: System.currentTimeMillis(),
                    expiresAt = profile.verificationExpiresAt
                )
            )
        } else {
            BusinessVerificationResult(isVerified = false)
        }
    }

    override suspend fun getVerifiedBusinessInformation(normalizedPhoneNumber: String): CallerProfile? {
        return verifiedBusinesses[normalizedPhoneNumber]
    }
}
