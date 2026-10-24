package com.afu.ledger.ui.component

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 用 `Dialog` 承载的底部面板，必须调用它。
 *
 * ## 为什么必须关掉系统 dim
 *
 * 面板自己画了一层 `ModalScrim`（黑色 32%）。但 `Dialog` 的窗口默认还带一份系统 dim，
 * 两层的效果是「32% 之上再压一层」，同一个面板在不同页面上深浅就不一样了 ——
 * 预算编辑器用了 Dialog 却没关，看起来就比定时记账编辑器明显更黑。
 *
 * 遮罩只允许一层：要么系统那层，要么自己画的那层。这里统一留自己画的，因为
 * 自己画的那层能跟着面板的进场动画一起淡入。
 *
 * @param hideKeyboardOnOpen 打开面板时是否阻止系统键盘自动弹出。金额用自定义键盘的
 *   面板应该传 true：系统键盘一上来就挡掉半屏，把「浏览分区」变成了「先关键盘」。
 */
@Composable
fun DialogScrimPolicy(hideKeyboardOnOpen: Boolean = false) {
    val view = LocalView.current
    SideEffect {
        runCatching {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@runCatching
            window.setDimAmount(0f)
            if (hideKeyboardOnOpen) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            }
        }
    }
}
