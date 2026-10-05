package com.viora.launcher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.version.model.VersionEntry
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VersionCard(
    version: VersionEntry,
    isInstalled: Boolean,
    isInstalling: Boolean,
    installProgress: Float,
    installedSizeMB: Long = 0,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VioraTheme.colors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceElevated
        ),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ===== Type Indicator =====
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            version.isRelease -> VioraColors.BrandGradient
                            version.isSnapshot -> androidx.compose.ui.graphics.SolidColor(
                                VioraColors.VioraOrange.copy(alpha = 0.3f)
                            )
                            else -> androidx.compose.ui.graphics.SolidColor(colors.surface)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        version.isRelease -> "R"
                        version.isSnapshot -> "S"
                        else -> "O"
                    },
                    color = if (version.isRelease) Color.White else colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.width(16.dp))

            // ===== Info =====
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = version.id,
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isInstalled) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Installed",
                            tint = VioraColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            version.isRelease -> "Release"
                            version.isSnapshot -> "Snapshot"
                            version.type == "old_beta" -> "Beta"
                            version.type == "old_alpha" -> "Alpha"
                            else -> version.type
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Text(
                        text = " • ${formatDate(version.releaseTime)}",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    if (isInstalled && installedSizeMB > 0) {
                        Text(
                            text = " • ${installedSizeMB} MB",
                            color = VioraColors.VioraOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ===== Actions =====
            when {
                isInstalling -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${(installProgress * 100).toInt()}%",
                            color = VioraColors.VioraMagenta,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(installProgress)
                                    .background(VioraColors.BrandGradientHorizontal)
                            )
                        }
                    }
                }

                isInstalled -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Delete Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface)
                                .clickable(onClick = onDelete),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = VioraColors.Error,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Play Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(VioraColors.BrandGradientHorizontal)
                                .clickable(onClick = onPlay)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Play",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .clickable(onClick = onInstall)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Install",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Install",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val output = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        output.format(input.parse(isoDate) ?: Date())
    } catch (e: Exception) {
        isoDate.take(10)
    }
}