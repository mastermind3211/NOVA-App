package com.nova.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object NovaColors {
    val ObsidianBg = Color(0xFF050811)
    val DeepSpacePanel = Color(0xCC0D1426)
    val GlassCard = Color(0x99111B33)
    val NeonCyan = Color(0xFF00F0FF)
    val ElectricBlue = Color(0xFF2979FF)
    val CyberPurple = Color(0xFFB537F2)
    val EmeraldActive = Color(0xFF00E676)
    val AmberWarn = Color(0xFFFFB300)
    val CrimsonError = Color(0xFFFF3D71)
    val TextPrimary = Color(0xFFEAF6FF)
    val TextMuted = Color(0xFF88A4C7)
    val BorderGlow = Color(0x5500F0FF)
}

private val NovaColorScheme = darkColorScheme(
    primary = NovaColors.NeonCyan,
    secondary = NovaColors.ElectricBlue,
    tertiary = NovaColors.CyberPurple,
    background = NovaColors.ObsidianBg,
    surface = NovaColors.DeepSpacePanel,
    onPrimary = NovaColors.ObsidianBg,
    onSecondary = Color.White,
    onBackground = NovaColors.TextPrimary,
    onSurface = NovaColors.TextPrimary,
    error = NovaColors.CrimsonError
)

private val NovaTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 3.sp,
        color = NovaColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 1.sp,
        color = NovaColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = NovaColors.TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = NovaColors.NeonCyan
    )
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NovaColorScheme,
        typography = NovaTypography,
        content = content
    )
}
