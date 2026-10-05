package com.viora.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.AuthManager
import com.viora.launcher.core.auth.model.Account

@Composable
fun MicrosoftLoginScreen(
    authManager: AuthManager,
    onSuccess: (Account) -> Unit,
    onError: () -> Unit,
    onBack: () -> Unit
) {
    var statusText by remember { mutableStateOf("جارٍ التهيئة...") }
    var userCode by remember { mutableStateOf<String?>(null) }
    var verificationUri by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPolling by remember { mutableStateOf(false) }

    // طلب Device Code عند فتح الشاشة
    LaunchedEffect(Unit) {
        try {
            val deviceCode = authManager.startMicrosoftLogin()
            userCode = deviceCode.userCode
            verificationUri = deviceCode.verificationUri
            statusText = "الرجاء زيارة الرابط وإدخال الكود"
            isPolling = true

            // بدء polling
            val account = authManager.completeMicrosoftLogin(deviceCode.deviceCode)
            onSuccess(account)
        } catch (e: Exception) {
            errorMessage = e.message ?: "خطأ غير معروف"
            isPolling = false
            println("❌ [UI] خطأ: ${e.message}")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Microsoft تسجيل الدخول بحساب",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (errorMessage == null) {
                // عرض حالة التحميل
                CircularProgressIndicator(color = Color(0xFFFF4081))
                Text(statusText, color = Color.Gray)

                // عرض الكود والرابط
                userCode?.let {
                    Spacer(Modifier.height(24.dp))
                    Text("الكود:", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        it,
                        color = Color(0xFFFF4081),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    verificationUri?.let { uri ->
                        Text("الرابط: $uri", color = Color.Cyan, fontSize = 14.sp)
                    }
                }
            } else {
                // عرض الخطأ
                Text(
                    "❌ فشل تسجيل الدخول",
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    errorMessage ?: "",
                    color = Color(0xFFFF8888),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4081)
                    )
                ) {
                    Text("رجوع", color = Color.White)
                }
            }
        }
    }
}