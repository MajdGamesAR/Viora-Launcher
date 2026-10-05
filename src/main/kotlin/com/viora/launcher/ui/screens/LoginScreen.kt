package com.viora.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.ui.components.ThemeToggleButton
import com.viora.launcher.ui.components.VioraButton
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme

@Composable
fun LoginScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onMicrosoftClick: () -> Unit,
    onVioraClick: () -> Unit
) {
    val colors = VioraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    // Dark: تدرج داكن خفيف
                    Brush.verticalGradient(
                        listOf(colors.background, colors.surface)
                    )
                } else {
                    // Light: خلفية بيضاء نظيفة
                    Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFF8F8FC))
                    )
                }
            )
    ) {
        // زر Theme Toggle في الأعلى يمين
        ThemeToggleButton(
            isDark = isDark,
            onToggle = onToggleTheme,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        )

        // المحتوى الرئيسي
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .width(440.dp)
        ) {
            // 🎨 الشعار (V بتدرج ألوان)
            Text(
                text = "V",
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                style = LocalTextStyle.current.copy(
                    brush = VioraColors.BrandGradient
                )
            )

            // ✨ اسم البرنامج
            Text(
                text = "VIORA",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                letterSpacing = 8.sp
            )

            Text(
                text = "L A U N C H E R",
                fontSize = 14.sp,
                letterSpacing = 12.sp,
                color = VioraColors.VioraOrange
            )

            Spacer(Modifier.height(40.dp))

            // زر Microsoft
            OutlinedButton(
                onClick = onMicrosoftClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Text(
                    "تسجيل الدخول بحساب Microsoft",
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
            }

            // فاصل "أو"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    Modifier.weight(1f),
                    color = colors.border
                )
                Text(
                    "أو",
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(
                    Modifier.weight(1f),
                    color = colors.border
                )
            }

            // 🎨 زر Viora (بتدرج ألوان)
            VioraButton(
                text = "حساب Viora",
                onClick = onVioraClick,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "v1.0.0-alpha",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}