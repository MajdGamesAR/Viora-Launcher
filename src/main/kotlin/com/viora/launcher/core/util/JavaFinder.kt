package com.viora.launcher.core.util

import java.io.File

/**
 * ✅ البحث عن Java المثبت + اختيار الأنسب لإصدار Minecraft
 *
 * ⚠️ قواعد مهمة:
 *   - MC 1.20.5+ → Java 21 (بالضبط، لا 22+)
 *   - MC 1.18-1.20.4 → Java 17
 *   - MC 1.17 → Java 16
 *   - MC 1.16- → Java 8
 */
object JavaFinder {

    data class JavaInstallation(
        val path: String,
        val version: Int,
        val vendor: String,
        val is64Bit: Boolean = true
    ) {
        val displayName: String
            get() = "Java $version ($vendor)"

        override fun toString(): String = "$displayName — $path"
    }

    // ============================================================
    //  FIND ALL
    // ============================================================
    fun findAll(): List<JavaInstallation> {
        val results = mutableListOf<JavaInstallation>()
        val seenPaths = mutableSetOf<String>()

        fun addIfNew(install: JavaInstallation?) {
            if (install == null) return
            val key = install.path.lowercase()
            if (seenPaths.add(key)) results.add(install)
        }

        // 1. JAVA_HOME
        System.getenv("JAVA_HOME")?.let { javaHome ->
            val exe = File(javaHome, "bin/${javaExe()}")
            if (exe.exists()) addIfNew(parseVersion(exe.absolutePath))
        }

        // 2. java في PATH
        try {
            val process = ProcessBuilder("java", "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val version = extractVersionFromOutput(output)
            if (version > 0) {
                addIfNew(JavaInstallation("java", version, extractVendor(output)))
            }
        } catch (_: Exception) { }

        // 3. Windows
        if (OSUtils.isWindows()) {
            val windowsPaths = listOf(
                "C:\\Program Files\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft",
                "C:\\Program Files\\Amazon Corretto",
                "C:\\Program Files\\Zulu",
                "C:\\Program Files\\BellSoft",
                "C:\\Program Files\\Semeru",
                "C:\\Program Files\\Liberica",
                "C:\\Program Files (x86)\\Java",
                "C:\\Program Files (x86)\\Eclipse Adoptium",
                "${System.getProperty("user.home")}\\.jdks"
            )

            windowsPaths.forEach { basePath ->
                val baseDir = File(basePath)
                if (!baseDir.exists() || !baseDir.isDirectory) return@forEach

                baseDir.listFiles()?.forEach { dir ->
                    if (!dir.isDirectory) return@forEach
                    val exe = File(dir, "bin/java.exe")
                    if (exe.exists()) addIfNew(parseVersion(exe.absolutePath))
                }
            }
        }

        // 4. macOS
        if (OSUtils.isMacOS()) {
            val macPaths = listOf(
                "/Library/Java/JavaVirtualMachines",
                "${System.getProperty("user.home")}/Library/Java/JavaVirtualMachines"
            )
            macPaths.forEach { basePath ->
                val baseDir = File(basePath)
                if (!baseDir.exists()) return@forEach
                baseDir.listFiles()?.forEach { dir ->
                    val exe = File(dir, "Contents/Home/bin/java")
                    if (exe.exists()) addIfNew(parseVersion(exe.absolutePath))
                }
            }
        }

        // 5. Linux
        if (OSUtils.isLinux()) {
            val linuxPaths = listOf(
                "/usr/lib/jvm",
                "/usr/java",
                "/opt/java",
                "/opt/jdk",
                "${System.getProperty("user.home")}/.jdks"
            )
            linuxPaths.forEach { basePath ->
                val baseDir = File(basePath)
                if (!baseDir.exists()) return@forEach
                baseDir.listFiles()?.forEach { dir ->
                    val exe = File(dir, "bin/java")
                    if (exe.exists()) addIfNew(parseVersion(exe.absolutePath))
                }
            }
        }

        // ✅ فلترة: احذف أي Java أقدم من 8
        return results
            .filter { it.version >= 8 }
            .sortedByDescending { it.version }
    }

    // ============================================================
    //  FIND FOR MINECRAFT — الأهم!
    // ============================================================
    fun findForMinecraft(mcVersion: String): JavaInstallation? {
        val required = getRequiredJavaVersion(mcVersion)
        val all = findAll()

        if (all.isEmpty()) {
            println("❌ No Java installations found!")
            return null
        }

        println("═══════════════════════════════════════")
        println("☕ Java Selection for Minecraft $mcVersion")
        println("   Required: Java $required")
        println("   Available Java installations:")
        all.forEach { println("      • ${it.displayName}") }
        println("───────────────────────────────────────")

        // ✅ الأولوية 1: الإصدار المطلوب بالضبط
        all.firstOrNull { it.version == required }?.let {
            println("✅ EXACT MATCH: ${it.displayName}")
            println("   Path: ${it.path}")
            println("═══════════════════════════════════════")
            return it
        }

        // ✅ الأولوية 2: النطاق المقبول (بدون تجاوز)
        val (minVersion, maxVersion) = getAcceptableRange(required)
        val acceptable = all.filter { it.version in minVersion..maxVersion }
            .sortedByDescending { it.version }

        if (acceptable.isNotEmpty()) {
            val chosen = acceptable.first()
            println("⚠️  No exact Java $required — using closest: ${chosen.displayName}")
            println("   Path: ${chosen.path}")
            println("═══════════════════════════════════════")
            return chosen
        }

        // ✅ الأولوية 3: fallback (لكن نحذّر)
        val fallback = all.maxByOrNull { it.version }
        println("❌ NO COMPATIBLE JAVA FOUND!")
        println("   Falling back to: ${fallback?.displayName}")
        println("   ⚠️  Install Java $required from:")
        println("      https://adoptium.net/temurin/releases/?version=$required")
        println("═══════════════════════════════════════")
        return fallback
    }

    // ============================================================
    //  VERSION REQUIREMENTS
    // ============================================================
    private fun getRequiredJavaVersion(mcVersion: String): Int {
        // ✅ إذا لم يبدأ بـ "1." → اقرأ النسخة الحقيقية من JSON
        if (!mcVersion.matches(Regex("^\\d+\\.\\d+.*"))) {
            val realVersion = readRealMcVersion(mcVersion)
            if (realVersion != null) {
                println("📖 Resolved '$mcVersion' → '$realVersion'")
                return getRequiredJavaVersion(realVersion)
            }
            // ⚠️ fallback: للأسماء المجهولة، افترض Java 21
            println("⚠️ Unknown version '$mcVersion' — defaulting to Java 21")
            return 21
        }

        val clean = mcVersion
            .substringBefore("-")
            .substringBefore("+")

        return when {
            // MC 1.20.5+ (بما فيها 1.21.x)
            clean.startsWith("1.21") -> 21
            clean.startsWith("1.20.5") || clean.startsWith("1.20.6") -> 21
            clean.startsWith("1.20") -> 17
            clean.startsWith("1.19") -> 17
            clean.startsWith("1.18") -> 17
            clean.startsWith("1.17") -> 16
            else -> 8
        }
    }

    private fun getAcceptableRange(required: Int): Pair<Int, Int> {
        return when (required) {
            21 -> 17 to 21    // ⚠️ لا 22+
            17 -> 17 to 21
            16 -> 16 to 17
            8  -> 8 to 17
            else -> 8 to 21
        }
    }

    /**
     * ✅ يقرأ إصدار MC الحقيقي من instance JSON
     * مثال: COBBLEVERSE.json → "inheritsFrom": "1.21.1" → "1.21.1"
     */
    fun readRealMcVersion(versionId: String): String? {
        return try {
            val mcDir = OSUtils.getMinecraftDir()

            // جرّب versions/ أولاً
            var jsonFile = File(mcDir, "versions/$versionId/$versionId.json")
            if (!jsonFile.exists()) {
                // جرّب instances/
                jsonFile = File(mcDir, "instances/$versionId/$versionId.json")
            }
            if (!jsonFile.exists()) return null

            val content = jsonFile.readText()

            // ابحث عن inheritsFrom أولاً
            val inheritRegex = """"inheritsFrom"\s*:\s*"([^"]+)"""".toRegex()
            inheritRegex.find(content)?.groupValues?.get(1)?.let {
                return it
            }

            // إذا لا inheritsFrom، اقرأ id
            val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
            idRegex.find(content)?.groupValues?.get(1)

        } catch (e: Exception) {
            println("⚠️ Failed to read MC version: ${e.message}")
            null
        }
    }

    // ============================================================
    //  PARSE VERSION
    // ============================================================
    private fun parseVersion(path: String): JavaInstallation? {
        return try {
            val process = ProcessBuilder(path, "-version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val version = extractVersionFromOutput(output)
            if (version <= 0) return null

            JavaInstallation(
                path = path,
                version = version,
                vendor = extractVendor(output),
                is64Bit = output.contains("64-Bit") || output.contains("x86_64")
            )
        } catch (_: Exception) { null }
    }

    private fun extractVersionFromOutput(output: String): Int {
        val quotedRegex = """"(\d+)(?:\.(\d+))?[^"]*"""".toRegex()
        val match = quotedRegex.find(output)

        if (match != null) {
            val first = match.groupValues[1].toIntOrNull() ?: 0
            if (first == 1) {
                val second = match.groupValues[2].toIntOrNull() ?: 0
                return if (second > 0) second else 1
            }
            return first
        }

        return """(\d+)""".toRegex().find(output)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractVendor(output: String): String {
        return when {
            output.contains("Temurin", true) -> "Temurin"
            output.contains("Adoptium", true) -> "Adoptium"
            output.contains("Oracle", true) -> "Oracle"
            output.contains("Microsoft", true) -> "Microsoft"
            output.contains("Amazon", true) || output.contains("Corretto", true) -> "Corretto"
            output.contains("Zulu", true) -> "Azul"
            output.contains("BellSoft", true) || output.contains("Liberica", true) -> "Liberica"
            output.contains("Semeru", true) -> "Semeru"
            output.contains("GraalVM", true) -> "GraalVM"
            output.contains("OpenJDK", true) -> "OpenJDK"
            else -> "Unknown"
        }
    }

    private fun javaExe(): String = if (OSUtils.isWindows()) "java.exe" else "java"
}