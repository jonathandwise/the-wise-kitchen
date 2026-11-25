package com.wisekitchen.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.wisekitchen.app.data.ThemeMode

// Light: cream paper and dark brown ink, like the printed cookbook, with a terracotta accent.
private val Light = lightColorScheme(
    primary = Color(0xFFA6462A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DED2),
    onPrimaryContainer = Color(0xFF3D1406),
    secondary = Color(0xFF6B5D52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADFD2),
    onSecondaryContainer = Color(0xFF2A231E),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF2A231E),
    surface = Color(0xFFFBF6EE),
    onSurface = Color(0xFF2A231E),
    surfaceVariant = Color(0xFFF2E9DC),
    onSurfaceVariant = Color(0xFF5F5349),
    surfaceContainer = Color(0xFFF5EDE1),
    surfaceContainerHigh = Color(0xFFF0E6D8),
    surfaceContainerHighest = Color(0xFFEBE0D0),
    outline = Color(0xFFCDBFAE),
    outlineVariant = Color(0xFFE4D8C8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFEE9A78),
    onPrimary = Color(0xFF4A1A08),
    primaryContainer = Color(0xFF5E2A17),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFD3C3B6),
    onSecondary = Color(0xFF392E25),
    secondaryContainer = Color(0xFF3D352E),
    onSecondaryContainer = Color(0xFFEEE6DC),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFEEE6DC),
    surface = Color(0xFF16130F),
    onSurface = Color(0xFFEEE6DC),
    surfaceVariant = Color(0xFF2A241E),
    onSurfaceVariant = Color(0xFFCBBFB2),
    surfaceContainer = Color(0xFF221D18),
    surfaceContainerHigh = Color(0xFF2B251F),
    surfaceContainerHighest = Color(0xFF352E27),
    outline = Color(0xFF6E6257),
    outlineVariant = Color(0xFF3D352E),
)

private val Serif = FontFamily.Serif

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    )
}

val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun WiseKitchenTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}

/** Draws [content] with text [scale] times its normal size (on top of the phone's own font size). */
@Composable
fun ScaledText(scale: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * scale), content = content)
}
