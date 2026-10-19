package com.afu.ledger.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.abs
import kotlin.math.sin

/**
 * 主题背景装饰。
 *
 * ## 性能约束（这是它长得「简单」的原因）
 *
 * 1. **静态**：位置全部由固定函数算出来，没有随机数、没有无限动画。
 *    规格明确禁止装饰持续动画 —— 那是耗电与掉帧的常见来源。
 * 2. **一次绘制**：用 [drawWithCache] 按尺寸缓存，滚动时不再重建路径。
 * 3. **不进列表**：装饰画在页面容器上，`LazyColumn` 的 item 里没有它，
 *    列表滚动不会触发它重组。
 *
 * 装饰只作为「氛围」存在：对比度很低（alpha 0.05–0.12），不参与信息表达。
 */
@Composable
fun ThemeBackdrop(
    modifier: Modifier = Modifier,
    style: ThemeStyle = LocalThemeStyle.current,
) {
    if (style.decoration == Decoration.NONE) return
    val tint = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val isDark = MaterialTheme.colorScheme.background.isDarkColor()

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val deco = style.decoration
                val alphaScale = if (isDark) 0.62f else 1f
                onDrawBehind {
                    when (deco) {
                        Decoration.BUBBLE -> drawBubbles(tint, secondary, alphaScale)
                        Decoration.PETAL -> drawPetals(tint, secondary, alphaScale)
                        Decoration.STAR -> drawStars(tint, alphaScale)
                        Decoration.NONE -> Unit
                    }
                }
            },
    )
}

/** 背景是否为深色（用相对亮度判断，不依赖外部传参）。 */
private fun Color.isDarkColor(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/**
 * 气泡与小鱼：确定性位置。
 *
 * 颜色固定为「白气泡 + 淡蓝描边」，不跟着主题色走：气泡是海水的意象，
 * 染成粉色/绿色会显得脏；固定下来后无论换什么主题色都还是水里的泡泡。
 */
private fun DrawScope.drawBubbles(tint: Color, secondary: Color, alphaScale: Float) {
    val bubbleFill = Color.White.copy(alpha = 0.55f * alphaScale)
    val bubbleStroke = BUBBLE_BLUE.copy(alpha = 0.45f * alphaScale)
    val fishColor = BUBBLE_BLUE.copy(alpha = 0.16f * alphaScale)
    val w = size.width
    val h = size.height
    repeat(9) { index ->
        val fx = 0.08f + 0.11f * index + 0.05f * sin(index * 1.7f)
        val fy = 0.10f + 0.095f * ((index * 5) % 9) + 0.03f * sin(index * 2.3f)
        val r = w * (0.022f + 0.012f * ((index % 3) + 1))
        val center = Offset(w * fx.coerceIn(0.04f, 0.96f), h * fy.coerceIn(0.04f, 0.96f))
        drawCircle(color = bubbleFill, radius = r, center = center)
        drawCircle(
            color = bubbleStroke,
            radius = r,
            center = center,
            style = Stroke(width = r * 0.14f),
        )
        // 气泡高光：一小段弧，让圆看起来是「泡」而不是「点」
        drawArc(
            color = Color.White.copy(alpha = 0.85f * alphaScale),
            startAngle = 200f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(center.x - r * 0.62f, center.y - r * 0.62f),
            size = Size(r * 1.24f, r * 1.24f),
            style = Stroke(width = r * 0.18f),
        )
    }
    // 两条小鱼剪影
    repeat(2) { index ->
        val cx = w * if (index == 0) 0.82f else 0.14f
        val cy = h * if (index == 0) 0.30f else 0.72f
        val s = w * 0.05f
        val fish = Path().apply {
            moveTo(cx - s, cy)
            quadraticTo(cx - s * 0.2f, cy - s * 0.75f, cx + s * 0.55f, cy)
            quadraticTo(cx - s * 0.2f, cy + s * 0.75f, cx - s, cy)
            close()
            moveTo(cx - s * 0.92f, cy)
            lineTo(cx - s * 1.45f, cy - s * 0.5f)
            lineTo(cx - s * 1.45f, cy + s * 0.5f)
            close()
        }
        drawPath(fish, fishColor)
    }
}

/** 气泡与小鱼的颜色：海水蓝，不随主题色。 */
private val BUBBLE_BLUE = Color(0xFF7FAFD8)

private fun DrawScope.drawPetals(tint: Color, secondary: Color, alphaScale: Float) {
    val w = size.width
    val h = size.height
    repeat(14) { index ->
        val fx = 0.06f + 0.068f * index
        val fy = 0.06f + 0.13f * ((index * 7) % 7)
        val cx = w * fx.coerceIn(0.04f, 0.96f)
        val cy = h * fy.coerceIn(0.04f, 0.96f)
        val r = w * (0.016f + 0.006f * (index % 3))
        val angle = (index * 37f) % 360f
        val color = if (index % 2 == 0) tint else secondary
        rotate(degrees = angle, pivot = Offset(cx, cy)) {
            drawOval(
                color = color.copy(alpha = 0.11f * alphaScale),
                topLeft = Offset(cx - r, cy - r * 0.62f),
                size = Size(r * 2f, r * 1.24f),
            )
        }
    }
}

private fun DrawScope.drawStars(tint: Color, alphaScale: Float) {
    val w = size.width
    val h = size.height
    repeat(18) { index ->
        val fx = 0.04f + 0.052f * index + 0.02f * sin(index * 1.9f)
        val fy = 0.05f + 0.11f * ((index * 5) % 9)
        val cx = w * fx.coerceIn(0.03f, 0.97f)
        val cy = h * fy.coerceIn(0.03f, 0.97f)
        val r = w * (0.004f + 0.003f * abs((index % 3) - 1))
        drawCircle(
            color = tint.copy(alpha = 0.16f * alphaScale),
            radius = r,
            center = Offset(cx, cy),
        )
    }
}
