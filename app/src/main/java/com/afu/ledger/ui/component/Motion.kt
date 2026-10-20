package com.afu.ledger.ui.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 系统动画开关是否可用。
 *
 * 开发者在「开发者选项 → 动画缩放」设为 0，或系统启用减弱动效时，
 * 动效应当退化为瞬时切换，而不是拖慢操作。
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}
