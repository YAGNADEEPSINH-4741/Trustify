package com.safecallai.app.domain.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.safecallai.app.domain.models.ContactInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Safely looks up local device contacts via Android's ContactsContract.
 *
 * Requirements:
 * 1. 100% On-Device: Contacts data is queried purely locally and is NEVER uploaded.
 * 2. Saved contact names are treated as user personal labels, not public business registries.
 * 3. Graceful fallback: If READ_CONTACTS permission is denied or no contact matches,
 *    returns an unverified ContactInfo with isSavedContact = false.
 * 4. Normalization and fallback queries to prevent missed matches across international formats.
 * 5. High performance in-memory cache to never block incoming call rendering.
 */
object ContactLookupHelper {

    private val cache = ConcurrentHashMap<String, ContactInfo>()

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun lookupContact(context: Context, phoneNumber: String): ContactInfo {
        if (phoneNumber.isBlank()) {
            return ContactInfo(isSavedContact = false)
        }

        val cacheKey = phoneNumber.trim()
        val cached = cache[cacheKey]
        if (cached != null) {
            return cached
        }

        if (!hasPermission(context)) {
            return ContactInfo(isSavedContact = false)
        }

        // Try lookup with raw phone number first
        var result = performQuery(context, phoneNumber)

        // If not found, try normalized phone number
        if (!result.isSavedContact) {
            val normalized = PhoneNumberNormalizer.normalize(phoneNumber)
            if (normalized != phoneNumber && normalized.isNotBlank()) {
                result = performQuery(context, normalized)
            }
        }

        cache[cacheKey] = result
        return result
    }

    private fun performQuery(context: Context, number: String): ContactInfo {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )

            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.LOOKUP_KEY
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)

                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val photoUriString = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                    val lookupKey = if (lookupKeyIndex >= 0) cursor.getString(lookupKeyIndex) else null

                    val photoUri = photoUriString?.let { Uri.parse(it) }

                    ContactInfo(
                        name = name?.takeIf { it.isNotBlank() },
                        photoUri = photoUri,
                        lookupKey = lookupKey,
                        isSavedContact = !name.isNullOrBlank()
                    )
                } else {
                    ContactInfo(isSavedContact = false)
                }
            } ?: ContactInfo(isSavedContact = false)
        } catch (_: Exception) {
            ContactInfo(isSavedContact = false)
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
