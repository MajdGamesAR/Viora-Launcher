package com.viora.launcher.core.auth.microsoft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Microsoft OAuth 2.0 - Device Code Flow
 */
class MicrosoftAuth {

    companion object {
        // ✅ Your Client ID from Entra
        private const val CLIENT_ID = "f3cc84dc-bb4c-414b-8d77-10ee8081aedc"
        private const val SCOPE = "XboxLive.signin offline_access"
        private const val DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        private const val TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int = 5,
        val message: String = ""
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    suspend fun requestDeviceCode(): DeviceCodeResponse {
        println("🔵 [Microsoft] Requesting device code...")

        // Read raw response first
        val rawResponse: String = client.submitForm(
            url = DEVICE_CODE_URL,
            formParameters = Parameters.build {
                append("client_id", CLIENT_ID)
                append("scope", SCOPE)
            }
        ).bodyAsText()

        // Print raw response to see what Microsoft actually returns
        println("📥 [Microsoft] Raw response:")
        println(rawResponse)

        // Try to parse it manually
        val json = Json { ignoreUnknownKeys = true }
        return try {
            val response = json.decodeFromString<DeviceCodeResponse>(rawResponse)
            println("✅ [Microsoft] Device Code: ${response.userCode}")
            println("🔗 [Microsoft] URL: ${response.verificationUri}")
            response
        } catch (e: Exception) {
            println("❌ [Microsoft] Failed to parse response: ${e.message}")
            throw Exception("Microsoft returned an unexpected response:\n$rawResponse")
        }
    }

    suspend fun pollForToken(
        deviceCode: String,
        intervalSeconds: Int = 5,
        maxAttempts: Int = 120
    ): TokenResponse {
        println("⏳ [Microsoft] Starting token polling...")
        repeat(maxAttempts) { attempt ->
            delay(intervalSeconds * 1000L)
            println("🔄 [Microsoft] Attempt ${attempt + 1}/$maxAttempts")

            val response: TokenResponse = client.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("client_id", CLIENT_ID)
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    append("device_code", deviceCode)
                }
            ).body()

            when (response.error) {
                null -> {
                    println("✅ [Microsoft] Token received successfully!")
                    return response
                }
                "authorization_pending" -> {
                    println("⏸️ [Microsoft] Waiting for user approval...")
                }
                "slow_down" -> {
                    println("🐢 [Microsoft] slow_down - increasing interval")
                }
                else -> {
                    val errorMsg = "❌ [Microsoft] Failed: ${response.error} - ${response.errorDescription}"
                    println(errorMsg)
                    throw Exception(errorMsg)
                }
            }
        }
        throw Exception("❌ [Microsoft] Timeout - user did not approve")
    }

    suspend fun refreshToken(refreshToken: String): TokenResponse {
        println("🔄 [Microsoft] Refreshing token...")
        return client.submitForm(
            url = TOKEN_URL,
            formParameters = Parameters.build {
                append("client_id", CLIENT_ID)
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("scope", SCOPE)
            }
        ).body()
    }
}