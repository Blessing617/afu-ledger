package com.afu.ledger.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

/**
 * 按下时轻微缩小，松手弹回。
 *
 * 只动小元素（按键、步进钮、胶囊），不牵动整页；弹簧带一点过冲，和面板进场的 Q 弹感一致。
 * 用 `graphicsLayer` 在绘制阶段完成，不触发重组。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 所有弹层的遮罩色。
 *
 * 固定用「黑 32%」而不是 `colorScheme.onSurface`：深色模式下 onSurface 是浅色，
 * 遮罩会变成一层白雾；而系统 AlertDialog 用的是黑色 dim，两者并排就会一个发白一个发黑。
 * 统一成与系统对话框一致的观感后，面板、选择器、确认框的背景才算同一种「被压暗」。
 */
val ModalScrim: Color = Color.Black.copy(alpha = 0.32f)

/**
 * 轻触反馈（震动）。
 *
 * `TextHandleMove` 是系统里最轻的一档，不申请任何权限，也不会在静音/勿扰下强行响。
 * 只给「按下去要有回应」的控件用：键盘、步进器、主按钮。
 */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/** 长按触发的反馈：比 [rememberTapHaptic] 更重一档，用于「按住清空」这类动作。 */
@Composable
fun rememberLongPressHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/**
 * 按住连续触发：按下超过 [firstDelayMs] 后开始重复，节奏逐渐加快，抬手即停。
 *
 * 返回的 [MutableInteractionSource] 同时给 `clickable` 与 [pressScale] 用：
 * 短按由 `clickable` 的 onClick 触发一次；长按由这里的循环触发。
 *
 * **为什么不用 pointerInput 自己算**：每次状态变化都会重组，pointerInput 的 block 跟着重建，
 * 长按循环会被打断（真机表现是「按住只加了五六次就停」）。这里改用
 * `collectIsPressedAsState + LaunchedEffect`，循环挂在组合的 effect 上，不随手势节点重建。
 * 回调通过 `rememberUpdatedState` 取最新值，避免一直读按下瞬间的快照。
 */
@Composable
fun rememberPressRepeater(
    enabled: Boolean = true,
    firstDelayMs: Long = 320L,
    accelerateMs: Long = 10L,
    minIntervalMs: Long = 40L,
    onRepeat: () -> Unit,
): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val latest by rememberUpdatedState(onRepeat)

    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        delay(firstDelayMs)
        var interval = 90L
        while (true) {
            latest()
            delay(interval)
            interval = (interval - accelerateMs).coerceAtLeast(minIntervalMs)
        }
    }
    return source
}
