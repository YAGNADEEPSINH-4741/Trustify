package com.safecallai.app.data.local

import android.content.ContentValues
import android.database.Cursor
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.domain.models.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalTrustedCallerRepository(private val dbHelper: SafeCallDatabaseHelper) {

    suspend fun getAllTrustedCallers(): List<UserTrustedCaller> = withContext(Dispatchers.IO) {
        val list = mutableListOf<UserTrustedCaller>()
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            SafeCallDatabaseHelper.TABLE_TRUSTED,
            null, null, null, null, null,
            "${SafeCallDatabaseHelper.COL_TIMESTAMP} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    UserTrustedCaller(
                        id = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_ID)),
                        phoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_PHONE)),
                        normalizedPhoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NORMALIZED)),
                        customLabel = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_LABEL)),
                        notes = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NOTES)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_TIMESTAMP))
                    )
                )
            }
        }
        list
    }

    suspend fun findByNormalizedNumber(normalized: String): UserTrustedCaller? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            SafeCallDatabaseHelper.TABLE_TRUSTED,
            null,
            "${SafeCallDatabaseHelper.COL_NORMALIZED} = ?",
            arrayOf(normalized),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                UserTrustedCaller(
                    id = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_ID)),
                    phoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_PHONE)),
                    normalizedPhoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NORMALIZED)),
                    customLabel = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_LABEL)),
                    notes = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NOTES)),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_TIMESTAMP))
                )
            } else null
        }
    }

    suspend fun addTrustedCaller(caller: UserTrustedCaller): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SafeCallDatabaseHelper.COL_PHONE, caller.phoneNumber)
            put(SafeCallDatabaseHelper.COL_NORMALIZED, caller.normalizedPhoneNumber)
            put(SafeCallDatabaseHelper.COL_LABEL, caller.customLabel)
            put(SafeCallDatabaseHelper.COL_NOTES, caller.notes)
            put(SafeCallDatabaseHelper.COL_TIMESTAMP, caller.createdAt)
        }
        db.insertWithOnConflict(
            SafeCallDatabaseHelper.TABLE_TRUSTED,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun removeTrustedCaller(id: Long): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val rows = db.delete(
            SafeCallDatabaseHelper.TABLE_TRUSTED,
            "${SafeCallDatabaseHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )
        rows > 0
    }

    suspend fun removeByNormalizedNumber(normalized: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val rows = db.delete(
            SafeCallDatabaseHelper.TABLE_TRUSTED,
            "${SafeCallDatabaseHelper.COL_NORMALIZED} = ?",
            arrayOf(normalized)
        )
        rows > 0
    }
}

class LocalCallHistoryRepository(private val dbHelper: SafeCallDatabaseHelper) {

    suspend fun getRecentCalls(limit: Int = 50): List<ScreenedCallRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScreenedCallRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SafeCallDatabaseHelper.TABLE_HISTORY,
            null, null, null, null, null,
            "${SafeCallDatabaseHelper.COL_TIMESTAMP} DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                val classificationStr = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_CLASSIFICATION))
                val statusStr = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_VERIFICATION_STATUS))
                val phone = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_PHONE))
                val rawCallerName = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_CALLER_NAME))

                // Resolve saved contact name from device contacts provider if available
                val contactInfo = com.safecallai.app.domain.engine.ContactLookupHelper.lookupContact(dbHelper.context, phone)
                val effectiveCallerName = if (contactInfo.isSavedContact && !contactInfo.name.isNullOrBlank()) {
                    contactInfo.name
                } else {
                    rawCallerName
                }

                list.add(
                    ScreenedCallRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_ID)),
                        phoneNumber = phone,
                        normalizedPhoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NORMALIZED)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_TIMESTAMP)),
                        classification = try { CallerClassification.valueOf(classificationStr) } catch (_: Exception) { CallerClassification.UNKNOWN },
                        verificationStatus = try { VerificationStatus.valueOf(statusStr) } catch (_: Exception) { VerificationStatus.UNVERIFIED },
                        callerName = effectiveCallerName,
                        reason = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_REASON)),
                        wasSilenced = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_WAS_SILENCED)) == 1,
                        falseSpamRecovered = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_FALSE_SPAM)) == 1
                    )
                )
            }
        }
        list
    }

    suspend fun recordCall(record: ScreenedCallRecord): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SafeCallDatabaseHelper.COL_PHONE, record.phoneNumber)
            put(SafeCallDatabaseHelper.COL_NORMALIZED, record.normalizedPhoneNumber)
            put(SafeCallDatabaseHelper.COL_TIMESTAMP, record.timestamp)
            put(SafeCallDatabaseHelper.COL_CLASSIFICATION, record.classification.name)
            put(SafeCallDatabaseHelper.COL_VERIFICATION_STATUS, record.verificationStatus.name)
            put(SafeCallDatabaseHelper.COL_CALLER_NAME, record.callerName)
            put(SafeCallDatabaseHelper.COL_REASON, record.reason)
            put(SafeCallDatabaseHelper.COL_WAS_SILENCED, if (record.wasSilenced) 1 else 0)
            put(SafeCallDatabaseHelper.COL_FALSE_SPAM, if (record.falseSpamRecovered) 1 else 0)
        }
        db.insert(SafeCallDatabaseHelper.TABLE_HISTORY, null, values)
    }

    suspend fun clearHistory(): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(SafeCallDatabaseHelper.TABLE_HISTORY, null, null) >= 0
    }

    suspend fun getStats(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        var total = 0
        var falseSpamRecovered = 0
        var verifiedCount = 0

        val cursor = db.rawQuery(
            "SELECT count(*), sum($COL_FALSE_SPAM), sum(CASE WHEN $COL_CLASSIFICATION = 'VERIFIED_LEGITIMATE' THEN 1 ELSE 0 END) FROM ${SafeCallDatabaseHelper.TABLE_HISTORY}",
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                total = it.getInt(0)
                falseSpamRecovered = it.getInt(1)
                verifiedCount = it.getInt(2)
            }
        }
        Triple(total, falseSpamRecovered, verifiedCount)
    }

    companion object {
        private const val COL_FALSE_SPAM = SafeCallDatabaseHelper.COL_FALSE_SPAM
        private const val COL_CLASSIFICATION = SafeCallDatabaseHelper.COL_CLASSIFICATION
    }
}

