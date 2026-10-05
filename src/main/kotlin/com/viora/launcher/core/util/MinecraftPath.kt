package com.viora.launcher.core.util

import java.io.File

object MinecraftPath {
    val root: File by lazy { OSUtils.getMinecraftDir() }

    val versions: File by lazy { File(root, "versions").apply { mkdirs() } }
    val libraries: File by lazy { File(root, "libraries").apply { mkdirs() } }
    val assets: File by lazy { File(root, "assets").apply { mkdirs() } }
    val mods: File by lazy { File(root, "mods").apply { mkdirs() } }
    val resourcePacks: File by lazy { File(root, "resourcepacks").apply { mkdirs() } }
    val shaderPacks: File by lazy { File(root, "shaderpacks").apply { mkdirs() } }
    val dataPacks: File by lazy { File(root, "datapacks").apply { mkdirs() } }
    val saves: File by lazy { File(root, "saves").apply { mkdirs() } }
    val instances: File by lazy { File(root, "instances").apply { mkdirs() } }
    val natives: File by lazy { File(root, "natives").apply { mkdirs() } }
    val logs: File by lazy { File(root, "logs").apply { mkdirs() } }

    val launcherRoot: File by lazy { OSUtils.getLauncherDataDir() }
    val launcherAssets: File by lazy { File(launcherRoot, "assets").apply { mkdirs() } }
    val launcherDownloads: File by lazy { File(launcherRoot, "downloads").apply { mkdirs() } }
    val launcherLogs: File by lazy { File(launcherRoot, "logs").apply { mkdirs() } }
    val launcherCache: File by lazy { File(launcherRoot, "cache").apply { mkdirs() } }

    fun versionDir(versionId: String): File = File(versions, versionId)
    fun versionJson(versionId: String): File = File(versionDir(versionId), "$versionId.json")
    fun versionJar(versionId: String): File = File(versionDir(versionId), "$versionId.jar")
    fun instanceDir(instanceId: String): File = File(instances, instanceId)
    fun libraryFile(path: String): File = File(libraries, path)
    fun assetFile(path: String): File = File(assets, path)

    fun dirSizeMB(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() } / 1024 / 1024
    }
}