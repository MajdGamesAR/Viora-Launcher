package com.viora.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.version.VersionFilter
import com.viora.launcher.core.version.VersionService
import com.viora.launcher.core.version.model.VersionEntry
import com.viora.launcher.ui.components.AccountBadge
import com.viora.launcher.ui.components.ConfirmDialog
import com.viora.launcher.ui.components.EmptyState
import com.viora.launcher.ui.components.NavItem
import com.viora.launcher.ui.components.SearchBar
import com.viora.launcher.ui.components.Sidebar
import com.viora.launcher.ui.components.VersionCard
import com.viora.launcher.ui.components.VersionFilterBar
import com.viora.launcher.ui.components.VioraButton
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme
import com.viora.launcher.ui.viewmodel.VersionsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VersionsScreen(
    account: Account,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigate: (NavItem) -> Unit,
    onAccountClick: () -> Unit,
    onBackToHome: () -> Unit,
    onPlayVersion: (String) -> Unit
) {
    val colors = VioraTheme.colors

    // Service + ViewModel
    val versionService = remember { VersionService() }
    val viewModel = remember {
        VersionsViewModel(versionService, CoroutineScope(Dispatchers.Default))
    }

    // UI State
    var currentNav by remember { mutableStateOf(NavItem.VERSIONS) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VersionFilter.RELEASES) }
    var deleteConfirmVersion by remember { mutableStateOf<VersionEntry?>(null) }

    // ✅ عدّاد داخلي لإعادة الفحص بعد الحذف
    var refreshKey by remember { mutableStateOf(0) }

    // ===== Filtered List =====
    val filteredVersions = remember(
        viewModel.allVersions,
        filter,
        searchQuery,
        viewModel.installedVersions,
        refreshKey
    ) {
        // 1. إذا كان الفلتر MODPACKS → اعرض الإصدارات المحلية التي فيها mods
        if (filter == VersionFilter.MODPACKS) {
            return@remember loadLocalModpacks()
                .filter { modpack ->
                    searchQuery.isBlank() ||
                            modpack.id.contains(searchQuery, ignoreCase = true)
                }
        }

        // 2. باقي الفلاتر → استخدم Mojang list
        viewModel.allVersions
            .filter { version ->
                when (filter) {
                    VersionFilter.ALL -> true
                    VersionFilter.RELEASES -> version.isRelease
                    VersionFilter.SNAPSHOTS -> version.isSnapshot
                    VersionFilter.OLD -> version.isOld
                    VersionFilter.INSTALLED -> checkIsInstalled(version.id)
                    VersionFilter.MODPACKS -> false
                }
            }
            .filter { version ->
                searchQuery.isBlank() ||
                        version.id.contains(searchQuery, ignoreCase = true)
            }
    }

    // ✅ عدد المودباكس المحلية (للعرض في الهيدر)
    val localModpacksCount = remember(refreshKey) {
        loadLocalModpacks().size
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // ===== Sidebar =====
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

        // ===== Main Content =====
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
                        text = "Versions",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${viewModel.allVersions.size} versions available • " +
                                "${viewModel.installedVersions.size} installed • " +
                                "$localModpacksCount modpacks",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }

                AccountBadge(
                    account = account,
                    onClick = onAccountClick
                )
            }

            Spacer(Modifier.height(20.dp))

            // ===== Search + Filters =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.width(320.dp)
                )

                VersionFilterBar(
                    selected = filter,
                    onSelected = { filter = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ===== Content =====
            when {
                viewModel.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = VioraColors.VioraMagenta)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Loading versions...",
                                color = colors.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                viewModel.errorMessage != null && filter != VersionFilter.MODPACKS -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "❌ ${viewModel.errorMessage}",
                                color = VioraColors.Error,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            VioraButton(
                                text = "Retry",
                                onClick = { viewModel.refresh() },
                                modifier = Modifier.width(150.dp)
                            )
                        }
                    }
                }

                filteredVersions.isEmpty() -> {
                    if (filter == VersionFilter.MODPACKS) {
                        EmptyState(
                            title = "No modpacks installed",
                            description = "Install modpacks from Mods & Packs tab"
                        )
                    } else {
                        EmptyState()
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredVersions, key = { it.id }) { version ->
                            val isInstalled = checkIsInstalled(version.id)
                            val isModpack = filter == VersionFilter.MODPACKS

                            VersionCard(
                                version = version,
                                isInstalled = isInstalled,
                                isInstalling = if (isModpack) false
                                else viewModel.isInstalling(version),
                                installProgress = if (isModpack) 0f
                                else viewModel.getProgress(version),
                                installedSizeMB = if (isInstalled)
                                    viewModel.getInstalledSize(version) else 0,
                                onClick = { println("Clicked: ${version.id}") },
                                onInstall = {
                                    if (!isInstalled && !isModpack) {
                                        viewModel.installVersion(version) {
                                            println("✅ Installed ${version.id}")
                                            refreshKey++
                                        }
                                    }
                                },
                                onPlay = { onPlayVersion(version.id) },
                                onDelete = { deleteConfirmVersion = version }
                            )
                        }

                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }

    // ===== Delete Confirmation Dialog =====
    deleteConfirmVersion?.let { version ->
        val sizeMB = viewModel.getInstalledSize(version)
        ConfirmDialog(
            title = "Delete ${version.id}?",
            message = buildString {
                append("This will remove version ")
                append(version.id)
                if (sizeMB > 0) {
                    append(" and free ")
                    append(sizeMB)
                    append(" MB of disk space")
                }
                append(".\n\nYou can reinstall it later.")
            },
            confirmText = "Delete",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                deleteLocalVersion(version.id)
                deleteConfirmVersion = null
                refreshKey++
            },
            onDismiss = {
                deleteConfirmVersion = null
            }
        )
    }
}

