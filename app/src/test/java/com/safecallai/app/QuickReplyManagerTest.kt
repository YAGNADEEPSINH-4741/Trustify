package com.safecallai.app

import com.safecallai.app.service.QuickReplyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyManagerTest {

    @Test
    fun testPredefinedMessagesContainAllSixRequiredOptions() {
        val expectedMessages = listOf(
            "I'm busy right now. I'll call you back later.",
            "I'm out of station at the moment.",
            "I'll call you in 5 minutes.",
            "I'm driving right now. I'll contact you when I arrive.",
            "I can't talk right now. Please message me.",
            "I'm in a meeting. I'll call you back when I'm free."
        )

        assertEquals(6, QuickReplyManager.PREDEFINED_MESSAGES.size)
        assertEquals(expectedMessages, QuickReplyManager.PREDEFINED_MESSAGES)
    }

    @Test
    fun testValidRecipientPhoneNumbers() {
        // Standard mobile numbers
        assertTrue(QuickReplyManager.isValidRecipient("+919876543210"))
        assertTrue(QuickReplyManager.isValidRecipient("9876543210"))
        assertTrue(QuickReplyManager.isValidRecipient("+14155552671"))
        assertTrue(QuickReplyManager.isValidRecipient("02228889999"))
        assertTrue(QuickReplyManager.isValidRecipient("+447911123456"))
    }

    @Test
    fun testInvalidRecipientPhoneNumbers() {
        // Null or blank
        assertFalse(QuickReplyManager.isValidRecipient(null))
        assertFalse(QuickReplyManager.isValidRecipient(""))
        assertFalse(QuickReplyManager.isValidRecipient("   "))

        // Special privacy caller IDs
        assertFalse(QuickReplyManager.isValidRecipient("Private Number"))
        assertFalse(QuickReplyManager.isValidRecipient("Unknown"))
        assertFalse(QuickReplyManager.isValidRecipient("restricted"))
        assertFalse(QuickReplyManager.isValidRecipient("anonymous"))

        // Too short or invalid
        assertFalse(QuickReplyManager.isValidRecipient("123"))
        assertFalse(QuickReplyManager.isValidRecipient("invalid_number"))
        assertFalse(QuickReplyManager.isValidRecipient("++"))
    }

    @Test
    fun testSmsSegmentInfoSingleGsmMessage() {
        val message = "I'm busy right now. I'll call you back later."
        val info = QuickReplyManager.calculateSmsSegmentInfo(message)

        assertEquals(message.length, info.charCount)
        assertEquals(1, info.segmentCount)
        assertEquals(160 - message.length, info.remainingInCurrentSegment)
        assertEquals(160, info.maxSingleSegmentChars)
        assertFalse(info.isUnicode)
    }

    @Test
    fun testSmsSegmentInfoBoundary160Gsm() {
        val message160 = "A".repeat(160)
        val info160 = QuickReplyManager.calculateSmsSegmentInfo(message160)

        assertEquals(160, info160.charCount)
        assertEquals(1, info160.segmentCount)
        assertEquals(0, info160.remainingInCurrentSegment)
        assertFalse(info160.isUnicode)

        val message161 = "A".repeat(161)
        val info161 = QuickReplyManager.calculateSmsSegmentInfo(message161)

        assertEquals(161, info161.charCount)
        assertEquals(2, info161.segmentCount)
        // Concatenated GSM has 153 chars per segment.
        // 161 - 153 = 8 chars in 2nd segment. Remaining in 2nd segment: 153 - 8 = 145.
        assertEquals(145, info161.remainingInCurrentSegment)
    }

    @Test
    fun testSmsSegmentInfoUnicodeEncoding() {
        val emojiMessage = "I'll call you soon! 🚗"
        val info = QuickReplyManager.calculateSmsSegmentInfo(emojiMessage)

        assertTrue(info.isUnicode)
        assertEquals(70, info.maxSingleSegmentChars)
        assertEquals(1, info.segmentCount)
        assertEquals(70 - emojiMessage.length, info.remainingInCurrentSegment)
    }

    @Test
    fun testBuildSmstoUriString() {
        val recipient = "+919876543210"
        val uriString = QuickReplyManager.buildSmstoUriString(recipient)
        assertEquals("smsto:+919876543210", uriString)
    }

    @Test
    fun testPredefinedMessagesAreNonEmptyAndSensible() {
        QuickReplyManager.PREDEFINED_MESSAGES.forEach { msg ->
            assertTrue(msg.isNotBlank())
            assertTrue(msg.length in 10..160)
            val info = QuickReplyManager.calculateSmsSegmentInfo(msg)
            assertEquals(1, info.segmentCount) // All predefined messages must comfortably fit in a single SMS
        }
    }
}
