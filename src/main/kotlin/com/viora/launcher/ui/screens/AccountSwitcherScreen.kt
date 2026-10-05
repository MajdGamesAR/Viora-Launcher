package com.viora.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.AuthManager
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.auth.model.AccountType
import com.viora.launcher.ui.components.SteveAvatar
import com.viora.launcher.ui.components.ThemeToggleButton
import com.viora.launcher.ui.components.VioraButton
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme

@Composable
fun AccountSwitcherScreen(
    authManager: AuthManager,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onAccountSelected: (Account) -> Unit,
    onAddAccount: () -> Unit
) {
    val colors = VioraTheme.colors

    var accounts by remember { mutableStateOf(authManager.getAllAccounts()) }
    var selected by remember { mutableStateOf<Account?>(accounts.firstOrNull()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.surface)
                )
            )
    ) {
        // زر تبديل الثيم في الأعلى يمين
        ThemeToggleButton(
            isDark = isDark,
            onToggle = onToggleTheme,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Account",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(accounts, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        isSelected = account.id == selected?.id,
                        isDark = isDark,
                        onClick = { selected = account },
                        onDelete = {
                            authManager.removeAccount(account)
                            accounts = authManager.getAllAccounts()
                            if (selected?.id == account.id) {
                                selected = accounts.firstOrNull()
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onAddAccount,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        "+ Add Account",
                        color = colors.textPrimary,
                        fontSize = 15.sp
                    )
                }

                VioraButton(
                    text = "Continue",
                    onClick = { selected?.let(onAccountSelected) },
                    enabled = selected != null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = VioraTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.surfaceHover else colors.surfaceElevated
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, VioraColors.VioraMagenta)
        } else {
            BorderStroke(1.dp, colors.border)
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ Steve أو سكن اللاعب
            SteveAvatar(
                skinUrl = account.skinUrl,
                username = account.username,
                size = 48
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = account.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = if (account.type == AccountType.MICROSOFT) "Microsoft" else "Viora",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }

            TextButton(onClick = onDelete) {
                Text("Remove", color = VioraColors.Error)
            }
        }
    }
}