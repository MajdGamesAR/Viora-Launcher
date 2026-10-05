package com.viora.launcher.core.mods

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class CurseForgeService(private val apiKey: String) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    // ============================================================
    //  SEARCH
    // ============================================================
    suspend fun searchMods(
        query: String,
        gameVersion: String? = null,
        modLoaderType: Int? = null,
        classId: Int? = null,
        limit: Int = 20,
        index: Int = 0
    ): CurseForgeSearchResponse {
        if (!isConfigured()) {
            println("⚠️ [CF] API key not configured")
            return CurseForgeSearchResponse()
        }

        val response: HttpResponse = client.get("https://api.curseforge.com/v1/mods/search") {
            header("X-Api-Key", apiKey)
            header("Accept", "application/json")

            parameter("gameId", 432)
            parameter("searchFilter", query)
            parameter("pageSize", limit)
            parameter("index", index)
            parameter("sortField", 2)

            gameVersion?.let { parameter("gameVersion", it) }
            modLoaderType?.let { parameter("modLoaderType", it) }
            classId?.let { parameter("classId", it) }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            println("❌ [CF] HTTP ${response.status.value}: $errorBody")
            return CurseForgeSearchResponse()
        }

        return json.decodeFromString<CurseForgeSearchResponse>(response.bodyAsText())
    }

    // ============================================================
    //  GET FILES FOR MOD
    // ============================================================
    suspend fun getModFiles(
        modId: Int,
        gameVersion: String? = null,
        modLoaderType: Int? = null
    ): List<CurseForgeFile> {
        if (!isConfigured()) return emptyList()

        val response: HttpResponse = client.get("https://api.curseforge.com/v1/mods/$modId/files") {
            header("X-Api-Key", apiKey)
            header("Accept", "application/json")

            parameter("pageSize", 50)
            gameVersion?.let { parameter("gameVersion", it) }
            modLoaderType?.let { parameter("modLoaderType", it) }
        }

        if (!response.status.isSuccess()) {
            println("❌ [CF] HTTP ${response.status.value} for mod $modId")
            return emptyList()
        }

        val data = json.decodeFromString<CurseForgeFilesResponse>(response.bodyAsText())
        return data.data
    }

    // ============================================================
    //  ✅ GET SPECIFIC FILE BY ID
    // ============================================================
    suspend fun getModFile(modId: Int, fileId: Int): CurseForgeFile? {
        if (!isConfigured()) return null

        return try {
            val response: HttpResponse = client.get(
                "https://api.curseforge.com/v1/mods/$modId/files/$fileId"
            ) {
                header("X-Api-Key", apiKey)
                header("Accept", "application/json")
            }

            if (!response.status.isSuccess()) {
                println("❌ [CF] HTTP ${response.status.value} for $modId/$fileId")
                return null
            }

            val data = json.decodeFromString<CurseForgeSingleFileResponse>(
                response.bodyAsText()
            )
            data.data
        } catch (e: Exception) {
            println("❌ [CF] getModFile failed: ${e.message}")
            null
        }
    }

    // ============================================================
    //  ✅ GET DOWNLOAD URL (with CDN fallback)
    // ============================================================
    suspend fun getDownloadUrl(modId: Int, fileId: Int): String? {
        // 1. جرّب من API
        val file = getModFile(modId, fileId)
        if (file?.downloadUrl != null) {
            return file.downloadUrl
        }

        // 2. CDN fallback
        // https://edge.forgecdn.net/files/{first4}/{last3}/{fileName}
        if (file?.fileName != null) {
            val first4 = fileId / 1000
            val last3 = fileId % 1000
            return "https://edge.forgecdn.net/files/$first4/$last3/${file.fileName}"
        }

        return null
    }

    companion object {
        fun fromSystemProperty(): CurseForgeService {
            val key = System.getProperty("CURSEFORGE_API_KEY") ?: ""
            println("🔑 [CF] Key: ${key.take(10)}... (${key.length} chars)")
            return CurseForgeService(key)
        }
    }
}