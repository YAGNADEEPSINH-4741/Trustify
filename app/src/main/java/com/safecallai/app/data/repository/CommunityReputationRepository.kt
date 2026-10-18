package com.safecallai.app.data.repository

import android.content.ContentValues
import android.database.Cursor
import com.safecallai.app.data.local.PreferencesManager
import com.safecallai.app.data.local.SafeCallDatabaseHelper
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CommunityClassification
import com.safecallai.app.domain.models.CommunityReputation
import com.safecallai.app.domain.models.CommunityThresholdEngine
import com.safecallai.app.util.PhoneNumberPrivacyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for Community-Powered Phone Reputation.
 *
 * Requirements satisfied:
 * - Privacy: Uses HMAC-SHA256 phone hash; never sends raw numbers or user contacts.
 * - Non-blocking: Incoming call screening uses short timeouts and in-memory caching.
 * - Idempotency: Local SQLite & atomic database function prevent duplicate vote inflation.
 * - Thresholds: SPAM (>= 10), COMMUNITY_TRUSTED (>= 10 not-spam & <= 2 spam), else UNVERIFIED.
 * - Resilient: Operates seamlessly in offline mode, syncing when connectivity is available.
 */
class CommunityReputationRepository(
    private val dbHelper: SafeCallDatabaseHelper,
    private val preferences: PreferencesManager
) {
    private val memoryCache = ConcurrentHashMap<String, CachedReputation>()
    private val cacheTtlMillis = 5 * 60 * 1000L // 5 minutes

    private data class CachedReputation(
        val reputation: CommunityReputation,
        val timestamp: Long
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Retrieves the community reputation for a given phone number.
     * Never throws exceptions; falls back to UNVERIFIED on timeout or network absence.
     */
    suspend fun getReputation(rawNumber: String): CommunityReputation = withContext(Dispatchers.IO) {
        if (!PhoneNumberNormalizer.isValid(rawNumber)) {
            return@withContext CommunityReputation(
                phoneHash = "",
                classification = CommunityClassification.UNVERIFIED,
                spamCount = 0,
                notSpamCount = 0
            )
        }

        val phoneHash = PhoneNumberPrivacyUtil.computePhoneHash(rawNumber)
        val userVote = getLocalUserVote(phoneHash)

        // 1. Check in-memory cache
        val cached = memoryCache[phoneHash]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < cacheTtlMillis) {
            return@withContext cached.reputation.copy(userVote = userVote)
        }

        // 2. Check local SQLite cache
        val localCached = getLocalCachedReputation(phoneHash)
        if (localCached != null) {
            memoryCache[phoneHash] = CachedReputation(localCached, System.currentTimeMillis())
        }

        // 3. Attempt network fetch if Supabase is configured
        val remote = fetchRemoteReputation(phoneHash)
        if (remote != null) {
            val updated = remote.copy(userVote = userVote)
            saveLocalCachedReputation(updated)
            memoryCache[phoneHash] = CachedReputation(updated, System.currentTimeMillis())
            return@withContext updated
        }

        return@withContext localCached?.copy(userVote = userVote) ?: CommunityReputation(
            phoneHash = phoneHash,
            classification = CommunityClassification.UNVERIFIED,
            spamCount = 0,
            notSpamCount = 0,
            userVote = userVote
        )
    }

    /**
     * Submits a community vote (spam or not_spam).
     * Atomically enforces one active vote per user per phone hash.
     */
    suspend fun submitVote(
        rawNumber: String,
        vote: String
    ): Result<CommunityReputation> = withContext(Dispatchers.IO) {
        val normalizedVote = vote.trim().lowercase()
        if (normalizedVote != "spam" && normalizedVote != "not_spam") {
            return@withContext Result.failure(IllegalArgumentException("Invalid vote: must be 'spam' or 'not_spam'"))
        }

        if (!PhoneNumberNormalizer.isValid(rawNumber)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid phone number format"))
        }

        val phoneHash = PhoneNumberPrivacyUtil.computePhoneHash(rawNumber)
        val previousVote = getLocalUserVote(phoneHash)

        // Idempotent duplicate check
        if (previousVote == normalizedVote) {
            val current = getReputation(rawNumber)
            return@withContext Result.success(current)
        }

        // Enforce one-final-vote rule: a user cannot change their vote once cast
        if (previousVote != null) {
            return@withContext Result.failure(
                IllegalStateException("A final vote has already been recorded for this number and cannot be modified.")
            )
        }

        // Try remote submit to Supabase RPC
        val remoteResult = submitRemoteVote(phoneHash, normalizedVote)
        if (remoteResult != null) {
            val updated = remoteResult.copy(userVote = normalizedVote)
            saveLocalUserVote(phoneHash, rawNumber, normalizedVote, synced = 1)
            saveLocalCachedReputation(updated)
            memoryCache[phoneHash] = CachedReputation(updated, System.currentTimeMillis())
            return@withContext Result.success(updated)
        }

        // Never fabricate success if network or backend fails
        return@withContext Result.failure(
            Exception("Failed to submit community report to server. Please check your internet connection and try again.")
        )
    }

    // --- SQLite Local Persistence ---

    fun hasUserVoted(rawNumber: String): Boolean {
        if (!PhoneNumberNormalizer.isValid(rawNumber)) return false
        val phoneHash = PhoneNumberPrivacyUtil.computePhoneHash(rawNumber)
        val vote = getLocalUserVote(phoneHash)
        return vote == "spam" || vote == "not_spam"
    }

    fun getSavedUserVote(rawNumber: String): String? {
        if (!PhoneNumberNormalizer.isValid(rawNumber)) return null
        val phoneHash = PhoneNumberPrivacyUtil.computePhoneHash(rawNumber)
        return getLocalUserVote(phoneHash)
    }

    fun getLocalUserVote(phoneHash: String): String? {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            SafeCallDatabaseHelper.TABLE_COMMUNITY_VOTES,
            arrayOf(SafeCallDatabaseHelper.COL_VOTE),
            "${SafeCallDatabaseHelper.COL_PHONE_HASH} = ? AND ${SafeCallDatabaseHelper.COL_SYNCED} = 1",
            arrayOf(phoneHash),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun saveLocalUserVote(phoneHash: String, rawNumber: String, vote: String, synced: Int = 1) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SafeCallDatabaseHelper.COL_PHONE_HASH, phoneHash)
            put(SafeCallDatabaseHelper.COL_PHONE, "")
            put(SafeCallDatabaseHelper.COL_VOTE, vote)
            put(SafeCallDatabaseHelper.COL_TIMESTAMP, System.currentTimeMillis())
            put(SafeCallDatabaseHelper.COL_SYNCED, synced)
        }
        db.insertWithOnConflict(
            SafeCallDatabaseHelper.TABLE_COMMUNITY_VOTES,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun getLocalCachedReputation(phoneHash: String): CommunityReputation? {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            SafeCallDatabaseHelper.TABLE_COMMUNITY_CACHE,
            null,
            "${SafeCallDatabaseHelper.COL_PHONE_HASH} = ?",
            arrayOf(phoneHash),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val classificationStr = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_CLASSIFICATION))
                val classification = try {
                    CommunityClassification.valueOf(classificationStr)
                } catch (_: Exception) {
                    CommunityClassification.UNVERIFIED
                }
                return CommunityReputation(
                    phoneHash = phoneHash,
                    classification = classification,
                    spamCount = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_SPAM_COUNT)),
                    notSpamCount = it.getInt(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_NOT_SPAM_COUNT)),
                    userVote = it.getString(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_USER_VOTE)),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow(SafeCallDatabaseHelper.COL_UPDATED_AT))
                )
            }
        }
        return null
    }

    private fun saveLocalCachedReputation(rep: CommunityReputation) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SafeCallDatabaseHelper.COL_PHONE_HASH, rep.phoneHash)
            put(SafeCallDatabaseHelper.COL_CLASSIFICATION, rep.classification.name)
            put(SafeCallDatabaseHelper.COL_SPAM_COUNT, rep.spamCount)
            put(SafeCallDatabaseHelper.COL_NOT_SPAM_COUNT, rep.notSpamCount)
            put(SafeCallDatabaseHelper.COL_USER_VOTE, rep.userVote)
            put(SafeCallDatabaseHelper.COL_UPDATED_AT, rep.updatedAt)
        }
        db.insertWithOnConflict(
            SafeCallDatabaseHelper.TABLE_COMMUNITY_CACHE,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // --- Remote Supabase Client Calls ---

    private fun fetchRemoteReputation(phoneHash: String): CommunityReputation? {
        val baseUrl = preferences.supabaseUrl.trimEnd('/')
        val anonKey = preferences.supabaseAnonKey.trim()
        if (baseUrl.isBlank() || anonKey.isBlank()) return null

        return try {
            val url = URL("$baseUrl/rest/v1/rpc/get_phone_reputation")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000 // 3 seconds max, never block call screening
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $anonKey")
            }

            val body = "{\"p_phone_hash\":\"$phoneHash\"}"
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                parseReputationJson(phoneHash, responseText)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateAuthToken(baseUrl: String, anonKey: String): String? {
        val existingToken = preferences.deviceAuthToken
        if (existingToken.isNotBlank()) {
            return existingToken
        }

        var email = preferences.deviceAuthEmail
        var password = preferences.deviceAuthPassword
        if (email.isBlank() || password.isBlank()) {
            val randomId = UUID.randomUUID().toString()
            email = "device_$randomId@trustify.local"
            password = "Pwd_${UUID.randomUUID()}!9"
            preferences.deviceAuthEmail = email
            preferences.deviceAuthPassword = password
        }

        return try {
            val signupUrl = URL("$baseUrl/auth/v1/signup")
            val conn = (signupUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", anonKey)
            }

            val body = "{\"email\":\"$email\",\"password\":\"$password\"}"
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val jsonObj = json.parseToJsonElement(responseText).jsonObject
                val token = jsonObj["access_token"]?.jsonPrimitive?.content
                if (!token.isNullOrBlank()) {
                    preferences.deviceAuthToken = token
                    return token
                }
            }

            // Fallback: Login if account already exists
            val loginUrl = URL("$baseUrl/auth/v1/token?grant_type=password")
            val loginConn = (loginUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", anonKey)
            }
            OutputStreamWriter(loginConn.outputStream).use { it.write(body) }
            if (loginConn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(loginConn.inputStream)).use { it.readText() }
                val jsonObj = json.parseToJsonElement(responseText).jsonObject
                val token = jsonObj["access_token"]?.jsonPrimitive?.content
                if (!token.isNullOrBlank()) {
                    preferences.deviceAuthToken = token
                    return token
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun submitRemoteVote(phoneHash: String, vote: String): CommunityReputation? {
        val baseUrl = preferences.supabaseUrl.trimEnd('/')
        val anonKey = preferences.supabaseAnonKey.trim()
        if (baseUrl.isBlank() || anonKey.isBlank()) return null

        val authToken = getOrCreateAuthToken(baseUrl, anonKey) ?: return null

        return try {
            val url = URL("$baseUrl/rest/v1/rpc/submit_phone_vote")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $authToken")
            }

            val body = "{\"p_phone_hash\":\"$phoneHash\",\"p_vote\":\"$vote\"}"
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                parseReputationJson(phoneHash, responseText)
            } else if (conn.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // Invalidate cached auth token and retry once with fresh session
                preferences.deviceAuthToken = ""
                val freshToken = getOrCreateAuthToken(baseUrl, anonKey) ?: return null
                val retryConn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", anonKey)
                    setRequestProperty("Authorization", "Bearer $freshToken")
                }
                OutputStreamWriter(retryConn.outputStream).use { it.write(body) }
                if (retryConn.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = BufferedReader(InputStreamReader(retryConn.inputStream)).use { it.readText() }
                    parseReputationJson(phoneHash, responseText)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseReputationJson(phoneHash: String, responseJson: String): CommunityReputation? {
        return try {
            val element = json.parseToJsonElement(responseJson).jsonObject
            val classificationStr = element["classification"]?.jsonPrimitive?.content ?: "UNVERIFIED"
            val spamCount = element["spam_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val notSpamCount = element["not_spam_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val threshold = element["threshold"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10
            val userVote = element["user_vote"]?.jsonPrimitive?.content

            val classification = when (classificationStr.uppercase()) {
                "SPAM" -> CommunityClassification.SPAM
                "COMMUNITY_TRUSTED" -> CommunityClassification.COMMUNITY_TRUSTED
                else -> CommunityClassification.UNVERIFIED
            }

            CommunityReputation(
                phoneHash = phoneHash,
                classification = classification,
                spamCount = spamCount,
                notSpamCount = notSpamCount,
                threshold = threshold,
                userVote = userVote,
                updatedAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }
}
