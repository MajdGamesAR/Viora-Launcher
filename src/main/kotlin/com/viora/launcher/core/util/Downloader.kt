package com.viora.launcher.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class DownloadTask(
    val url: String,
    val destination: File,
    val sha1: String? = null,
    val size: Long = 0
)

class Downloader {

    data class Progress(
        val currentFile: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val filesCompleted: Int,
        val totalFiles: Int
    ) {
        val percentage: Float
            get() = if (totalFiles > 0) filesCompleted.toFloat() / totalFiles else 0f
    }

    /**
     * تحميل ملف واحد باستخدام java.net.URL (بديل Ktor)
     * أكثر موثوقية مع الملفات الكبيرة والشبكات البطيئة
     */
    suspend fun download(
        url: String,
        destination: File,
        expectedSha1: String? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            destination.parentFile?.mkdirs()

            // ✅ إذا موجود وصحيح، تجاهل التحميل
            if (destination.exists() && destination.length() > 0) {
                if (expectedSha1 == null || verifySha1(destination, expectedSha1)) {
                    onProgress(destination.length(), destination.length())
                    return@withContext true
                } else {
                    println("⚠️ SHA1 mismatch — re-downloading: ${destination.name}")
                    destination.delete()
                }
            }

            println("⬇️ Downloading: ${destination.name}")
            println("   URL: $url")

            // ✅ فتح الاتصال
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "VioraLauncher/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                println("❌ HTTP $responseCode for $url")
                return@withContext false
            }

            val contentLength = connection.contentLengthLong
            println("   Size: $contentLength bytes")

            // ✅ تحميل مع progress
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(65536) // 64KB
                    var downloaded = 0L
                    var lastUpdate = System.currentTimeMillis()
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } > 0) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 200) {
                            onProgress(downloaded, contentLength)
                            lastUpdate = now
                        }
                    }
                    onProgress(downloaded, contentLength)
                }
            }

            // ✅ التحقق من الحجم
            val actualSize = destination.length()
            if (contentLength > 0 && actualSize != contentLength) {
                println("❌ Size mismatch: expected $contentLength, got $actualSize")
                destination.delete()
                return@withContext false
            }

            // ✅ التحقق من SHA1
            if (expectedSha1 != null) {
                if (!verifySha1(destination, expectedSha1)) {
                    val actual = calculateSha1(destination)
                    println("❌ SHA1 mismatch for ${destination.name}")
                    println("   Expected: $expectedSha1")
                    println("   Actual:   $actual")
                    destination.delete()
                    return@withContext false
                }
            }

            println("✅ Downloaded: ${destination.name} (${actualSize} bytes)")
            return@withContext true

        } catch (e: Exception) {
            println("❌ Download error: ${e.message}")
            println("   URL: $url")
            e.printStackTrace()
            destination.delete()
            return@withContext false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * تحميل مجموعة ملفات بالتوازي
     */
    suspend fun downloadBatch(
        files: List<DownloadTask>,
        concurrency: Int = 5,
        onProgress: (Progress) -> Unit
    ) {
        val total = files.size
        var completed = 0
        val mutex = Mutex()

        println("📦 Starting batch download: $total files (concurrency: $concurrency)")

        coroutineScope {
            files.chunked(concurrency).forEach { chunk ->
                chunk.map { task ->
                    async(Dispatchers.IO) {
                        val ok = download(task.url, task.destination, task.sha1)
                        if (ok) {
                            mutex.withLock { completed++ }
                            onProgress(Progress(
                                currentFile = task.destination.name,
                                downloadedBytes = 0,
                                totalBytes = 0,
                                filesCompleted = completed,
                                totalFiles = total
                            ))
                        }
                    }
                }.awaitAll()
            }
        }

        println("✅ Batch complete: $completed/$total files")
    }

    /**
     * تحقق من SHA1
     */
    private fun verifySha1(file: File, expected: String): Boolean {
        if (!file.exists()) return false
        val actual = calculateSha1(file)
        return actual.equals(expected, ignoreCase = true)
    }

    /**
     * احسب SHA1 للملف
     */
    private fun calculateSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}