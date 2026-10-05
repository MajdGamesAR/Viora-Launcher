package com.viora.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * بيانات الألوان المخصصة (خارج Material)
 */
data class VioraColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHover: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val isDark: Boolean
)

val LocalVioraColors = staticCompositionLocalOf {
    VioraColorScheme(
        background = VioraColors.Dark.Background,
        surface = VioraColors.Dark.Surface,
        surfaceElevated = VioraColors.Dark.SurfaceElevated,
        surfaceHover = VioraColors.Dark.SurfaceHover,
        border = VioraColors.Dark.Border,
        textPrimary = VioraColors.Dark.TextPrimary,
        textSecondary = VioraColors.Dark.TextSecondary,
        textDisabled = VioraColors.Dark.TextDisabled,
        isDark = true
    )
}

/**
 * الوصول السريع للألوان من أي Composable:
 *   VioraTheme.colors.textPrimary
 */
object VioraTheme {
    val colors: VioraColorScheme
        @Composable get() = LocalVioraColors.current
}

// ===== Material Color Schemes =====
private val VioraDarkMaterial = darkColorScheme(
    primary = VioraColors.VioraMagenta,
    onPrimary = Color.White,
    secondary = VioraColors.VioraOrange,
    onSecondary = Color.White,
    tertiary = VioraColors.VioraRed,
    background = VioraColors.Dark.Background,
    onBackground = VioraColors.Dark.TextPrimary,
    surface = VioraColors.Dark.Surface,
    onSurface = VioraColors.Dark.TextPrimary,
    surfaceVariant = VioraColors.Dark.SurfaceElevated,
    onSurfaceVariant = VioraColors.Dark.TextSecondary,
    error = VioraColors.Error,
    outline = VioraColors.Dark.Border
)

private val VioraLightMaterial = lightColorScheme(
    primary = VioraColors.VioraMagenta,
    onPrimary = Color.White,
    secondary = VioraColors.VioraOrange,
    onSecondary = Color.White,
    tertiary = VioraColors.VioraRed,
    background = VioraColors.Light.Background,
    onBackground = VioraColors.Light.TextPrimary,
    surface = VioraColors.Light.Surface,
    onSurface = VioraColors.Light.TextPrimary,
    surfaceVariant = VioraColors.Light.SurfaceElevated,
    onSurfaceVariant = VioraColors.Light.TextSecondary,
    error = VioraColors.Error,
    outline = VioraColors.Light.Border
)

@Composable
fun VioraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VioraDarkMaterial else VioraLightMaterial

    val vioraColors = if (darkTheme) {
        VioraColorScheme(
            background = VioraColors.Dark.Background,
            surface = VioraColors.Dark.Surface,
            surfaceElevated = VioraColors.Dark.SurfaceElevated,
            surfaceHover = VioraColors.Dark.SurfaceHover,
            border = VioraColors.Dark.Border,
            textPrimary = VioraColors.Dark.TextPrimary,
            textSecondary = VioraColors.Dark.TextSecondary,
            textDisabled = VioraColors.Dark.TextDisabled,
            isDark = true
        )
    } else {
        VioraColorScheme(
            background = VioraColors.Light.Background,
            surface = VioraColors.Light.Surface,
            surfaceElevated = VioraColors.Light.SurfaceElevated,
            surfaceHover = VioraColors.Light.SurfaceHover,
            border = VioraColors.Light.Border,
            textPrimary = VioraColors.Light.TextPrimary,
            textSecondary = VioraColors.Light.TextSecondary,
            textDisabled = VioraColors.Light.TextDisabled,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalVioraColors provides vioraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VioraTypography,
            content = content
        )
    }
}