// ============================================================
//  HELPERS
// ============================================================

/**
 * ✅ يفحص إذا كان الإصدار مثبتًا فعليًا (رسميًا أو مودباك محلي)
 */
private fun checkIsInstalled(versionId: String): Boolean {
    val minecraftDir = File(
        System.getProperty("user.home"),
        "AppData/Roaming/.minecraft"
    )
    val versionDir = File(minecraftDir, "versions/$versionId")
    return File(versionDir, "$versionId.json").exists()
}

/**
 * ✅ يقرأ المودباكس من instances/ أولًا (البنية الجديدة)
 * ثم من versions/ (البنية القديمة) كاحتياطي
 */
private fun loadLocalModpacks(): List<VersionEntry> {
    val minecraftDir = File(
        System.getProperty("user.home"),
        "AppData/Roaming/.minecraft"
    )
    val result = mutableListOf<VersionEntry>()

    // ============================================================
    // 1. instances/ — البنية الجديدة (أولوية)
    // ============================================================
    val instancesDir = File(minecraftDir, "instances")
    if (instancesDir.exists()) {
        instancesDir.listFiles()
            ?.filter { vDir ->
                val modsDir = File(vDir, "mods")
                modsDir.exists() &&
                        modsDir.listFiles()?.any { it.extension == "jar" } == true
            }
            ?.forEach { dir ->
                result.add(
                    VersionEntry(
                        id = dir.name,
                        type = "release",
                        url = "",
                        time = "",
                        releaseTime = SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ssXXX",
                            Locale.US
                        ).format(Date(dir.lastModified())),
                        sha1 = null,
                        complianceLevel = 0
                    )
                )
            }
    }

    // ============================================================
    // 2. versions/ — احتياطي (للتوافق مع بنية قديمة)
    // ============================================================
    val versionsDir = File(minecraftDir, "versions")
    if (versionsDir.exists()) {
        versionsDir.listFiles()
            ?.filter { vDir ->
                val modsDir = File(vDir, "mods")
                modsDir.exists() &&
                        modsDir.listFiles()?.any { it.extension == "jar" } == true
            }
            ?.forEach { dir ->
                val alreadyExists = result.any {
                    it.id.equals(dir.name, ignoreCase = true)
                }
                if (!alreadyExists) {
                    result.add(
                        VersionEntry(
                            id = dir.name,
                            type = "release",
                            url = "",
                            time = "",
                            releaseTime = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ssXXX",
                                Locale.US
                            ).format(Date(dir.lastModified())),
                            sha1 = null,
                            complianceLevel = 0
                        )
                    )
                }
            }
    }

    return result.sortedByDescending { it.releaseTime }
}

/**
 * ✅ يحذف إصدارًا محليًا (مودباك أو vanilla) من القرص
 * - يحذف من versions/
 * - يحذف من instances/ (إن وُجد)
 */
private fun deleteLocalVersion(versionId: String): Boolean {
    return try {
        val minecraftDir = File(
            System.getProperty("user.home"),
            "AppData/Roaming/.minecraft"
        )

        var deleted = false

        // 1. احذف من versions/
        val versionDir = File(minecraftDir, "versions/$versionId")
        if (versionDir.exists()) {
            versionDir.deleteRecursively()
            println("🗑️ Deleted version: $versionId (from versions/)")
            deleted = true
        }

        // 2. احذف من instances/
        val instanceDir = File(minecraftDir, "instances/$versionId")
        if (instanceDir.exists()) {
            instanceDir.deleteRecursively()
            println("🗑️ Deleted instance: $versionId (from instances/)")
            deleted = true
        }

        if (!deleted) {
            println("⚠️ Nothing to delete for: $versionId")
        }

        deleted
    } catch (e: Exception) {
        println("❌ Failed to delete $versionId: ${e.message}")
        false
    }
}