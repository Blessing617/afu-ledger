package com.afu.ledger.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afu.ledger.R

/**
 * 大肥鱼吉祥物。
 *
 * ## 形象来源
 *
 * 用户本机皮肤 `maid-atelier` 的 Q 版立绘（蓝发女仆鲸鱼娘）。
 * 头像版是从立绘裁出的头部 + 深海蓝圆底，与应用图标同源，保证「点开应用看到的
 * 和图标是同一个角色」。
 *
 * 两张图都放在 `res/drawable-nodpi`：立绘本身是位图，不需要按 DPI 出多套，
 * 放 nodpi 可以避免系统再放大一份。头像 302KB、立绘 91KB，对 APK 体积影响可忽略。
 */
@Composable
fun MascotAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
) {
    Image(
        painter = painterResource(R.drawable.mascot_avatar),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}

/** 全身立绘：用于空状态插画，比头像更有「在场感」。 */
@Composable
fun MascotChibi(
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    Image(
        painter = painterResource(R.drawable.maid_chibi),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(height = height, width = height * 1.15f),
    )
}

/** 带淡蓝泡泡底的头像卡，用于首页今日卡与主题预览。 */
@Composable
fun MascotBadge(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFFDCEEFF),
                        androidx.compose.ui.graphics.Color(0xFFA9C6EA),
                    ),
                ),
            ),
    ) {
        MascotAvatar(modifier = Modifier.size(size), size = size)
    }
}
