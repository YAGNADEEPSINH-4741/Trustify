package com.safecallai.app.domain.engine

import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.ClassificationResult
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
                dataSource = "SafeCall AI Input Validator",
                explanation = "Classification unavailable: The phone number format is invalid or incomplete."
            )
        }

        // 2. Offline / Connectivity Indicator
        if (!isBackendAvailable) {
            warnings.add(
                Warning(
                    code = "OFFLINE_MODE",
                    title = "Backend Unreachable",
                    message = "Cloud verification registry could not be reached. Local offline rules and cached data are being used.",
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
                    source = "SafeCall AI Threat Intelligence",
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
                dataSource = "SafeCall Threat Intelligence",
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
                        source = "SafeCall Recovery Engine",
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
                        source = "SafeCall Cross-Verification",
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

        // 6. User Personal Trust
        if (userTrustedRecord != null) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.USER_WHITELIST,
                    title = "Personal Trusted Contact",
                    description = "Explicitly designated by you as '${userTrustedRecord.customLabel}'.",
                    source = "Device Local Whitelist",
                    isIndependent = false,
                    timestamp = userTrustedRecord.createdAt
                )
            )

            val spamReports = profile?.spamReportsCount ?: 0
            if (spamReports >= 15) {
                warnings.add(
                    Warning(
                        code = "CONFLICTING_TRUST",
                        title = "Contradictory Community Signals",
                        message = "This number is in your personal trusted list, but has recent community reports of spam.",
                        severity = WarningSeverity.HIGH
                    )
                )
                return ClassificationResult(
                    rawNumber = rawNumber,
                    normalizedNumber = normalized,
                    classification = CallerClassification.LIKELY_LEGITIMATE,
                    verificationStatus = VerificationStatus.CONFLICTING_SIGNALS,
                    callerName = userTrustedRecord.customLabel,
                    category = "Personal Whitelist",
                    evidenceList = evidenceList,
                    warnings = warnings,
                    dataSource = "Local Whitelist with Community Alert",
                    explanation = "CONTRADICTORY EVIDENCE: Marked as trusted by your device settings as '${userTrustedRecord.customLabel}', but community reports show suspicious activity. Verify caller authenticity before sharing credentials.",
                    isSafeToRingNormally = true
                )
            }

            return ClassificationResult(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                classification = CallerClassification.LIKELY_LEGITIMATE,
                verificationStatus = VerificationStatus.USER_TRUSTED,
                callerName = userTrustedRecord.customLabel,
                category = "Personal Whitelist",
                evidenceList = evidenceList,
                warnings = warnings,
                dataSource = "Local Whitelist",
                explanation = "TRUSTED CALLER: Marked as trusted by your personal device settings ('${userTrustedRecord.customLabel}'). Note: Personal trust does not certify this caller as a public business.",
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
                    dataSource = "SafeCall Context Evaluator",
                    explanation = "UNKNOWN CALLER: Delivery verification is not connected. We could not independently confirm whether this caller is your delivery partner."
                )
            }
        }

        // 8. Community Spam Reports Thresholds
        val spamCount = profile?.spamReportsCount ?: 0
        val falseSpamCount = profile?.falseSpamReportsCount ?: 0

        if (spamCount >= 40 && falseSpamCount <= 1) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.COMMUNITY_REPUTATION,
                    title = "Severe Spam Activity",
                    description = "$spamCount verified community reports of robocalls or scam attempts.",
                    source = "Community Consensus Database",
                    isIndependent = true,
                    timestamp = currentTimeMillis
                )
            )
            warnings.add(
                Warning(
                    code = "HIGH_VOLUME_SPAM",
                    title = "High Volume Spam",
                    message = "Significant consensus indicates aggressive unsolicited calling.",
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
                dataSource = "Community Consensus",
                explanation = "HIGH-RISK ALERT: Significant volume of consistent reports identify this number as abusive robocalls or fraud.",
                isSafeToRingNormally = false
            )
        }

        if (spamCount >= 10 && falseSpamCount <= (spamCount / 3)) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.COMMUNITY_REPUTATION,
                    title = "Suspicious Calling Reports",
                    description = "$spamCount reports of telemarketing or unwanted solicitation.",
                    source = "Community Consensus Database",
                    isIndependent = true,
                    timestamp = currentTimeMillis
                )
            )
            warnings.add(
                Warning(
                    code = "POTENTIAL_SPAM_DETECTED",
                    title = "Potential Spam",
                    message = "Reports may be inaccurate. Review the available context before answering.",
                    severity = WarningSeverity.MEDIUM
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
                dataSource = "Community Consensus",
                explanation = "POTENTIAL SPAM: This number has suspicious reports. Reports may be inaccurate, so review the available evidence before deciding."
            )
        }

        // 9. Consistent Legitimate History without Enterprise Verification
        val positiveConfirmations = profile?.positiveConfirmationsCount ?: 0
        if (positiveConfirmations >= 10 && spamCount <= 2) {
            evidenceList.add(
                Evidence(
                    type = EvidenceType.HISTORICAL_LEGITIMATE,
                    title = "Positive Caller History",
                    description = "$positiveConfirmations positive confirmations of legitimate service or business inquiries.",
                    source = "SafeCall Historical Ledger",
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
                dataSource = "Community Historical Ledger",
                explanation = "LIKELY LEGITIMATE: This number has a consistent history of legitimate interactions, although official corporate registration is not recorded."
            )
        }

        // 10. Default Safe Fallback: UNKNOWN CALLER
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
            dataSource = if (isBackendAvailable) "SafeCall Global Directory" else "Local Offline Cache",
            explanation = "UNKNOWN CALLER: We could not independently verify this caller. Use caution when sharing personal or financial information.",
            isSafeToRingNormally = true
        )
    }
}
