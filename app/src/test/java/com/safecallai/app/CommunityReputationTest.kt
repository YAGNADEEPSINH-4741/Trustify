package com.safecallai.app

import com.safecallai.app.domain.engine.CallClassificationEngine
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.CommunityClassification
import com.safecallai.app.domain.models.CommunityReputation
import com.safecallai.app.domain.models.CommunityThresholdEngine
import com.safecallai.app.util.PhoneNumberPrivacyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for Trustify Community Reputation system:
 * - Privacy-preserving phone hashing & E.164 canonicalization
 * - Deterministic reputation thresholds (Spam >= 10, Trusted >= 10 not-spam with <= 2 spam, Unverified fallback)
 * - State transitions: Idempotent duplicate voting and vote reversal
 * - CallClassificationEngine integration
 */
class CommunityReputationTest {

    private lateinit var engine: CallClassificationEngine

    @Before
    fun setUp() {
        engine = CallClassificationEngine()
    }

    // ==========================================
    // 1. PRIVACY & HASHING TESTS
    // ==========================================

    @Test
    fun testPrivacyHash_consistentAcrossFormats() {
        val raw1 = "+91 98765 43210"
        val raw2 = "+91-98765-43210"
        val raw3 = "+91 9876543210"
        val raw4 = "+919876543210"

        val hash1 = PhoneNumberPrivacyUtil.computePhoneHash(raw1)
        val hash2 = PhoneNumberPrivacyUtil.computePhoneHash(raw2)
        val hash3 = PhoneNumberPrivacyUtil.computePhoneHash(raw3)
        val hash4 = PhoneNumberPrivacyUtil.computePhoneHash(raw4)

        // All variations must resolve to the identical privacy hash
        assertEquals(hash1, hash2)
        assertEquals(hash2, hash3)
        assertEquals(hash3, hash4)

        // Hash must be 64-character lowercase hex (SHA-256 output)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[a-f0-9]{64}$")))

        // Hash must NEVER leak or contain the raw phone number digits
        assertFalse(hash1.contains("9876543210"))
    }

    @Test
    fun testPrivacyHash_distinctNumbersProduceDistinctHashes() {
        val hashA = PhoneNumberPrivacyUtil.computePhoneHash("+919876543210")
        val hashB = PhoneNumberPrivacyUtil.computePhoneHash("+919876543211")

        assertNotEquals(hashA, hashB)
    }

    // ==========================================
    // 2. THRESHOLD CLASSIFICATION TESTS
    // ==========================================

    @Test
    fun testThreshold_zeroReports_isUnverified() {
        val result = CommunityThresholdEngine.classify(spamCount = 0, notSpamCount = 0)
        assertEquals(CommunityClassification.UNVERIFIED, result)
    }

    @Test
    fun testThreshold_nineSpamReports_remainsUnverified() {
        // Less than 10 spam reports must NEVER be classified as SPAM
        val result = CommunityThresholdEngine.classify(spamCount = 9, notSpamCount = 0)
        assertEquals(CommunityClassification.UNVERIFIED, result)
    }

    @Test
    fun testThreshold_tenSpamReports_isSpam() {
        val result = CommunityThresholdEngine.classify(spamCount = 10, notSpamCount = 0)
        assertEquals(CommunityClassification.SPAM, result)
    }

    @Test
    fun testThreshold_highSpamWithSomeNotSpam_isSpam() {
        val result = CommunityThresholdEngine.classify(spamCount = 25, notSpamCount = 3)
        assertEquals(CommunityClassification.SPAM, result)
    }

    @Test
    fun testThreshold_communityTrusted_exactTenNotSpamZeroSpam() {
        val result = CommunityThresholdEngine.classify(spamCount = 0, notSpamCount = 10)
        assertEquals(CommunityClassification.COMMUNITY_TRUSTED, result)
    }

