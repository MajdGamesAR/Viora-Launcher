package com.viora.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.mods.*
import com.viora.launcher.core.util.MinecraftPath
import com.viora.launcher.ui.components.*
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
//  TABS
// ============================================================
enum class ModsTab(
    val label: String,
    val modrinthType: String
) {
    MODS("Mods", "mod"),
    MODPACKS("Modpacks", "modpack"),
    RESOURCE_PACKS("Resource Packs", "resourcepack"),
    SHADERS("Shaders", "shader"),
    DATA_PACKS("Data Packs", "datapack"),
    INSTALLED("Installed", "")
}

// ============================================================
//  MAIN SCREEN — التعريف الوحيد
// ============================================================
@Composable
fun ModsScreen(
    account: Account,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigate: (NavItem) -> Unit,
    onAccountClick: () -> Unit,
    onBackToHome: () -> Unit
) {
    val colors = VioraTheme.colors
    val scope = rememberCoroutineScope()

    // ✅ الخدمات
    val modrinth = remember { ModrinthService.shared }
    val curseForge = remember { CurseForgeService.fromSystemProperty() }
    val unifiedService = remember { UnifiedModService(modrinth, curseForge) }

    // ✅ حالة المصدر المختار
    var selectedSource: ModSource by remember {
        mutableStateOf<ModSource>(ModSource.ALL)
    }

    var currentNav by remember { mutableStateOf(NavItem.MODS) }
    var selectedTab by remember { mutableStateOf(ModsTab.MODS) }
    var globalRefreshKey by remember { mutableStateOf(0) }

    // ✅ تحقق من توفر CurseForge
    val curseForgeAvailable: Boolean = remember {
        unifiedService.isCurseForgeAvailable()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Sidebar(
            currentItem = currentNav,
            onItemSelected = {
                currentNav = it
                if (it == NavItem.HOME) onBackToHome()
                else onNavigate(it)
            },
            isDark = isDark,
            onToggleTheme = onToggleTheme
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // ===== Top Bar =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mods & Content",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Browse, download, and manage your content",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }

                AccountBadge(account = account, onClick = onAccountClick)
            }

            Spacer(Modifier.height(20.dp))

            // ===== Tabs =====
            ModsTabBar(
                selected = selectedTab,
                onSelected = { selectedTab = it }
            )

            Spacer(Modifier.height(16.dp))

            // ===== Source Selector =====
            if (selectedTab != ModsTab.INSTALLED) {
                SourceSelector(
                    selected = selectedSource,
                    curseForgeAvailable = curseForgeAvailable,
                    onSelect = { selectedSource = it }
                )
                Spacer(Modifier.height(16.dp))
            }

            // ===== Content =====
            when (selectedTab) {
                ModsTab.INSTALLED -> InstalledTabContent(
                    externalRefreshKey = globalRefreshKey
                )
                else -> ModsTabContent(
                    unifiedService = unifiedService,
                    selectedSource = selectedSource,
                    scope = scope,
                    projectType = selectedTab.modrinthType,
                    tabLabel = selectedTab.label,
                    onInstalled = { globalRefreshKey++ }
                )
            }
        }
    }
}

