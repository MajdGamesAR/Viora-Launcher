package com.viora.launcher.core.auth.microsoft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class XboxAuth {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // ===== Xbox Live Auth =====

    @Serializable
    private data class XblAuthRequest(
        val Properties: XblProperties,
        val RelyingParty: String = "http://auth.xboxlive.com",
        val TokenType: String = "JWT"
    )

    @Serializable
    private data class XblProperties(
        val AuthMethod: String = "RPS",
        val SiteName: String = "user.auth.xboxlive.com",
        val RpsTicket: String
    )

    @Serializable
    data class XblResponse(
        val Token: String,
        val DisplayClaims: DisplayClaimsData
    )

    @Serializable
    data class DisplayClaimsData(val xui: List<XuiEntry>)

    @Serializable
    data class XuiEntry(val uhs: String)

    // ===== XSTS Auth =====

    @Serializable
    private data class XstsAuthRequest(
        val Properties: XstsProperties,
        val RelyingParty: String = "rp://api.minecraftservices.com/",
        val TokenType: String = "JWT"
    )

    @Serializable
    private data class XstsProperties(
        val SandboxId: String = "RETAIL",
        val UserTokens: List<String>
    )

    @Serializable
    data class XstsError(
        val Identity: String? = null,
        val XErr: Long? = null,
        val Message: String? = null
    )

    // ===== Public API =====

    suspend fun authenticateWithXbl(msAccessToken: String): XblResponse {
        println("🔵 [Xbox] Authenticating XBL...")
        println("🔵 [Xbox] RpsTicket: d=${msAccessToken.take(20)}...")

        // Build the JSON request manually to ensure correct format
        val requestBody = """
            {
                "Properties": {
                    "AuthMethod": "RPS",
                    "SiteName": "user.auth.xboxlive.com",
                    "RpsTicket": "d=$msAccessToken"
                },
                "RelyingParty": "http://auth.xboxlive.com",
                "TokenType": "JWT"
            }
        """.trimIndent()

        println("📤 [Xbox] Request body:")
        println(requestBody)

        val response = client.post("https://user.auth.xboxlive.com/user/authenticate") {
            contentType(ContentType.Application.Json)
            setBody(TextContent(requestBody, ContentType.Application.Json))
        }

        val statusCode = response.status.value
        val rawResponse = response.bodyAsText()

        println("📥 [Xbox] Status: $statusCode")
        println("📥 [Xbox] Raw response:")
        println(rawResponse)

        if (statusCode != 200) {
            throw Exception("Xbox Live returned HTTP $statusCode:\n$rawResponse")
        }

        val json = Json { ignoreUnknownKeys = true }
        return try {
            json.decodeFromString<XblResponse>(rawResponse)
        } catch (e: Exception) {
            println("❌ [Xbox] Failed to parse XBL response: ${e.message}")
            throw Exception("Xbox Live returned unexpected response:\n$rawResponse")
        }
    }

    suspend fun authorizeWithXsts(xblToken: String): XblResponse {
        println("🔵 [Xbox] Authorizing XSTS...")

        val requestBody = """
            {
                "Properties": {
                    "SandboxId": "RETAIL",
                    "UserTokens": ["$xblToken"]
                },
                "RelyingParty": "rp://api.minecraftservices.com/",
                "TokenType": "JWT"
            }
        """.trimIndent()

        println("📤 [Xbox] XSTS request body:")
        println(requestBody)

        val response = client.post("https://xsts.auth.xboxlive.com/xsts/authorize") {
            contentType(ContentType.Application.Json)
            setBody(TextContent(requestBody, ContentType.Application.Json))
        }

        val statusCode = response.status.value
        val rawResponse = response.bodyAsText()

        println("📥 [Xbox] XSTS Status: $statusCode")
        println("📥 [Xbox] XSTS Raw response:")
        println(rawResponse)

        if (statusCode != 200) {
            // Try to parse XSTS error for better message
            val json = Json { ignoreUnknownKeys = true }
            val errorInfo = try {
                json.decodeFromString<XstsError>(rawResponse)
            } catch (e: Exception) {
                null
            }

            val errorMessage = when (errorInfo?.XErr) {
                2148916233L -> "This Microsoft account doesn't have an Xbox account. Please sign in at https://xbox.com to create one."
                2148916235L -> "Xbox Live is not available in your country. Please use a VPN."
                2148916238L -> "This account is under 18. It must be added to a Family by an adult."
                else -> "XSTS authorization failed: HTTP $statusCode\n$rawResponse"
            }

            println("❌ [Xbox] $errorMessage")
            throw Exception(errorMessage)
        }

        val json = Json { ignoreUnknownKeys = true }
        return try {
            json.decodeFromString<XblResponse>(rawResponse)
        } catch (e: Exception) {
            println("❌ [Xbox] Failed to parse XSTS response: ${e.message}")
            throw Exception("XSTS returned unexpected response:\n$rawResponse")
        }
    }
}