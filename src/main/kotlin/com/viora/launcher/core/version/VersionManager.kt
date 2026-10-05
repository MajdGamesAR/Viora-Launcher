package com.viora.launcher.core.version

import com.viora.launcher.core.version.model.VersionDetails
import com.viora.launcher.core.version.model.VersionEntry
import com.viora.launcher.core.version.model.VersionManifest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

class VersionManager(
    private val minecraftDir: File = File(
        System.getProperty("user.home"),
        "AppData/Roaming/.minecraft"
    )
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    private val manifestUrl =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    private val versionsDir = File(minecraftDir, "versions").apply { mkdirs() }

    suspend fun fetchManifest(): VersionManifest {
        return client.get(manifestUrl).body()
    }

    suspend fun fetchVersionDetails(versionUrl: String): VersionDetails {
        return client.get(versionUrl).body()
    }

    fun isInstalled(versionId: String): Boolean {
        val dir = File(versionsDir, versionId)
        return File(dir, "$versionId.json").exists() &&
                File(dir, "$versionId.jar").exists()
    }

    fun getInstalledVersions(): List<String> {
        return versionsDir.listFiles()
            ?.filter { it.isDirectory && File(it, "${it.name}.json").exists() }
            ?.map { it.name }
            ?: emptyList()
    }

    fun filterVersions(
        all: List<VersionEntry>,
        installed: List<String>,
        filter: VersionFilter
    ): List<VersionEntry> {
        return when (filter) {
            VersionFilter.ALL -> all
            VersionFilter.RELEASES -> all.filter { it.isRelease }
            VersionFilter.SNAPSHOTS -> all.filter { it.isSnapshot }
            VersionFilter.OLD -> all.filter { it.isOld }
            VersionFilter.INSTALLED -> all.filter { it.id in installed }
            VersionFilter.MODPACKS -> all.filter { it.id in installed } // TODO: فحص المودات الفعلي
        }
    }

    /**
     * إغلاق الـ HttpClient عند إغلاق التطبيق
     */
    fun close() {
        client.close()
    }
}