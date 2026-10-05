package com.viora.launcher.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Viora Launcher — Color Palette
 * مستوحى من شعار V: Magenta → Red → Orange
 */
object VioraColors {

    // ===== Brand Colors (من الشعار) =====
    val VioraMagenta = Color(0xFFFF0080)   // الوردي الفاقع
    val VioraPink    = Color(0xFFFF1E5A)   // الوردي المحمر
    val VioraRed     = Color(0xFFFF3D2E)   // الأحمر
    val VioraOrange  = Color(0xFFFF6B00)   // البرتقالي
    val VioraAmber   = Color(0xFFFF9500)   // البرتقالي الفاتح

    // ===== Gradient Brush (للاستخدام في الأزرار) =====
    val BrandGradient = Brush.linearGradient(
        colors = listOf(VioraMagenta, VioraRed, VioraOrange)
    )

    val BrandGradientHorizontal = Brush.horizontalGradient(
        colors = listOf(VioraMagenta, VioraOrange)
    )

    // ===== Dark Theme =====
    object Dark {
        val Background       = Color(0xFF0A0A0F)
        val Surface          = Color(0xFF14141C)
        val SurfaceElevated  = Color(0xFF1C1C26)
        val SurfaceHover     = Color(0xFF24242E)
        val Border           = Color(0xFF2A2A38)

        val TextPrimary      = Color(0xFFF5F5FA)
        val TextSecondary    = Color(0xFF9A9AAE)
        val TextDisabled     = Color(0xFF5A5A6E)
    }

    // ===== Light Theme =====
    object Light {
        val Background       = Color(0xFFFFFFFF)
        val Surface          = Color(0xFFF8F8FC)
        val SurfaceElevated  = Color(0xFFFFFFFF)
        val SurfaceHover     = Color(0xFFF0F0F5)
        val Border           = Color(0xFFE0E0E8)

        val TextPrimary      = Color(0xFF1A1A24)
        val TextSecondary    = Color(0xFF6A6A7E)
        val TextDisabled     = Color(0xFFAAAAAA)
    }

    // ===== Status (مشتركة) =====
    val Error   = Color(0xFFFF4757)
    val Warning = Color(0xFFFFD93D)
    val Success = Color(0xFF2ED573)
    val Info    = Color(0xFF00B4FF)
}