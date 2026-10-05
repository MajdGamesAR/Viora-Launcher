package com.viora.launcher.core.storage

import com.viora.launcher.core.auth.model.Account
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class AccountRepository(private val secureStorage: SecureStorage) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val accountsFile: File = File(
        System.getProperty("user.home"),
        ".viora-launcher/accounts.json"
    ).apply { parentFile?.mkdirs() }

    fun loadAll(): List<Account> {
        if (!accountsFile.exists()) return emptyList()
        return try {
            json.decodeFromString(
                ListSerializer(Account.serializer()),
                accountsFile.readText()
            )
        } catch (e: Exception) {
            println("⚠️ فشل قراءة الحسابات: ${e.message}")
            emptyList()
        }
    }

    fun saveAll(accounts: List<Account>) {
        accountsFile.writeText(
            json.encodeToString(ListSerializer(Account.serializer()), accounts)
        )
        accounts.forEach { acc ->
            secureStorage.saveToken("${acc.id}_access", acc.accessToken)
            acc.refreshToken?.let { secureStorage.saveToken("${acc.id}_refresh", it) }
        }
    }

    fun getAccessToken(account: Account): String? =
        secureStorage.getToken("${account.id}_access")

    fun delete(account: Account) {
        val remaining = loadAll().filterNot { it.id == account.id }
        saveAll(remaining)
        secureStorage.deleteToken("${account.id}_access")
        secureStorage.deleteToken("${account.id}_refresh")
    }
}