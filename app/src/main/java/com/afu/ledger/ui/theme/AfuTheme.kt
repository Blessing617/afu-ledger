package com.afu.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.afu.ledger.data.prefs.DarkModeSetting
import com.afu.ledger.data.prefs.ThemeSettings

/** 由种子色派生浅色配色。 */
private fun lightScheme(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = seed.copy(alpha = 0.16f),
    onPrimaryContainer = seed,
    secondary = seed.copy(alpha = 0.75f),
    secondaryContainer = seed.copy(alpha = 0.12f),
    onSecondaryContainer = seed,
    tertiary = seed.copy(alpha = 0.55f),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1C1B22),
    surface = Color.White,
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFF1EEF9),
    onSurfaceVariant = Color(0xFF5A5766),
    outline = Color(0xFFD6D2E3),
)

/** 由种子色派生深色配色。 */
private fun darkScheme(seed: Color) = darkColorScheme(
    primary = seed.copy(alpha = 0.9f),
    onPrimary = Color(0xFF16151B),
    primaryContainer = seed.copy(alpha = 0.28f),
    onPrimaryContainer = Color.White,
    secondary = seed.copy(alpha = 0.8f),
    secondaryContainer = seed.copy(alpha = 0.22f),
    onSecondaryContainer = Color.White,
    tertiary = seed.copy(alpha = 0.65f),
    background = Color(0xFF131218),
    onBackground = Color(0xFFEDEBF2),
    surface = Color(0xFF1B1A21),
    onSurface = Color(0xFFEDEBF2),
    surfaceVariant = Color(0xFF27252F),
    onSurfaceVariant = Color(0xFFB8B4C4),
    outline = Color(0xFF3A3745),
)

@Composable
fun AfuTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (settings.darkMode) {
        DarkModeSetting.SYSTEM -> systemDark
        DarkModeSetting.LIGHT -> false
        DarkModeSetting.DARK -> true
    }
    val seed = themeColorPreset(settings.themeColorId).seed
    val colorScheme = if (isDark) darkScheme(seed) else lightScheme(seed)

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AfuShapes,
        typography = AfuTypography,
        content = content,
    )
}
