package com.safecallai.app.domain.models

import android.net.Uri

/**
 * Information retrieved from the device's local ContactsContract.
 * Kept strictly separate from independent enterprise verification.
 * A contact's saved name does not verify a business for other users.
 */
data class ContactInfo(
    val name: String? = null,
    val photoUri: Uri? = null,
    val lookupKey: String? = null,
    val isSavedContact: Boolean = false
)
