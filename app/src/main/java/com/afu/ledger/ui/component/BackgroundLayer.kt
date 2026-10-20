package com.afu.ledger.ui.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.afu.ledger.data.prefs.BackgroundThumbnail

/**
 * 自定义背景层：图片 + 可调遮罩，加载完成时淡入而不是硬切。
 *
 * 画质要求：**按原始分辨率解码**，不做降采样（用户明确要求）。
 * 性能靠两点回收：
 * 1. 淡入用 [Animatable] 并写入 `graphicsLayer` 的 alpha —— 动画结束后 alpha 恒为 1，
 *    这一层才能被 RenderNode 缓存、稳态下不重绘；
 * 2. 遮罩与图片放在同一层里绘制，减少一次全屏合成。
 */
@Composable
fun BackgroundLayer(
    uri: String,
    visibility: Float,
    blur: Boolean,
    scrim: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loaded by remember(uri) { mutableStateOf(false) }
    val fade = remember(uri) { Animatable(0f) }

    LaunchedEffect(uri, loaded) {
        // 缓存命中时这一帧已经是全亮的；未命中时才需要这段淡入。
        // 140ms 足够消除硬切感，又不至于让「图出来了」显得迟。
        if (loaded) fade.animateTo(1f, animationSpec = tween(durationMillis = 140))
    }

    // 按屏幕尺寸解码 + 固定内存缓存 key（理由见 BackgroundImageRequest）
    val request = remember(uri) { backgroundImageRequest(context, uri) }
    // 极小缩略图：解码只要几毫秒，先铺上，避免第一帧露出纯色底
    val thumbnail = remember(uri) { BackgroundThumbnail.load(context)?.asImageBitmap() }
    val blurModifier = if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(20.dp)
    } else {
        Modifier
    }
    val background = MaterialTheme.colorScheme.background
    val isDarkBackground = background.luminance() < 0.5f
    val clampedVisibility = visibility.coerceIn(0f, 1f)
    // 压暗量：
    //  - 浅色模式：完全按用户设定（可见度越高越不压）
    //  - 深色模式：在用户设定之上取一个最小下限。深色主题的语义就是「暗」，
    //    一张亮背景图（照片里常见整片白/高光）会同时毁掉文字对比度与整体氛围。
    //    用户把可见度拉满、遮罩开着时，浅色模式下 scrimAlpha 会是 0，
    //    深色模式下这里仍然保留 DARK_MIN_SCRIM 的压暗。
    val userScrim = if (scrim) (1f - clampedVisibility) * 0.92f else 0f
    val scrimAlpha = if (isDarkBackground) {
        maxOf(userScrim, DARK_MIN_SCRIM * (1f - clampedVisibility * 0.4f))
    } else {
        userScrim
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 图片就绪前铺主题底色，避免露出窗口黑底
            .background(background),
    ) {
        // 占位缩略图：不淡入，第一帧就全亮（它很小，看起来只是一层模糊的底色）
        thumbnail?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
                    .drawWithScrim(scrimAlpha, background),
            )
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier)
                .drawWithScrim(scrimAlpha, background)
                .graphicsLayer { alpha = fade.value },
        )
    }
}

/** 深色模式下背景图的最小压暗量。 */
private const val DARK_MIN_SCRIM = 0.55f
