package com.afu.ledger.ui.report

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.rememberMotionEnabled

/**
 * 报表用的三个图形，全部用 Canvas 手绘。
 *
 * 不引第三方图表库的原因：包体、离线约束，以及**动效可控** ——
 * 图表库往往自带无限循环的入场动画，滚动长列表时那种动画会持续重绘。
 */

/** 环形占比图。中心留给调用方的文字（用 Box 叠上去）。 */
@Composable
fun DonutChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 20.dp,
) {
    val motion = rememberMotionEnabled()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress = animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (motion) AfuMotion.settle() else snap(),
        label = "donut",
    )

    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 底环：让「一根色带」的空数据也有可读的轮廓
        drawCircle(
            color = Color(0x11000000),
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )

        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = slice.ratio * 360f * progress.value
            if (sweep <= 0f) return@forEach
            // 留一点缝隙，色块之间不糊成一片
            val gap = if (sweep > 4f) 2.5f else 0f
            drawArc(
                color = Color(slice.colorArgb),
                startAngle = startAngle + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            )
            startAngle += sweep
        }
    }
}

/** 每日支出折线：渐变填充 + 最高点标记。 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionEnabled()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress = animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (motion) AfuMotion.settle() else snap(),
        label = "trend",
    )

    if (points.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(120.dp))
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val max = points.maxOf { it.amountCents }.coerceAtLeast(1L).toFloat()
        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
        val baseline = size.height
        val topPadding = size.height * 0.12f

        fun x(index: Int) = stepX * index
        fun y(amount: Long) =
            baseline - (amount / max) * (size.height - topPadding)

        // 三条水平参考线
        for (i in 1..3) {
            val gy = baseline - (size.height - topPadding) * i / 3f
            drawLine(
                color = Color(0x14000000),
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 1f,
            )
        }

        val animatedPoints = points.mapIndexed { index, point ->
            Offset(x(index), y((point.amountCents * progress.value).toLong()))
        }

        if (animatedPoints.size >= 2) {
            val linePath = Path().apply {
                moveTo(animatedPoints.first().x, animatedPoints.first().y)
                animatedPoints.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(animatedPoints.last().x, baseline)
                lineTo(animatedPoints.first().x, baseline)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = baseline,
                ),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // 最高点：一个环 + 圆心，让人一眼看到「哪天最花钱」
        val peakIndex = points.indexOfFirst { it.amountCents == points.maxOf { p -> p.amountCents } }
        if (peakIndex >= 0 && progress.value > 0.6f) {
            val peak = animatedPoints[peakIndex]
            drawCircle(color = lineColor.copy(alpha = 0.18f), radius = 7.dp.toPx(), center = peak)
            drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = peak)
        }

        // 底部基线
        drawLine(
            color = Color(0x22000000),
            start = Offset(0f, baseline - 0.5f),
            end = Offset(size.width, baseline - 0.5f),
            strokeWidth = 1f,
        )
    }
}

/** 排行条：一条按占比填充的胶囊。 */
@Composable
fun RatioBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val motion = rememberMotionEnabled()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animated by animateFloatAsState(
        targetValue = if (appeared) ratio.coerceIn(0f, 1f) else 0f,
        animationSpec = if (motion) AfuMotion.settle() else snap(),
        label = "ratio",
    )

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = Color(0x11000000),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            size = size,
        )
        if (animated > 0f) {
            drawRoundRect(
                color = color,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                size = androidx.compose.ui.geometry.Size(size.width * animated, size.height),
            )
        }
    }
}

/** 只用来占位一个空矩形，避免空数据时布局跳动。 */
@Composable
fun ChartSpacer(modifier: Modifier = Modifier, height: Dp = 120.dp) {
    Box(modifier = modifier.fillMaxWidth().height(height))
}
