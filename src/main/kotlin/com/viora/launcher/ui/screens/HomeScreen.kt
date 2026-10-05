package com.viora.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.ui.components.*
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme

@Composable
fun HomeScreen(
    account: Account,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigate: (NavItem) -> Unit,
    onAccountClick: () -> Unit,
    onPlay: () -> Unit
) {
    val colors = VioraTheme.colors
    var currentNav by remember { mutableStateOf(NavItem.HOME) }

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
                onNavigate(it)
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
                Text(
                    text = "Home",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )

                AccountBadge(
                    account = account,
                    onClick = onAccountClick
                )
            }

            Spacer(Modifier.height(24.dp))

            // ===== Scrollable Content =====
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PlayCard(
                    account = account,
                    onPlay = onPlay
                )

                Text(
                    text = "Latest News",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                val newsList = listOf(
                    NewsItem(
                        title = "Minecraft 1.21.4 Released",
                        description = "The latest Minecraft version is now available with new features and improvements.",
                        date = "3 days ago",
                        tag = "Minecraft"
                    ),
                    NewsItem(
                        title = "Viora Launcher v1.0",
                        description = "First release of Viora Launcher with full multilingual support.",
                        date = "Today",
                        tag = "Update"
                    ),
                    NewsItem(
                        title = "Modpacks Support Coming Soon",
                        description = "Download and install Modpacks from CurseForge directly in the launcher.",
                        date = "Soon",
                        tag = "Upcoming"
                    )
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(newsList) { news ->
                        NewsCard(
                            news = news,
                            modifier = Modifier.width(320.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlayCard(
    account: Account,
    onPlay: () -> Unit
) {
    val colors = VioraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        VioraColors.VioraMagenta.copy(alpha = 0.35f),
                        VioraColors.VioraOrange.copy(alpha = 0.35f)
                    )
                )
            )
            .background(colors.surfaceElevated.copy(alpha = 0.6f))
            .padding(28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Ready to Play?",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Welcome, ${account.username}!",
                    fontSize = 15.sp,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pick a version and start your adventure",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }

            VioraButton(
                text = "▶  Play",
                onClick = onPlay,
                modifier = Modifier
                    .width(180.dp)
                    .height(64.dp)
            )
        }
    }
}