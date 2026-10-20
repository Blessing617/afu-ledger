package com.afu.ledger.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 主题**风格**。
 *
 * ## 为什么风格与主题色是两个维度
 *
 * 只有「换个主色」不叫主题：形状、描边、装饰、吉祥物、动效手感全都不变，
 * 换完还是同一个界面换了个色号。
 *
 * 这里把风格（形状 / 描边 / 装饰 / 吉祥物 / 动效强度）与色相（[ThemeColorPreset]）
 * 拆开：风格决定「长什么样」，色相决定「什么颜色」。两者组合覆盖日常使用，
 * 又不至于像「任意形状 × 任意颜色」那样组合爆炸，让每个页面都要写兼容分支。
 *
 * 风格由 [LocalThemeStyle] 下发，页面不用逐层传参。
 */
data class ThemeStyle(
    val id: String,
    val label: String,
    val tagline: String,
    /** 该风格推荐的色相（[ThemeColorPreset.id]）；色板本身对全部风格可用。 */
    val recommendedColors: List<String>,
    val decoration: Decoration,
    val cardStyle: CardStyle,
    val motion: MotionFlavor,
    val showsMascot: Boolean,
    /** 卡片圆角。 */
    val cardCorner: Dp,
    /** 大容器（面板、对话框、预览卡）圆角。 */
    val containerCorner: Dp,
    /** 描边宽度；0 表示不描边。 */
    val outlineWidth: Dp,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    val lightOutline: Color,
    val lightOnSurface: Color,
    val lightOnSurfaceVariant: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val darkOutline: Color,
    val darkOnSurface: Color,
    val darkOnSurfaceVariant: Color,
) {
    val labeled: String get() = "$label · $tagline"

    /** 该风格下卡片表面的目标不透明度倍率：毛玻璃更透，描边卡片更实。 */
    val surfaceAlphaScale: Float
        get() = when (cardStyle) {
            CardStyle.GLASS -> 0.82f
            CardStyle.OUTLINED -> 1f
            CardStyle.PLAIN -> 1f
        }
}

/** 背景装饰种类。全部为静态绘制，不带动画。 */
enum class Decoration { NONE, BUBBLE, PETAL, STAR }

/** 卡片质感。 */
enum class CardStyle {
    /** 纯色、无边框、无阴影。 */
    PLAIN,

    /** 描边卡片：手绘贴纸质感。 */
    OUTLINED,

    /** 细描边 + 半透明表面：毛玻璃质感。 */
    GLASS,
}

/** 动效手感。 */
enum class MotionFlavor {
    /** 克制：只用减速曲线，不带回弹。 */
    CALM,

    /** Q 弹：弹簧带轻微回弹，用于小元素。 */
    BOUNCY,
}

/** 内置风格。顺序即选择页展示顺序。 */
val ThemeStyles: List<ThemeStyle> = listOf(
    ThemeStyle(
        id = "plain",
        label = "默认",
        tagline = "干净利落",
        recommendedColors = listOf("default", "mint", "sky"),
        decoration = Decoration.NONE,
        cardStyle = CardStyle.PLAIN,
        motion = MotionFlavor.CALM,
        showsMascot = false,
        cardCorner = 20.dp,
        containerCorner = 28.dp,
        outlineWidth = 0.dp,
        lightBackground = Color(0xFFFAF8FF),
        lightSurface = Color.White,
        lightSurfaceVariant = Color(0xFFF1EEF9),
        lightOutline = Color(0xFFD6D2E3),
        lightOnSurface = Color(0xFF1C1B22),
        lightOnSurfaceVariant = Color(0xFF5A5766),
        darkBackground = Color(0xFF131218),
        darkSurface = Color(0xFF1B1A21),
        darkSurfaceVariant = Color(0xFF27252F),
        darkOutline = Color(0xFF3A3745),
        darkOnSurface = Color(0xFFEDEBF2),
        darkOnSurfaceVariant = Color(0xFFB8B4C4),
    ),
    ThemeStyle(
        id = "fatfish",
        label = "大肥鱼",
        tagline = "蓝发女仆鲸鱼娘",
        recommendedColors = listOf("sky", "default", "lavender", "sakura"),
        decoration = Decoration.BUBBLE,
        cardStyle = CardStyle.OUTLINED,
        motion = MotionFlavor.BOUNCY,
        showsMascot = true,
        cardCorner = 26.dp,
        containerCorner = 34.dp,
        outlineWidth = 2.dp,
        // 配色取自应用图标/女仆鲸鱼娘立绘：深海蓝、白、淡蓝气泡
        lightBackground = Color(0xFFEAF3FF),
        lightSurface = Color(0xFFFFFFFF),
        lightSurfaceVariant = Color(0xFFDCEBFC),
        lightOutline = Color(0xFF2B4C8C),
        lightOnSurface = Color(0xFF17233A),
        lightOnSurfaceVariant = Color(0xFF56698A),
        darkBackground = Color(0xFF0E1524),
        darkSurface = Color(0xFF16203A),
        darkSurfaceVariant = Color(0xFF1F2C4A),
        darkOutline = Color(0xFFA9C6EA),
        darkOnSurface = Color(0xFFEAF1FF),
        darkOnSurfaceVariant = Color(0xFFA9BBD6),
    ),
)

/** 按 id 取风格，未知 id 回退到第一个（清爽），保证升级/脏数据不会崩。 */
fun themeStyle(id: String): ThemeStyle =
    ThemeStyles.firstOrNull { it.id == id } ?: ThemeStyles.first()

/** 当前风格。页面通过它取描边、圆角、装饰等，不再各自写死。 */
val LocalThemeStyle = staticCompositionLocalOf { ThemeStyles.first() }

/** 当前动效手感。 */
val LocalMotionFlavor = staticCompositionLocalOf { MotionFlavor.CALM }
