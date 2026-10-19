package com.afu.ledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.LocalThemeStyle

/**
 * 应用统一的滑块：**保留 Material 的手势实现，只换外观**。
 *
 * ## 为什么不再自绘手势
 *
 * 之前用 Canvas + pointerInput 自绘，外观是好了，但真机上手感反而变差
 * （拖动掉帧、偶尔只跳到起点终点）。Material3 的 `Slider` 内部是成熟的
 * 拖动状态机（touch slop、多点、与父滚动的手势竞争都处理过），
 * 流畅度本来就可靠 —— 不好看的只是它的轨道和拇指。
 *
 * 所以这里用 `Slider` + 自定义 `thumb` / `track`：
 * - 手势、位置、无障碍全部交给官方实现
 * - 视觉换成粗圆角轨道 + 白色圆扣（描边主题下加深色描边）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val style = LocalThemeStyle.current
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val thumbFill = MaterialTheme.colorScheme.surface
    val thumbRing = MaterialTheme.colorScheme.outline
    val sticker = style.outlineWidth > 0.dp

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = thumbFill,
            activeTrackColor = primary,
            inactiveTrackColor = track,
            activeTickColor = primary,
            inactiveTickColor = track,
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(thumbFill)
                    .border(
                        width = 2.dp,
                        color = if (sticker) thumbRing.copy(alpha = 0.85f) else primary,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primary),
                )
            }
        },
        track = { sliderState ->
            val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
            val fraction = ((sliderState.value - valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(primary),
                )
            }
        },
    )
}
