package com.afu.ledger.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * 按住后连续触发：按下立即执行一次，随后以逐渐加快的节奏重复，抬手立即停。
 *
 * 用于「退格连删」与「步进器连续加减」。不用 `detectTapGestures` 的原因见 AmountKeypad：
 * 系统长按判定后 onPress 会被取消；而长按期间系统又会持续派发 MOVE 事件，
 * 「等不到事件才重复」也永远触发不了。
 */
fun Modifier.repeatOnPress(
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit = {},
    firstDelayMs: Long = 380L,
    accelerateMs: Long = 12L,
    minIntervalMs: Long = 45L,
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                onPress()
                val repeat = launch {
                    var interval = firstDelayMs
                    while (true) {
                        delay(interval)
                        onPress()
                        interval = (interval - accelerateMs).coerceAtLeast(minIntervalMs)
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                } finally {
                    repeat.cancel()
                    onRelease()
                }
            }
        }
    }
}
