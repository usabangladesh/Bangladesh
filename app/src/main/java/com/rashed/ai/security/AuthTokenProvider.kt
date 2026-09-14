package com.rashed.ai.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Interface for providing ephemeral authentication tokens for Gemini Live.
 * In production architectures, clients obtain short-lived tokens from a secure backend
 * rather than hardcoding permanent API keys in the APK.
 */
interface AuthTokenProvider {
    suspend fun getEphemeralToken(): String
    fun isDevelopmentMode(): Boolean
}

/**
 * Production implementation that fetches an ephemeral Gemini session token from a secure backend.
 */
class BackendAuthTokenProvider(
    private val tokenEndpointUrl: String,
    private val client: OkHttpClient = OkHttpClient()
) : AuthTokenProvider {

    override suspend fun getEphemeralToken(): String = withContext(Dispatchers.IO) {
        if (tokenEndpointUrl.isBlank()) {
            throw IllegalStateException("Token endpoint URL is not configured.")
        }
        val request = Request.Builder()
            .url(tokenEndpointUrl)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Backend token request failed: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response body from token endpoint")
            val json = JSONObject(body)
            // Expecting {"token": "..."} or {"ephemeralToken": "..."}
            json.optString("token", json.optString("ephemeralToken", ""))
        }
    }

    override fun isDevelopmentMode(): Boolean = false
}

/**
 * Isolated development configuration for local testing with BuildConfig / Secrets.
 * Strictly separates development keys from production credentials.
 */
class LocalDevAuthTokenProvider(
    private val devApiKey: String
) : AuthTokenProvider {

    override suspend fun getEphemeralToken(): String = withContext(Dispatchers.IO) {
        if (devApiKey.isBlank() || devApiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel or provide a backend token URL.")
        }
        devApiKey
    }

    override fun isDevelopmentMode(): Boolean = true
}
