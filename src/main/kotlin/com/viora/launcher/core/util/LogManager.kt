package com.viora.launcher.core.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * إدارة اللوجات — حفظ، قراءة، فتح المجلد
 */
object LogManager {

    private val logsDir: File = File(
        System.getProperty("user.home"),
        ".viora-launcher/logs"
    ).apply { mkdirs() }

    /**
     * احفظ لوجات تشغيل في ملف
     */
    fun saveLaunchLog(versionId: String, lines: List<String>): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            .format(Date())
        val file = File(logsDir, "launch_${versionId}_$timestamp.log")
        file.writeText(lines.joinToString("\n"))
        return file
    }

    /**
     * احصل على آخر N ملفات لوج
     */
    fun getRecentLogs(limit: Int = 10): List<File> {
        return logsDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?: emptyList()
    }

    /**
     * احذف اللوجات الأقدم من X يوم
     */
    fun cleanOldLogs(daysOld: Int = 7) {
        val threshold = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        logsDir.listFiles()?.forEach { file ->
            if (file.lastModified() < threshold) {
                file.delete()
            }
        }
    }

    /**
     * احصل على مسار مجلد اللوجات (للعرض)
     */
    fun getLogsPath(): String = logsDir.absolutePath
}