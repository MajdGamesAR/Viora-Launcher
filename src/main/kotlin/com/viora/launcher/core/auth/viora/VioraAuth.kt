package com.viora.launcher.core.auth.viora

import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.auth.model.AccountType
import java.security.MessageDigest
import java.util.UUID

class VioraAuth(private val apiClient: VioraApiClient? = null) {

    /**
     * وضع Offline — لا يحتاج سيرفر
     */
    fun offlineLogin(username: String): Account {
        require(username.matches(Regex("^[a-zA-Z0-9_]{3,16}$"))) {
            "الاسم يجب أن يكون 3-16 حرفاً إنجليزياً/أرقام/شرطة سفلية"
        }
        val uuid = generateOfflineUuid(username)
        return Account(
            id = UUID.randomUUID().toString(),
            username = username,
            uuid = uuid,
            type = AccountType.VIORA,
            accessToken = "offline",
            expiresAt = Long.MAX_VALUE
        )
    }

    /**
     * وضع Online — مع سيرفر Viora
     */
    suspend fun onlineLogin(username: String, password: String): Account? {
        val api = apiClient ?: return null
        val response = api.login(VioraApiClient.LoginRequest(username, password))
        return if (response.success && response.uuid != null) {
            Account(
                id = UUID.randomUUID().toString(),
                username = response.username ?: username,
                uuid = response.uuid,
                type = AccountType.VIORA,
                accessToken = response.accessToken ?: "online",
                expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            )
        } else null
    }

    /**
     * توليد UUID بنفس طريقة Minecraft offline mode
     */
    private fun generateOfflineUuid(username: String): String {
        val data = "OfflinePlayer:$username".toByteArray(Charsets.UTF_8)
        val md5 = MessageDigest.getInstance("MD5").digest(data)
        md5[6] = (md5[6].toInt() and 0x0F or 0x30).toByte()
        md5[8] = (md5[8].toInt() and 0x3F or 0x80).toByte()

        val hex = md5.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
               "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}