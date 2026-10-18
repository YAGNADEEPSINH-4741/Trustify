package com.safecallai.app

import com.safecallai.app.data.local.AppLanguage
import com.safecallai.app.service.QuickReplyManager
import com.safecallai.app.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {

    @Test
    fun testAppLanguageEnumCodesAndNames() {
        assertEquals("en", AppLanguage.ENGLISH.code)
        assertEquals("English", AppLanguage.ENGLISH.nativeName)
        assertEquals("🇬🇧", AppLanguage.ENGLISH.flag)

        assertEquals("hi", AppLanguage.HINDI.code)
        assertEquals("हिंदी", AppLanguage.HINDI.nativeName)
        assertEquals("🇮🇳", AppLanguage.HINDI.flag)

        assertEquals("gu", AppLanguage.GUJARATI.code)
        assertEquals("ગુજરાતી", AppLanguage.GUJARATI.nativeName)
        assertEquals("🇮🇳", AppLanguage.GUJARATI.flag)
    }

    @Test
    fun testAppLanguageFromCode() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("EN"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("hi"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("HI"))
        assertEquals(AppLanguage.GUJARATI, AppLanguage.fromCode("gu"))
        assertEquals(AppLanguage.GUJARATI, AppLanguage.fromCode("GU"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("invalid_code"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode(null))
    }

    @Test
    fun testLocaleHelperResolvesLocales() {
        val enLocale = LocaleHelper.getLocale(AppLanguage.ENGLISH)
        assertEquals("en", enLocale.language)

        val hiLocale = LocaleHelper.getLocale(AppLanguage.HINDI)
        assertEquals("hi", hiLocale.language)
        assertEquals("IN", hiLocale.country)

        val guLocale = LocaleHelper.getLocale(AppLanguage.GUJARATI)
        assertEquals("gu", guLocale.language)
        assertEquals("IN", guLocale.country)
    }

    @Test
    fun testQuickReplyMessageResIds() {
        val resIds = QuickReplyManager.PREDEFINED_MESSAGE_RES_IDS
        assertEquals(6, resIds.size)
        resIds.forEach { id ->
            assertTrue("Resource ID should be valid non-zero integer", id != 0)
        }
    }

    @Test
    fun testStringResourceFilesParity() {
        val baseResDir = if (File("app/src/main/res").exists()) {
            File("app/src/main/res")
        } else {
            File("src/main/res")
        }
        val enStringsFile = File(baseResDir, "values/strings.xml")
        val hiStringsFile = File(baseResDir, "values-hi/strings.xml")
        val guStringsFile = File(baseResDir, "values-gu/strings.xml")

        assertTrue("English strings.xml must exist: ${enStringsFile.absolutePath}", enStringsFile.exists())
        assertTrue("Hindi strings.xml must exist: ${hiStringsFile.absolutePath}", hiStringsFile.exists())
        assertTrue("Gujarati strings.xml must exist: ${guStringsFile.absolutePath}", guStringsFile.exists())

        fun parseAllStringKeys(file: File): List<String> {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(file)
            val nodes = doc.getElementsByTagName("string")
            val keys = mutableListOf<String>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val nameAttr = node.attributes.getNamedItem("name")
                if (nameAttr != null) {
                    keys.add(nameAttr.nodeValue)
                }
            }
            return keys
        }

        val enKeyList = parseAllStringKeys(enStringsFile)
        val hiKeyList = parseAllStringKeys(hiStringsFile)
        val guKeyList = parseAllStringKeys(guStringsFile)

        // Verify no duplicate keys in any resource file
        val enDuplicates = enKeyList.groupBy { it }.filter { it.value.size > 1 }.keys
        val hiDuplicates = hiKeyList.groupBy { it }.filter { it.value.size > 1 }.keys
        val guDuplicates = guKeyList.groupBy { it }.filter { it.value.size > 1 }.keys

        assertTrue("English strings.xml has duplicate keys: $enDuplicates", enDuplicates.isEmpty())
        assertTrue("Hindi strings.xml has duplicate keys: $hiDuplicates", hiDuplicates.isEmpty())
        assertTrue("Gujarati strings.xml has duplicate keys: $guDuplicates", guDuplicates.isEmpty())

        val enKeys = enKeyList.toSet()
        val hiKeys = hiKeyList.toSet()
        val guKeys = guKeyList.toSet()

        // Essential core keys required across all languages
        val essentialKeys = listOf(
            "app_name",
            "language_selection_title",
            "language_selection_subtitle",
            "language_english",
            "language_hindi",
            "language_gujarati",
            "language_continue",
            "language_settings_title",
            "nav_home",
            "nav_activity",
            "nav_trusted",
            "nav_report",
            "nav_settings",
            "call_incoming",
            "call_answer",
            "call_decline",
            "call_send_message",
            "call_quick_reply_title",
            "quick_reply_busy",
            "quick_reply_out_of_station",
            "quick_reply_call_5_min",
            "quick_reply_driving",
            "quick_reply_cant_talk",
            "quick_reply_in_meeting",
            "badge_verified_legitimate",
            "badge_likely_legitimate",
            "badge_unknown",
            "badge_potential_spam",
            "badge_high_risk",
            "dashboard_tagline",
            "dashboard_pill_protected",
            "dashboard_pill_paused",
            "dashboard_instant_lookup",
            "caller_details_title",
            "caller_details_mark_trusted",
            "caller_details_remove_trusted"
        )

        essentialKeys.forEach { key ->
            assertTrue("English strings.xml missing essential key: $key", enKeys.contains(key))
            assertTrue("Hindi strings.xml missing essential key: $key", hiKeys.contains(key))
            assertTrue("Gujarati strings.xml missing essential key: $key", guKeys.contains(key))
        }

        // Verify key count parity is within reasonable bounds
        assertTrue("English strings should have at least 80 keys", enKeys.size >= 80)
        assertTrue("Hindi strings should have at least 80 keys", hiKeys.size >= 80)
        assertTrue("Gujarati strings should have at least 80 keys", guKeys.size >= 80)
    }
}