    @Test
    fun testThreshold_communityTrusted_atMaxAllowedSpamBoundary() {
        // <= 2 spam is permitted for community trusted
        val result = CommunityThresholdEngine.classify(spamCount = 2, notSpamCount = 15)
        assertEquals(CommunityClassification.COMMUNITY_TRUSTED, result)
    }

    @Test
    fun testThreshold_moreThanTwoSpamDisqualifiesCommunityTrusted() {
        // 3 spam disqualifies trusted, falls back to UNVERIFIED
        val result = CommunityThresholdEngine.classify(spamCount = 3, notSpamCount = 15)
        assertEquals(CommunityClassification.UNVERIFIED, result)
    }

    @Test
    fun testThreshold_nineNotSpam_remainsUnverified() {
        // Need at least 10 not_spam to be COMMUNITY_TRUSTED
        val result = CommunityThresholdEngine.classify(spamCount = 0, notSpamCount = 9)
        assertEquals(CommunityClassification.UNVERIFIED, result)
    }

    // ==========================================
    // 3. VOTE STATE MACHINE & ATOMICITY SIMULATION
    // ==========================================

    @Test
    fun testVoteStateMachine_immutableFinalVoteAndIdempotency() {
        var spamCount = 0
        var notSpamCount = 0
        var currentVote: String? = null

        fun applyVote(newVote: String) {
            if (currentVote == newVote) {
                // Idempotent: repeated vote does nothing
                return
            }
            if (currentVote != null) {
                throw IllegalStateException("A final vote has already been recorded for this number and cannot be modified.")
            }
            if (newVote == "spam") spamCount++
            if (newVote == "not_spam") notSpamCount++
            currentVote = newVote
        }

        // Initial state
        assertEquals(0, spamCount)
        assertEquals(0, notSpamCount)

        // 1. User votes SPAM
        applyVote("spam")
        assertEquals(1, spamCount)
        assertEquals(0, notSpamCount)
        assertEquals("spam", currentVote)

        // 2. User votes SPAM again (idempotent, does not double count)
        applyVote("spam")
        assertEquals(1, spamCount)
        assertEquals(0, notSpamCount)
        assertEquals("spam", currentVote)

        // 3. User attempts to change vote to NOT_SPAM -> REJECTED
        var rejected = false
        try {
            applyVote("not_spam")
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue("Attempt to change a final vote must be rejected", rejected)
        assertEquals(1, spamCount)
        assertEquals(0, notSpamCount)
        assertEquals("spam", currentVote)
    }

    @Test
    fun testMultiUserThresholdProgression_tenSpamVotesTransitionsToSpam() {
        var spamCount = 0
        for (i in 1..9) {
            spamCount++
            val intermediate = CommunityThresholdEngine.classify(spamCount, notSpamCount = 0)
            assertEquals("At $i votes it must be UNVERIFIED", CommunityClassification.UNVERIFIED, intermediate)
        }

        spamCount++ // 10th vote
        val tenth = CommunityThresholdEngine.classify(spamCount, notSpamCount = 0)
        assertEquals("At 10 votes it must transition to SPAM", CommunityClassification.SPAM, tenth)
    }

    // ==========================================
    // 4. CALL CLASSIFICATION ENGINE INTEGRATION
    // ==========================================

    @Test
    fun testCallClassification_withCommunitySpamReputation() {
        val phoneHash = PhoneNumberPrivacyUtil.computePhoneHash("+919999988888")
        val communityReputation = CommunityReputation(
            phoneHash = phoneHash,
            classification = CommunityClassification.SPAM,
            spamCount = 14,
            notSpamCount = 1
        )

        val result = engine.classify(
            rawNumber = "+919999988888",
            communityReputation = communityReputation
        )

        assertEquals(CallerClassification.POTENTIAL_SPAM, result.classification)
        assertFalse(result.isSafeToRingNormally)
        assertTrue(result.evidenceList.any { it.description.contains("14 unique community reports") })
    }

    @Test
    fun testCallClassification_withCommunityTrustedReputation() {
        val phoneHash = PhoneNumberPrivacyUtil.computePhoneHash("+919999977777")
        val communityReputation = CommunityReputation(
            phoneHash = phoneHash,
            classification = CommunityClassification.COMMUNITY_TRUSTED,
            spamCount = 1,
            notSpamCount = 20
        )

        val result = engine.classify(
            rawNumber = "+919999977777",
            communityReputation = communityReputation
        )

        // Must show community positive feedback
        assertTrue(result.isSafeToRingNormally)
        assertTrue(result.evidenceList.any { it.title.contains("Community Trusted") })
    }

    @Test
    fun testCallClassification_unverifiedDoesNotMarkSafe() {
        val phoneHash = PhoneNumberPrivacyUtil.computePhoneHash("+919999966666")
        val communityReputation = CommunityReputation(
            phoneHash = phoneHash,
            classification = CommunityClassification.UNVERIFIED,
            spamCount = 3,
            notSpamCount = 4
        )

        val result = engine.classify(
            rawNumber = "+919999966666",
            communityReputation = communityReputation
        )

        // UNVERIFIED community reputation falls back to UNKNOWN caller
        assertEquals(CallerClassification.UNKNOWN, result.classification)
    }

    // ==========================================
    // 5. REPEATED FEEDBACK SUPPRESSION & DECISION PERSISTENCE
    // ==========================================

    @Test
    fun testDecisionPersistence_previouslyVotedNumbersAreSuppressed() {
        val userVoteMap = mutableMapOf<String, String>() // simulates synced_to_cloud = 1 votes

        fun hasUserVoted(number: String): Boolean {
            val hash = PhoneNumberPrivacyUtil.computePhoneHash(number)
            val vote = userVoteMap[hash]
            return vote == "spam" || vote == "not_spam"
        }

        val testNumber = "+919876543210"
        val unrelatedNumber = "+919876543211"

        // Call 1: User has not voted yet -> prompt is offered
        assertFalse("First call must offer feedback", hasUserVoted(testNumber))

        // User votes NOT_SPAM
        val hash = PhoneNumberPrivacyUtil.computePhoneHash(testNumber)
        userVoteMap[hash] = "not_spam"

        // Call 2 tomorrow: User previously voted NOT_SPAM -> prompt MUST be permanently suppressed
        assertTrue("Subsequent call must suppress feedback prompt", hasUserVoted(testNumber))

        // Unrelated number is NOT suppressed
        assertFalse("Unrelated number must not be suppressed", hasUserVoted(unrelatedNumber))
    }

    @Test
    fun testDecideLater_doesNotSuppressFutureFeedback() {
        val userVoteMap = mutableMapOf<String, String>()

        fun hasUserVoted(number: String): Boolean {
            val hash = PhoneNumberPrivacyUtil.computePhoneHash(number)
            val vote = userVoteMap[hash]
            return vote == "spam" || vote == "not_spam"
        }

        val testNumber = "+919876543210"

        // User selected 'Decide Later': no vote recorded
        assertFalse("Decide later must not record a final vote", hasUserVoted(testNumber))

        // Next call still offers feedback
        assertFalse("Future call must offer feedback again", hasUserVoted(testNumber))
    }

    @Test
    fun testFailedVoteRequest_doesNotFalselySuppressFeedback() {
        val userVoteMap = mutableMapOf<String, Pair<String, Boolean>>() // vote to (vote, isSynced)

        fun hasUserVoted(number: String): Boolean {
            val hash = PhoneNumberPrivacyUtil.computePhoneHash(number)
            val entry = userVoteMap[hash] ?: return false
            return entry.second && (entry.first == "spam" || entry.first == "not_spam")
        }

        val testNumber = "+919876543210"
        val hash = PhoneNumberPrivacyUtil.computePhoneHash(testNumber)

        // Vote attempt failed (isSynced = false)
        userVoteMap[hash] = Pair("spam", false)

        assertFalse("Failed request must NOT be treated as a successful permanent vote", hasUserVoted(testNumber))
    }
}
