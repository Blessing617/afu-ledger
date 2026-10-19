package com.afu.ledger.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.CardStyle
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.stickerEdge

/**
 * 应用统一卡片：圆角随主题风格，质感随风格变化。
 *
 * 不用阴影：卡片是半透明的（不透明度由用户调），阴影会沿着圆角显出一圈色边，
 * 在背景图上尤其明显。层级感交给底色与描边，不靠投影。
 *
 * 三种质感：
 * - [CardStyle.PLAIN] 纯色无边框（清爽）
 * - [CardStyle.OUTLINED] 深色描边（大肥鱼 / 樱花），手绘贴纸感
 * - [CardStyle.GLASS] 细描边 + 更透的表面（深夜）
 */
@Composable
fun AfuCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    shape: Shape? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalThemeStyle.current
    val cardShape = shape ?: remember(style.cardCorner) { RoundedCornerShape(style.cardCorner) }
    val outlineColor = MaterialTheme.colorScheme.outline
    // 描边风格（大肥鱼）的卡片同样跟随「内容卡片不透明度」设置，
    // 下限由 ThemeSettings.MIN_CARD_OPACITY 兜住可读性。
    //
    // 之前这里写死 1f，直接导致设置页的滑块在描边主题下完全没效果（用户反馈「盲调」）；
    // 性能问题改用「拖动时只更新内存 + 节流」解决，而不是牺牲这个可调项。
    val alpha = (effectiveSurfaceAlpha() * style.surfaceAlphaScale)
        .coerceIn(0.55f, 1f)

    val borderModifier = when (style.cardStyle) {
        CardStyle.OUTLINED -> Modifier.border(
            BorderStroke(style.outlineWidth, outlineColor.copy(alpha = 0.55f)),
            cardShape,
        )

        CardStyle.GLASS -> Modifier.border(
            BorderStroke(style.outlineWidth, outlineColor.copy(alpha = 0.7f)),
            cardShape,
        )

        CardStyle.PLAIN -> Modifier
    }

    Card(
        modifier = modifier
            .then(borderModifier)
            .stickerEdge(style, outlineColor),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            // 深色模式会自动提高不透明度：深色卡片压不住背景图的高光，
            // 浅色文字会与透出来的亮块糊在一起。详见 effectiveSurfaceAlpha。
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
