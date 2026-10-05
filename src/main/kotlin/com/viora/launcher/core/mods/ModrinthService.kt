package com.viora.launcher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ModrinthService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
    }

    // ✅ Modrinth يتطلب User-Agent فريد بصيغة محددة
    private val userAgent = "VioraLauncher/1.0.0 (contact@viora-launcher.com)"

    // ✅ كائن Json واحد لإعادة الاستخدام (تحسين الأداء)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * البحث عن مودات أو حزم مودات
     */
    suspend fun searchProjects(
        query: String,
        projectType: String = "mod",
        gameVersion: String? = null,
        loader: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<ModrinthProject> {
        try {
            val url = "https://api.modrinth.com/v2/search"
            println("🔍 Searching Modrinth: query='$query', type='$projectType'")

            val response: HttpResponse = client.get(url) {
                header("User-Agent", userAgent)
                parameter("query", query)
                parameter("limit", limit)
                parameter("offset", offset)

                // ✅ بناء facets بشكل صحيح
                val facets = mutableListOf<List<String>>()
                facets.add(listOf("project_type:$projectType"))

                gameVersion?.let { facets.add(listOf("versions:$it")) }
                loader?.let { facets.add(listOf("categories:$it")) }

                if (facets.isNotEmpty()) {
                    val facetsJson = facets.joinToString(",") { innerList ->
                        "[" + innerList.joinToString(",") { "\"$it\"" } + "]"
                    }
                    parameter("facets", "[$facetsJson]")
                    println("📋 Facets: [$facetsJson]")
                }
            }

            val status = response.status
            val body = response.bodyAsText()

            println("📥 Modrinth Status: $status")

            if (!status.isSuccess()) {
                println("❌ Modrinth returned error: $status")
                return emptyList()
            }

            val searchResponse = json.decodeFromString<ModrinthSearchResponse>(body)
            println("✅ Found ${searchResponse.hits.size} results")
            return searchResponse.hits

        } catch (e: Exception) {
            println("❌ Modrinth search failed: ${e.message}")
            return emptyList()
        }
    }

    /**
     * الحصول على إصدارات مشروع معين
     */
    suspend fun getProjectVersions(
        projectId: String,
        gameVersion: String? = null,
        loader: String? = null
    ): List<ModrinthVersion> {
        try {
            val url = "https://api.modrinth.com/v2/project/$projectId/version"
            println("🔍 Getting versions for: $projectId")

            val response: HttpResponse = client.get(url) {
                header("User-Agent", userAgent)
                gameVersion?.let { parameter("game_versions", "[\"$it\"]") }
                loader?.let { parameter("loaders", "[\"$it\"]") }
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                println("❌ Failed to get versions: ${response.status}")
                return emptyList()
            }

            return json.decodeFromString<List<ModrinthVersion>>(body)

        } catch (e: Exception) {
            println("❌ Failed to get versions: ${e.message}")
            return emptyList()
        }
    }

    /**
     * إغلاق الـ client
     */
    fun close() {
        client.close()
    }

    companion object {
        /**
         * ✅ Singleton مشترك — يمنع إنشاء HttpClient في كل composition
         * ويحل مشكلة CancellationException عند تبديل التبويبات
         *
         * الاستخدام: `ModrinthService.shared`
         */
        val shared: ModrinthService by lazy { ModrinthService() }
    }
}