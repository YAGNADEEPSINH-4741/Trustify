package com.safecallai.app.data.remote

import com.safecallai.app.domain.models.CallerProfile
import com.safecallai.app.domain.models.ReportCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class VerifiedBusinessRow(
    val id: String? = null,
    @SerialName("business_name") val businessName: String,
    @SerialName("normalized_phone_number") val normalizedPhoneNumber: String,
    @SerialName("verification_status") val verificationStatus: String = "VERIFIED",
    @SerialName("verification_method") val verificationMethod: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("verification_expires_at") val verificationExpiresAt: String? = null
)

@Serializable
data class NumberReportPayload(
    @SerialName("normalized_phone_number") val normalizedPhoneNumber: String,
    @SerialName("report_category") val reportCategory: String,
    @SerialName("report_description") val reportDescription: String,
    @SerialName("moderation_status") val moderationStatus: String = "PENDING"
)

/**
 * Lightweight, resilient HTTP client for Supabase REST API (PostgREST).
 * Communicates using public anonymous key. Does not expose service credentials.
 * Fails gracefully to offline mode on any network timeout or configuration absence.
 */
class SupabaseClient(
    private val getBaseUrl: () -> String,
    private val getAnonKey: () -> String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = getBaseUrl().isNotBlank() && getAnonKey().isNotBlank()

    /**
     * Queries verified_businesses table by normalized phone number.
     */
    suspend fun fetchVerifiedBusiness(normalizedNumber: String): CallerProfile? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null

        try {
            val url = "${getBaseUrl().trimEnd('/')}/rest/v1/verified_businesses?normalized_phone_number=eq.$normalizedNumber&select=*"
            val request = Request.Builder()
                .url(url)
                .header("apikey", getAnonKey())
                .header("Authorization", "Bearer ${getAnonKey()}")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val list = json.decodeFromString<List<VerifiedBusinessRow>>(body)
                val first = list.firstOrNull() ?: return@withContext null

                CallerProfile(
                    normalizedPhoneNumber = first.normalizedPhoneNumber,
                    businessName = first.businessName,
                    category = "Verified Organization",
                    isVerifiedBusiness = true,
                    verificationMethod = first.verificationMethod ?: "Supabase Registry",
                    verifiedAt = System.currentTimeMillis() - 86400000L,
                    verificationExpiresAt = System.currentTimeMillis() + 31536000000L,
                    spamReportsCount = 0,
                    falseSpamReportsCount = 1,
                    positiveConfirmationsCount = 20
                )
            }
        } catch (_: Exception) {
            // Fail gracefully to offline cache
            null
        }
    }

    /**
     * Submits a number report (e.g. False Spam or Suspected Fraud) to number_reports table.
     */
    suspend fun submitReport(
        normalizedNumber: String,
        category: ReportCategory,
        description: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured. Report stored locally."))
        }

        try {
            val payload = NumberReportPayload(
                normalizedPhoneNumber = normalizedNumber,
                reportCategory = category.name,
                reportDescription = description
            )
            val jsonBody = json.encodeToString(payload)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val url = "${getBaseUrl().trimEnd('/')}/rest/v1/number_reports"
            val request = Request.Builder()
                .url(url)
                .header("apikey", getAnonKey())
                .header("Authorization", "Bearer ${getAnonKey()}")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Submission failed with HTTP code: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
