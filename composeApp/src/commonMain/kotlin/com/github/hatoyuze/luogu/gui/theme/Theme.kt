package com.github.hatoyuze.luogu.gui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalThemeIsDark = compositionLocalOf { mutableStateOf(true) }

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    background = BackgroundDark,
    surfaceBright = SurfaceDark.copy(alpha = 0.8f).compositeOver(Color(0xFF22262B)),
    error = ErrorDark,
    onError = OnErrorDark,
    outline = OutlineDark,
)

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    background = BackgroundLight,
    surfaceBright = SurfaceLight,
    error = ErrorLight,
    onError = OnErrorLight,
    outline = OutlineLight,
)

@Composable
fun LuoguTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val isDark = remember { mutableStateOf(darkTheme) }
    CompositionLocalProvider(LocalThemeIsDark provides isDark) {
        val colorScheme = if (isDark.value) DarkColors else LightColors
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

private val Typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

object AppShapes {
    val extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    val small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
}
