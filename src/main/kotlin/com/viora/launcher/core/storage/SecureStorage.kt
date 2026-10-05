package com.viora.launcher.core.storage

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class SecureStorage {

    private val json = Json { prettyPrint = true }

    private val storageFile: File = File(
        System.getProperty("user.home"),
        ".viora-launcher/secure.json"
    ).apply { parentFile?.mkdirs() }

    private val secretKey: SecretKeySpec by lazy {
        val machineId = System.getProperty("user.name") + "::" +
                System.getProperty("os.name") + "::" +
                System.getProperty("user.home") + "::viora-key"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(machineId.toByteArray())
        SecretKeySpec(digest, "AES")
    }

    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun loadAll(): MutableMap<String, String> {
        if (!storageFile.exists()) {
            return HashMap()
        }
        return try {
            val text = storageFile.readText()
            if (text.isBlank()) {
                return HashMap()
            }
            val map: Map<String, String> = json.decodeFromString(mapSerializer, text)
            HashMap(map)
        } catch (e: Exception) {
            HashMap()
        }
    }

    private fun saveAll(data: Map<String, String>) {
        storageFile.writeText(json.encodeToString(mapSerializer, data))
    }

    fun saveToken(key: String, token: String) {
        val all = loadAll()
        all[key] = encrypt(token)
        saveAll(all)
    }

    fun getToken(key: String): String? {
        val all = loadAll()
        val encrypted = all[key] ?: return null
        return try {
            decrypt(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteToken(key: String) {
        val all = loadAll()
        all.remove(key)
        saveAll(all)
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun decrypt(cipherText: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText))
        return String(decrypted, Charsets.UTF_8)
    }
}