package com.afu.ledger.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.LocalThemeStyle

/**
 * 应用统一的滑块。
 *
 * ## 为什么不用 Material Slider
 *
 * Material3 默认 Slider 是「一条 4dp 细线 + 小圆点」的仪表盘样子：
 * 轨道太细、拇指太小、没有任何「可以拖」的暗示，和应用的圆润贴纸语言对不上。
 *
 * ## 手势为什么手写
 *
 * 之前用「`detectTapGestures` + `detectHorizontalDragGestures` 两个 pointerInput」，
 * 真机上表现为「只跳到起点和终点、中间不跟手」—— 两个手势检测器在同一个指针事件流上
 * 互相争抢，中间的 move 被吞掉了。这里用 `awaitEachGesture` 自己处理：
 * 按下即定位，之后每次 move 都更新，抬手才回调结束。
 */
@Composable
fun AfuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val style = LocalThemeStyle.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val thumbFill = MaterialTheme.colorScheme.surface
    val thumbRing = MaterialTheme.colorScheme.outline
    val outlineWidth = style.outlineWidth

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(44.dp)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
        val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

        fun updateFromX(x: Float) {
            val raw = (x / widthPx).coerceIn(0f, 1f)
            onValueChange(valueRange.start + raw * span)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(valueRange) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        updateFromX(down.position.x)
                        var lastX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.position.x != lastX) {
                                lastX = change.position.x
                                updateFromX(lastX)
                                change.consume()
                            }
                        }
                        onValueChangeFinished()
                    }
                },
        ) {
            val trackHeight = 12.dp.toPx()
            val thumbRadius = 12.dp.toPx()
            val cy = size.height / 2f
            val trackLeft = thumbRadius
            val trackWidth = (size.width - thumbRadius * 2).coerceAtLeast(1f)
            val activeWidth = trackWidth * fraction

            // 轨道
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackLeft, cy - trackHeight / 2f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
            )
            // 已选段
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(trackLeft, cy - trackHeight / 2f),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                )
            }
            // 拇指：白扣子 + 主色描边 + 中心点
            val thumbX = trackLeft + activeWidth
            drawCircle(color = thumbFill, radius = thumbRadius, center = Offset(thumbX, cy))
            drawCircle(
                color = if (outlineWidth > 0.dp) thumbRing.copy(alpha = 0.85f) else activeColor,
                radius = thumbRadius,
                center = Offset(thumbX, cy),
                style = Stroke(width = (if (outlineWidth > 0.dp) 2.dp else 2.5.dp).toPx()),
            )
            drawCircle(color = activeColor, radius = 4.dp.toPx(), center = Offset(thumbX, cy))
        }
    }
}
