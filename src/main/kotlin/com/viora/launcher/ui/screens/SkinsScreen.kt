package com.viora.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.ui.components.AccountBadge
import com.viora.launcher.ui.components.NavItem
import com.viora.launcher.ui.components.Sidebar
import com.viora.launcher.ui.components.SteveAvatar
import com.viora.launcher.ui.components.VioraButton
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun SkinsScreen(
    account: Account,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigate: (NavItem) -> Unit,
    onAccountClick: () -> Unit,
    onBackToHome: () -> Unit
) {
    val colors = VioraTheme.colors
    var currentNav by remember { mutableStateOf(NavItem.SKINS) }
    val scope = rememberCoroutineScope()

    // ✅ حالات الأزرار
    var selectedSkinFile by remember { mutableStateOf<File?>(null) }
    var selectedSkinUrl by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    var isApplying by remember { mutableStateOf(false) }

    // ✅ URL السكن للعرض
    val skinRenderUrl: String = remember(account) {
        if (!account.skinUrl.isNullOrBlank()) account.skinUrl
        else if (account.uuid.isNotBlank()) "https://crafatar.com/renders/body/${account.uuid}?size=512&overlay"
        else "https://mc-heads.net/skin/MHF_Steve"
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
                        text = "Skins",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Customize your character",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }

                AccountBadge(account = account, onClick = onAccountClick)
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ===== Left: Skin Preview =====
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SteveAvatar(
                        skinUrl = account.skinUrl,
                        username = account.username,
                        uuid = account.uuid,
                        size = 300
                    )

                    Spacer(Modifier.height(24.dp))

                    // ✅ زر 3D Preview
                    VioraButton(
                        text = "🎮  Open 3D Preview",
                        onClick = {
                            try {
                                val htmlFile = File("src/main/resources/skin_preview.html")
                                if (!htmlFile.exists()) {
                                    println("⚠️ skin_preview.html not found")
                                    return@VioraButton
                                }
                                val encoded = URLEncoder.encode(skinRenderUrl, "UTF-8")
                                val targetUrl = "${htmlFile.toURI()}?skin=$encoded"
                                println("🌐 Opening 3D preview for: $skinRenderUrl")
                                Desktop.getDesktop().browse(URI(targetUrl))
                            } catch (e: Exception) {
                                println("❌ Failed: ${e.message}")
                            }
                        },
                        modifier = Modifier.width(300.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Opens 3D preview in your default browser",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                // ===== Right: Controls =====
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ===== Current Skin =====
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = "Current Skin",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SteveAvatar(
                                    skinUrl = account.skinUrl,
                                    username = account.username,
                                    uuid = account.uuid,
                                    size = 60
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = account.username,
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Custom skin",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // ===== Upload Skin =====
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = "Upload Skin",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(12.dp))

                            // ✅ زر "Upload from File" — يعمل
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val file = withContext(Dispatchers.IO) {
                                            openFileChooser()
                                        }
                                        if (file != null) {
                                            selectedSkinFile = file
                                            selectedSkinUrl = ""
                                            applyMessage = "✅ Selected: ${file.name}"
                                            println("📁 Selected file: ${file.absolutePath}")
                                        } else {
                                            println("⚠️ No file selected")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Icon(
                                    Icons.Default.Upload,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (selectedSkinFile != null)
                                        selectedSkinFile!!.name.take(25)
                                    else "Upload from File",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // ✅ زر "From URL" — يعمل
                            OutlinedButton(
                                onClick = {
                                    showUrlDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (selectedSkinUrl.isNotBlank())
                                        selectedSkinUrl.take(25)
                                    else "From URL",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // ===== Apply Button — يعمل =====
                    VioraButton(
                        text = if (isApplying) "Applying..." else "Apply Skin",
                        onClick = {
                            scope.launch {
                                isApplying = true
                                applyMessage = "⏳ Applying skin..."

                                // ⚠️ تحقق من إمكانية التطبيق
                                if (account.type.name != "MICROSOFT") {
                                    applyMessage = "❌ Only Microsoft accounts can change skins"
                                    isApplying = false
                                    return@launch
                                }

                                if (selectedSkinFile == null && selectedSkinUrl.isBlank()) {
                                    applyMessage = "❌ Please select a file or URL first"
                                    isApplying = false
                                    return@launch
                                }

                                // ✅ هنا تطبيق السكن (يحتاج API implementation)
                                applyMessage = "⏳ Uploading to Mojang..."

                                // TODO: implement real Mojang API upload
                                // 1. جلب access token من account
                                // 2. PUT https://api.minecraftservices.com/minecraft/profile/skins
                                // 3. مع multipart: variant=classic, file=skin.png

                                applyMessage = "⚠️ Skin upload not yet implemented"
                                isApplying = false
                            }
                        },
                        enabled = !isApplying && (selectedSkinFile != null || selectedSkinUrl.isNotBlank()),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ✅ رسالة الحالة
                    applyMessage?.let { msg ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.startsWith("✅"))
                                    VioraColors.Success.copy(alpha = 0.15f)
                                else if (msg.startsWith("❌"))
                                    VioraColors.Error.copy(alpha = 0.15f)
                                else
                                    VioraColors.VioraOrange.copy(alpha = 0.15f)
                            )
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // ===== Info =====
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = "ℹ️ Note",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VioraColors.VioraOrange
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Click 'Open 3D Preview' to view your skin in 3D.",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== URL Dialog =====
    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf(selectedSkinUrl) }

        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = {
                Text("Skin URL", color = colors.textPrimary)
            },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = {
                        Text("https://example.com/skin.png", color = colors.textSecondary)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (urlInput.isNotBlank()) {
                        selectedSkinUrl = urlInput
                        selectedSkinFile = null
                        applyMessage = "✅ URL saved: ${urlInput.take(30)}..."
                        println("🔗 URL saved: $urlInput")
                    }
                    showUrlDialog = false
                }) {
                    Text("OK", color = VioraColors.VioraMagenta)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }
}

// ============================================================
//  FILE CHOOSER (Swing)
// ============================================================
private fun openFileChooser(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select Minecraft Skin (.png)"
        fileFilter = FileNameExtensionFilter("PNG Images (*.png)", "png")
        isAcceptAllFileFilterUsed = false
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}