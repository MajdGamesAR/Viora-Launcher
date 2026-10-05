package com.viora.launcher.core.auth.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType {
    MICROSOFT,
    VIORA
}

@Serializable
data class Account(
    val id: String,                    // UUID داخلي للـ launcher
    val username: String,              // اسم اللاعب
    val uuid: String,                  // Minecraft UUID
    val type: AccountType,
    val accessToken: String,
    val refreshToken: String? = null,  // MS فقط
    val expiresAt: Long,               // epoch millis
    val skinUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt

    val isMicrosoft: Boolean
        get() = type == AccountType.MICROSOFT

    val isViora: Boolean
        get() = type == AccountType.VIORA
}

@Serializable
data class AuthResult(
    val account: Account? = null,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = account != null && error == null
}