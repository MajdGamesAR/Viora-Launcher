package com.viora.launcher.core.version.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionDetails(
    val id: String,
    val type: String = "release",
    val mainClass: String = "net.minecraft.client.main.Main",
    val assets: String = "",
    @SerialName("assetIndex") val assetIndex: AssetIndex = AssetIndex(),
    val downloads: Downloads = Downloads(),
    val libraries: List<Library> = emptyList(),
    val arguments: Arguments? = null,
    @SerialName("minecraftArguments") val legacyArguments: String? = null,
    @SerialName("javaVersion") val javaVersion: JavaVersion? = null,
    @SerialName("inheritsFrom") val inheritsFrom: String? = null
) {
    @Serializable
    data class AssetIndex(
        val id: String = "",
        val sha1: String = "",
        val size: Long = 0,
        @SerialName("totalSize") val totalSize: Long = 0,
        val url: String = ""
    )

    @Serializable
    data class Downloads(
        val client: DownloadInfo = DownloadInfo(),
        val server: DownloadInfo? = null,
        @SerialName("client_mappings") val clientMappings: DownloadInfo? = null
    )

    @Serializable
    data class DownloadInfo(
        val sha1: String = "",
        val size: Long = 0,
        val url: String = ""
    )

    @Serializable
    data class JavaVersion(
        val component: String = "jdk",
        @SerialName("majorVersion") val majorVersion: Double = 21.0
    ) {
        val majorVersionInt: Int
            get() = majorVersion.toInt()
    }
}