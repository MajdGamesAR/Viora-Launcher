package com.viora.launcher.core.mods

enum class ModSource(val displayName: String, val icon: String) {
    MODRINTH("Modrinth", "🟢"),
    CURSEFORGE("CurseForge", "🟠"),
    ALL("All Sources", "🌐")
}

data class UnifiedMod(
    val id: String,
    val source: ModSource,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val downloads: Long,
    val follows: Long,
    val slug: String,
    val projectType: String,
    val rawModrinth: ModrinthProject? = null,
    val rawCurseForge: CurseForgeMod? = null
)