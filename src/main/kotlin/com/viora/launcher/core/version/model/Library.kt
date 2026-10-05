package com.viora.launcher.core.version.model

import com.viora.launcher.core.util.OSUtils
import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val name: String,
    val url: String? = null,
    val artifact: Artifact? = null,
    val downloads: LibraryDownloads? = null,
    val natives: Map<String, String>? = null,
    val rules: List<Rule>? = null,
    val extract: Extract? = null
) {
    @Serializable
    data class LibraryDownloads(
        val artifact: Artifact? = null,
        val classifiers: Map<String, Artifact>? = null
    )

    @Serializable
    data class Artifact(
        val path: String? = null,
        val sha1: String = "",
        val size: Long = 0,
        val url: String = ""
    )

    @Serializable
    data class Extract(val exclude: List<String> = emptyList())

    @Serializable
    data class Rule(
        val action: String,
        val os: OS? = null
    ) {
        @Serializable
        data class OS(
            val name: String? = null,
            val version: String? = null,
            val arch: String? = null
        )
    }

    fun isAllowed(): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (ruleMatches(rule)) allowed = rule.action == "allow"
        }
        return allowed
    }

    private fun ruleMatches(rule: Rule): Boolean {
        val os = rule.os ?: return true
        val current = OSUtils.currentOS().id
        return os.name == null || os.name == current
    }

    fun resolvePath(): String? {
        // 1. Mojang style
        downloads?.artifact?.path?.let { return it }
        // 2. TLauncher style
        artifact?.path?.let { return it }
        // 3. Fabric style — ابنِ من name
        return nameToPath(name)
    }

    fun resolveUrl(): String? {
        downloads?.artifact?.url?.let { if (it.isNotBlank()) return it }
        artifact?.url?.let { if (it.isNotBlank()) return it }
        if (!url.isNullOrBlank()) {
            val path = nameToPath(name) ?: return null
            return "${url.trimEnd('/')}/$path"
        }
        return null
    }
}

private fun nameToPath(name: String): String? {
    return try {
        // ✅ دعم @extension في النهاية
        val (rawName, extension) = if (name.contains("@")) {
            val idx = name.lastIndexOf("@")
            name.substring(0, idx) to name.substring(idx + 1)
        } else {
            name to "jar"
        }

        val parts = rawName.split(":")
        if (parts.size < 3) return null

        val group = parts[0].replace(".", "/")
        val artifact = parts[1]
        val version = parts[2]
        val classifier = if (parts.size >= 4 && parts[3].isNotBlank()) parts[3] else null

        // ✅ ابنِ اسم الملف مع classifier و extension
        val fileName = if (classifier != null) {
            "$artifact-$version-$classifier.$extension"
        } else {
            "$artifact-$version.$extension"
        }

        "$group/$artifact/$version/$fileName"
    } catch (e: Exception) {
        null
    }
}