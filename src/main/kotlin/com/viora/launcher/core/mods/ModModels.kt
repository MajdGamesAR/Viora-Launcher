package com.viora.launcher.core.mods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ===== Modrinth =====
@Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthProject> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 20,
    @SerialName("total_hits") val totalHits: Int = 0
)

@Serializable
data class ModrinthProject(
    @SerialName("project_id") val projectId: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("icon_url") val iconUrl: String? = null,
    val downloads: Long = 0,
    val follows: Long = 0,
    val categories: List<String> = emptyList(),
    @SerialName("project_type") val projectType: String = "mod",
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList()
)

@Serializable
data class ModrinthVersion(
    val id: String = "",
    @SerialName("project_id") val projectId: String = "",
    val name: String = "",
    @SerialName("version_number") val versionNumber: String = "",
    @SerialName("date_published") val datePublished: String? = null,
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val files: List<ModrinthFile> = emptyList()
)

@Serializable
data class ModrinthFile(
    val url: String = "",
    val filename: String = "",
    val size: Long = 0,
    val hashes: Map<String, String> = emptyMap()
)

// ===== CurseForge =====
@Serializable
data class CurseForgeMod(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
    val summary: String = "",
    @SerialName("downloadCount") val downloadCount: Long = 0,
    @SerialName("logo") val logo: CurseForgeLogo? = null,
    val categories: List<CurseForgeCategory> = emptyList(),
    @SerialName("latestFiles") val latestFiles: List<CurseForgeFile> = emptyList(),
    @SerialName("latestFilesIndexes") val latestFilesIndexes: List<CurseForgeFileIndex> = emptyList(),
    @SerialName("classId") val classId: Int? = null,
    @SerialName("dateModified") val dateModified: String? = null,
    @SerialName("dateReleased") val dateReleased: String? = null
)

@Serializable
data class CurseForgeFileIndex(
    @SerialName("gameVersion") val gameVersion: String = "",
    @SerialName("fileId") val fileId: Int = 0,
    val filename: String = "",
    @SerialName("releaseType") val releaseType: Int = 0,
    @SerialName("gameVersionTypeId") val gameVersionTypeId: Int? = null,
    @SerialName("modLoader") val modLoader: Int? = null
)

@Serializable
data class CurseForgeLogo(
    val url: String = "",
    val thumbnailUrl: String? = null
)

@Serializable
data class CurseForgeCategory(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
data class CurseForgeFile(
    val id: Int = 0,
    @SerialName("displayName") val displayName: String = "",
    @SerialName("fileName") val fileName: String = "",
    @SerialName("downloadUrl") val downloadUrl: String? = null,
    @SerialName("fileLength") val fileLength: Long = 0,
    @SerialName("gameVersions") val gameVersions: List<String> = emptyList(),
    @SerialName("modLoader") val modLoader: Int? = null,
    @SerialName("fileDate") val fileDate: String? = null,
    @SerialName("releaseType") val releaseType: Int = 1
)

@Serializable
data class CurseForgeSearchResponse(
    val data: List<CurseForgeMod> = emptyList(),
    val pagination: CurseForgePagination = CurseForgePagination()
)

@Serializable
data class CurseForgePagination(
    val index: Int = 0,
    @SerialName("pageSize") val pageSize: Int = 20,
    val total: Int = 0
)

@Serializable
data class CurseForgeFilesResponse(
    val data: List<CurseForgeFile> = emptyList()
)

// ✅ استجابة ملف واحد
@Serializable
data class CurseForgeSingleFileResponse(
    val data: CurseForgeFile? = null
)