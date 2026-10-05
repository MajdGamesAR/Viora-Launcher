package com.viora.launcher.core.launcher

import com.viora.launcher.core.util.OSUtils
import com.viora.launcher.core.version.model.VersionDetails
import kotlinx.serialization.json.*
import java.io.File

class GameLauncher {

    private var currentAssetIndex: String = ""

    fun launch(
        profile: LaunchProfile,
        details: VersionDetails,
        onLog: (String) -> Unit = {}
    ): Process {
        currentAssetIndex = details.assets

        val classpath = buildClasspath(profile.versionId, details, profile.minecraftDir, onLog)
        val jvmArgs = buildJvmArgs(details, profile, classpath, onLog)
        val gameArgs = buildGameArgs(details, profile)

        val javaVersion = detectJavaMajorVersion(profile.javaPath)
        onLog("☕ Java major version: $javaVersion")

        val process = if (javaVersion >= 9) {
            launchWithArgFile(profile, details, jvmArgs, gameArgs, onLog)
        } else {
            launchDirect(profile, details, jvmArgs, gameArgs, onLog)
        }

        return process
    }

    // ============================================================
    //  LAUNCH — Java 9+ (argfile)
    // ============================================================
    private fun launchWithArgFile(
        profile: LaunchProfile,
        details: VersionDetails,
        jvmArgs: List<String>,
        gameArgs: List<String>,
        onLog: (String) -> Unit
    ): Process {
        val fullArgs = mutableListOf<String>().apply {
            addAll(jvmArgs)
            add(details.mainClass)
            addAll(gameArgs)
        }

        onLog("🚀 Launching Minecraft ${profile.versionId}")
        onLog("☕ Java: ${profile.javaPath}")
        onLog("💾 RAM: ${profile.ramMB}MB")
        onLog("📦 Asset Index: ${details.assets}")
        onLog("📁 Game Directory: ${profile.effectiveGameDir.absolutePath}")
        onLog("👤 Username: ${profile.account.username}")
        onLog("🆔 UUID: ${profile.account.uuid}")
        if (profile.isIsolated) onLog("🔒 Isolated instance")

        val argFile = File.createTempFile("viora_args_", ".txt")
        argFile.deleteOnExit()
        argFile.writeText(
            fullArgs.joinToString("\n") { arg ->
                if (arg.contains(" ") || arg.contains("\"")) {
                    "\"${arg.replace("\"", "\\\"")}\""
                } else arg
            }
        )

        onLog("📝 Args file: ${argFile.absolutePath} (${argFile.length()} bytes)")
        onLog("📋 Command length: ${fullArgs.joinToString(" ").length} chars")
        onLog("───────────────────────────────────────────")

        val pb = ProcessBuilder(profile.javaPath, "@${argFile.absolutePath}")
        pb.directory(profile.effectiveGameDir)
        pb.redirectErrorStream(true)

        val process = pb.start()

        Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach(onLog)
            }
        }.start()

        return process
    }

    private fun launchDirect(
        profile: LaunchProfile,
        details: VersionDetails,
        jvmArgs: List<String>,
        gameArgs: List<String>,
        onLog: (String) -> Unit
    ): Process {
        onLog("⚠️ Java 8 detected — using direct args (no argfile)")
        onLog("🚀 Launching Minecraft ${profile.versionId}")
        onLog("☕ Java: ${profile.javaPath}")
        onLog("💾 RAM: ${profile.ramMB}MB")
        onLog("📁 Game Directory: ${profile.effectiveGameDir.absolutePath}")
        onLog("👤 Username: ${profile.account.username}")
        onLog("───────────────────────────────────────────")

        val command = mutableListOf<String>().apply {
            add(profile.javaPath)
            addAll(jvmArgs)
            add(details.mainClass)
            addAll(gameArgs)
        }

        val commandLength = command.joinToString(" ").length
        onLog("📋 Command length: $commandLength chars")

        if (commandLength > 8000 && OSUtils.isWindows()) {
            onLog("⚠️ Warning: Command is very long — may fail on Windows")
        }

        val pb = ProcessBuilder(command)
        pb.directory(profile.effectiveGameDir)
        pb.redirectErrorStream(true)

        val process = pb.start()

        Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach(onLog)
            }
        }.start()

        return process
    }

    private fun detectJavaMajorVersion(javaPath: String): Int {
        return try {
            val process = ProcessBuilder(javaPath, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val regex = """"(\d+)(?:\.(\d+))?[^"]*"""".toRegex()
            val match = regex.find(output) ?: return 8
            val first = match.groupValues[1].toIntOrNull() ?: 8
            if (first == 1) {
                match.groupValues[2].toIntOrNull() ?: 8
            } else first
        } catch (e: Exception) {
            8
        }
    }

    // ============================================================
    //  CLASSPATH
    // ============================================================
    private fun buildClasspath(
        versionId: String,
        details: VersionDetails,
        minecraftDir: File,
        onLog: (String) -> Unit
    ): String {
        val classpath = mutableListOf<String>()
        val seenPaths = mutableSetOf<String>()

        val clientJar = findClientJar(versionId, details, minecraftDir)
            ?: throw IllegalStateException("❌ client.jar not found for version $versionId")

        classpath.add(clientJar.absolutePath)
        try { seenPaths.add(clientJar.canonicalPath) } catch (_: Exception) {}

        // ✅ اجمع كل الـ JARs في مجموعات (library → version) لتجنب التكرار
        val groupedLibs = mutableMapOf<String, MutableList<Pair<String, File>>>()

        for (lib in details.libraries) {
            if (!lib.isAllowed()) continue

            val libPath = lib.downloads?.artifact?.path
                ?: lib.resolvePath()
                ?: nameToLibraryPath(lib.name)

            if (libPath.isNullOrBlank()) continue

            val libFile = File(minecraftDir, "libraries/$libPath")
            if (!libFile.exists() || libFile.length() == 0L) {
                onLog("⚠️ Missing library: ${libFile.name}")
                continue
            }

            val relPath = libPath.replace('\\', '/')
            val parts = relPath.split("/")

            if (parts.size >= 3) {
                val version = parts[parts.size - 2]
                val artifact = parts[parts.size - 3]
                val group = parts.subList(0, parts.size - 3).joinToString("/")
                val key = "$group/$artifact"

                groupedLibs.getOrPut(key) { mutableListOf() }
                    .add(version to libFile)
            } else {
                val canonical = try { libFile.canonicalPath } catch (e: Exception) { libFile.absolutePath }
                if (canonical !in seenPaths) {
                    classpath.add(libFile.absolutePath)
                    seenPaths.add(canonical)
                }
            }
        }

        // ✅ لكل مجموعة، خذ الأحدث فقط
        for ((_, versions) in groupedLibs) {
            val sorted = versions.sortedByDescending { (version, _) ->
                versionCompareKey(version)
            }

            val (_, latestJar) = sorted.first()
            val canonical = try {
                latestJar.canonicalPath
            } catch (e: Exception) {
                latestJar.absolutePath
            }

            if (canonical !in seenPaths) {
                classpath.add(latestJar.absolutePath)
                seenPaths.add(canonical)
            }
        }

        val mainClass = details.mainClass
        if (mainClass.contains("fabricmc") || mainClass.contains("quiltmc")) {
            ensureFabricLoader(classpath, minecraftDir, versionId, onLog)
        }

        return classpath.joinToString(File.pathSeparator)
    }

    private fun nameToLibraryPath(name: String): String? {
        val parts = name.split(":")
        if (parts.size < 3) return null

        val group = parts[0].replace(".", "/")
        val artifact = parts[1]
        val version = parts[2]
        val classifier = if (parts.size >= 4) parts[3] else null

        val fileName = if (classifier != null && classifier.isNotBlank()) {
            "$artifact-$version-$classifier.jar"
        } else {
            "$artifact-$version.jar"
        }

        return "$group/$artifact/$version/$fileName"
    }

    private fun ensureFabricLoader(
        classpath: MutableList<String>,
        minecraftDir: File,
        versionId: String,
        onLog: (String) -> Unit
    ) {
        val fabricDir = File(minecraftDir, "libraries/net/fabricmc/fabric-loader")
        val existing = fabricDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("fabric-loader-") && it.extension == "jar" }
            .maxByOrNull { it.lastModified() }

        if (existing != null && existing.exists()) {
            if (!classpath.contains(existing.absolutePath)) {
                classpath.add(existing.absolutePath)
                onLog("✅ Added Fabric Loader: ${existing.name}")
            }
            return
        }

        onLog("⚠️ Fabric Loader not found in libraries!")
    }

    private fun findClientJar(
        versionId: String,
        details: VersionDetails,
        minecraftDir: File
    ): File? {
        val ownJar = File(minecraftDir, "versions/$versionId/$versionId.jar")
        if (ownJar.exists() && ownJar.length() > 0L) return ownJar

        val parentId = details.inheritsFrom
        if (parentId != null) {
            val parentJar = File(minecraftDir, "versions/$parentId/$parentId.jar")
            if (parentJar.exists() && parentJar.length() > 0L) return parentJar
        }

        val versionDir = File(minecraftDir, "versions/$versionId")
        return versionDir.listFiles()?.firstOrNull {
            it.extension == "jar" && it.length() > 0L
        }
    }

    // ============================================================
    //  JVM ARGS — النسخة النهائية
    // ============================================================
    private fun buildJvmArgs(
        details: VersionDetails,
        profile: LaunchProfile,
        classpath: String,
        onLog: (String) -> Unit
    ): List<String> {
        val args = mutableListOf<String>()
        val nativesDir = profile.nativesDir.absolutePath

        args.add("-Xmx${profile.ramMB}M")
        args.add("-Xms${profile.minRamMB}M")
        args.add("-Djava.library.path=$nativesDir")

        val mainClass = details.mainClass
        val isModLauncher = mainClass.contains("bootstraplauncher") ||
                mainClass.contains("modlauncher") ||
                mainClass.contains("cpw.mods")

        if (isModLauncher) {
            onLog("🔧 Applying NeoForge/Forge JVM args...")

            // ═══════════════════════════════════════════════════════════
            //  add-opens للـ classpath (ALL-UNNAMED)
            // ═══════════════════════════════════════════════════════════
            args.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
            args.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
            args.add("--add-opens=java.base/java.util=ALL-UNNAMED")
            args.add("--add-opens=java.base/java.nio=ALL-UNNAMED")
            args.add("--add-opens=java.base/java.io=ALL-UNNAMED")
            args.add("--add-opens=java.base/java.net=ALL-UNNAMED")
            args.add("--add-opens=java.base/java.util.jar=ALL-UNNAMED")
            args.add("--add-opens=java.base/java.util.zip=ALL-UNNAMED")
            args.add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")

            // ═══════════════════════════════════════════════════════════
            //  ✅ add-opens للـ modules (بدون modules غير موجودة)
            // ═══════════════════════════════════════════════════════════
            val moduleOpens = listOf(
                "cpw.mods.securejarhandler",
                "cpw.mods.bootstraplauncher",
                "cpw.mods.modlauncher",
                "net.neoforged.fancymodloader",
                "net.neoforged.bus",
                "net.neoforged.accesstransformer",
                "net.neoforged.accesstransformer.modlauncher",
                "fml_loader"
            )

            for (module in moduleOpens) {
                args.add("--add-opens=java.base/java.lang.invoke=$module")
                args.add("--add-opens=java.base/java.lang.reflect=$module")
                args.add("--add-opens=java.base/java.util=$module")
                args.add("--add-opens=java.base/java.nio=$module")
                args.add("--add-opens=java.base/sun.nio.ch=$module")
            }

            // ═══════════════════════════════════════════════════════════
            //  add-exports
            // ═══════════════════════════════════════════════════════════
            args.add("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
            args.add("--add-exports=java.base/sun.security.util=cpw.mods.securejarhandler")
            args.add("--add-exports=java.base/sun.security.util=cpw.mods.bootstraplauncher")
            args.add("--add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED")

            // ═══════════════════════════════════════════════════════════
            //  Module Path
            // ═══════════════════════════════════════════════════════════
            val modulePath = buildModulePath(profile.minecraftDir, onLog)
            if (modulePath.isNotEmpty()) {
                args.add("--module-path")
                args.add(modulePath)
                args.add("--add-modules=ALL-MODULE-PATH")
                onLog("📚 Module path: ${modulePath.split(File.pathSeparator).size} entries")
            }

            args.add("-Djava.net.preferIPv4Stack=true")
            args.add("-DlegacyClassPath=$classpath")
        }

        args.add("-cp")
        args.add(classpath)

        details.arguments?.jvm?.forEach { element ->
            processJvmArgument(element, profile, nativesDir, classpath, args)
        }

        args.addAll(profile.customJvmArgs)
        return args
    }

    // ============================================================
    //  MODULE PATH — النسخة النهائية مع LWJGL
    // ============================================================
    private fun buildModulePath(
        minecraftDir: File,
        onLog: (String) -> Unit
    ): String {
        val libsDir = File(minecraftDir, "libraries")

        val basePaths = listOf(
            // ═══ BootstrapLauncher + Module System ═══
            "cpw/mods/securejarhandler",
            "cpw/mods/bootstraplauncher",
            "cpw/mods/modlauncher",

            // ═══ Command-line parser ═══
            "net/sf/jopt-simple",

            // ═══ ASM ═══
            "org/ow2/asm",

            // ═══ JarJar ═══
            "net/neoforged/JarJarSelector",
            "net/neoforged/JarJarMetadata",
            "net/neoforged/JarJarFileSystems",

            // ═══ NeoForge core ═══
            "net/neoforged/bus",
            "net/neoforged/coremods",
            "net/neoforged/mergetool",
            "net/neoforged/fancymodloader",
            "net/neoforged/accesstransformers",

            // ═══ Logging ═══
            "org/slf4j/slf4j-api",
            "org/apache/logging/log4j",

            // ═══ Apache ═══
            "org/apache/commons/commons-lang3",
            "org/antlr/antlr4-runtime",

            // ═══ Utilities ═══
            "net/jodah/typetools",
            "com/electronwill/night-config",
            "net/minecrell/terminalconsoleappender",
            "org/jline",
            "org/openjdk/nashorn/nashorn-core",

            // ═══════════════════════════════════════════════════════════
            //  ✅ LWJGL — يحتاجها Sodium ومودات الرسوميات
            //  (نستثني natives تلقائياً في shouldSkip)
            // ═══════════════════════════════════════════════════════════
            "org/lwjgl/lwjgl",
            "org/lwjgl/lwjgl-glfw",
            "org/lwjgl/lwjgl-opengl",
            "org/lwjgl/lwjgl-stb",
            "org/lwjgl/lwjgl-tinyfd",
            "org/lwjgl/lwjgl-jemalloc",
            "org/lwjgl/lwjgl-openal"
        )

        // ═══════════════════════════════════════════════════════════
        //  الخطوة 1: اجمع كل JARs في مجموعات (library → versions)
        // ═══════════════════════════════════════════════════════════
        val groupedJars = mutableMapOf<String, MutableList<Pair<String, File>>>()

        for (basePath in basePaths) {
            val baseDir = File(libsDir, basePath)
            if (!baseDir.exists()) {
                onLog("   ⚠️ Not found: $basePath")
                continue
            }

            baseDir.walkTopDown()
                .filter { it.isFile && it.extension == "jar" }
                .forEach { jar ->
                    val relPath = jar.relativeTo(libsDir).path.replace('\\', '/')

                    val parts = relPath.split("/")
                    if (parts.size >= 3) {
                        val version = parts[parts.size - 2]
                        val artifact = parts[parts.size - 3]
                        val group = parts.subList(0, parts.size - 3).joinToString("/")
                        val key = "$group/$artifact"

                        groupedJars.getOrPut(key) { mutableListOf() }
                            .add(version to jar)
                    }
                }
        }

        // ═══════════════════════════════════════════════════════════
        //  الخطوة 2: لكل مجموعة، جرّب الإصدارات من الأحدث للأقدم
        // ═══════════════════════════════════════════════════════════
        val modules = mutableListOf<String>()
        var skipped = 0

        for ((key, versions) in groupedJars) {
            onLog("   📦 $key (${versions.size} version(s))")

            val sorted = versions.sortedWith(
                compareByDescending<Pair<String, File>> {
                    versionCompareKey(it.first)
                }.thenByDescending { (_, file) ->
                    file.lastModified()
                }
            )

            var selectedJar: File? = null
            var selectedVersion: String? = null

            for ((version, jar) in sorted) {
                val name = jar.name.lowercase()
                val relPath = jar.relativeTo(libsDir).path.replace('\\', '/').lowercase()

                val shouldSkip = when {
                    // ═══ ASM-ALL conflict ═══
                    name.contains("asm-all") -> "ASM-ALL conflict"

                    // ═══ log4j-slf4j 1.x conflict ═══
                    relPath.contains("log4j-slf4j")
                            && !relPath.contains("log4j-slf4j2")
                        -> "log4j-slf4j (1.x) conflict"

                    // ═══ typetools 0.8.3 بدون module-info ═══
                    relPath.contains("net/jodah/typetools")
                            && version == "0.8.3"
                        -> "typetools 0.8.3 (no module-info)"

                    // ═══ jline 3.12.1 القديم ═══
                    relPath.contains("org/jline/jline/")
                        -> "jline 3.12.1 (superseded by jline-reader)"

                    // ═══════════════════════════════════════════════════
                    //  ✅ LWJGL natives — استثنِها (تُستخدم في classpath فقط)
                    // ═══════════════════════════════════════════════════
                    name.contains("-natives-") -> "LWJGL natives"
                    name.contains("-natives.") -> "LWJGL natives"

                    // ═══ JARs عامة ═══
                    name.endsWith("-all.jar") -> "shaded -all"
                    name.endsWith("-shaded.jar") -> "shaded"
                    name.contains("sources") -> "sources"
                    name.contains("javadoc") -> "javadoc"
                    name.contains("all-deps") -> "all-deps"

                    else -> null
                }

                if (shouldSkip != null) {
                    onLog("      ⏭️ SKIPPED: ${jar.name} → $shouldSkip")
                    skipped++
                    continue
                }

                selectedJar = jar
                selectedVersion = version
                break
            }

            if (selectedJar == null) {
                onLog("      ❌ All versions skipped")
                continue
            }

            modules.add(selectedJar.absolutePath)
            onLog("      ✅ ADDED: $selectedVersion (from ${versions.size} versions)")

            if (versions.size > 1) {
                skipped += (versions.size - 1)
            }
        }

        onLog("📚 Module path: ${modules.size} entries (skipped: $skipped)")

        return modules.distinct().joinToString(File.pathSeparator)
    }

    /**
     * ✅ يحوّل نص الإصدار إلى String قابل للمقارنة
     */
    private fun versionCompareKey(version: String): String {
        return version
            .split(".", "-", "_")
            .joinToString(".") { part ->
                (part.toIntOrNull() ?: 0).toString().padStart(6, '0')
            }
    }

    private fun processJvmArgument(
        element: JsonElement,
        profile: LaunchProfile,
        nativesDir: String,
        classpath: String,
        target: MutableList<String>
    ) {
        when (element) {
            is JsonPrimitive -> if (element.isString) {
                val content = element.content

                if (content.contains("FabricMcEmu")) return

                when {
                    content == "-cp" ||
                            content == "-classpath" ||
                            content == "\${classpath}" -> { }
                    content == "-Djava.library.path=\${natives_directory}" -> { }
                    content.contains("\${launcher_name}") -> {
                        target.add("-Dminecraft.launcher.brand=VioraLauncher")
                    }
                    content.contains("\${launcher_version}") -> {
                        target.add("-Dminecraft.launcher.version=1.0.0")
                    }
                    content.contains("\${") -> {
                        val replaced = replacePlaceholders(content, profile, nativesDir)
                        if (!replaced.contains("\${") && replaced.isNotBlank()) {
                            target.add(replaced)
                        }
                    }
                    else -> target.add(content)
                }
            }
            is JsonObject -> {
                val values = element["values"]
                if (values is JsonArray) {
                    val hasEmu = values.any {
                        (it as? JsonPrimitive)?.contentOrNull?.contains("FabricMcEmu") == true
                    }
                    if (hasEmu) return
                }

                val rules = element["rules"]?.jsonArray
                val value = element["value"]
                if (shouldInclude(rules)) {
                    when (value) {
                        is JsonPrimitive -> processJvmArgument(
                            value, profile, nativesDir, classpath, target
                        )
                        is JsonArray -> value.forEach {
                            processJvmArgument(it, profile, nativesDir, classpath, target)
                        }
                        else -> { }
                    }
                }
            }
            else -> { }
        }
    }

    private fun buildGameArgs(
        details: VersionDetails,
        profile: LaunchProfile
    ): List<String> {
        val args = mutableListOf<String>()

        if (details.arguments != null) {
            details.arguments.game.forEach { element ->
                processGameArgument(element, profile, args)
            }
        } else if (details.legacyArguments != null) {
            details.legacyArguments.split(" ").forEach {
                val replaced = replacePlaceholders(it, profile, "")
                if (replaced.isNotBlank() && !replaced.contains("\${")) {
                    args.add(replaced)
                }
            }
        }

        val cleanArgs = args.filter {
            it != "--demo" && it != "--demoMode"
        }.toMutableList()

        cleanArgs.addAll(profile.customGameArgs)
        return cleanArgs
    }

    private fun processGameArgument(
        element: JsonElement,
        profile: LaunchProfile,
        target: MutableList<String>
    ) {
        when (element) {
            is JsonPrimitive -> if (element.isString) {
                if (element.content.contains("quickPlay", true)) return
                val replaced = replacePlaceholders(element.content, profile, "")
                if (replaced.isNotBlank() && !replaced.contains("\${")) {
                    target.add(replaced)
                }
            }
            is JsonObject -> {
                val rules = element["rules"]?.jsonArray
                val value = element["value"]
                if (shouldInclude(rules)) {
                    when (value) {
                        is JsonPrimitive -> {
                            if (value.content.contains("quickPlay", true)) return
                            val replaced = replacePlaceholders(value.content, profile, "")
                            if (replaced.isNotBlank() && !replaced.contains("\${")) {
                                target.add(replaced)
                            }
                        }
                        is JsonArray -> value.forEach {
                            val content = it.jsonPrimitive.content
                            if (content.contains("quickPlay", true)) return@forEach
                            val replaced = replacePlaceholders(content, profile, "")
                            if (replaced.isNotBlank() && !replaced.contains("\${")) {
                                target.add(replaced)
                            }
                        }
                        else -> { }
                    }
                }
            }
            else -> { }
        }
    }

    private fun replacePlaceholders(
        arg: String,
        profile: LaunchProfile,
        nativesDir: String
    ): String {
        return arg
            .replace("\${auth_player_name}", profile.account.username)
            .replace("\${version_name}", profile.versionId)
            .replace("\${game_directory}", profile.effectiveGameDir.absolutePath)
            .replace("\${assets_root}", profile.assetsDir.absolutePath)
            .replace("\${assets_index_name}", currentAssetIndex)
            .replace("\${auth_uuid}", profile.account.uuid.replace("-", ""))
            .replace("\${auth_access_token}", profile.account.accessToken)
            .replace("\${user_type}", if (profile.account.isMicrosoft) "msa" else "legacy")
            .replace("\${version_type}", "release")
            .replace("\${natives_directory}", nativesDir)
            .replace("\${launcher_name}", "VioraLauncher")
            .replace("\${launcher_version}", "1.0.0")
            .replace("\${resolution_width}", profile.width.toString())
            .replace("\${resolution_height}", profile.height.toString())
            .replace("\${user_properties}", "{}")
            .replace("\${clientid}", "viora")
            .replace("\${auth_xuid}", "0")
    }

    private fun shouldInclude(rules: JsonArray?): Boolean {
        if (rules == null || rules.isEmpty()) return true
        var allowed = false
        rules.forEach { rule ->
            val obj = rule.jsonObject
            val action = obj["action"]?.jsonPrimitive?.content ?: return@forEach
            val os = obj["os"]?.jsonObject
            val osMatches = if (os == null) true else {
                val osName = os["name"]?.jsonPrimitive?.content
                osName == null || osName == OSUtils.currentOS().id
            }
            if (osMatches) allowed = action == "allow"
        }
        return allowed
    }
}