package com.viora.launcher.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.viora.launcher.core.util.MinecraftPath
import com.viora.launcher.core.version.VersionService
import com.viora.launcher.core.version.model.VersionEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class VersionsViewModel(
    private val versionService: VersionService,
    private val scope: CoroutineScope
) {
    // ============================================================
    //  STATE
    // ============================================================
    var allVersions by mutableStateOf<List<VersionEntry>>(emptyList())
        private set

    var installedVersions by mutableStateOf<List<String>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val installingProgress = mutableStateMapOf<String, Float>()

    // ============================================================
    //  INIT
    // ============================================================
    init {
        refresh()
    }

    // ============================================================
    //  REFRESH
    // ============================================================
    fun refresh() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                allVersions = versionService.loadVersions()
                installedVersions = versionService.manager.getInstalledVersions()
                println("✅ Loaded ${allVersions.size} versions, ${installedVersions.size} installed")
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load versions"
                println("❌ Failed to load versions: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // ============================================================
    //  INSTALL
    // ============================================================
    fun installVersion(version: VersionEntry, onComplete: () -> Unit) {
        if (installingProgress.containsKey(version.id)) return

        scope.launch {
            installingProgress[version.id] = 0f
            try {
                versionService.installVersion(
                    versionId = version.id,
                    versionUrl = version.url
                ) { _, progress ->
                    installingProgress[version.id] = progress
                }
                installedVersions = versionService.manager.getInstalledVersions()
                onComplete()
                println("✅ Installation completed: ${version.id}")
            } catch (e: Exception) {
                errorMessage = e.message ?: "Install failed"
                println("❌ Install failed for ${version.id}: ${e.message}")
            } finally {
                installingProgress.remove(version.id)
            }
        }
    }

    // ============================================================
    //  DELETE
    // ============================================================
    fun deleteVersion(version: VersionEntry) {
        scope.launch {
            try {
                val versionDir = MinecraftPath.versionDir(version.id)
                if (versionDir.exists()) {
                    versionDir.deleteRecursively()
                    println("🗑️ Deleted version: ${version.id}")
                    installedVersions = versionService.manager.getInstalledVersions()
                } else {
                    println("⚠️ Version dir not found: ${versionDir.absolutePath}")
                }
            } catch (e: Exception) {
                errorMessage = "Failed to delete: ${e.message}"
                println("❌ Delete failed: ${e.message}")
            }
        }
    }

    // ============================================================
    //  SIZE
    // ============================================================
    fun getInstalledSize(version: VersionEntry): Long {
        return try {
            val versionDir = MinecraftPath.versionDir(version.id)
            if (!versionDir.exists()) return 0L
            MinecraftPath.dirSizeMB(versionDir)
        } catch (e: Exception) {
            0L
        }
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    fun isInstalled(version: VersionEntry): Boolean =
        version.id in installedVersions

    fun isInstalling(version: VersionEntry): Boolean =
        installingProgress.containsKey(version.id)

    fun getProgress(version: VersionEntry): Float =
        installingProgress[version.id] ?: 0f

    fun clearError() {
        errorMessage = null
    }
}