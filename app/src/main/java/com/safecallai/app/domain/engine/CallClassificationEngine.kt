package com.safecallai.app.domain.engine

import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.CommunityClassification
import com.safecallai.app.domain.models.CommunityReputation
import com.safecallai.app.domain.models.Evidence
import com.safecallai.app.domain.models.EvidenceType
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.domain.models.VerificationStatus
import com.safecallai.app.domain.models.Warning
import com.safecallai.app.domain.models.WarningSeverity

/**
 * Transparent, rules-based engine for evaluating caller legitimacy.
 * Evaluates verified business records, active dispatches, user trust,
 * contradictory signals, and community reports.
 *
 * Core principles:
 * - A spam report does not automatically mean a caller is malicious.
 * - An unknown number is not automatically safe.
 * - Never fabricate confidence percentages.
 * - Independent verification is strictly separated from user assumptions.
 */
class CallClassificationEngine {

    fun classify(
        rawNumber: String,
        profile: CallerProfile? = null,
        activeBookingEvidence: Evidence? = null,
        userTrustedRecord: UserTrustedCaller? = null,
        userExpectingDelivery: Boolean = false,
        isBackendAvailable: Boolean = true,
        communityReputation: CommunityReputation? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ClassificationResult {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val evidenceList = mutableListOf<Evidence>()
        val warnings = mutableListOf<Warning>()

        // 1. Structural Validation
        if (!PhoneNumberNormalizer.isValid(rawNumber)) {
            warnings.add(
                Warning(
                    code = "INVALID_NUMBER_FORMAT",
                    title = "Invalid Number Format",
                    message = "The provided phone number is incomplete or does not conform to standard telecom formats.",
                    severity = WarningSeverity.HIGH
                )
            )
            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.UNKNOWN,
                verificationStatus = VerificationStatus.UNVERIFIED,
                evidenceList = emptyList(),
                warnings = warnings,
                dataSource = "Trustify Input Validator",
                explanation = "Classification unavailable: The phone number format is invalid or incomplete."
            )
        }

        // 2. Offline / Connectivity Indicator
        if (!isBackendAvailable) {
            warnings.add(
                Warning(
                    code = "OFFLINE_MODE",
                    title = "Offline Engine Active",
                    message = "Operating 100% locally on-device. Local verified rules and cached data are being used.",
                    severity = WarningSeverity.INFO
                )
            )
        }

        // 3. Impersonation / Critical Spoof Check
        // Critical: A legitimate business number can be spoofed or reported for active fraud.
        if (profile?.isImpersonationReported == true) {
            warnings.add(
                Warning(
                    code = "SUSPECTED_IMPERSONATION",
                    title = "High Impersonation Risk",
                    message = "Recent reports suggest this caller identity is being actively spoofed or used for unauthorized contact.",
                    severity = WarningSeverity.CRITICAL
                )
            )
            evidenceList.add(
                Evidence(
                    type = EvidenceType.COMMUNITY_REPUTATION,
                    title = "Impersonation Alert",
                    description = "Confirmed reports of identity spoofing associated with this phone number.",
                    source = "Trustify Threat Intelligence",
                    isIndependent = true,
                    timestamp = currentTimeMillis
                )
            )
            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.HIGH_RISK,
                verificationStatus = VerificationStatus.CONFLICTING_SIGNALS,
                callerName = profile.businessName,
                businessName = profile.businessName,
                category = profile.category,
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = "Trustify Threat Intelligence",
                lastVerifiedTimestamp = profile.verifiedAt,
                explanation = "HIGH-RISK ALERT: Although this number may resemble a known entity, multiple active reports indicate suspected impersonation or caller ID spoofing. Do NOT share OTPs, banking information, or passwords.",
                isSafeToRingNormally = false,
                falseSpamRecovered = false
            )
        }

