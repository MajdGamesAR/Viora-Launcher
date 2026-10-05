package com.viora.launcher.core.mods

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class UnifiedModService(
    private val modrinth: ModrinthService = ModrinthService.shared,
    private val curseForge: CurseForgeService
) {

    fun isCurseForgeAvailable(): Boolean = curseForge.isConfigured()

    suspend fun getCurseForgeFiles(modId: Int): List<CurseForgeFile> {
        if (!curseForge.isConfigured()) return emptyList()
        return try {
            curseForge.getModFiles(modId)
        } catch (e: Exception) {
            println("❌ Failed to get CF files: ${e.message}")
            emptyList()
        }
    }

    /**
     * ✅ احصل على ملف محدد
     */
    suspend fun getCurseForgeFile(modId: Int, fileId: Int): CurseForgeFile? {
        if (!curseForge.isConfigured()) return null
        return try {
            curseForge.getModFile(modId, fileId)
        } catch (e: Exception) {
            println("❌ Failed to get CF file: ${e.message}")
            null
        }
    }

    /**
     * ✅ احصل على رابط تحميل موثوق
     */
    suspend fun getCurseForgeDownloadUrl(modId: Int, fileId: Int): String? {
        if (!curseForge.isConfigured()) return null
        return try {
            curseForge.getDownloadUrl(modId, fileId)
        } catch (e: Exception) {
            println("❌ Failed to get download URL: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SEARCH
    // ═══════════════════════════════════════════════════════════
    suspend fun search(
        query: String,
        projectType: String,
        source: ModSource = ModSource.ALL,
        limit: Int = 20
    ): List<UnifiedMod> = coroutineScope {

        val modrinthResults = mutableListOf<UnifiedMod>()
        val curseForgeResults = mutableListOf<UnifiedMod>()

        // ─── Modrinth ───
        if (source == ModSource.MODRINTH || source == ModSource.ALL) {
            val job = async {
                try {
                    println("🔍 [Unified] Modrinth: '$query' ($projectType)")
                    val projects = modrinth.searchProjects(
                        query = query,
                        projectType = projectType,
                        limit = limit
                    )
                    println("✅ [Unified] Modrinth: ${projects.size}")
                    projects.map { it.toUnified() }
                } catch (e: Exception) {
                    println("❌ [Unified] Modrinth: ${e.message}")
                    emptyList()
                }
            }
            modrinthResults.addAll(job.await())
        }

        // ─── CurseForge ───
        if (source == ModSource.CURSEFORGE || source == ModSource.ALL) {
            val job = async {
                if (!curseForge.isConfigured()) {
                    println("⚠️ [Unified] CurseForge not configured")
                    return@async emptyList()
                }
                try {
                    println("🔍 [Unified] CurseForge: '$query' ($projectType)")

                    val classId = when (projectType) {
                        "mod" -> 6
                        "modpack" -> 4471
                        "resourcepack" -> 12
                        "shader" -> 6552
                        "datapack" -> 6945
                        else -> null
                    }

                    println("   classId: $classId")

                    val response = curseForge.searchMods(
                        query = query,
                        classId = classId,
                        limit = limit
                    )
                    println("✅ [Unified] CurseForge: ${response.data.size}")

                    // ✅ Debug: اطبع أول نتيجة
                    response.data.firstOrNull()?.let {
                        println("   First: id=${it.id}, name=${it.name}, latestFiles=${it.latestFiles.size}")
                    }

                    response.data.map { it.toUnified(projectType) }
                } catch (e: Exception) {
                    println("❌ [Unified] CurseForge: ${e.message}")
                    emptyList()
                }
            }
            curseForgeResults.addAll(job.await())
        }

        val combined = (modrinthResults + curseForgeResults)
        println("📊 [Unified] Total: ${combined.size} (M:${modrinthResults.size} CF:${curseForgeResults.size})")
        combined.sortedByDescending { it.downloads }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Converters
// ═══════════════════════════════════════════════════════════════
private fun ModrinthProject.toUnified() = UnifiedMod(
    id = projectId,
    source = ModSource.MODRINTH,
    title = title,
    description = description,
    iconUrl = iconUrl,
    downloads = downloads,
    follows = follows,
    slug = slug,
    projectType = projectType,
    rawModrinth = this
)

private fun CurseForgeMod.toUnified(projectType: String = "mod") = UnifiedMod(
    id = id.toString(),
    source = ModSource.CURSEFORGE,
    title = name,
    description = summary,
    iconUrl = logo?.url,
    downloads = downloadCount,
    follows = 0,
    slug = slug,
    projectType = projectType,
    rawCurseForge = this
)