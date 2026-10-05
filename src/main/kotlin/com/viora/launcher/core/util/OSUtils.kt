package com.viora.launcher.core.util

import java.io.File

/**
 * ✅ أدوات الكشف عن نظام التشغيل + مسارات ماين كرافت
 */
object OSUtils {

    enum class OS(val id: String, val displayName: String) {
        WINDOWS("windows", "Windows"),
        MACOS("osx", "macOS"),
        LINUX("linux", "Linux"),
        UNKNOWN("unknown", "Unknown")
    }

    fun currentOS(): OS {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> OS.WINDOWS
            osName.contains("mac") || osName.contains("darwin") -> OS.MACOS
            osName.contains("nux") || osName.contains("nix") -> OS.LINUX
            else -> OS.UNKNOWN
        }
    }

    fun isWindows(): Boolean = currentOS() == OS.WINDOWS
    fun isMacOS(): Boolean = currentOS() == OS.MACOS
    fun isLinux(): Boolean = currentOS() == OS.LINUX

    /**
     * ✅ مسار مجلد .minecraft حسب نظام التشغيل
     */
    fun getMinecraftDir(): File {
        return when (currentOS()) {
            OS.WINDOWS -> {
                val appData = System.getenv("APPDATA")
                    ?: "${System.getProperty("user.home")}/AppData/Roaming"
                File(appData, ".minecraft")
            }
            OS.MACOS -> {
                File(System.getProperty("user.home"), "Library/Application Support/minecraft")
            }
            OS.LINUX -> {
                File(System.getProperty("user.home"), ".minecraft")
            }
            OS.UNKNOWN -> {
                File(System.getProperty("user.home"), ".minecraft")
            }
        }
    }

    /**
     * ✅ مسار مجلد بيانات اللانشر
     */
    fun getLauncherDataDir(): File {
        val dir = when (currentOS()) {
            OS.WINDOWS -> {
                val appData = System.getenv("APPDATA")
                    ?: "${System.getProperty("user.home")}/AppData/Roaming"
                File(appData, ".viora-launcher")
            }
            OS.MACOS -> {
                File(System.getProperty("user.home"), "Library/Application Support/VioraLauncher")
            }
            OS.LINUX -> {
                val xdg = System.getenv("XDG_DATA_HOME")
                if (xdg != null) File(xdg, "viora-launcher")
                else File(System.getProperty("user.home"), ".local/share/viora-launcher")
            }
            OS.UNKNOWN -> {
                File(System.getProperty("user.home"), ".viora-launcher")
            }
        }
        dir.mkdirs()
        return dir
    }

    /**
     * ✅ مسار قابل للتنفيذ لـ java.exe أو java
     */
    fun getJavaExecutableName(): String {
        return if (isWindows()) "java.exe" else "java"
    }

    /**
     * ✅ مسار مجلد Java الافتراضي للبحث
     */
    fun getDefaultJavaSearchPaths(): List<File> {
        val paths = mutableListOf<File>()

        when (currentOS()) {
            OS.WINDOWS -> {
                paths.add(File("C:/Program Files/Java"))
                paths.add(File("C:/Program Files/Eclipse Adoptium"))
                paths.add(File("C:/Program Files/Microsoft"))
                paths.add(File("C:/Program Files/Zulu"))
                System.getenv("JAVA_HOME")?.let { paths.add(File(it)) }
            }
            OS.MACOS -> {
                paths.add(File("/Library/Java/JavaVirtualMachines"))
                paths.add(File(System.getProperty("user.home"), "Library/Java/JavaVirtualMachines"))
                System.getenv("JAVA_HOME")?.let { paths.add(File(it)) }
            }
            OS.LINUX -> {
                paths.add(File("/usr/lib/jvm"))
                paths.add(File("/usr/java"))
                System.getenv("JAVA_HOME")?.let { paths.add(File(it)) }
            }
            OS.UNKNOWN -> {}
        }

        return paths.filter { it.exists() }
    }
}