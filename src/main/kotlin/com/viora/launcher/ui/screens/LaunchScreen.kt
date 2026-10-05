package com.viora.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.util.JavaFinder
import com.viora.launcher.core.util.LogManager
import com.viora.launcher.core.version.VersionService
import com.viora.launcher.ui.components.VioraButton
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class LaunchState {
    object Preparing : LaunchState()
    object Launching : LaunchState()
    data class Running(val process: Process, val startTime: Long) : LaunchState()
    data class Error(val message: String) : LaunchState()
    data class Stopped(val exitCode: Int, val playtimeMs: Long) : LaunchState()
}

enum class LogLevel {
    INFO, WARNING, ERROR
}

@Composable
fun LaunchScreen(
    account: Account,
    versionId: String,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val colors = VioraTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var state by remember { mutableStateOf<LaunchState>(LaunchState.Preparing) }
    val logs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    // معلومات اللعبة
    var ramUsage by remember { mutableStateOf(0L) }
    var playtimeSeconds by remember { mutableStateOf(0L) }
    var showOnlyErrors by remember { mutableStateOf(false) }

    // Auto-scroll
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // تتبع وقت اللعب + RAM
    LaunchedEffect(state) {
        val s = state
        if (s is LaunchState.Running) {
            while (true) {
                delay(1000)
                playtimeSeconds = (System.currentTimeMillis() - s.startTime) / 1000

                // RAM usage
                try {
                    val process = s.process
                    val runtime = Runtime.getRuntime()
                    ramUsage = runtime.totalMemory() - runtime.freeMemory()
                } catch (e: Exception) { /* تجاهل */ }
            }
        }
    }

    // تشغيل اللعبة عند البداية
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                log(logs, "🔧 Preparing to launch Minecraft $versionId")
                log(logs, "👤 Account: ${account.username}")
                log(logs, "")

                // 1. البحث عن Java
                // 1. اقرأ إصدار MC الحقيقي
                log(logs, "📖 Resolving Minecraft version...")
                val realMcVersion = JavaFinder.readRealMcVersion(versionId) ?: versionId
                log(logs, "   Instance: $versionId")
                log(logs, "   Real MC version: $realMcVersion")
                log(logs, "")

// 2. البحث عن Java المناسب
                log(logs, "☕ Looking for Java $realMcVersion...")
                val java = JavaFinder.findForMinecraft(realMcVersion)
                if (java == null) {
                    state = LaunchState.Error(
                        "❌ Java not found!\n\n" +
                                "Please install Java 21 from:\n" +
                                "https://adoptium.net/temurin/releases/?version=21"
                    )
                    log(logs, "❌ Java not found")
                    return@withContext
                }

                log(logs, "✅ Java found: ${java.vendor} ${java.version}")
                log(logs, "   Path: ${java.path}")
                log(logs, "")

// ✅ تحذير إذا كانت Java قديمة جداً أو حديثة جداً
                if (realMcVersion.startsWith("1.21") && java.version > 21) {
                    log(logs, "⚠️ WARNING: Java ${java.version} may not work with MC 1.21!")
                    log(logs, "   Recommended: Java 21")
                    log(logs, "   Download: https://adoptium.net/temurin/releases/?version=21")
                    log(logs, "")
                }

                if (realMcVersion.startsWith("1.21") && java.version < 21) {
                    log(logs, "⚠️ WARNING: Java ${java.version} is too old for MC 1.21!")
                    log(logs, "   Required: Java 21")
                    log(logs, "")
                }
                log(logs, "")

                // 2. التشغيل
                state = LaunchState.Launching
                log(logs, "🚀 Starting Minecraft...")
                log(logs, "─".repeat(60))
                log(logs, "")

                val versionService = VersionService()
                val startTime = System.currentTimeMillis()

                val process = versionService.launchVersion(
                    versionId = versionId,
                    account = account,
                    javaPath = java.path,
                    ramMB = 4096
                ) { line ->
                    logs.add(line)
                }

                state = LaunchState.Running(process, startTime)

                // 3. انتظار الإغلاق
                val exitCode = process.waitFor()
                val playtime = System.currentTimeMillis() - startTime

                log(logs, "")
                log(logs, "─".repeat(60))
                log(logs, "🛑 Minecraft exited (code: $exitCode)")
                log(logs, "⏱️ Playtime: ${formatDuration(playtime)}")

                state = LaunchState.Stopped(exitCode, playtime)

                // حفظ اللوجات
                val logFile = LogManager.saveLaunchLog(versionId, logs)
                log(logs, "💾 Logs saved to: ${logFile.absolutePath}")

            } catch (e: Exception) {
                log(logs, "❌ Error: ${e.message}")
                e.stackTrace.take(5).forEach { log(logs, "   at $it") }
                state = LaunchState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // تصفية اللوجات
    val displayedLogs = remember(logs.size, showOnlyErrors) {
        if (showOnlyErrors) {
            logs.filter {
                it.contains("❌") || it.contains("⚠️") ||
                        it.contains("Error", true) || it.contains("Warning", true)
            }
        } else logs.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // ===== Top Bar =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Launching Minecraft",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version: $versionId • Account: ${account.username}",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }

            StatusBadge(state)
        }

        // ===== Content =====
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== Console (Left) =====
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0A0F))
            ) {
                // Console Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CONSOLE",
                            fontSize = 11.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold,
                            color = VioraColors.VioraOrange
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${displayedLogs.size} lines",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Filter Errors Only
                        IconButton(
                            onClick = { showOnlyErrors = !showOnlyErrors },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showOnlyErrors) Icons.Default.FilterAlt
                                else Icons.Default.FilterAltOff,
                                contentDescription = "Filter errors",
                                tint = if (showOnlyErrors) VioraColors.VioraOrange
                                else colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Copy Logs
                        IconButton(
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(logs.joinToString("\n"))
                                )
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy logs",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Open Logs Folder
                        IconButton(
                            onClick = {
                                try {
                                    java.awt.Desktop.getDesktop()
                                        .open(java.io.File(LogManager.getLogsPath()))
                                } catch (e: Exception) { }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open logs",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Divider(color = colors.border, thickness = 1.dp)

                // Console Content
                if (displayedLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showOnlyErrors) "No errors found ✅"
                            else "Waiting for output...",
                            color = colors.textDisabled,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(displayedLogs) { line ->
                            LogLine(line)
                        }
                    }
                }
            }

            // ===== Side Panel (Right) =====
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Game Info
                InfoCard("Game Info") {
                    InfoRow("Version", versionId)
                    InfoRow("Account", account.username)
                    InfoRow("RAM", "4096 MB")
                    InfoRow("Java", "Auto-detected")
                }

                // Live Stats
                if (state is LaunchState.Running) {
                    InfoCard("Live Stats") {
                        InfoRow("Playtime", formatDuration(playtimeSeconds * 1000))
                        InfoRow("Launcher RAM", formatBytes(ramUsage))
                        InfoRow("Status", "🟢 Running")
                    }
                }

                if (state is LaunchState.Stopped) {
                    val s = state as LaunchState.Stopped
                    InfoCard("Session Ended") {
                        InfoRow("Exit Code", s.exitCode.toString())
                        InfoRow("Playtime", formatDuration(s.playtimeMs))
                    }
                }

                Spacer(Modifier.weight(1f))

                // Action Buttons
                when (val s = state) {
                    is LaunchState.Running -> {
                        // Stop Button
                        Button(
                            onClick = {
                                s.process.destroy()
                                log(logs, "⏹️ User stopped the game")
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VioraColors.Error
                            )
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Stop Game",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    is LaunchState.Stopped -> {
                        // Play Again Button
                        VioraButton(
                            text = "▶  Play Again",
                            onClick = {
                                logs.clear()
                                state = LaunchState.Preparing
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> { /* لا شيء */ }
                }

                // Back Button
                OutlinedButton(
                    onClick = {
                        when (val s = state) {
                            is LaunchState.Running -> s.process.destroy()
                            else -> {}
                        }
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        "← Back to Versions",
                        color = colors.textPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ============================================================
//  HELPER COMPOSABLES
// ============================================================

@Composable
private fun LogLine(line: String) {
    val color = when {
        line.contains("❌") || line.contains("ERROR", true) ||
                line.contains("Error", false) -> Color(0xFFFF6B6B)
        line.contains("⚠️") || line.contains("WARN", true) -> Color(0xFFFFD93D)
        line.contains("✅") || line.contains("✓") -> Color(0xFF69F0AE)
        line.contains("🚀") || line.contains("🎮") -> VioraColors.VioraOrange
        line.contains("🔧") || line.contains("⚙") -> VioraColors.VioraOrange
        else -> Color(0xFFA8FF60)
    }

    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = color,
        lineHeight = 17.sp
    )
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    val colors = VioraTheme.colors
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceElevated
        )
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = VioraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textSecondary
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun StatusBadge(state: LaunchState) {
    val (text, color) = when (state) {
        is LaunchState.Preparing -> "PREPARING" to VioraColors.Warning
        is LaunchState.Launching -> "LAUNCHING" to VioraColors.VioraOrange
        is LaunchState.Running -> "RUNNING" to VioraColors.Success
        is LaunchState.Error -> "ERROR" to VioraColors.Error
        is LaunchState.Stopped -> "STOPPED" to Color(0xFF8B95B5)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 1.sp
            )
        }
    }
}

// ============================================================
//  UTILITIES
// ============================================================

private fun log(logs: MutableList<String>, line: String) {
    println(line)
    logs.add(line)
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "${hours}h ${minutes}m ${secs}s"
    else if (minutes > 0) "${minutes}m ${secs}s"
    else "${secs}s"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return "%.0f MB".format(mb)
}