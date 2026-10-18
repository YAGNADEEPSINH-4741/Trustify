package com.safecallai.app.data.local

import android.content.ContentValues
import android.database.Cursor
import com.safecallai.app.domain.models.CallerIdentityResult
import com.safecallai.app.domain.models.CallerIdentitySource
import com.safecallai.app.domain.models.CallerIdentityType
import com.safecallai.app.domain.models.IdentityConfidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages local on-device SQLite caching for resolved caller identities.
 *
 * Rules:
 * 1. Saved contacts are NEVER cached in SQLite (they must always be read dynamically
 *    from ContactsContract to immediately reflect user edits or permission changes).
 * 2. Only high/medium confidence results (Network CNAP, TRAI series, Public Directory)
 *    are cached.
 * 3. Cache entries expire after a configurable TTL (default 14 days) and are purged on expiry.
 * 4. 100% On-Device: Identity cache records are never synchronized to external servers.
 */
open class CallerIdentityCache(protected val dbHelper: SafeCallDatabaseHelper? = null) {

    companion object {
        const val DEFAULT_TTL_MILLIS = 14L * 24L * 60L * 60L * 1000L // 14 days
    }

    /**
     * Retrieves an unexpired cached caller identity, or null if missing or expired.
     */
    open suspend fun get(normalizedNumber: String): CallerIdentityResult? = withContext(Dispatchers.IO) {
        if (normalizedNumber.isBlank() || dbHelper == null) return@withContext null

        val db = dbHelper.readableDatabase
        val currentTime = System.currentTimeMillis()

        val cursor: Cursor = db.query(
            SafeCallDatabaseHelper.TABLE_IDENTITY_CACHE,
            null,
            "${SafeCallDatabaseHelper.COL_NORMALIZED} = ?",
            arrayOf(normalizedNumber),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val expiresAt = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_EXPIRES_AT))
                if (expiresAt <= currentTime) {
                    // Expired entry - clean up asynchronously
                    evict(normalizedNumber)
                    return@withContext null
                }

                val sourceStr = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IDENTITY_SOURCE))
                val typeStr = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IDENTITY_TYPE))
                val confStr = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IDENTITY_CONFIDENCE))

                CallerIdentityResult(
                    rawNumber = normalizedNumber,
                    normalizedNumber = normalizedNumber,
                    displayName = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IDENTITY_DISPLAY_NAME)),
                    source = try { CallerIdentitySource.valueOf(sourceStr) } catch (_: Exception) { CallerIdentitySource.LOCAL_CACHE },
                    identityType = try { CallerIdentityType.valueOf(typeStr) } catch (_: Exception) { CallerIdentityType.UNKNOWN },
                    confidence = try { IdentityConfidence.valueOf(confStr) } catch (_: Exception) { IdentityConfidence.MEDIUM },
                    sourceLabel = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IDENTITY_SOURCE_LABEL)),
                    isVerified = true,
                    organizationName = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IDENTITY_ORG)),
                    category = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_CATEGORY)),
                    attribution = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_IDENTITY_ATTRIBUTION)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_TIMESTAMP))
                )
            } else {
                null
            }
        }
    }

    /**
     * Caches a resolved caller identity.
     * Saved contacts are explicitly excluded to respect real-time address book state.
     */
    open suspend fun put(result: CallerIdentityResult, ttlMillis: Long = DEFAULT_TTL_MILLIS): Boolean = withContext(Dispatchers.IO) {
        // Do not cache blank names, unknown results, or local saved contacts
        if (!result.hasReliableName || result.source == CallerIdentitySource.SAVED_CONTACT || dbHelper == null) {
            return@withContext false
        }

        val db = dbHelper.writableDatabase
        val currentTime = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(SafeCallDatabaseHelper.COL_NORMALIZED, result.normalizedNumber)
            put(SafeCallDatabaseHelper.COL_IDENTITY_DISPLAY_NAME, result.displayName ?: "")
            put(SafeCallDatabaseHelper.COL_IDENTITY_SOURCE, result.source.name)
            put(SafeCallDatabaseHelper.COL_IDENTITY_TYPE, result.identityType.name)
            put(SafeCallDatabaseHelper.COL_IDENTITY_CONFIDENCE, result.confidence.name)
            put(SafeCallDatabaseHelper.COL_IDENTITY_SOURCE_LABEL, result.sourceLabel)
            put(SafeCallDatabaseHelper.COL_IDENTITY_ORG, result.organizationName)
            put(SafeCallDatabaseHelper.COL_CATEGORY, result.category)
            put(SafeCallDatabaseHelper.COL_IDENTITY_ATTRIBUTION, result.attribution)
            put(SafeCallDatabaseHelper.COL_TIMESTAMP, currentTime)
            put(SafeCallDatabaseHelper.COL_EXPIRES_AT, currentTime + ttlMillis)
        }

        val rowId = db.insertWithOnConflict(
            SafeCallDatabaseHelper.TABLE_IDENTITY_CACHE,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        rowId >= 0
    }

    /**
     * Removes an entry from the cache.
     */
    open suspend fun evict(normalizedNumber: String): Boolean = withContext(Dispatchers.IO) {
        if (dbHelper == null) return@withContext false
        val db = dbHelper.writableDatabase
        db.delete(
            SafeCallDatabaseHelper.TABLE_IDENTITY_CACHE,
            "${SafeCallDatabaseHelper.COL_NORMALIZED} = ?",
            arrayOf(normalizedNumber)
        ) > 0
    }

    /**
     * Clears all cached identities.
     */
    open suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        if (dbHelper == null) return@withContext false
        val db = dbHelper.writableDatabase
        db.delete(SafeCallDatabaseHelper.TABLE_IDENTITY_CACHE, null, null) >= 0
    }
}
