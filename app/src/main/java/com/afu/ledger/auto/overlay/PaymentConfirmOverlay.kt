package com.afu.ledger.auto.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 无障碍悬浮窗容器。
 *
 * 用 `TYPE_ACCESSIBILITY_OVERLAY`：随无障碍服务授权生效，不需要
 * `SYSTEM_ALERT_WINDOW`。窗口不可获焦、不阻挡下层其余区域，
 * 用户仍能操作微信/支付宝本身。
 */
class PaymentConfirmOverlay(
    private val context: Context,
) {

    private val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)

    private var composeView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    val isShowing: Boolean get() = composeView != null

    fun show(content: @Composable () -> Unit) {
        hide()

        val owner = ServiceLifecycleOwner().apply { attach() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = BOTTOM_MARGIN_PX
        }

        val added = runCatching {
            windowManager.addView(view, params)
        }.onFailure { error ->
            Log.e(TAG, "addView 失败: ${error.message}", error)
        }.isSuccess

        view.post {
            Log.d(TAG, "post attach=${view.isAttachedToWindow} size=${view.width}x${view.height}")
        }
        Log.d(TAG, "addView added=$added")

        if (added) {
            composeView = view
            lifecycleOwner = owner
        } else {
            owner.detach()
        }
    }

    fun hide() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        lifecycleOwner?.detach()
        composeView = null
        lifecycleOwner = null
    }

    private companion object {
        const val TAG = "AutoLedgerOverlay"
        const val BOTTOM_MARGIN_PX = 160
    }
}