        // 4. Active Booking / Delivery Dispatch Association (FALSE SPAM RECOVERY)
        if (activeBookingEvidence != null) {
            evidenceList.add(activeBookingEvidence)
            val hasSpamFlags = (profile?.spamReportsCount ?: 0) > 3

            if (hasSpamFlags) {
                evidenceList.add(
                    Evidence(
                        type = EvidenceType.FALSE_SPAM_RECOVERY,
                        title = "False Spam Recovered",
                        description = "External spam reports were superseded by independent active dispatch verification.",
                        source = "Trustify Recovery Engine",
                        isIndependent = true,
                        timestamp = currentTimeMillis
                    )
                )
            }

            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.VERIFIED_LEGITIMATE,
                verificationStatus = VerificationStatus.ACTIVE_BOOKING_ASSOCIATED,
                callerName = profile?.businessName ?: "Verified Delivery / Ride Partner",
                businessName = profile?.businessName,
                category = profile?.category ?: "Logistics / Dispatch",
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = activeBookingEvidence.source,
                lastVerifiedTimestamp = activeBookingEvidence.timestamp,
                explanation = if (hasSpamFlags) {
                    "FALSE SPAM RECOVERY: This number may be marked as spam on other apps due to high calling frequency, but is independently verified as your active delivery or ride partner."
                } else {
                    "VERIFIED ACTIVE DISPATCH: Independently verified as your active delivery or ride service partner."
                },
                isSafeToRingNormally = true,
                falseSpamRecovered = hasSpamFlags
            )
        }

        // 5. Independently Verified Business Registration
        if (profile?.isVerifiedBusiness == true) {
            val isExpired = profile.verificationExpiresAt != null && profile.verificationExpiresAt < currentTimeMillis

            if (isExpired) {
                warnings.add(
                    Warning(
                        code = "VERIFICATION_EXPIRED",
                        title = "Verification Expired",
                        message = "Business registry credentials for this number expired and have not been renewed.",
                        severity = WarningSeverity.MEDIUM
                    )
                )
                evidenceList.add(
                    Evidence(
                        type = EvidenceType.BUSINESS_REGISTRATION,
                        title = "Expired Business Registration",
                        description = "Registered to ${profile.businessName ?: "Organization"}, but certification lapsed.",
                        source = profile.verificationMethod ?: "Registry Audit",
                        isIndependent = true,
                        timestamp = profile.verifiedAt ?: currentTimeMillis,
                        expiresAt = profile.verificationExpiresAt
                    )
                )

                val spamCount = profile.spamReportsCount
                return if (spamCount >= 10) {
                    ClassificationResult(
                        rawNumber = rawNumber,
                        normalizedNumber = normalized,
                        classification = CallerClassification.POTENTIAL_SPAM,
                        verificationStatus = VerificationStatus.EXPIRED,
                        callerName = profile.businessName,
                        businessName = profile.businessName,
                        category = profile.category,
                        evidenceList = evidenceList,
                        warnings = warnings,
                        dataSource = "Local Business Registry",
                        lastVerifiedTimestamp = profile.verifiedAt,
                        explanation = "POTENTIAL SPAM: Verification expired and this number has received recent spam reports. Proceed with caution."
                    )
                } else {
                    ClassificationResult(
                        rawNumber = rawNumber,
                        normalizedNumber = normalized,
                        classification = CallerClassification.LIKELY_LEGITIMATE,
                        verificationStatus = VerificationStatus.EXPIRED,
                        callerName = profile.businessName,
                        businessName = profile.businessName,
                        category = profile.category,
                        evidenceList = evidenceList,
                        warnings = warnings,
                        dataSource = "Local Business Registry",
                        lastVerifiedTimestamp = profile.verifiedAt,
                        explanation = "LIKELY LEGITIMATE: Previously verified as ${profile.businessName}. Registration has expired, but no malicious patterns detected."
                    )
                }
            }

            // Active Verified Business
            evidenceList.add(
                Evidence(
                    type = EvidenceType.BUSINESS_REGISTRATION,
                    title = "Verified Corporate Entity",
                    description = "Confirmed identity belonging to registered organization ${profile.businessName ?: ""}.",
                    source = profile.verificationMethod ?: "Official Telecommunications Registry",
                    isIndependent = true,
                    timestamp = profile.verifiedAt ?: currentTimeMillis,
                    expiresAt = profile.verificationExpiresAt
                )
            )

            val spamReports = profile.spamReportsCount
            val recovered = spamReports > 5

            if (recovered) {
                evidenceList.add(
                    Evidence(
                        type = EvidenceType.FALSE_SPAM_RECOVERY,
                        title = "False Spam Mitigated",
                        description = "Community spam flags identified as false positives against a registered business entity.",
                        source = "Trustify Cross-Verification",
                        isIndependent = true,
                        timestamp = currentTimeMillis
                    )
                )
            }

            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.VERIFIED_LEGITIMATE,
                verificationStatus = VerificationStatus.VERIFIED_BUSINESS,
                callerName = profile.businessName,
                businessName = profile.businessName,
                category = profile.category,
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = "Official Verified Registry",
                lastVerifiedTimestamp = profile.verifiedAt,
                explanation = "VERIFIED BUSINESS NUMBER: This number has been verified as belonging to ${profile.businessName}. This does not guarantee that every call from the number is safe.",
                isSafeToRingNormally = true,
                falseSpamRecovered = recovered
            )
        }

        // 6. User Personal Trust & Saved Contacts
        if (userTrustedRecord != null) {
            val isContact = userTrustedRecord.notes == "Saved in Phone Contacts"
            evidenceList.add(
                Evidence(
                    type = EvidenceType.USER_WHITELIST,
                    title = if (isContact) "Saved Contact" else "Personal Trusted Contact",
                    description = if (isContact) "Saved in your phone contacts as '${userTrustedRecord.customLabel}'." else "Explicitly designated by you as '${userTrustedRecord.customLabel}'.",
                    source = if (isContact) "Device Contacts" else "Device Local Whitelist",
                    isIndependent = false,
                    timestamp = userTrustedRecord.createdAt
                )
            )

            val spamReports = profile?.spamReportsCount ?: (communityReputation?.spamCount ?: 0)
            if (spamReports >= 15) {
                warnings.add(
                    Warning(
                        code = "CONFLICTING_TRUST",
                        title = "Contradictory Community Signals",
                        message = if (isContact) "This number is in your contacts as '${userTrustedRecord.customLabel}', but has $spamReports recent community reports of spam." else "This number is in your personal trusted list, but has recent community reports of spam.",
                        severity = WarningSeverity.HIGH
                    )
                )
                return ClassificationResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    classification = CallerClassification.LIKELY_LEGITIMATE,
                    verificationStatus = VerificationStatus.CONFLICTING_SIGNALS,
                    callerName = userTrustedRecord.customLabel,
                    category = if (isContact) "Saved Contact" else "Personal Whitelist",
                    evidenceList = evidenceList,
                    warnings = warnings,
                    dataSource = if (isContact) "Device Contacts with Community Alert" else "Local Whitelist with Community Alert",
                    explanation = if (isContact) "SAVED CONTACT (COMMUNITY ALERT): Saved in your contacts as '${userTrustedRecord.customLabel}', but community reports show suspicious activity ($spamReports reports). Confirm identity before sharing sensitive details." else "CONTRADICTORY EVIDENCE: Marked as trusted by your device settings as '${userTrustedRecord.customLabel}', but community reports show suspicious activity. Verify caller authenticity before sharing credentials.",
                    isSafeToRingNormally = true
                )
            }

            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = if (isContact) CallerClassification.VERIFIED_LEGITIMATE else CallerClassification.LIKELY_LEGITIMATE,
                verificationStatus = VerificationStatus.USER_TRUSTED,
                callerName = userTrustedRecord.customLabel,
                category = if (isContact) "Saved Contact" else "Personal Whitelist",
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = if (isContact) "Device Contacts" else "Local Whitelist",
                explanation = if (isContact) "SAVED CONTACT: Saved in your phone contacts as '${userTrustedRecord.customLabel}'. Recognized as a trusted personal caller." else "TRUSTED CALLER: Marked as trusted by your personal device settings ('${userTrustedRecord.customLabel}'). Note: Personal trust does not certify this caller as a public business.",
                isSafeToRingNormally = true
            )
        }

        // 7. User Expecting Delivery without Independent Verification
        if (userExpectingDelivery) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.SERVICE_APPOINTMENT,
                    title = "User Delivery Context",
                    description = "You flagged that you are expecting an incoming delivery.",
                    source = "User Supplied Expectation",
                    isIndependent = false,
                    timestamp = currentTimeMillis
                )
            )
            warnings.add(
                Warning(
                    code = "UNVERIFIED_DELIVERY_CLAIM",
                    title = "Delivery Verification Not Connected",
                    message = "We could not independently confirm whether this caller is your delivery partner.",
                    severity = WarningSeverity.MEDIUM
                )
            )

            val spamReports = profile?.spamReportsCount ?: 0
            return if (spamReports > 15) {
                ClassificationResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    classification = CallerClassification.POTENTIAL_SPAM,
                    verificationStatus = VerificationStatus.UNVERIFIED,
                    evidenceList = evidenceList,
                    warnings = warnings,
                    dataSource = "User Expectation vs Community Reports",
                    explanation = "POTENTIAL SPAM: You are expecting a delivery, but this number has high spam reports and no independent partner dispatch was found. Exercise caution."
                )
            } else {
                ClassificationResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    classification = CallerClassification.UNKNOWN,
                    verificationStatus = VerificationStatus.UNVERIFIED,
                    evidenceList = evidenceList,
                    warnings = warnings,
                    dataSource = "Trustify Context Evaluator",
                    explanation = "UNKNOWN CALLER: Delivery verification is not connected. We could not independently confirm whether this caller is your delivery partner."
                )
            }
        }

        // 8. Community-Powered Spam Detection Thresholds
        // Core rule: At least 10 unique spam reports -> SPAM.
        // Fewer than 10 spam reports -> must NOT automatically be classified as spam.
        // Community Trusted: at least 10 not_spam votes and <= 2 spam votes.
        val effectiveSpamCount = maxOf(profile?.spamReportsCount ?: 0, communityReputation?.spamCount ?: 0)
        val effectiveNotSpamCount = maxOf(profile?.positiveConfirmationsCount ?: 0, communityReputation?.notSpamCount ?: 0)
        val falseSpamCount = profile?.falseSpamReportsCount ?: 0

        if (effectiveSpamCount >= 40 && falseSpamCount <= 1) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.COMMUNITY_REPUTATION,
                    title = "Severe Spam Activity",
                    description = "$effectiveSpamCount verified community reports of robocalls or scam attempts.",
                    source = "Trustify Community Database",
                    isIndependent = true,
                    timestamp = currentTimeMillis
                )
            )
            warnings.add(
                Warning(
                    code = "HIGH_VOLUME_SPAM",
                    title = "High Volume Spam",
                    message = "Significant consensus indicates aggressive unsolicited calling ($effectiveSpamCount reports).",
                    severity = WarningSeverity.HIGH
                )
            )
            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.HIGH_RISK,
                verificationStatus = VerificationStatus.COMMUNITY_REPUTATION,
                category = profile?.category ?: "Suspected Scam / Robocall",
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = "Trustify Community Consensus",
                explanation = "HIGH-RISK ALERT: Significant consensus ($effectiveSpamCount reports) identifies this number as abusive robocalls or fraud.",
                isSafeToRingNormally = false
            )
        }

        if (effectiveSpamCount >= 10 && falseSpamCount <= (effectiveSpamCount / 3)) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.COMMUNITY_REPUTATION,
                    title = "Community Reported Spam",
                    description = "$effectiveSpamCount unique community reports (threshold: 10).",
                    source = "Trustify Community Database",
                    isIndependent = true,
                    timestamp = currentTimeMillis
                )
            )
            warnings.add(
                Warning(
                    code = "COMMUNITY_SPAM_DETECTED",
                    title = "Community Reported Spam",
                    message = "Classified as SPAM based on $effectiveSpamCount unique community reports.",
                    severity = WarningSeverity.HIGH
                )
            )
            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.POTENTIAL_SPAM,
                verificationStatus = VerificationStatus.COMMUNITY_REPUTATION,
                category = profile?.category ?: "Telemarketing / Unsolicited",
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = "Trustify Community Consensus",
                explanation = "COMMUNITY REPORTED SPAM: Classified as SPAM based on $effectiveSpamCount unique community reports.",
                isSafeToRingNormally = false
            )
        }

        // 9. Community Trusted: at least 10 not_spam votes and <= 2 spam votes
        if (effectiveNotSpamCount >= 10 && effectiveSpamCount <= 2) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.HISTORICAL_LEGITIMATE,
                    title = "Community Trusted",
                    description = "$effectiveNotSpamCount positive community votes (under 10 spam reports). Note: Reflects community feedback, not a guarantee of safety.",
                    source = "Trustify Community Ledger",
                    isIndependent = true,
                    timestamp = currentTimeMillis
                )
            )
            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.LIKELY_LEGITIMATE,
                verificationStatus = VerificationStatus.COMMUNITY_REPUTATION,
                callerName = profile?.businessName,
                businessName = profile?.businessName,
                category = profile?.category ?: "Service Provider",
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = "Trustify Community Ledger",
                explanation = "COMMUNITY TRUSTED: Supported by $effectiveNotSpamCount positive community votes. Note: Community reputation reflects user feedback, not verified enterprise identity.",
                isSafeToRingNormally = true
            )
        }

        // 10. Default Fallback: UNVERIFIED / UNKNOWN CALLER
        // A number with fewer than 10 spam reports or no reports must never be presented as definitively safe.
        return ClassificationResult(
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            classification = CallerClassification.UNKNOWN,
            verificationStatus = VerificationStatus.UNVERIFIED,
            callerName = null,
            businessName = null,
            category = null,
            evidenceList = evidenceList,
            warnings = warnings,
            dataSource = if (!isBackendAvailable) "Trustify Local Offline Directory" else "Trustify Community Directory",
            explanation = if (effectiveSpamCount in 1..9) {
                "UNVERIFIED NUMBER: Received $effectiveSpamCount community spam reports (fewer than threshold of 10). Not automatically classified as spam."
            } else {
                "UNKNOWN CALLER: We could not independently verify this caller. Use caution when sharing personal or financial information."
            },
            isSafeToRingNormally = true
        )
    }
}
