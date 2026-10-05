package com.viora.launcher.core.version

import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.launcher.GameLauncher
import com.viora.launcher.core.launcher.LaunchProfile
import com.viora.launcher.core.util.OSUtils
import com.viora.launcher.core.version.model.VersionDetails
import com.viora.launcher.core.version.model.VersionEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant

class VersionService(
    val minecraftDir: File = File(
        System.getProperty("user.home"),
        "AppData/Roaming/.minecraft"
    )
) {
    val manager = VersionManager(minecraftDir)
    val installer = VersionInstaller(minecraftDir)
    val launcher = GameLauncher()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun loadVersions(): List<VersionEntry> =
        manager.fetchManifest().versions

    suspend fun installVersion(
        versionId: String,
        versionUrl: String,
        onProgress: (String, Float) -> Unit
    ) {
        val details = manager.fetchVersionDetails(versionUrl)
        installer.install(versionId, details, onProgress)
    }

    /**
     * ✅ يشغّل نسخة Minecraft مع دعم:
     *   - inheritsFrom
     *   - isolated instance (instances/)
     *   - TLauncher versions (versions/<name>/mods/)
     *   - Essential mod auth (launcher_accounts.json)
     */
    fun launchVersion(
        versionId: String,
        account: Account,
        javaPath: String = findJavaPath(),
        ramMB: Int = 4096,
        onLog: (String) -> Unit
    ): Process {
        val detailsFile = File(minecraftDir, "versions/$versionId/$versionId.json")
        require(detailsFile.exists()) { "الإصدار $versionId غير مثبت" }

        // ✅ 1. اقرأ الـ JSON
        val rawJson = json.parseToJsonElement(detailsFile.readText()) as JsonObject
        val inheritsFrom = rawJson["inheritsFrom"]?.jsonPrimitive?.contentOrNull

        // ✅ 2. دمج مع parent (إن وُجد)
        val mergedDetails = if (inheritsFrom != null) {
            onLog("🔗 Merging parent version: $inheritsFrom")
            mergeWithParent(versionId, inheritsFrom, onLog)
        } else {
            json.decodeFromString(VersionDetails.serializer(), detailsFile.readText())
        }

        // ✅ 3. تحقق من الحقول المطلوبة
        require(mergedDetails.assetIndex.id.isNotBlank()) {
            "assetIndex مفقود — تأكد من تثبيت $inheritsFrom"
        }

        // ============================================================
        // ✅✅✅ 4. اكتشف gameDir — يدعم 3 بنى
        // ============================================================
        val effectiveGameDir = detectGameDir(versionId, onLog)

        // ============================================================
        // ✅✅✅ 5. NEW: أنشئ launcher_profiles.json + launcher_accounts.json
        //          لكي يعمل Essential mod مع Microsoft Auth
        // ============================================================
        ensureLauncherProfiles(account, versionId, effectiveGameDir, onLog)

        // ✅ 6. ابنِ الـ profile
        val profile = LaunchProfile(
            account = account,
            versionId = versionId,
            minecraftDir = minecraftDir,
            javaPath = javaPath,
            ramMB = ramMB,
            gameDir = effectiveGameDir
        )

        return launcher.launch(profile, mergedDetails, onLog)
    }

    /**
     * ✅ يكتشف مجلد اللعب المناسب حسب البنية:
     *   1. instances/<name>/      (Viora isolated)
     *   2. versions/<name>/       (TLauncher/CurseForge with mods/)
     *   3. .minecraft/            (default)
     */
    private fun detectGameDir(versionId: String, onLog: (String) -> Unit): File? {
        // 1. instances/ — بنية Viora
        val instanceDir = File(minecraftDir, "instances/$versionId")
        if (instanceDir.exists()) {
            val modsDir = File(instanceDir, "mods")
            if (modsDir.exists() && modsDir.listFiles()?.any { it.extension == "jar" } == true) {
                onLog("📁 Isolated instance found: $instanceDir")
                ensureInstanceStructure(instanceDir, onLog)
                return instanceDir
            }
        }

        // 2. versions/<name>/ — بنية TLauncher
        val versionDir = File(minecraftDir, "versions/$versionId")
        if (versionDir.exists()) {
            val modsDir = File(versionDir, "mods")
            if (modsDir.exists() && modsDir.listFiles()?.any { it.extension == "jar" } == true) {
                onLog("📁 TLauncher-style version found with mods: $versionDir")
                return versionDir
            }
        }

        // 3. احتياطي — .minecraft/
        onLog("📁 No mods/ found — using .minecraft/")
        return null
    }

    private fun ensureInstanceStructure(instanceDir: File, onLog: (String) -> Unit) {
        val folders = listOf(
            "mods", "config", "saves", "resourcepacks",
            "shaderpacks", "screenshots", "logs",
            "crash-reports", "downloads", "schematics"
        )
        var created = 0
        folders.forEach { name ->
            val dir = File(instanceDir, name)
            if (!dir.exists()) {
                dir.mkdirs()
                created++
            }
        }
        if (created > 0) {
            onLog("📂 Created $created subdirectories")
        }
    }

    // ============================================================
    //  ✅ NEW: إنشاء ملفات launcher_profiles.json و launcher_accounts.json
    // ============================================================
    /**
     * ✅ Essential mod يحتاج هذين الملفين ليقرأ session الحساب:
     *
     *   1. launcher_profiles.json:
     *      - معلومات الملف النشط (name, lastVersionId, gameDir)
     *      - clientToken placeholder
     *
     *   2. launcher_accounts.json:
     *      - accessToken (JWT حقيقي)
     *      - uuid
     *      - username
     *      - type = "msa" (Microsoft)
     *
     * بدون هذه الملفات، Essential يعرض:
     *   "Unknown account" + "Failed to refresh session"
     */
    private fun ensureLauncherProfiles(
        account: Account,
        versionId: String,
        effectiveGameDir: File?,
        onLog: (String) -> Unit
    ) {
        try {
            // 1. launcher_profiles.json
            val profilesFile = File(minecraftDir, "launcher_profiles.json")
            val gameDirPath = (effectiveGameDir ?: File(minecraftDir, "versions/$versionId"))
                .absolutePath.replace("\\", "\\\\")

            val profilesJson = """
        {
          "profiles": {
            "VioraProfile": {
              "name": "Viora Profile",
              "type": "custom",
              "created": "${Instant.now()}",
              "lastUsed": "${Instant.now()}",
              "icon": "Furnace",
              "lastVersionId": "$versionId",
              "gameDir": "$gameDirPath"
            }
          },
          "selectedProfile": "VioraProfile",
          "clientToken": "00000000000000000000000000000000",
          "authenticationDatabase": {},
          "launcherVersion": { "name": "Viora Launcher", "format": 21 }
        }
        """.trimIndent()
            profilesFile.writeText(profilesJson)
            onLog("✅ Created launcher_profiles.json")

            // 2. launcher_accounts.json
            val accountsFile = File(minecraftDir, "launcher_accounts.json")
            val uuidNoDash = account.uuid.replace("-", "")
            val xuid = extractXuidFromJwt(account.accessToken) ?: "0"

            // ✅ refreshToken من account
            val refreshToken = account.refreshToken

            val accountsJson = """
        {
          "accounts": {
            "$uuidNoDash": {
              "accessToken": "${account.accessToken}",
              "refreshToken": "$refreshToken",
              "username": "${account.username}",
              "uuid": "${account.uuid}",
              "type": "msa",
              "xuid": "$xuid",
              "entitlements": [],
              "properties": [],
              "profile": {
                "id": "${account.uuid}",
                "name": "${account.username}",
                "skins": [],
                "capes": []
              }
            }
          },
          "activeAccount": "$uuidNoDash",
          "mojangClientToken": "00000000000000000000000000000000",
          "formatVersion": 3
        }
        """.trimIndent()
            accountsFile.writeText(accountsJson)
            onLog("✅ Created launcher_accounts.json (xuid=$xuid)")
        } catch (e: Exception) {
            onLog("⚠️ Failed: ${e.message}")
        }
    }

    /**
     * ✅ يستخرج xuid من JWT token (اختياري)
     * للاستخدام في Essential Friends list
     */
    private fun extractXuidFromJwt(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val decoded = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
            val jsonObj = json.parseToJsonElement(decoded) as JsonObject
            jsonObj["xuid"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null
        }
    }

    private fun mergeWithParent(
        versionId: String,
        parentId: String,
        onLog: (String) -> Unit
    ): VersionDetails {
        val parentFile = File(minecraftDir, "versions/$parentId/$parentId.json")
        require(parentFile.exists()) {
            "❌ الإصدار الأصلي $parentId غير مثبت — ثبّته من Versions أولًا"
        }

        onLog("📖 Loading parent: $parentId")
        val parentDetails = json.decodeFromString(
            VersionDetails.serializer(),
            parentFile.readText()
        )

        val childFile = File(minecraftDir, "versions/$versionId/$versionId.json")
        val childJson = json.parseToJsonElement(childFile.readText()) as JsonObject

        val childMainClass = childJson["mainClass"]?.jsonPrimitive?.contentOrNull
            ?: parentDetails.mainClass

        val childLibrariesJson = childJson["libraries"]
        val childLibraries: List<com.viora.launcher.core.version.model.Library> =
            if (childLibrariesJson != null) {
                try {
                    json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.viora.launcher.core.version.model.Library.serializer()
                        ),
                        childLibrariesJson
                    )
                } catch (e: Exception) {
                    onLog("⚠️ فشل قراءة libraries: ${e.message}")
                    emptyList()
                }
            } else emptyList()

        val allLibraries = (childLibraries + parentDetails.libraries)
            .distinctBy { it.name }

        onLog("📚 Merged libraries: ${allLibraries.size}")

        return parentDetails.copy(
            id = versionId,
            mainClass = childMainClass,
            libraries = allLibraries,
            inheritsFrom = parentId
        )
    }

    private fun findJavaPath(): String {
        val javaHome = System.getProperty("java.home") ?: return "java"
        val exe = if (OSUtils.isWindows()) "java.exe" else "java"
        val candidate = File(javaHome, "bin/$exe")
        return if (candidate.exists()) candidate.absolutePath else "java"
    }
}