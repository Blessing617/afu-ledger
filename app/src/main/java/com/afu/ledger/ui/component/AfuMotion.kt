package com.afu.ledger.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 动效曲线与时长 token。
 *
 * ## 为什么要单独一份
 *
 * 1. **规格要求减速曲线**：`design-system` 明确写着「缓动 MUST 使用减速曲线或
 *    spring，MUST NOT 线性匀速」。此前各处直接用 `tween(300)`，即线性缓动，
 *    观感上是「机械地平移」，这是动效不流畅的根源之一。
 * 2. **统一手感**：同一个应用里每处动画各写一个时长与曲线，拼在一起就会
 *    「一段快一段慢」，看着不连贯。这里把它们收成一套档位。
 *
 * ## 三个档位的语义
 *
 * - [settle]：元素**已经到位**，只是从一种形态变成另一种（标签、开关、选中态）。
 *   用 spring，带一点回弹，像真实的物理落位。
 * - [enter]：元素**从别处进来**（二级页、对话框、悬浮窗）。用减速曲线，
 *   起始快、结尾缓，符合「减速进入」的视觉预期。
 * - [exit]：元素**离开**。比进入更快，因为用户此时已经不看它了。
 */
object AfuMotion {

    /**
     * Material 3 的标准减速曲线，用于「进入」类过渡。
     *
     * 前 30% 走完约 70% 的位移，视觉上「一下就到位，然后轻轻收住」。
     */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /**
     * 标准加速曲线，用于「退出」类过渡：慢起快走，让元素干脆地离场。
     */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 「已经到位、只是变形」：轻微回弹的 spring，模拟落位的重量感。 */
    fun settle(): SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 位移动画的 spring：阻尼更高，避免整块内容来回晃。 */
    fun glide(): SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * Q 弹手感：用于**小元素**（分类格、按键、图标）的按下与选中。
     *
     * 阻尼 0.55 会产生轻微过冲（约 5%），视觉上有「弹一下才落住」的可爱感；
     * 只允许用于小元素 —— 整页或大卡片过冲会露出底色，规格明确禁止。
     */
    fun bouncy(): SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 底部面板上滑/回位：阻尼更高，因为面板面积大，过冲会明显。 */
    fun sheet(): SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * 底部面板的「Q 弹」进场：比 [sheet] 更有弹性，过冲约 5%。
     *
     * 用它的地方必须自己处理过冲方向：面板顶部从屏幕外进来，过冲不能表现为「整体继续上移」，
     * 否则面板底边会离开屏幕下沿、露出底页。正确做法是以底边为锚做一点点缩放（见记账面板），
     * 这样回弹是「顶部弹一下」，底部始终贴着屏幕。
     */
    fun sheetBouncy(): SpringSpec<Float> = spring(
        dampingRatio = 0.66f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 时长档位（毫秒）。与规格的 200 / 260 上限对齐。 */
    const val DURATION_TAB_MS = 180
    const val DURATION_ENTER_MS = 260
    const val DURATION_EXIT_MS = 160
    const val DURATION_OVERLAY_MS = 240
    const val DURATION_SCRIM_MS = 200
    const val DURATION_MICRO_MS = 140
}
