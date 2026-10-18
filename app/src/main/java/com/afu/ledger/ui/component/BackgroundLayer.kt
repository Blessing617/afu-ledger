package com.afu.ledger.ui.component

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 自定义背景层：图片 + 可调遮罩，加载完成时淡入而不是硬切。
 *
 * 可读性托底交给上层卡片（卡片本身不透明），所以遮罩强度可以压得很低，
 * 让用户真正看见自己的图，而不是被一层灰盖住。
 */
@Composable
fun BackgroundLayer(
    uri: String,
    visibility: Float,
    blur: Boolean,
    scrim: Boolean,
    modifier: Modifier = Modifier,
) {
    var loaded by remember(uri) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundFade",
    )
    // 模糊 API 需要 Android 12+，低版本静默忽略（不降级成错误）
    val blurModifier = if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(24.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 图片就绪前先铺主题底色：否则会露出窗口黑底，观感像闪黑屏
            .background(MaterialTheme.colorScheme.background),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier)
                .graphicsLayer { this.alpha = alpha },
        )
        if (scrim) {
            val scrimAlpha = (1f - visibility.coerceIn(0f, 1f)) * 0.92f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha)),
            )
        }
    }
}
