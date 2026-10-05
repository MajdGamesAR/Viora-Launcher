package com.viora.launcher.core.auth.viora

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ⚠️ يحتاج backend خاص بك
 * للبدء: نستخدم Offline Mode فقط بدون هذا الـ client
 */
class VioraApiClient {

    // غيّرها لاحقاً لسيرفرك
    private val baseUrl = "https://api.vioralauncher.net/v1"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Serializable
    data class RegisterRequest(
        val username: String,
        val password: String,
        val email: String? = null
    )

    @Serializable
    data class LoginRequest(
        val username: String,
        val password: String
    )

    @Serializable
    data class AuthResponse(
        val success: Boolean,
        val accessToken: String? = null,
        val uuid: String? = null,
        val username: String? = null,
        val error: String? = null
    )

    suspend fun register(request: RegisterRequest): AuthResponse {
        return try {
            client.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            AuthResponse(success = false, error = "خطأ في الاتصال: ${e.message}")
        }
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        return try {
            client.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            AuthResponse(success = false, error = "خطأ في الاتصال: ${e.message}")
        }
    }
}