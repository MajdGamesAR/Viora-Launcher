package com.viora.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.AuthManager
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.ui.components.VioraButton
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme
import kotlinx.coroutines.launch

@Composable
fun VioraLoginScreen(
    authManager: AuthManager,
    onSuccess: (Account) -> Unit,
    onError: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = VioraTheme.colors
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var offlineMode by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.width(440.dp)
        ) {
            // الشعار
            Text(
                text = "V",
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = VioraColors.VioraMagenta
            )

            Text(
                text = "حساب Viora",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            // Toggle Offline
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "وضع Offline",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = offlineMode,
                    onCheckedChange = { offlineMode = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VioraColors.VioraOrange,
                        checkedTrackColor = VioraColors.VioraMagenta
                    )
                )
            }

            if (offlineMode) {
                Text(
                    "لا يحتاج كلمة مرور — للعب الفردي",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("اسم المستخدم") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VioraColors.VioraMagenta,
                    unfocusedBorderColor = colors.border,
                    focusedLabelColor = VioraColors.VioraMagenta,
                    unfocusedLabelColor = colors.textSecondary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = VioraColors.VioraMagenta
                )
            )

            // Password (Online mode only)
            if (!offlineMode) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioraColors.VioraMagenta,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = VioraColors.VioraMagenta,
                        unfocusedLabelColor = colors.textSecondary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = VioraColors.VioraMagenta
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // زر الدخول
            VioraButton(
                text = if (loading) "جارٍ..." else "دخول",
                onClick = {
                    scope.launch {
                        loading = true
                        try {
                            val account = if (offlineMode) {
                                authManager.loginVioraOffline(username)
                            } else {
                                authManager.loginVioraOnline(username, password)
                            }
                            onSuccess(account)
                        } catch (e: Exception) {
                            onError(e.message ?: "خطأ")
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = username.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(onClick = onBack) {
                Text("رجوع", color = colors.textSecondary)
            }
        }
    }
}