// ============================================================
//  SOURCE SELECTOR
// ============================================================
@Composable
private fun SourceSelector(
    selected: ModSource,
    curseForgeAvailable: Boolean,
    onSelect: (ModSource) -> Unit
) {
    val colors = VioraTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Source:",
            fontSize = 12.sp,
            color = colors.textSecondary,
            fontWeight = FontWeight.Medium
        )

        ModSource.entries.forEach { source ->
            val enabled = when (source) {
                ModSource.CURSEFORGE -> curseForgeAvailable
                else -> true
            }
            val isSelected = selected == source

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            !enabled -> SolidColor(colors.surface.copy(alpha = 0.5f))
                            isSelected -> VioraColors.BrandGradientHorizontal
                            else -> SolidColor(colors.surfaceElevated)
                        }
                    )
                    .clickable(enabled = enabled) { onSelect(source) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = source.icon, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = source.displayName,
                        color = when {
                            !enabled -> colors.textDisabled
                            isSelected -> Color.White
                            else -> colors.textSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold
                        else FontWeight.Medium
                    )
                    if (!enabled && source == ModSource.CURSEFORGE) {
                        Spacer(Modifier.width(4.dp))
                        Text(text = "⚠️", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ============================================================
//  TAB BAR
// ============================================================
@Composable
private fun ModsTabBar(
    selected: ModsTab,
    onSelected: (ModsTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModsTab.entries.forEach { tab ->
            TabButton(
                label = tab.label,
                isSelected = tab == selected,
                onClick = { onSelected(tab) }
            )
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = VioraTheme.colors

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) VioraColors.BrandGradientHorizontal
                else SolidColor(colors.surfaceElevated)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ============================================================
//  MODS TAB CONTENT
// ============================================================
@Composable
private fun ModsTabContent(
    unifiedService: UnifiedModService,
    selectedSource: ModSource,
    scope: kotlinx.coroutines.CoroutineScope,
    projectType: String,
    tabLabel: String,
    onInstalled: () -> Unit = {}
) {
    val colors = VioraTheme.colors

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UnifiedMod>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val installingState = remember {
        mutableStateMapOf<String, Pair<Float, String>>()
    }

    var installedKeys by remember { mutableStateOf(setOf<String>()) }

    fun refreshInstalled() {
        installedKeys = results
            .filter { isUnifiedInstalled(it, projectType) }
            .map { "${it.source}_${it.id}" }
            .toSet()
    }

    // ✅ تحميل أولي
    LaunchedEffect(projectType, selectedSource) {
        isLoading = true
        errorMessage = null
        try {
            results = unifiedService.search(
                query = "",
                projectType = projectType,
                source = selectedSource,
                limit = 20
            )
            refreshInstalled()
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ===== Search Bar =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search $tabLabel...",
                modifier = Modifier.weight(1f)
            )

            VioraButton(
                text = "Search",
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            results = unifiedService.search(
                                query = query,
                                projectType = projectType,
                                source = selectedSource,
                                limit = 20
                            )
                            refreshInstalled()
                        } catch (e: Exception) {
                            errorMessage = e.message
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.width(120.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VioraColors.VioraMagenta)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Searching $tabLabel...",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            errorMessage != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "❌ $errorMessage",
                        color = VioraColors.Error,
                        fontSize = 16.sp
                    )
                }
            }

            results.isEmpty() -> {
                EmptyState(
                    title = "No $tabLabel found",
                    description = "Try a different search query or source"
                )
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results, key = { "${it.source}_${it.id}" }) { mod ->
                        val key = "${mod.source}_${mod.id}"
                        val installState = installingState[key]

                        UnifiedModCard(
                            mod = mod,
                            isInstalling = installState != null,
                            isInstalled = key in installedKeys,
                            progress = installState?.first ?: 0f,
                            statusMessage = installState?.second ?: "",
                            onInstall = {
                                scope.launch {
                                    installingState[key] = 0f to "Starting..."

                                    var success = false
                                    try {
                                        installUnifiedMod(
                                            unifiedService = unifiedService,
                                            mod = mod,
                                            projectType = projectType,
                                            onProgress = { p, msg ->
                                                installingState[key] = p to msg
                                            }
                                        )
                                        success = true
                                        installedKeys = installedKeys + key
                                        onInstalled()
                                    } catch (e: Exception) {
                                        println("❌ Install failed: ${e.message}")
                                        installingState[key] = 0f to "❌ ${e.message}"
                                    } finally {
                                        if (success) {
                                            delay(1500)
                                        } else {
                                            delay(5000)
                                        }
                                        installingState.remove(key)
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

// ============================================================
//  UNIFIED MOD CARD
// ============================================================
@Composable
private fun UnifiedModCard(
    mod: UnifiedMod,
    isInstalling: Boolean,
    isInstalled: Boolean,
    progress: Float,
    statusMessage: String,
    onInstall: () -> Unit
) {
    val colors = VioraTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModIcon(url = mod.iconUrl, size = 64)
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mod.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    SourceBadge(mod.source)
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = mod.description,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        "⬇ ${formatNumber(mod.downloads)}",
                        fontSize = 11.sp,
                        color = VioraColors.VioraOrange
                    )
                    if (mod.follows > 0) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "❤ ${formatNumber(mod.follows)}",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            when {
                isInstalling -> {
                    Column(
                        modifier = Modifier.width(160.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = VioraColors.VioraMagenta
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = VioraColors.VioraMagenta
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = statusMessage.take(40),
                            fontSize = 10.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                isInstalled -> {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VioraColors.Success.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = VioraColors.Success, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Installed",
                                color = VioraColors.Success,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                else -> {
                    VioraButton(
                        text = "Install",
                        onClick = onInstall,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
//  SOURCE BADGE
// ============================================================
@Composable
private fun SourceBadge(source: ModSource) {
    val (bgColor, textColor) = when (source) {
        ModSource.MODRINTH -> Color(0xFF1BD96A) to Color(0xFF1BD96A)
        ModSource.CURSEFORGE -> Color(0xFFF16436) to Color(0xFFF16436)
        ModSource.ALL -> Color.Gray to Color.Gray
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${source.icon} ${source.displayName}",
            fontSize = 9.sp,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================
//  INSTALL UNIFIED — Router
// ============================================================
private suspend fun installUnifiedMod(
    unifiedService: UnifiedModService,
    mod: UnifiedMod,
    projectType: String,
    onProgress: (Float, String) -> Unit
) {
    println("═══════════════════════════════════════════════")
    println("⬇️  Installing: ${mod.title}")
    println("   Source: ${mod.source}")
    println("   Type: $projectType")
    println("═══════════════════════════════════════════════")

    when (mod.source) {
        ModSource.MODRINTH -> {
            val project = mod.rawModrinth ?: run {
                onProgress(0f, "❌ Missing Modrinth data")
                return
            }
            installFromModrinth(
                modrinth = ModrinthService.shared,
                project = project,
                projectType = projectType,
                onProgress = onProgress
            )
        }

        ModSource.CURSEFORGE -> {
            val cfMod = mod.rawCurseForge ?: run {
                onProgress(0f, "❌ Missing CurseForge data")
                return
            }
            installFromCurseForge(
                unifiedService = unifiedService,
                cfMod = cfMod,
                projectType = projectType,
                onProgress = onProgress
            )
        }

        ModSource.ALL -> {
            onProgress(0f, "❌ Unknown source")
        }
    }
}

// ============================================================
//  INSTALL FROM MODRINTH
// ============================================================
private suspend fun installFromModrinth(
    modrinth: ModrinthService,
    project: ModrinthProject,
    projectType: String,
    onProgress: (Float, String) -> Unit
) {
    val targetVersion = "1.21.1"
    val targetLoader = "fabric"

    onProgress(0.02f, "🔍 Looking up versions...")

    val versions = modrinth.getProjectVersions(
        projectId = project.projectId,
        gameVersion = targetVersion,
        loader = if (projectType == "mod") targetLoader else null
    )

    val compatible = pickCompatibleVersion(
        versions = versions,
        targetVersion = targetVersion,
        targetLoader = targetLoader,
        projectType = projectType
    )

    if (compatible == null) {
        onProgress(0f, "❌ No compatible version")
        return
    }

    val primaryFile = compatible.files.firstOrNull() ?: run {
        onProgress(0f, "❌ No files available")
        return
    }

    println("✅ [Modrinth] Selected: ${compatible.versionNumber}")

    when (projectType) {
        "modpack" -> installModpackFromModrinth(primaryFile, onProgress)
        "resourcepack" -> installToFolder(primaryFile, MinecraftPath.resourcePacks, "resourcepacks", onProgress)
        "shader" -> installToFolder(primaryFile, MinecraftPath.shaderPacks, "shaderpacks", onProgress)
        "datapack" -> installToFolder(primaryFile, MinecraftPath.dataPacks, "datapacks", onProgress)
        else -> {
            onProgress(0.10f, "⬇️ Downloading mod...")
            val result = ModDownloader.downloadMod(
                downloadUrl = primaryFile.url,
                fileName = primaryFile.filename,
                gameVersion = targetVersion
            )
            if (result != null) onProgress(1f, "✅ Installed!")
            else onProgress(1f, "❌ Failed")
        }
    }
}

// ============================================================
//  INSTALL FROM CURSEFORGE — النسخة الكاملة
// ============================================================
private suspend fun installFromCurseForge(
    unifiedService: UnifiedModService,
    cfMod: CurseForgeMod,
    projectType: String,
    onProgress: (Float, String) -> Unit
) {
    println("═══════════════════════════════════════════════")
    println("🔍 [CF] Install Request")
    println("   ID:          ${cfMod.id}")
    println("   Name:        ${cfMod.name}")
    println("   Type:        $projectType")
    println("   latestFiles: ${cfMod.latestFiles.size}")
    println("═══════════════════════════════════════════════")

    if (cfMod.id == 0) {
        onProgress(0f, "❌ Invalid mod ID (0)")
        println("❌ [CF] cfMod.id is 0!")
        return
    }

    onProgress(0.05f, "🔍 Fetching files...")

    // ✅ جرّب latestFiles أولاً، ثم API
    var files: List<CurseForgeFile> = cfMod.latestFiles

    if (files.isEmpty()) {
        println("⚠️ [CF] latestFiles empty — calling API")
        files = unifiedService.getCurseForgeFiles(cfMod.id)
    }

    println("📁 [CF] Files available: ${files.size}")

    if (files.isEmpty()) {
        onProgress(0f, "❌ No files available")
        return
    }

    // ✅ اختر الملف الأفضل
    val best = files
        .filter { it.fileLength > 0 }
        .maxByOrNull { it.fileLength }
        ?: files.first()

    println("✅ [CF] Selected: ${best.displayName}")
    println("   fileId:   ${best.id}")
    println("   fileName: ${best.fileName}")
    println("   size:     ${formatSize(best.fileLength)}")
    println("   URL:      ${best.downloadUrl ?: "(null)"}")

    // ✅ احصل على URL — جرّب downloadUrl أولاً
    var downloadUrl = best.downloadUrl

    // ✅ fallback: توليد CDN URL
    if (downloadUrl == null && best.id != 0 && best.fileName.isNotBlank()) {
        val first4 = best.id / 1000
        val last3 = best.id % 1000
        downloadUrl = "https://edge.forgecdn.net/files/$first4/$last3/${best.fileName}"
        println("🔧 [CF] Generated CDN URL: $downloadUrl")
    }

    if (downloadUrl == null) {
        onProgress(0f, "❌ Cannot resolve download URL")
        return
    }

    println("📥 [CF] Final URL: $downloadUrl")

    // ═══════════════════════════════════════════════════════════
//  MODPACK
// ═══════════════════════════════════════════════════════════
    if (projectType == "modpack") {
        onProgress(0.10f, "⬇️ Downloading (${formatSize(best.fileLength)})...")

        val downloadsDir = File(MinecraftPath.launcherRoot, "downloads").apply { mkdirs() }
        val targetFile = File(downloadsDir, best.fileName)

        // ✅ إذا الملف موجود، تحقق من حجمه
        if (targetFile.exists() && targetFile.length() > 0) {
            val expectedSize = best.fileLength
            val actualSize = targetFile.length()

            if (expectedSize > 0 && actualSize < expectedSize * 0.95) {
                println("⚠️ [CF] Existing file is incomplete ($actualSize / $expectedSize) — re-downloading")
                targetFile.delete()
            } else {
                println("✅ [CF] File already downloaded and complete: ${targetFile.length()} bytes")
            }
        }

        // تحميل مع تقدم
        if (!targetFile.exists() || targetFile.length() == 0L) {
            try {
                val url = URI(downloadUrl).toURL()
                val connection = url.openConnection()
                connection.connect()

                val totalBytes = connection.contentLengthLong
                var downloaded = 0L

                connection.getInputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalBytes > 0) {
                                val p = 0.10f + 0.40f * (downloaded.toFloat() / totalBytes)
                                onProgress(
                                    p,
                                    "⬇️ ${formatSize(downloaded)} / ${formatSize(totalBytes)}"
                                )
                            }
                        }
                    }
                }
                println("✅ [CF] Downloaded: ${targetFile.length()} bytes")
            } catch (e: Exception) {
                onProgress(0f, "❌ Download failed: ${e.message}")
                println("❌ [CF] Download error: ${e.message}")
                targetFile.delete()
                return
            }
        }

        onProgress(0.50f, "📦 Installing modpack...")

        val success = try {
            ModpackInstaller.installModpack(targetFile) { p, msg ->
                onProgress(0.50f + p * 0.50f, msg)
            }
        } catch (e: Exception) {
            println("⚠️ [CF] Install failed, deleting corrupted file")
            e.printStackTrace()
            targetFile.delete()
            false
        }

        if (success) {
            onProgress(1f, "✅ Installed successfully!")
            println("✅ [CF] Modpack installed: ${cfMod.name}")
        } else {
            onProgress(1f, "❌ Installation failed — file deleted, try again")
            println("❌ [CF] Modpack install returned false")
        }
        return
    }
    // ═══════════════════════════════════════════════════════════
    //  RESOURCEPACK / SHADER / DATAPACK / MOD
    // ═══════════════════════════════════════════════════════════
    val (targetDir, folderName) = when (projectType) {
        "resourcepack" -> MinecraftPath.resourcePacks to "resourcepacks"
        "shader" -> MinecraftPath.shaderPacks to "shaderpacks"
        "datapack" -> MinecraftPath.dataPacks to "datapacks"
        else -> MinecraftPath.mods to "mods"
    }

    downloadAndSave(
        url = downloadUrl,
        fileName = best.fileName,
        targetDir = targetDir,
        folderName = folderName,
        onProgress = onProgress
    )
}

// ============================================================
//  HELPERS — Modrinth
// ============================================================
private fun pickCompatibleVersion(
    versions: List<ModrinthVersion>,
    targetVersion: String,
    targetLoader: String,
    projectType: String
): ModrinthVersion? {
    val needsLoader = projectType == "mod"

    return versions
        .filter { v ->
            val mcMatch = v.gameVersions.any { it == targetVersion }
            val loaderMatch = !needsLoader || v.loaders.any { it == targetLoader }
            mcMatch && loaderMatch
        }
        .sortedWith(
            compareByDescending<ModrinthVersion> {
                val vn = it.versionNumber.lowercase()
                when {
                    vn.contains("alpha") -> 0
                    vn.contains("beta") -> 1
                    vn.contains("rc") -> 2
                    else -> 3
                }
            }.thenByDescending {
                it.datePublished ?: ""
            }
        )
        .firstOrNull()
}

private suspend fun installModpackFromModrinth(
    primaryFile: ModrinthFile,
    onProgress: (Float, String) -> Unit
) {
    onProgress(0.05f, "⬇️ Downloading modpack...")

    val downloadsDir = File(
        MinecraftPath.launcherRoot,
        "downloads"
    ).apply { mkdirs() }

    val targetFile = File(downloadsDir, primaryFile.filename)

    try {
        URI(primaryFile.url).toURL().openStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        onProgress(0f, "❌ Download failed")
        return
    }

    onProgress(0.15f, "📦 Installing...")

    val success = ModpackInstaller.installModpack(targetFile) { p, msg ->
        onProgress(0.15f + p * 0.80f, msg)
    }

    if (success) onProgress(1f, "✅ Installed!")
    else onProgress(1f, "❌ Failed")
}

private suspend fun installToFolder(
    primaryFile: ModrinthFile,
    targetDir: File,
    folderName: String,
    onProgress: (Float, String) -> Unit
) {
    downloadAndSave(
        url = primaryFile.url,
        fileName = primaryFile.filename,
        targetDir = targetDir,
        folderName = folderName,
        onProgress = onProgress
    )
}

private suspend fun downloadAndSave(
    url: String,
    fileName: String,
    targetDir: File,
    folderName: String,
    onProgress: (Float, String) -> Unit
) {
    onProgress(0.10f, "⬇️ Downloading...")
    targetDir.mkdirs()
    val targetFile = File(targetDir, fileName)

    try {
        URI(url).toURL().openStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        onProgress(1f, "✅ Installed to $folderName/")
        println("✅ Installed: ${targetFile.absolutePath}")
    } catch (e: Exception) {
        onProgress(1f, "❌ Failed: ${e.message}")
    }
}

// ============================================================
//  INSTALLED TAB
// ============================================================
@Composable
private fun InstalledTabContent(
    externalRefreshKey: Int = 0
) {
    val colors = VioraTheme.colors

    var installedMods by remember { mutableStateOf<List<File>>(emptyList()) }
    var installedModpacks by remember { mutableStateOf<List<File>>(emptyList()) }
    var installedResourcePacks by remember { mutableStateOf<List<File>>(emptyList()) }
    var installedShaders by remember { mutableStateOf<List<File>>(emptyList()) }
    var installedDatapacks by remember { mutableStateOf<List<File>>(emptyList()) }
    var localRefreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(localRefreshKey, externalRefreshKey) {
        installedMods = ModDownloader.getInstalledMods()
        installedModpacks = getInstalledModpacks()
        installedResourcePacks = listFilesIn(MinecraftPath.resourcePacks)
        installedShaders = listFilesIn(MinecraftPath.shaderPacks)
        installedDatapacks = listFilesIn(MinecraftPath.dataPacks)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${installedMods.size + installedModpacks.size + installedResourcePacks.size + installedShaders.size + installedDatapacks.size} items installed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    text = "mods • modpacks • resourcepacks • shaderpacks • datapacks",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            VioraButton(
                text = "🔄 Refresh",
                onClick = { localRefreshKey++ },
                modifier = Modifier.width(140.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installedModpacks.isNotEmpty()) {
                item { SectionHeader("📦 Modpacks (${installedModpacks.size})") }
                items(installedModpacks, key = { "mp_${it.absolutePath}" }) { dir ->
                    InstalledFolderRow(
                        icon = "🎮",
                        name = dir.name,
                        subtitle = "${File(dir, "mods").listFiles()?.count { it.extension == "jar" } ?: 0} mods",
                        onDelete = { if (dir.deleteRecursively()) localRefreshKey++ }
                    )
                }
            }

            if (installedMods.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("🔧 Mods (${installedMods.size})")
                }
                items(installedMods, key = { "m_${it.absolutePath}" }) { file ->
                    InstalledFileRow(
                        icon = "🔧",
                        file = file,
                        onDelete = { if (file.delete()) localRefreshKey++ }
                    )
                }
            }

            if (installedResourcePacks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("🎨 Resource Packs (${installedResourcePacks.size})")
                }
                items(installedResourcePacks, key = { "rp_${it.absolutePath}" }) { file ->
                    InstalledFileRow(
                        icon = "🎨",
                        file = file,
                        onDelete = { if (file.delete()) localRefreshKey++ }
                    )
                }
            }

            if (installedShaders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("✨ Shaders (${installedShaders.size})")
                }
                items(installedShaders, key = { "sh_${it.absolutePath}" }) { file ->
                    InstalledFileRow(
                        icon = "✨",
                        file = file,
                        onDelete = { if (file.delete()) localRefreshKey++ }
                    )
                }
            }

            if (installedDatapacks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("📊 Data Packs (${installedDatapacks.size})")
                }
                items(installedDatapacks, key = { "dp_${it.absolutePath}" }) { file ->
                    InstalledFileRow(
                        icon = "📊",
                        file = file,
                        onDelete = { if (file.delete()) localRefreshKey++ }
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ============================================================
//  UI HELPERS
// ============================================================
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = VioraColors.VioraOrange,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun InstalledFileRow(
    icon: String,
    file: File,
    onDelete: () -> Unit
) {
    val colors = VioraTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VioraColors.BrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 22.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatSize(file.length())} • ${formatDate(file.lastModified())}",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Text("🗑", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun InstalledFolderRow(
    icon: String,
    name: String,
    subtitle: String,
    onDelete: () -> Unit
) {
    val colors = VioraTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VioraColors.BrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 22.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = VioraColors.VioraOrange
                )
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Text("🗑", fontSize = 16.sp)
            }
        }
    }
}

// ============================================================
//  HELPERS
// ============================================================
private fun listFilesIn(dir: File): List<File> {
    if (!dir.exists()) return emptyList()
    return dir.listFiles()
        ?.filter { it.isFile }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

private fun isUnifiedInstalled(mod: UnifiedMod, projectType: String): Boolean {
    return when (projectType) {
        "modpack" -> {
            val instancesDir = MinecraftPath.instances
            if (instancesDir.exists()) {
                val slugKeyword = mod.slug.lowercase().take(20)
                val titleKeyword = sanitize(mod.title).take(20)
                instancesDir.listFiles()?.any { dir ->
                    val modsDir = File(dir, "mods")
                    val hasMods = modsDir.exists() &&
                            modsDir.listFiles()?.any { it.extension == "jar" } == true
                    hasMods && (
                            dir.name.lowercase().contains(slugKeyword) ||
                                    dir.name.lowercase().contains(titleKeyword)
                            )
                } ?: false
            } else false
        }
        "mod" -> {
            val modsDir = MinecraftPath.mods
            if (!modsDir.exists()) false
            else {
                val keyword = mod.slug.lowercase().take(15)
                modsDir.listFiles()?.any { it.name.lowercase().contains(keyword) } ?: false
            }
        }
        "resourcepack" -> {
            val dir = MinecraftPath.resourcePacks
            if (!dir.exists()) false
            else {
                val keyword = mod.slug.lowercase().take(15)
                dir.listFiles()?.any { it.name.lowercase().contains(keyword) } ?: false
            }
        }
        "shader" -> {
            val dir = MinecraftPath.shaderPacks
            if (!dir.exists()) false
            else {
                val keyword = mod.slug.lowercase().take(15)
                dir.listFiles()?.any { it.name.lowercase().contains(keyword) } ?: false
            }
        }
        "datapack" -> {
            val dir = MinecraftPath.dataPacks
            if (!dir.exists()) false
            else {
                val keyword = mod.slug.lowercase().take(15)
                dir.listFiles()?.any { it.name.lowercase().contains(keyword) } ?: false
            }
        }
        else -> false
    }
}

private fun sanitize(s: String): String {
    return s.replace(Regex("[^a-zA-Z0-9._-]"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .lowercase()
}

private fun getInstalledModpacks(): List<File> {
    val instancesDir = MinecraftPath.instances
    if (!instancesDir.exists()) return emptyList()

    return instancesDir.listFiles()
        ?.filter { vDir ->
            val modsDir = File(vDir, "mods")
            modsDir.exists() &&
                    modsDir.listFiles()?.any { it.extension == "jar" } == true
        }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

private fun formatNumber(n: Long): String {
    return when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(timestamp))
}