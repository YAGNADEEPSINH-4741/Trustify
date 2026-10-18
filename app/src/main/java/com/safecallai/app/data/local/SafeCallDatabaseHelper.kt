package com.safecallai.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.domain.models.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust local SQLite storage for SafeCall AI.
 * Stores trusted numbers, screened call logs, and cached verified profiles offline.
 */
class SafeCallDatabaseHelper(val context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "safecall_local.db"
        const val DATABASE_VERSION = 3

        // Tables
        const val TABLE_TRUSTED = "user_trusted_callers"
        const val TABLE_HISTORY = "screened_call_history"
        const val TABLE_CACHE = "cached_verified_businesses"

        // Common
        const val COL_ID = "id"
        const val COL_PHONE = "phone_number"
        const val COL_NORMALIZED = "normalized_phone_number"
        const val COL_TIMESTAMP = "timestamp"

        // Trusted
        const val COL_LABEL = "custom_label"
        const val COL_NOTES = "notes"

        // History
        const val COL_CLASSIFICATION = "classification"
        const val COL_VERIFICATION_STATUS = "verification_status"
        const val COL_CALLER_NAME = "caller_name"
        const val COL_REASON = "reason"
        const val COL_WAS_SILENCED = "was_silenced"
        const val COL_FALSE_SPAM = "false_spam_recovered"

        // Cache
        const val COL_BUSINESS_NAME = "business_name"
        const val COL_CATEGORY = "category"
        const val COL_METHOD = "verification_method"
        const val COL_VERIFIED_AT = "verified_at"
        const val COL_EXPIRES_AT = "expires_at"
        const val COL_SPAM_COUNT = "spam_reports_count"
        const val COL_FALSE_SPAM_COUNT = "false_spam_reports_count"
        const val COL_CONFIRMATIONS = "positive_confirmations_count"
        const val COL_IMPERSONATION = "is_impersonation_reported"

        // Reports / Feedback Drafts
        const val TABLE_REPORTS = "user_number_reports"
        const val COL_REPORT_CATEGORY = "report_category"
        const val COL_REPORT_DESC = "description"

        // Community Votes & Reputation
        const val TABLE_COMMUNITY_VOTES = "user_community_votes"
        const val COL_PHONE_HASH = "phone_hash"
        const val COL_VOTE = "vote"
        const val COL_SYNCED = "synced_to_cloud"

        const val TABLE_COMMUNITY_CACHE = "cached_community_reputation"
        const val COL_NOT_SPAM_COUNT = "not_spam_reports_count"
        const val COL_USER_VOTE = "user_vote"
        const val COL_UPDATED_AT = "updated_at"

        // Caller Identity Cache (Independent of spam reputation)
        const val TABLE_IDENTITY_CACHE = "caller_identity_cache"
        const val COL_IDENTITY_DISPLAY_NAME = "display_name"
        const val COL_IDENTITY_SOURCE = "source"
        const val COL_IDENTITY_TYPE = "identity_type"
        const val COL_IDENTITY_CONFIDENCE = "confidence"
        const val COL_IDENTITY_SOURCE_LABEL = "source_label"
        const val COL_IDENTITY_ORG = "organization_name"
        const val COL_IDENTITY_ATTRIBUTION = "attribution"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRUSTED (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PHONE TEXT NOT NULL,
                $COL_NORMALIZED TEXT UNIQUE NOT NULL,
                $COL_LABEL TEXT NOT NULL,
                $COL_NOTES TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PHONE TEXT NOT NULL,
                $COL_NORMALIZED TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_CLASSIFICATION TEXT NOT NULL,
                $COL_VERIFICATION_STATUS TEXT NOT NULL,
                $COL_CALLER_NAME TEXT,
                $COL_REASON TEXT NOT NULL,
                $COL_WAS_SILENCED INTEGER DEFAULT 0,
                $COL_FALSE_SPAM INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CACHE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NORMALIZED TEXT UNIQUE NOT NULL,
                $COL_BUSINESS_NAME TEXT NOT NULL,
                $COL_CATEGORY TEXT,
                $COL_METHOD TEXT,
                $COL_VERIFIED_AT INTEGER,
                $COL_EXPIRES_AT INTEGER,
                $COL_SPAM_COUNT INTEGER DEFAULT 0,
                $COL_FALSE_SPAM_COUNT INTEGER DEFAULT 0,
                $COL_CONFIRMATIONS INTEGER DEFAULT 0,
                $COL_IMPERSONATION INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_REPORTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PHONE TEXT NOT NULL,
                $COL_NORMALIZED TEXT NOT NULL,
                $COL_REPORT_CATEGORY TEXT NOT NULL,
                $COL_REPORT_DESC TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )

        createCommunityTables(db)
        createIdentityCacheTable(db)
    }

    private fun createCommunityTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_COMMUNITY_VOTES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PHONE_HASH TEXT UNIQUE NOT NULL,
                $COL_PHONE TEXT NOT NULL,
                $COL_VOTE TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_SYNCED INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_COMMUNITY_CACHE (
                $COL_PHONE_HASH TEXT PRIMARY KEY,
                $COL_CLASSIFICATION TEXT NOT NULL,
                $COL_SPAM_COUNT INTEGER NOT NULL,
                $COL_NOT_SPAM_COUNT INTEGER NOT NULL,
                $COL_USER_VOTE TEXT,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createIdentityCacheTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_IDENTITY_CACHE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NORMALIZED TEXT UNIQUE NOT NULL,
                $COL_IDENTITY_DISPLAY_NAME TEXT NOT NULL,
                $COL_IDENTITY_SOURCE TEXT NOT NULL,
                $COL_IDENTITY_TYPE TEXT NOT NULL,
                $COL_IDENTITY_CONFIDENCE TEXT NOT NULL,
                $COL_IDENTITY_SOURCE_LABEL TEXT NOT NULL,
                $COL_IDENTITY_ORG TEXT,
                $COL_CATEGORY TEXT,
                $COL_IDENTITY_ATTRIBUTION TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_EXPIRES_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createCommunityTables(db)
        }
        if (oldVersion < 3) {
            createIdentityCacheTable(db)
        }
    }
}
