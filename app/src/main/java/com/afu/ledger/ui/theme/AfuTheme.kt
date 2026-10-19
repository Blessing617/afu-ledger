package com.afu.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.afu.ledger.data.prefs.DarkModeSetting
import com.afu.ledger.data.prefs.ThemeSettings

/** 由「风格 + 种子色」派生浅色配色。 */
private fun lightScheme(style: ThemeStyle, seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = seed.copy(alpha = 0.16f),
    onPrimaryContainer = seed,
    secondary = seed.copy(alpha = 0.75f),
    secondaryContainer = seed.copy(alpha = 0.12f),
    onSecondaryContainer = seed,
    tertiary = seed.copy(alpha = 0.55f),
    background = style.lightBackground,
    onBackground = style.lightOnSurface,
    surface = style.lightSurface,
    onSurface = style.lightOnSurface,
    surfaceVariant = style.lightSurfaceVariant,
    onSurfaceVariant = style.lightOnSurfaceVariant,
    outline = style.lightOutline,
    outlineVariant = style.lightOutline.copy(alpha = 0.42f),
)

/** 由「风格 + 种子色」派生深色配色。 */
private fun darkScheme(style: ThemeStyle, seed: Color) = darkColorScheme(
    primary = seed.copy(alpha = 0.92f),
    onPrimary = Color(0xFF16151B),
    primaryContainer = seed.copy(alpha = 0.28f),
    onPrimaryContainer = Color.White,
    secondary = seed.copy(alpha = 0.8f),
    secondaryContainer = seed.copy(alpha = 0.22f),
    onSecondaryContainer = Color.White,
    tertiary = seed.copy(alpha = 0.65f),
    background = style.darkBackground,
    onBackground = style.darkOnSurface,
    surface = style.darkSurface,
    onSurface = style.darkOnSurface,
    surfaceVariant = style.darkSurfaceVariant,
    onSurfaceVariant = style.darkOnSurfaceVariant,
    outline = style.darkOutline,
    outlineVariant = style.darkOutline.copy(alpha = 0.55f),
)

/** 形状随风格变化：圆角档位与卡片一致，避免「卡片圆、输入框方」。 */
private fun shapesFor(style: ThemeStyle) = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(style.cardCorner - 4.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(style.cardCorner),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(style.containerCorner),
)

/**
 * 应用主题。
 *
 * 风格与色相分别下发：色相进 [MaterialTheme]（各页面已按 `colorScheme` 取色），
 * 风格进 [LocalThemeStyle]（描边、圆角、装饰、吉祥物、动效手感）。
 *
 * **切换风格不做逐帧颜色动画**：那会让每个读取颜色的 composable 每帧重组，
 * 与帧预算冲突。直接换值，一次重组完成。
 */
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
    val style = themeStyle(settings.themeStyleId)
    val seed = themeColorPreset(settings.themeColorId).seed
    val colorScheme = remember(style, seed, isDark) {
        if (isDark) darkScheme(style, seed) else lightScheme(style, seed)
    }
    val shapes = remember(style) { shapesFor(style) }

    CompositionLocalProvider(
        LocalThemeStyle provides style,
        LocalMotionFlavor provides style.motion,
        LocalDarkTheme provides isDark,
    ) {
        SystemBarsEffect(isDark)
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = AfuTypography,
            content = content,
        )
    }
}

/** 当前是否深色主题。用于系统栏图标、插画配色等需要「知道深浅」的地方。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * 系统栏图标明暗。
 *
 * edge-to-edge 之后应用内容会画到状态栏与导航条后面，此时系统栏图标的颜色
 * **不会自动跟随内容**：浅色背景上仍是白色图标，就表现为「顶部内容被压住/看不清」。
 * 这里按当前主题切换图标明暗。
 */
@Composable
private fun SystemBarsEffect(isDark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
