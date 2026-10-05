package com.viora.launcher.core.version.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionManifest(
    val latest: Latest,
    val versions: List<VersionEntry>
) {
    @Serializable
    data class Latest(
        val release: String,
        val snapshot: String
    )
}

@Serializable
data class VersionEntry(
    val id: String,
    val type: String,
    val url: String,
    val time: String,
    @SerialName("releaseTime") val releaseTime: String,
    val sha1: String? = null,
    @SerialName("complianceLevel") val complianceLevel: Int = 0
) {
    val isRelease: Boolean get() = type == "release"
    val isSnapshot: Boolean get() = type == "snapshot"
    val isOld: Boolean get() = type.startsWith("old_")
}