package com.viora.launcher.core.mods

import com.viora.launcher.core.util.DownloadTask
import com.viora.launcher.core.util.Downloader
import com.viora.launcher.core.util.MinecraftPath
import com.viora.launcher.core.version.VersionInstaller
import com.viora.launcher.core.version.VersionManager
import com.viora.launcher.core.version.model.Library
import com.viora.launcher.core.version.model.VersionDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

object ModpackInstaller {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val minecraftDir: File = MinecraftPath.root
    private val librariesDir = File(minecraftDir, "libraries")
    private val downloader = Downloader()

    // ============================================================
    //  MAIN
    // ============================================================
    suspend fun installModpack(
        mrpackFile: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(0f, "📦 Extracting modpack...")

            val extractDir = File(mrpackFile.parent, mrpackFile.nameWithoutExtension)
            if (extractDir.exists()) extractDir.deleteRecursively()
            extractDir.mkdirs()

            ZipFile(mrpackFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val target = File(extractDir, entry.name)
                    if (!target.canonicalPath.startsWith(extractDir.canonicalPath)) {
                        return@forEach
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            val modrinthIndex = File(extractDir, "modrinth.index.json")
            val curseForgeManifest = File(extractDir, "manifest.json")

            when {
                modrinthIndex.exists() -> {
                    println("📦 Detected: Modrinth (.mrpack)")
                    installModrinthModpack(extractDir, modrinthIndex, onProgress)
                }
                curseForgeManifest.exists() -> {
                    println("📦 Detected: CurseForge (manifest.json)")
                    installCurseForgeModpack(extractDir, curseForgeManifest, onProgress)
                }
                else -> {
                    onProgress(0f, "❌ Invalid modpack: no manifest found")
                    false
                }
            }

        } catch (e: Exception) {
            println("❌ Modpack install failed: ${e.message}")
            e.printStackTrace()
            onProgress(0f, "❌ ${e.message}")
            false
        }
    }

    // ============================================================
    //  MODRINTH
    // ============================================================
    private suspend fun installModrinthModpack(
        extractDir: File,
        indexFile: File,
        onProgress: (Float, String) -> Unit
    ): Boolean {
        try {
            val index = json.decodeFromString<ModpackIndex>(indexFile.readText())
            println("📦 Modrinth: ${index.name} v${index.versionId}")

            val safeName = sanitizeVersionId(index.name)
            val mcVersion = index.dependencies["minecraft"] ?: "1.21.1"
            val fabricLoader = index.dependencies["fabric-loader"]
            val quiltLoader = index.dependencies["quilt-loader"]
            val neoForgeVersion = index.dependencies["neoforge"]
            val forgeVersion = index.dependencies["forge"]

            if (!isParentInstalled(mcVersion)) {
                onProgress(0.02f, "⬇️ Installing parent $mcVersion...")
                if (!installVanillaVersion(mcVersion) { p, msg ->
                        onProgress(0.02f + p * 0.15f, msg)
                    }) {
                    onProgress(0f, "❌ Failed to install parent")
                    return false
                }
            }

            val loaderLibraries = when {
                fabricLoader != null -> fetchFabricLibraries(mcVersion, fabricLoader) { p, msg ->
                    onProgress(0.20f + p * 0.15f, msg)
                }
                quiltLoader != null -> fetchQuiltLibraries(mcVersion, quiltLoader) { p, msg ->
                    onProgress(0.20f + p * 0.15f, msg)
                }
                neoForgeVersion != null -> installNeoForge(mcVersion, neoForgeVersion) { p, msg ->
                    onProgress(0.20f + p * 0.20f, msg)
                }
                forgeVersion != null -> installForge(mcVersion, forgeVersion) { p, msg ->
                    onProgress(0.20f + p * 0.20f, msg)
                }
                else -> emptyList()
            }

            val versionDir = File(minecraftDir, "versions/$safeName")
            val instanceDir = File(minecraftDir, "instances/$safeName")

            if (versionDir.exists()) versionDir.deleteRecursively()
            if (instanceDir.exists()) instanceDir.deleteRecursively()
            versionDir.mkdirs()
            instanceDir.mkdirs()
            ensureInstanceStructure(instanceDir)

            val overridesDir = File(extractDir, "overrides")
            if (overridesDir.exists()) copyDirectory(overridesDir, instanceDir)

            val totalFiles = index.files.size
            var downloaded = 0
            var failed = 0

            index.files.forEach { file ->
                onProgress(
                    0.40f + 0.55f * (downloaded.toFloat() / totalFiles.coerceAtLeast(1)),
                    "⬇️ ${downloaded + 1}/$totalFiles"
                )
                try {
                    val targetFile = File(instanceDir, file.path)
                    targetFile.parentFile?.mkdirs()
                    if (targetFile.exists() && targetFile.length() > 0) {
                        downloaded++
                        return@forEach
                    }
                    if (file.downloads.isEmpty()) {
                        failed++
                        return@forEach
                    }
                    URI(file.downloads.first()).toURL().openStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    downloaded++
                } catch (e: Exception) {
                    failed++
                }
            }

            createVersionJson(
                safeName, mcVersion, versionDir, loaderLibraries,
                fabricLoader, quiltLoader, neoForgeVersion, forgeVersion
            )

            extractDir.deleteRecursively()
            onProgress(1f, if (failed > 0) "⚠️ Done ($failed failed)" else "✅ Installed!")
            return true
        } catch (e: Exception) {
            println("❌ Modrinth install failed: ${e.message}")
            onProgress(0f, "❌ ${e.message}")
            return false
        }
    }

    // ============================================================
    //  CURSEFORGE
    // ============================================================
    private suspend fun installCurseForgeModpack(
        extractDir: File,
        manifestFile: File,
        onProgress: (Float, String) -> Unit
    ): Boolean {
        try {
            val manifest = json.decodeFromString<CurseForgeManifest>(manifestFile.readText())
            println("📦 CurseForge: ${manifest.name} v${manifest.version}")

            val loader = manifest.minecraft.modLoaders.firstOrNull { it.primary }
                ?: manifest.minecraft.modLoaders.firstOrNull()
            val loaderId = loader?.id ?: ""

            val (loaderType, loaderVersion) = when {
                loaderId.startsWith("fabric-") -> "fabric" to loaderId.removePrefix("fabric-")
                loaderId.startsWith("forge-") -> "forge" to loaderId.removePrefix("forge-")
                loaderId.startsWith("neoforge-") -> "neoforge" to loaderId.removePrefix("neoforge-")
                loaderId.startsWith("quilt-") -> "quilt" to loaderId.removePrefix("quilt-")
                else -> "" to ""
            }

            println("   Loader: $loaderType $loaderVersion")
            println("   MC:     ${manifest.minecraft.version}")
            println("   Files:  ${manifest.files.size}")

            val mcVersion = manifest.minecraft.version
            val safeName = sanitizeVersionId(manifest.name)

            if (!isParentInstalled(mcVersion)) {
                onProgress(0.05f, "⬇️ Installing $mcVersion...")
                if (!installVanillaVersion(mcVersion) { p, msg ->
                        onProgress(0.05f + p * 0.10f, msg)
                    }) return false
            }

            val loaderLibraries = when (loaderType) {
                "fabric" -> fetchFabricLibraries(mcVersion, loaderVersion) { p, msg ->
                    onProgress(0.20f + p * 0.15f, msg)
                }
                "quilt" -> fetchQuiltLibraries(mcVersion, loaderVersion) { p, msg ->
                    onProgress(0.20f + p * 0.15f, msg)
                }
                "neoforge" -> installNeoForge(mcVersion, loaderVersion) { p, msg ->
                    onProgress(0.20f + p * 0.20f, msg)
                }
                "forge" -> installForge(mcVersion, loaderVersion) { p, msg ->
                    onProgress(0.20f + p * 0.20f, msg)
                }
                else -> emptyList()
            }

            val versionDir = File(minecraftDir, "versions/$safeName")
            val instanceDir = File(minecraftDir, "instances/$safeName")

            if (versionDir.exists()) versionDir.deleteRecursively()
            if (instanceDir.exists()) instanceDir.deleteRecursively()
            versionDir.mkdirs()
            instanceDir.mkdirs()
            ensureInstanceStructure(instanceDir)

            val overridesDir = File(extractDir, manifest.overrides.ifBlank { "overrides" })
            if (overridesDir.exists()) copyDirectory(overridesDir, instanceDir)

            val cfService = CurseForgeService.fromSystemProperty()
            if (!cfService.isConfigured()) {
                onProgress(0f, "❌ CurseForge API not configured")
                return false
            }

            val totalFiles = manifest.files.size
            var downloaded = 0
            var failed = 0
            var skipped = 0

            for ((index, file) in manifest.files.withIndex()) {
                onProgress(
                    0.40f + 0.55f * (index.toFloat() / totalFiles.coerceAtLeast(1)),
                    "⬇️ ${index + 1}/$totalFiles"
                )
                try {
                    val targetFile = cfService.getModFile(file.projectID, file.fileID)
                        ?: cfService.getModFiles(file.projectID).firstOrNull { it.id == file.fileID }

                    if (targetFile == null) {
                        failed++
                        continue
                    }

                    var downloadUrl = targetFile.downloadUrl
                    if (downloadUrl == null) {
                        downloadUrl = cfService.getDownloadUrl(file.projectID, file.fileID)
                    }
                    if (downloadUrl == null) {
                        skipped++
                        continue
                    }

                    val destFile = File(instanceDir, "mods/${targetFile.fileName}")
                    destFile.parentFile?.mkdirs()

                    if (destFile.exists() && destFile.length() > 0) {
                        downloaded++
                        continue
                    }

                    URI(downloadUrl).toURL().openStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    downloaded++
                } catch (e: Exception) {
                    failed++
                }
            }

            createVersionJson(
                safeName, mcVersion, versionDir, loaderLibraries,
                if (loaderType == "fabric") loaderVersion else null,
                if (loaderType == "quilt") loaderVersion else null,
                if (loaderType == "neoforge") loaderVersion else null,
                if (loaderType == "forge") loaderVersion else null
            )

            extractDir.deleteRecursively()

            println("📊 Summary: $downloaded / $totalFiles (failed: $failed, skipped: $skipped)")
            onProgress(1f, if (failed > 0 || skipped > 0) "⚠️ Done ($failed failed)" else "✅ Installed!")
            return true
        } catch (e: Exception) {
            println("❌ CF install failed: ${e.message}")
            onProgress(0f, "❌ ${e.message}")
            return false
        }
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    private fun isParentInstalled(mcVersion: String): Boolean {
        val json = File(minecraftDir, "versions/$mcVersion/$mcVersion.json")
        val jar = File(minecraftDir, "versions/$mcVersion/$mcVersion.jar")
        return json.exists() && jar.exists()
    }

    private fun ensureInstanceStructure(instanceDir: File) {
        listOf(
            "mods", "config", "saves", "resourcepacks", "shaderpacks",
            "screenshots", "logs", "crash-reports", "downloads", "schematics"
        ).forEach { File(instanceDir, it).mkdirs() }
    }

    // ============================================================
    //  FABRIC
    // ============================================================
    private suspend fun fetchFabricLibraries(
        mcVersion: String,
        loaderVersion: String,
        onProgress: (Float, String) -> Unit
    ): List<Library> {
        return try {
            val url = "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVersion/profile/json"
            val profileJson = URI(url).toURL().readText()
            val profile = json.parseToJsonElement(profileJson).jsonObject
            val librariesArray = profile["libraries"]?.jsonArray ?: return emptyList()

            val tasks = mutableListOf<DownloadTask>()
            val libraries = mutableListOf<Library>()

            librariesArray.forEach { libElement ->
                val libObj = libElement.jsonObject
                val name = libObj["name"]?.jsonPrimitive?.content ?: return@forEach
                val baseUrl = libObj["url"]?.jsonPrimitive?.content ?: ""

                val artifactPath = nameToPath(name)
                val artifactUrl = if (baseUrl.isNotBlank()) {
                    "${baseUrl.trimEnd('/')}/$artifactPath"
                } else {
                    "https://repo1.maven.org/maven2/$artifactPath"
                }

                tasks.add(DownloadTask(artifactUrl, File(librariesDir, artifactPath), null))
                libraries.add(Library(
                    name = name,
                    downloads = Library.LibraryDownloads(
                        artifact = Library.Artifact(
                            path = artifactPath, sha1 = "", size = 0, url = artifactUrl
                        )
                    )
                ))
            }

            var completed = 0
            val total = tasks.size
            downloader.downloadBatch(tasks, concurrency = 8) {
                completed++
                onProgress(
                    0.3f + 0.7f * (completed.toFloat() / total.coerceAtLeast(1)),
                    "Fabric libs ($completed/$total)"
                )
            }

            val fabricLoaderPath = "net/fabricmc/fabric-loader/$loaderVersion/fabric-loader-$loaderVersion.jar"
            val fabricLoaderJar = File(librariesDir, fabricLoaderPath)
            if (!fabricLoaderJar.exists() || fabricLoaderJar.length() == 0L) {
                val loaderUrl = "https://maven.fabricmc.net/$fabricLoaderPath"
                try {
                    fabricLoaderJar.parentFile?.mkdirs()
                    URI(loaderUrl).toURL().openStream().use { input ->
                        fabricLoaderJar.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (libraries.none { it.name.contains("fabric-loader") }) {
                        libraries.add(Library(
                            name = "net.fabricmc:fabric-loader:$loaderVersion",
                            downloads = Library.LibraryDownloads(
                                artifact = Library.Artifact(
                                    path = fabricLoaderPath, sha1 = "", size = 0, url = loaderUrl
                                )
                            )
                        ))
                    }
                } catch (e: Exception) {
                    println("❌ fabric-loader: ${e.message}")
                }
            }

            onProgress(1f, "✅ Fabric libs ready (${libraries.size})")
            libraries
        } catch (e: Exception) {
            println("❌ Fabric fetch failed: ${e.message}")
            emptyList()
        }
    }

    // ============================================================
    //  QUILT
    // ============================================================
    private suspend fun fetchQuiltLibraries(
        mcVersion: String,
        loaderVersion: String,
        onProgress: (Float, String) -> Unit
    ): List<Library> {
        return try {
            val url = "https://meta.quiltmc.org/v3/versions/loader/$mcVersion/$loaderVersion/profile/json"
            val profileJson = URI(url).toURL().readText()
            val profile = json.parseToJsonElement(profileJson).jsonObject
            val librariesArray = profile["libraries"]?.jsonArray ?: return emptyList()

            val tasks = mutableListOf<DownloadTask>()
            val libraries = mutableListOf<Library>()

            librariesArray.forEach { libElement ->
                val libObj = libElement.jsonObject
                val name = libObj["name"]?.jsonPrimitive?.content ?: return@forEach
                val baseUrl = libObj["url"]?.jsonPrimitive?.content ?: ""

                val artifactPath = nameToPath(name)
                val artifactUrl = if (baseUrl.isNotBlank()) {
                    "${baseUrl.trimEnd('/')}/$artifactPath"
                } else {
                    "https://repo1.maven.org/maven2/$artifactPath"
                }

                tasks.add(DownloadTask(artifactUrl, File(librariesDir, artifactPath), null))
                libraries.add(Library(
                    name = name,
                    downloads = Library.LibraryDownloads(
                        artifact = Library.Artifact(
                            path = artifactPath, sha1 = "", size = 0, url = artifactUrl
                        )
                    )
                ))
            }

            var completed = 0
            val total = tasks.size
            downloader.downloadBatch(tasks, concurrency = 8) {
                completed++
                onProgress(
                    0.3f + 0.7f * (completed.toFloat() / total.coerceAtLeast(1)),
                    "Quilt libs ($completed/$total)"
                )
            }

            onProgress(1f, "✅ Quilt libs ready")
            libraries
        } catch (e: Exception) {
            println("❌ Quilt fetch failed: ${e.message}")
            emptyList()
        }
    }

    // ============================================================
    //  NEOFORGE — عبر Installer JAR
    // ============================================================
    /**
     * ✅ NeoForge — يستخدم Installer الرسمي
     *
     * 1. حمّل installer.jar من maven.neoforged.net
     * 2. شغّله --installClient
     * 3. النسخ الناتجة في libraries/ و versions/
     */
    private suspend fun installNeoForge(
        mcVersion: String,
        neoVersion: String,
        onProgress: (Float, String) -> Unit
    ): List<Library> {
        return try {
            onProgress(0.1f, "⬇️ Downloading NeoForge installer...")

            // ✅ 1. حمّل installer
            val installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoVersion/neoforge-$neoVersion-installer.jar"
            val installerFile = File(MinecraftPath.launcherDownloads, "neoforge-$neoVersion-installer.jar")
            installerFile.parentFile?.mkdirs()

            println("📥 NeoForge Installer: $installerUrl")
            URI(installerUrl).toURL().openStream().use { input ->
                installerFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("✅ Installer downloaded: ${installerFile.length()} bytes")

            onProgress(0.4f, "⚙️ Running NeoForge installer...")

            // ✅ 2. شغّل installer
            val javaExe = findJavaForMc(mcVersion)
                ?: throw Exception("Java not found")

            val process = ProcessBuilder(
                javaExe,
                "-jar", installerFile.absolutePath,
                "--installClient"
            )
                .directory(minecraftDir)
                .redirectErrorStream(true)
                .start()

            // اقرأ output
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { println("   [NeoForge] $it") }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw Exception("NeoForge installer failed with exit code $exitCode")
            }

            onProgress(0.9f, "✅ NeoForge installed")

            // ✅ 3. اقرأ مكتبات NeoForge من نسخة "neoforge-$neoVersion"
            val neoVersionId = "neoforge-$neoVersion"
            val neoJsonFile = File(minecraftDir, "versions/$neoVersionId/$neoVersionId.json")

            if (!neoJsonFile.exists()) {
                println("⚠️ NeoForge version.json not found at expected location")
                return emptyList()
            }

            // اقرأ المكتبات من JSON
            val neoJson = json.parseToJsonElement(neoJsonFile.readText()).jsonObject
            val libsArray = neoJson["libraries"]?.jsonArray ?: return emptyList()

            val libraries = mutableListOf<Library>()
            libsArray.forEach { libElement ->
                val libObj = libElement.jsonObject
                val name = libObj["name"]?.jsonPrimitive?.content ?: return@forEach
                libraries.add(Library(name = name))
            }

            println("✅ NeoForge libraries parsed: ${libraries.size}")

            // احذف الـ installer
            installerFile.delete()

            onProgress(1f, "✅ NeoForge ready")
            libraries

        } catch (e: Exception) {
            println("❌ NeoForge install failed: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // ============================================================
    //  FORGE — عبر Installer JAR
    // ============================================================
    /**
     * ✅ Forge — يستخدم Installer الرسمي
     *
     * Installer الـ Forge:
     *   URL: https://maven.minecraftforge.net/net/minecraftforge/forge/{mc}-{forge}/forge-{mc}-{forge}-installer.jar
     */
    private suspend fun installForge(
        mcVersion: String,
        forgeVersion: String,
        onProgress: (Float, String) -> Unit
    ): List<Library> {
        return try {
            onProgress(0.1f, "⬇️ Downloading Forge installer...")

            // ✅ 1. حمّل installer
            val installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$mcVersion-$forgeVersion/forge-$mcVersion-$forgeVersion-installer.jar"
            val installerFile = File(MinecraftPath.launcherDownloads, "forge-$mcVersion-$forgeVersion-installer.jar")
            installerFile.parentFile?.mkdirs()

            println("📥 Forge Installer: $installerUrl")
            URI(installerUrl).toURL().openStream().use { input ->
                installerFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("✅ Installer downloaded: ${installerFile.length()} bytes")

            onProgress(0.4f, "⚙️ Running Forge installer...")

            // ✅ 2. شغّل installer
            val javaExe = findJavaForMc(mcVersion)
                ?: throw Exception("Java not found")

            val process = ProcessBuilder(
                javaExe,
                "-jar", installerFile.absolutePath,
                "--installClient"
            )
                .directory(minecraftDir)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { println("   [Forge] $it") }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw Exception("Forge installer failed with exit code $exitCode")
            }

            onProgress(0.9f, "✅ Forge installed")

            // ✅ 3. اقرأ مكتبات Forge
            val forgeVersionId = "$mcVersion-forge-$forgeVersion"
            val forgeJsonFile = File(minecraftDir, "versions/$forgeVersionId/$forgeVersionId.json")

            if (!forgeJsonFile.exists()) {
                println("⚠️ Forge version.json not found")
                return emptyList()
            }

            val forgeJson = json.parseToJsonElement(forgeJsonFile.readText()).jsonObject
            val libsArray = forgeJson["libraries"]?.jsonArray ?: return emptyList()

            val libraries = mutableListOf<Library>()
            libsArray.forEach { libElement ->
                val libObj = libElement.jsonObject
                val name = libObj["name"]?.jsonPrimitive?.content ?: return@forEach
                libraries.add(Library(name = name))
            }

            println("✅ Forge libraries parsed: ${libraries.size}")

            installerFile.delete()

            onProgress(1f, "✅ Forge ready")
            libraries

        } catch (e: Exception) {
            println("❌ Forge install failed: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * ✅ ابحث عن Java مناسب لتشغيل Installer
     */
    private fun findJavaForMc(mcVersion: String): String? {
        return try {
            val installation = com.viora.launcher.core.util.JavaFinder.findForMinecraft(mcVersion)
            installation?.path
        } catch (e: Exception) {
            println("⚠️ Java not found: ${e.message}")
            null
        }
    }

    // ============================================================
    //  PARENT (Vanilla)
    // ============================================================
    private suspend fun installVanillaVersion(
        mcVersion: String,
        onProgress: (Float, String) -> Unit
    ): Boolean {
        return try {
            val manager = VersionManager(minecraftDir)
            val installer = VersionInstaller(minecraftDir)
            val manifest = manager.fetchManifest()
            val entry = manifest.versions.firstOrNull { it.id == mcVersion } ?: return false
            val details: VersionDetails = manager.fetchVersionDetails(entry.url)
            installer.install(mcVersion, details) { msg, p -> onProgress(p, msg) }
            true
        } catch (e: Exception) {
            println("❌ Vanilla install failed: ${e.message}")
            false
        }
    }

    // ============================================================
    //  UTILS
    // ============================================================
    private fun nameToPath(name: String): String {
        val parts = name.split(":")
        val group = parts[0].replace(".", "/")
        return "$group/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}.jar"
    }

    private fun sanitizeVersionId(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9._-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(64)
            .ifBlank { "modpack-${System.currentTimeMillis()}" }
    }

    private fun copyDirectory(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source)
            val dest = File(target, relative.path)
            if (file.isDirectory) dest.mkdirs()
            else {
                dest.parentFile?.mkdirs()
                file.copyTo(dest, overwrite = true)
            }
        }
    }

    private fun createVersionJson(
        safeName: String,
        mcVersion: String,
        versionDir: File,
        loaderLibraries: List<Library>,
        fabricLoader: String?,
        quiltLoader: String?,
        neoForgeVersion: String?,
        forgeVersion: String?
    ) {
        val mainClass = when {
            fabricLoader != null -> "net.fabricmc.loader.impl.launch.knot.KnotClient"
            quiltLoader != null -> "org.quiltmc.loader.impl.launch.knot.KnotClient"
            neoForgeVersion != null -> "cpw.mods.bootstraplauncher.BootstrapLauncher"
            forgeVersion != null -> "cpw.mods.modlauncher.Launcher"
            else -> "net.minecraft.client.main.Main"
        }

        val libsJson = loaderLibraries.joinToString(",\n      ") { lib ->
            val artifact = lib.downloads?.artifact
            if (artifact != null) {
                """{"name": "${lib.name}", "downloads": {"artifact": {"path": "${artifact.path}", "sha1": "${artifact.sha1}", "size": ${artifact.size}, "url": "${artifact.url}"}}}"""
            } else """{"name": "${lib.name}"}"""
        }

        val versionJson = buildString {
            appendLine("{")
            appendLine("""  "id": "$safeName",""")
            appendLine("""  "inheritsFrom": "$mcVersion",""")
            appendLine("""  "type": "release",""")
            appendLine("""  "mainClass": "$mainClass",""")
            appendLine("""  "libraries": [""")
            if (loaderLibraries.isNotEmpty()) appendLine("      $libsJson")
            appendLine("""  ],""")
            appendLine("""  "arguments": {"game": [], "jvm": []}""")
            appendLine("}")
        }

        File(versionDir, "$safeName.json").writeText(versionJson)
        val oldJar = File(versionDir, "$safeName.jar")
        if (oldJar.exists()) oldJar.delete()
    }
}

// ============================================================
//  MODELS
// ============================================================
@Serializable
data class ModpackIndex(
    val formatVersion: Int = 1,
    val game: String = "minecraft",
    val versionId: String = "",
    val name: String = "",
    val files: List<ModpackFile> = emptyList(),
    val dependencies: Map<String, String> = emptyMap()
)

@Serializable
data class ModpackFile(
    val path: String = "",
    val hashes: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val downloads: List<String> = emptyList(),
    @SerialName("fileSize") val fileSize: Long = 0
)

@Serializable
data class CurseForgeManifest(
    val minecraft: CfMinecraft = CfMinecraft(),
    val manifestType: String = "minecraftModpack",
    val manifestVersion: Int = 1,
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val files: List<CfManifestFile> = emptyList(),
    val overrides: String = "overrides"
)

@Serializable
data class CfMinecraft(
    val version: String = "1.21.1",
    val modLoaders: List<CfModLoader> = emptyList()
)

@Serializable
data class CfModLoader(
    val id: String = "",
    val primary: Boolean = true
)

@Serializable
data class CfManifestFile(
    val projectID: Int = 0,
    val fileID: Int = 0,
    val required: Boolean = true
)