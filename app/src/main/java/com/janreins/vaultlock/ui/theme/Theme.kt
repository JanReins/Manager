package com.janreins.vaultlock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultDarkColorScheme = darkColorScheme(
    primary = Amber400,
    onPrimary = VaultBlack,
    primaryContainer = Amber900,
    onPrimaryContainer = Amber300,
    secondary = BlueInfo,
    onSecondary = VaultBlack,
    secondaryContainer = VaultCardDark,
    onSecondaryContainer = BlueInfo,
    tertiary = EmeraldSuccess,
    onTertiary = VaultBlack,
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = EmeraldLight,
    background = VaultBlack,
    onBackground = TextPrimaryDark,
    surface = VaultDarkNavy,
    onSurface = TextPrimaryDark,
    surfaceVariant = VaultCardDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = VaultBorder,
    error = RedError,
    onError = Color.White
)

private val VaultLightColorScheme = lightColorScheme(
    primary = Amber600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Amber900,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    background = VaultLightBg,
    onBackground = TextPrimaryLight,
    surface = VaultLightCard,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = VaultLightBorder,
    error = RedError,
    onError = Color.White
)

@Composable
fun VaultLockTheme(
    themeMode: String = "dark", // "dark", "light", "system"
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) VaultDarkColorScheme else VaultLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
