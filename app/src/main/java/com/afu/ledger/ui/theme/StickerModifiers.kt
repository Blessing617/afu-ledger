package com.afu.ledger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 大肥鱼风格的「贴纸边」：外描边之内再画一圈虚线。
 *
 * 虚线是手账贴纸的常见语言 —— 实线给出轮廓，虚线给出「贴上去的」那一层。
 * 只在描边风格下生效，默认主题直接返回原 Modifier，不增加任何绘制。
 */
fun Modifier.stickerEdge(
    style: ThemeStyle,
    outline: Color,
    corner: Dp = style.cardCorner,
): Modifier {
    if (style.cardStyle != CardStyle.OUTLINED) return this
    return drawWithCache {
        val inset = 4.dp.toPx()
        if (size.width <= inset * 2 + 8f || size.height <= inset * 2 + 8f) {
            return@drawWithCache onDrawWithContent { drawContent() }
        }
        val radius = (corner.toPx() - inset).coerceAtLeast(2f)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = inset,
                    top = inset,
                    right = size.width - inset,
                    bottom = size.height - inset,
                    cornerRadius = CornerRadius(radius, radius),
                ),
            )
        }
        val dash = PathEffect.dashPathEffect(
            floatArrayOf(7.dp.toPx(), 5.dp.toPx()),
            0f,
        )
        onDrawWithContent {
            drawContent()
            // 用**实线**而不是虚线：虚线描边在每一帧都要重新切段，
            // 滚动时几十张卡片叠在一起会明显拖慢绘制。实线的「贴纸压边」感一样在。
            drawPath(
                path = path,
                color = outline.copy(alpha = 0.20f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** 虚线分隔线；默认主题下返回 null，由调用方回退到实线。 */
fun dashedLineEffect(style: ThemeStyle): PathEffect? = null

/** 卡片形状助手：所有容器都用同一份圆角定义。 */
fun ThemeStyle.cardShape() = RoundedCornerShape(cardCorner)

/** 大圆角容器（面板、对话框）。 */
fun ThemeStyle.containerShape() = RoundedCornerShape(containerCorner)

/** 画一条水平虚线（供 Canvas 使用）。 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.dashedHLine(
    color: Color,
    effect: PathEffect,
    strokeWidth: Float,
) {
    drawLine(
        color = color,
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = strokeWidth,
        pathEffect = effect,
    )
}
