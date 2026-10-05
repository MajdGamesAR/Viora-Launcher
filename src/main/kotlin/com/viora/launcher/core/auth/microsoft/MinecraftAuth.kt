package com.viora.launcher.core.auth.microsoft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MinecraftAuth {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Serializable
    private data class McLoginRequest(val identityToken: String)

    @Serializable
    data class McLoginResponse(
        val access_token: String,
        val expires_in: Int
    )

    @Serializable
    data class McProfile(
        val id: String,
        val name: String,
        val skins: List<Skin>? = null
    ) {
        @Serializable
        data class Skin(
            val id: String,
            val state: String,
            val url: String
        )
    }

    suspend fun loginWithXbox(userHash: String, xstsToken: String): McLoginResponse {
        println("🔵 [Minecraft] login_with_xbox...")
        val response = client.post("https://api.minecraftservices.com/authentication/login_with_xbox") {
            contentType(ContentType.Application.Json)
            setBody(McLoginRequest("XBL3.0 x=$userHash;$xstsToken"))
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val errorMsg = "❌ [Minecraft] فشل login_with_xbox — HTTP ${response.status.value}\n$errorBody"
            println(errorMsg)
            throw Exception(errorMsg)
        }

        println("✅ [Minecraft] Minecraft access token مستلم")
        return response.body()
    }

    suspend fun getProfile(mcAccessToken: String): McProfile? {
        println("🔵 [Minecraft] جلب Profile...")
        val response = client.get("https://api.minecraftservices.com/minecraft/profile") {
            header(HttpHeaders.Authorization, "Bearer $mcAccessToken")
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            println("❌ [Minecraft] فشل جلب Profile — HTTP ${response.status.value}\n$errorBody")
            return null
        }

        println("✅ [Minecraft] Profile مستلم")
        return response.body()
    }
}