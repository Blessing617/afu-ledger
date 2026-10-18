package com.afu.ledger.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 内容卡片的底色不透明度。
 *
 * 由「背景可见度」派生：用户越想让图明显，卡片就越透，让图透出来；
 * 用户把背景调淡时，卡片接近不透明，保证可读。选择权交给用户。
 */
val LocalSurfaceAlpha = staticCompositionLocalOf { 0.95f }
