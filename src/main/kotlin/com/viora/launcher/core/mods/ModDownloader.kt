package com.viora.launcher.core.mods

import com.viora.launcher.core.util.MinecraftPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

object ModDownloader {

    /**
     * ✅ تحميل ملف مود إلى مجلد mods الصحيح
     */
    suspend fun downloadMod(
        downloadUrl: String,
        fileName: String,
        gameVersion: String = "1.21.1",
        versionId: String? = null,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        try {
            // ✅ اختيار المجلد الصحيح
            val modsDir = if (versionId != null) {
                File(MinecraftPath.versions, "$versionId/mods").apply { mkdirs() }
            } else {
                MinecraftPath.mods
            }

            if (!modsDir.exists()) modsDir.mkdirs()

            val targetFile = File(modsDir, fileName)

            // إذا موجود، تجاهل
            if (targetFile.exists() && targetFile.length() > 0) {
                println("⚠️ Already exists: ${targetFile.name}")
                return@withContext targetFile
            }

            println("⬇️ Downloading: $downloadUrl")
            println("📁 Destination: ${targetFile.absolutePath}")

            // ✅ تحميل مع تتبع التقدم
            val url = URI(downloadUrl).toURL()
            val connection = url.openConnection()
            connection.connect()

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.getInputStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }

            onProgress(1f)
            println("✅ Downloaded: ${targetFile.name} (${targetFile.length()} bytes)")
            targetFile

        } catch (e: Exception) {
            println("❌ Download failed: ${e.message}")
            null
        }
    }

    /**
     * ✅ احذف موداً معيّناً
     */
    fun deleteMod(fileName: String, versionId: String? = null): Boolean {
        return try {
            val modsDir = if (versionId != null) {
                File(MinecraftPath.versions, "$versionId/mods")
            } else {
                MinecraftPath.mods
            }
            File(modsDir, fileName).delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ✅ اجلب كل المودات المثبتة (عامة)
     */
    fun getInstalledMods(): List<File> {
        return if (MinecraftPath.mods.exists()) {
            MinecraftPath.mods.listFiles()
                ?.filter { it.extension == "jar" }
                ?: emptyList()
        } else emptyList()
    }

    /**
     * ✅ اجلب كل المودات المثبتة لإصدار معيّن
     */
    fun getInstalledMods(versionId: String): List<File> {
        val modsDir = File(MinecraftPath.versions, "$versionId/mods")
        return if (modsDir.exists()) {
            modsDir.listFiles()?.filter { it.extension == "jar" } ?: emptyList()
        } else emptyList()
    }
}