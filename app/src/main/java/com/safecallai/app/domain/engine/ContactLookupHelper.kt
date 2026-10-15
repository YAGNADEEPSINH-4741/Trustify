package com.safecallai.app.domain.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.safecallai.app.domain.models.ContactInfo

/**
 * Safely looks up local device contacts via ContactsContract.
 *
 * Privacy Guarantees:
 * 1. Contact data is queried purely on-device and is NEVER uploaded.
 * 2. Saved contact names are treated as personal labels, not as independent enterprise verification.
 * 3. If READ_CONTACTS permission is denied, lookup gracefully returns an unverified ContactInfo.
 */
object ContactLookupHelper {

    fun lookupContact(context: Context, phoneNumber: String): ContactInfo {
        if (phoneNumber.isBlank()) {
            return ContactInfo(isSavedContact = false)
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ContactInfo(isSavedContact = false)
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
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
        } catch (e: Exception) {
            // Content provider error or security exception handled safely
            ContactInfo(isSavedContact = false)
        }
    }
}
