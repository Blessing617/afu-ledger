package com.afu.ledger.ui.component

import android.content.Context
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * 背景图的统一请求构建。
 *
 * 为什么不再按原始分辨率解码：
 * 1. **打开时的延迟**：一张 4000×3000 的相册图，原始分辨率解码要几十到上百毫秒，
 *    这段时间用户看到的是纯色底 —— 就是「打开/切回来先白一下」的来源。
 * 2. **滚动时的带宽**：整张大图常驻 GPU 纹理，页面内容每帧变化时都要和它重新合成，
 *    半透明卡片越多，混合开销越大，快速滑动就更容易掉帧。
 * 3. **内存缓存命中率**：1440×3200 的 ARGB 位图约 18 MB，Coil 默认内存缓存
 *    只有可用内存的 15% 左右，两张大图就能把它挤爆，于是「关闭再打开」又要重新解码。
 *
 * 而显示区域只有屏幕这么大、并且用 `ContentScale.Crop` 铺满 —— 按屏幕像素解码就是
 * 1:1，多余像素本来也看不见。所以这一步没有任何画质损失。
 *
 * 固定 `memoryCacheKey` 让「启动预加载」与「界面显示」共用同一份内存缓存，
 * 冷启动预热过之后，界面直接命中，不再有可感知的加载空窗。
 */
fun backgroundImageRequest(context: Context, uri: String): ImageRequest {
    val metrics = context.resources.displayMetrics
    return ImageRequest.Builder(context)
        .data(uri)
        .size(Size(metrics.widthPixels, metrics.heightPixels))
        .memoryCacheKey("afu-background:$uri")
        .build()
}
