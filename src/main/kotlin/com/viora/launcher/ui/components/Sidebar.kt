package com.viora.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme

enum class NavItem(
    val label: String,
    val icon: ImageVector
) {
    HOME("Home", Icons.Default.Home),
    VERSIONS("Versions", Icons.Default.Layers),
    MODS("Mods & Packs", Icons.Default.Extension),
    SKINS("Skins", Icons.Default.Person),
    SERVERS("Servers", Icons.Default.Dns),
    STORE("Store", Icons.Default.ShoppingBag),
    NEWS("News", Icons.Default.Newspaper),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun Sidebar(
    currentItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VioraTheme.colors

    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ===== Logo =====
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "V",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = VioraColors.VioraMagenta
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "VIORA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "LAUNCHER",
                    fontSize = 9.sp,
                    letterSpacing = 3.sp,
                    color = VioraColors.VioraOrange
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== Nav Items =====
        NavItem.entries.forEach { item ->
            SidebarItem(
                item = item,
                isSelected = item == currentItem,
                onClick = { onItemSelected(item) }
            )
        }

        Spacer(Modifier.weight(1f))

        // ===== Theme Toggle =====
        ThemeToggleButton(
            isDark = isDark,
            onToggle = onToggleTheme,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SidebarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = VioraTheme.colors

    // ✅ حل التعارض: نستخدم Brush موحّد
    val backgroundBrush = if (isSelected) {
        VioraColors.BrandGradientHorizontal
    } else {
        androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(brush = backgroundBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (isSelected) androidx.compose.ui.graphics.Color.White
                else colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.label,
                color = if (isSelected) androidx.compose.ui.graphics.Color.White
                else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold
                else FontWeight.Normal
            )
        }
    }
}