package com.afu.ledger.auto.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
 *
 * 性能：服务连接后先 [warmUp] 建好 ComposeView 并挂一个 1×1 的空窗口，
 * 命中时只替换内容与窗口尺寸，避免每次弹出都重新初始化组合环境。
 */
class PaymentConfirmOverlay(
    private val context: Context,
) {

    private val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)

    /** 当前要显示的内容；为空表示「预热中、不展示任何东西」。 */
    private val contentState = mutableStateOf<(@Composable () -> Unit)?>(null)

    private var composeView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var showing = false

    val isShowing: Boolean get() = showing

    /**
     * 预建组合环境。多次调用安全；失败不抛出，交由 [show] 走降级路径。
     */
    fun warmUp() {
        if (composeView != null) return

        val owner = ServiceLifecycleOwner().apply { attach() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { contentState.value?.invoke() }
        }
        val params = buildParams(visible = false)

        val added = runCatching { windowManager.addView(view, params) }
            .onFailure { Log.e(TAG, "warmUp addView 失败: ${it.message}", it) }
            .isSuccess

        if (added) {
            composeView = view
            lifecycleOwner = owner
            layoutParams = params
            Log.d(TAG, "warmUp ok")
        } else {
            owner.detach()
        }
    }

    fun show(content: @Composable () -> Unit, onFirstFrame: (Long) -> Unit = {}) {
        val startedAt = SystemClock.uptimeMillis()

        if (composeView == null) {
            // 预热没成功（或已被系统移除）：降级为即时创建，功能优先
            warmUp()
            if (composeView == null) {
                Log.e(TAG, "show 失败：无法创建窗口")
                return
            }
        }

        val view = composeView ?: return
        val params = layoutParams ?: return

        contentState.value = content
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = BOTTOM_MARGIN_PX
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.e(TAG, "updateViewLayout 失败: ${it.message}", it) }

        showing = true
        view.post {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            Log.d(TAG, "可见 attach=${view.isAttachedToWindow} size=${view.width}x${view.height} 首帧=${elapsed}ms")
            onFirstFrame(elapsed)
        }
    }

    /** 隐藏内容但保留预热窗口，下次弹出仍然快。 */
    fun hide() {
        showing = false
        contentState.value = null

        val view = composeView ?: return
        val params = layoutParams ?: return
        params.width = 1
        params.height = 1
        params.y = 0
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /** 真正移除窗口。服务销毁时必须调用，否则窗口泄漏。 */
    fun release() {
        showing = false
        contentState.value = null
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
            .onFailure { Log.e(TAG, "removeView 失败: ${it.message}", it) }
        lifecycleOwner?.detach()
        composeView = null
        lifecycleOwner = null
        layoutParams = null
    }

    private fun buildParams(visible: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            if (visible) WindowManager.LayoutParams.MATCH_PARENT else 1,
            if (visible) WindowManager.LayoutParams.WRAP_CONTENT else 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS or
                if (visible) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (visible) {
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            } else {
                Gravity.TOP or Gravity.START
            }
            y = if (visible) BOTTOM_MARGIN_PX else 0
        }

    private companion object {
        const val TAG = "AutoLedgerOverlay"
        const val BOTTOM_MARGIN_PX = 160

        const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
}
