package com.viora.launcher.core.version

import com.viora.launcher.core.util.DownloadTask
import com.viora.launcher.core.util.Downloader
import com.viora.launcher.core.util.MinecraftPath
import com.viora.launcher.core.util.OSUtils
import com.viora.launcher.core.version.model.Library
import com.viora.launcher.core.version.model.VersionDetails
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipFile

class VersionInstaller(
    private val minecraftDir: File = MinecraftPath.root,
    private val downloader: Downloader = Downloader()
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // ============================================================
    //  المجلدات
    // ============================================================
    private val versionsDir = File(minecraftDir, "versions")
    private val librariesDir = File(minecraftDir, "libraries")
    private val assetsDir = File(minecraftDir, "assets")
    private val nativesDir = File(minecraftDir, "natives")

    // ============================================================
    //  MAIN INSTALL
    // ============================================================
    suspend fun install(
        versionId: String,
        details: VersionDetails,
        onProgress: (String, Float) -> Unit
    ) {
        val versionDir = File(versionsDir, versionId).apply { mkdirs() }

        // ---------- 1. حفظ JSON ----------
        onProgress("Saving version data...", 0.05f)
        File(versionDir, "$versionId.json")
            .writeText(json.encodeToString(VersionDetails.serializer(), details))

        // ---------- 2. تحميل client.jar ----------
        onProgress("Downloading client.jar...", 0.10f)
        val clientJar = File(versionDir, "$versionId.jar")
        val clientUrl = details.downloads.client.url

        if (clientUrl.isBlank()) {
            throw IllegalStateException(
                "❌ client.jar URL is empty for version $versionId"
            )
        }

        println("📥 Downloading client.jar:")
        println("   URL:    $clientUrl")
        println("   Target: ${clientJar.absolutePath}")
        println("   Size:   ${details.downloads.client.size} bytes")

        val success = downloader.download(
            url = clientUrl,
            destination = clientJar,
            expectedSha1 = details.downloads.client.sha1
        ) { downloaded, total ->
            if (total > 0) {
                val pct = 0.10f + (downloaded.toFloat() / total) * 0.15f
                onProgress(
                    "client.jar: ${downloaded / 1024 / 1024}MB / ${total / 1024 / 1024}MB",
                    pct
                )
            }
        }

        if (!success || !clientJar.exists() || clientJar.length() == 0L) {
            throw IllegalStateException(
                "❌ Failed to download client.jar for $versionId\n" +
                        "URL: $clientUrl\n" +
                        "Check your internet connection."
            )
        }

        println("✅ client.jar downloaded: ${clientJar.length()} bytes")

        // ---------- 3. تحميل المكتبات ----------
        onProgress("Downloading libraries...", 0.25f)
        val libraries = details.libraries.filter { it.isAllowed() }
        installLibraries(libraries) { done, total ->
            val pct = 0.25f + 0.55f * (done.toFloat() / total.coerceAtLeast(1))
            onProgress("Libraries ($done/$total)", pct)
        }

        // ---------- 4. تحميل فهرس الأصول ----------
        onProgress("Downloading asset index...", 0.80f)
        val assetIndexFile = File(assetsDir, "indexes/${details.assetIndex.id}.json")
        assetIndexFile.parentFile?.mkdirs()

        val indexSuccess = downloader.download(
            details.assetIndex.url,
            assetIndexFile,
            details.assetIndex.sha1.takeIf { it.isNotBlank() }
        )
        if (!indexSuccess) {
            throw IllegalStateException("❌ Failed to download asset index")
        }

        // ---------- 5. تحميل الأصول ----------
        onProgress("Downloading assets...", 0.85f)
        installAssets(assetIndexFile) { done, total ->
            val pct = 0.85f + 0.14f * (done.toFloat() / total.coerceAtLeast(1))
            onProgress("Assets ($done/$total)", pct)
        }

        // ---------- 6. استخراج الـ natives ----------
        // (يتم داخل installLibraries)

        onProgress("✅ Installation complete!", 1.0f)
        println("🎉 Version $versionId installed successfully!")
    }

    // ============================================================
    //  LIBRARIES
    // ============================================================
    private suspend fun installLibraries(
        libraries: List<Library>,
        onProgress: (Int, Int) -> Unit
    ) {
        val tasks = mutableListOf<DownloadTask>()
        val nativesToExtract = mutableListOf<Pair<File, Library>>()

        for (lib in libraries) {
            // --- Artifact عادي ---
            lib.downloads?.artifact?.let { artifact ->
                val path = artifact.path ?: lib.nameToPath()
                tasks.add(
                    DownloadTask(
                        artifact.url,
                        File(librariesDir, path),
                        artifact.sha1
                    )
                )
            }

            // --- Native ---
            val nativeKey = getNativeKey()
            lib.natives?.get(nativeKey)?.let { classifier ->
                // ✅ استبدل ${arch} بالقيمة الصحيحة
                val resolved = classifier.replace("\${arch}", getArchBits())
                lib.downloads?.classifiers?.get(resolved)?.let { artifact ->
                    val path = artifact.path ?: return@let
                    val dest = File(librariesDir, path)
                    tasks.add(DownloadTask(artifact.url, dest, artifact.sha1))
                    nativesToExtract.add(dest to lib)
                }
            }
        }

        println("📚 Downloading ${tasks.size} library files...")

        var completed = 0
        val total = tasks.size
        downloader.downloadBatch(tasks, concurrency = 8) {
            completed++
            onProgress(completed, total)
        }

        // استخراج الـ natives
        nativesToExtract.forEach { (jar, lib) ->
            try {
                extractNatives(jar, lib)
            } catch (e: Exception) {
                println("⚠️ Failed to extract natives for ${lib.name}: ${e.message}")
            }
        }
    }

    private fun extractNatives(jar: File, lib: Library) {
        if (!jar.exists()) return
        val targetDir = File(nativesDir, lib.name.replace(":", "_")).apply { mkdirs() }

        ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && !it.name.startsWith("META-INF/") }
                .filter { entry ->
                    val excluded = lib.extract?.exclude ?: emptyList()
                    excluded.none { entry.name.startsWith(it) }
                }
                .forEach { entry ->
                    val outFile = File(targetDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }
    }

    // ============================================================
    //  ASSETS
    // ============================================================
    private suspend fun installAssets(
        assetIndexFile: File,
        onProgress: (Int, Int) -> Unit
    ) {
        if (!assetIndexFile.exists()) return
        val root = json.parseToJsonElement(assetIndexFile.readText()).jsonObject
        val objects = root["objects"]?.jsonObject ?: return

        val tasks = objects.mapNotNull { (_, element) ->
            val obj = element.jsonObject
            val hash = obj["hash"]?.toString()?.trim('"') ?: return@mapNotNull null
            val prefix = hash.substring(0, 2)
            val url = "https://resources.download.minecraft.net/$prefix/$hash"
            DownloadTask(url, File(assetsDir, "objects/$prefix/$hash"), hash)
        }

        println("🎨 Downloading ${tasks.size} asset files...")

        var completed = 0
        val total = tasks.size
        downloader.downloadBatch(tasks, concurrency = 10) {
            completed++
            if (completed % 20 == 0 || completed == total) {
                onProgress(completed, total)
            }
        }
    }

    // ============================================================
    //  HELPERS — Library paths
    // ============================================================
    private fun Library.nameToPath(): String {
        val parts = name.split(":")
        require(parts.size >= 3) { "Invalid library name: $name" }

        val group = parts[0].replace(".", "/")
        val artifact = parts[1]
        val version = parts[2]
        val classifier = if (parts.size >= 4) parts[3] else null

        val fileName = if (classifier != null) {
            "$artifact-$version-$classifier.jar"
        } else {
            "$artifact-$version.jar"
        }

        return "$group/$artifact/$version/$fileName"
    }

    // ============================================================
    //  HELPERS — Native key (مع دعم كامل لكل الأنظمة)
    // ============================================================
    private fun getNativeKey(): String = when (OSUtils.currentOS()) {
        OSUtils.OS.WINDOWS -> {
            val arch = System.getProperty("os.arch").lowercase()
            if (arch.contains("aarch64")) "natives-windows-arm64"
            else "natives-windows"
        }
        OSUtils.OS.MACOS -> {
            val arch = System.getProperty("os.arch").lowercase()
            if (arch.contains("aarch64")) "natives-macos-arm64"
            else "natives-macos"
        }
        OSUtils.OS.LINUX -> {
            val arch = System.getProperty("os.arch").lowercase()
            if (arch.contains("aarch64")) "natives-linux-arm64"
            else "natives-linux"
        }
        OSUtils.OS.UNKNOWN -> {
            println("⚠️ Unknown OS — defaulting to natives-windows")
            "natives-windows"
        }
    }

    /**
     * ✅ عدد البتات (64 أو 32) — يُستخدم لاستبدال ${arch}
     */
    private fun getArchBits(): String {
        val arch = System.getProperty("os.arch").lowercase()
        return if (arch.contains("64") || arch.contains("aarch64")) "64" else "32"
    }
}