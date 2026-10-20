package com.afu.ledger.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
 * 拖动滑块时的**临时**卡片不透明度。
 *
 * 曾经用它让主界面卡片在拖动时实时变化，但真机上掉帧严重：
 * 每帧都要让所有半透明卡片与背景图重新合成，拖起来一顿一顿的（用户反馈「卡」）。
 *
 * 现在拖动只在**外观页内部**预览（缩略图上的示例卡），松手才把值写进 DataStore、
 * 全局生效。这样拖动只重绘一小块，跟手且不掉帧。
 *
 * 保留这个 Local 是为了将来需要「全局跟手预览」时不必改调用方；当前不驱动全局卡片。
 */
val LocalCardOpacityPreview = staticCompositionLocalOf<androidx.compose.runtime.MutableState<Float?>?> { null }

/**
 * 设置类页面（设置、外观与主题）强制卡片不透明。
 *
 * 这些页面本身承载的是「控件」而不是「内容」，半透明会让每一帧都要与背景图重新混合：
 * 拖动滑块时重绘区域虽小，但合成是全屏的，真机上表现为一顿一顿。
 * 强制不透明后拖动只重绘控件，流畅度立刻回来；「要图还是要字」的效果
 * 由外观页缩略图上的示例卡演示。
 */
val LocalForceOpaqueCard = staticCompositionLocalOf { false }

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
