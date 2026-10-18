package com.afu.ledger.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance

/**
 * 内容卡片的底色不透明度。
 *
 * 由用户设置直接给出：越想让图明显，卡片就越透。
 * 但深色模式不能照搬同一个值 —— 见 [effectiveSurfaceAlpha]。
 */
val LocalSurfaceAlpha = staticCompositionLocalOf { 0.95f }

/**
 * 深色模式下卡片不透明度的额外补偿。
 *
 * 同一张背景图、同一个 alpha 下，浅色模式（白色卡片）把背景提亮，
 * 文字是深色，对比度天然够；深色模式（深色卡片）则是把背景压暗，
 * 一旦背景某处本身很亮（照片里常见的高光、白色区域），
 * 透出来的亮块会和浅色文字糊在一起，卡片内亮度差能达到数倍。
 *
 * 实测（小米 14 Pro，1440×3200，深色模式 + 用户自定义背景）：
 * 同一张卡片内取样点亮度从 36 到 183 不等，正文显著可读性下降。
 *
 * 因此深色模式下把 alpha 往 1 收敛：用户仍能看见背景的氛围，
 * 但文字不会与背景图的高光混在一起。
 */
private const val DARK_MODE_ALPHA_BONUS = 0.05f

/**
 * 当前应该用多少不透明度画卡片。
 *
 * 浅色模式直接用用户设定值；深色模式在此基础上收敛一部分，
 * 保证「背景图能看见」与「文字看得清」同时成立。
 */
@Composable
fun effectiveSurfaceAlpha(): Float {
    val userAlpha = LocalSurfaceAlpha.current
    // surface 是深色还是浅色，直接决定要不要补偿；不依赖外部传进来的 isDark，
    // 这样在嵌套的 AfuTheme（例如悬浮窗里的确认卡片）里也自动正确。
    val surfaceIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (surfaceIsDark) {
        (userAlpha + DARK_MODE_ALPHA_BONUS).coerceAtMost(1f)
    } else {
        userAlpha
    }
}