class LocalBusinessCacheRepository(private val dbHelper: SafeCallDatabaseHelper) {

    suspend fun getCachedProfile(normalized: String): CallerProfile? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SafeCallDatabaseHelper.TABLE_CACHE,
            null,
            "${SafeCallDatabaseHelper.COL_NORMALIZED} = ?",
            arrayOf(normalized),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                CallerProfile(
                    normalizedPhoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NORMALIZED)),
                    businessName = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_BUSINESS_NAME)),
                    category = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_CATEGORY)),
                    isVerifiedBusiness = true,
                    verificationMethod = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_METHOD)),
                    verifiedAt = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_VERIFIED_AT)),
                    verificationExpiresAt = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_EXPIRES_AT)),
                    spamReportsCount = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_SPAM_COUNT)),
                    falseSpamReportsCount = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_FALSE_SPAM_COUNT)),
                    positiveConfirmationsCount = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_CONFIRMATIONS)),
                    isImpersonationReported = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IMPERSONATION)) == 1
                )
            } else null
        }
    }

    suspend fun cacheProfile(profile: CallerProfile): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SafeCallDatabaseHelper.COL_NORMALIZED, profile.normalizedPhoneNumber)
            put(SafeCallDatabaseHelper.COL_BUSINESS_NAME, profile.businessName ?: "")
            put(SafeCallDatabaseHelper.COL_CATEGORY, profile.category)
            put(SafeCallDatabaseHelper.COL_METHOD, profile.verificationMethod)
            put(SafeCallDatabaseHelper.COL_VERIFIED_AT, profile.verifiedAt)
            put(SafeCallDatabaseHelper.COL_EXPIRES_AT, profile.verificationExpiresAt)
            put(SafeCallDatabaseHelper.COL_SPAM_COUNT, profile.spamReportsCount)
            put(SafeCallDatabaseHelper.COL_FALSE_SPAM_COUNT, profile.falseSpamReportsCount)
            put(SafeCallDatabaseHelper.COL_CONFIRMATIONS, profile.positiveConfirmationsCount)
            put(SafeCallDatabaseHelper.COL_IMPERSONATION, if (profile.isImpersonationReported) 1 else 0)
        }
        db.insertWithOnConflict(
            SafeCallDatabaseHelper.TABLE_CACHE,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(SafeCallDatabaseHelper.TABLE_CACHE, null, null) >= 0
    }
}

data class LocalReportDraft(
    val id: Long = 0,
    val phoneNumber: String,
    val normalizedPhoneNumber: String,
    val category: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LocalReportRepository(private val dbHelper: SafeCallDatabaseHelper) {

    suspend fun saveReport(
        phoneNumber: String,
        normalizedPhoneNumber: String,
        category: String,
        description: String
    ): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SafeCallDatabaseHelper.COL_PHONE, phoneNumber)
            put(SafeCallDatabaseHelper.COL_NORMALIZED, normalizedPhoneNumber)
            put(SafeCallDatabaseHelper.COL_REPORT_CATEGORY, category)
            put(SafeCallDatabaseHelper.COL_REPORT_DESC, description)
            put(SafeCallDatabaseHelper.COL_TIMESTAMP, System.currentTimeMillis())
        }
        db.insert(SafeCallDatabaseHelper.TABLE_REPORTS, null, values)
    }

    suspend fun getAllReports(): List<LocalReportDraft> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalReportDraft>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SafeCallDatabaseHelper.TABLE_REPORTS,
            null, null, null, null, null,
            "${SafeCallDatabaseHelper.COL_TIMESTAMP} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LocalReportDraft(
                        id = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_ID)),
                        phoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_PHONE)),
                        normalizedPhoneNumber = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NORMALIZED)),
                        category = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_REPORT_CATEGORY)),
                        description = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_REPORT_DESC)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_TIMESTAMP))
                    )
                )
            }
        }
        list
    }

    suspend fun clearReports(): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(SafeCallDatabaseHelper.TABLE_REPORTS, null, null) >= 0
    }
}
