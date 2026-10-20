package com.afu.ledger.ui.theme

import androidx.compose.ui.graphics.Color

/** 一组可选主题色。id 用于持久化，不要随意改动。 */
data class ThemeColorPreset(
    val id: String,
    val label: String,
    val seed: Color,
)

/** 默认色板，顺序即设置页展示顺序。 */
val ThemeColorPresets: List<ThemeColorPreset> = listOf(
    ThemeColorPreset(id = "default", label = "云白紫", seed = Color(0xFF7C6BF0)),
    ThemeColorPreset(id = "sakura", label = "樱花粉", seed = Color(0xFFF06292)),
    ThemeColorPreset(id = "mint", label = "薄荷绿", seed = Color(0xFF4DB6AC)),
    ThemeColorPreset(id = "sky", label = "天空蓝", seed = Color(0xFF4FC3F7)),
    ThemeColorPreset(id = "lavender", label = "薰衣草", seed = Color(0xFF9575CD)),
    ThemeColorPreset(id = "peach", label = "蜜桃橙", seed = Color(0xFFFF8A65)),
    ThemeColorPreset(id = "matcha", label = "抹茶", seed = Color(0xFF8BC34A)),
    ThemeColorPreset(id = "cream", label = "奶油黄", seed = Color(0xFFFFCA28)),
)

fun themeColorPreset(id: String): ThemeColorPreset =
    ThemeColorPresets.firstOrNull { it.id == id } ?: ThemeColorPresets.first()
