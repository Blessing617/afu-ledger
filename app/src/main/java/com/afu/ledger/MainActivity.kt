package com.afu.ledger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.afu.ledger.auto.keepalive.AccessibilitySelfHealer
import com.afu.ledger.auto.keepalive.AutoLedgerKeepAlive
import com.afu.ledger.perf.FrameStats
import com.afu.ledger.ui.AfuApp
import com.afu.ledger.ui.component.LocalCardOpacityPreview
import com.afu.ledger.ui.component.LocalSurfaceAlpha
import com.afu.ledger.ui.theme.AfuTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    /** 通知权限只影响「常驻通知是否可见」，拒绝后自动记账仍然可用。 */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果由设置页读取系统状态 */ }

    override fun onResume() {
        super.onResume()
        // 帧指标只能由应用自己量：HyperOS 上 dumpsys gfxinfo / SurfaceFlinger --latency 都取不到数据
        if (BuildConfig.DEBUG) FrameStats.start()
        // 回到应用时尝试把被系统关掉的无障碍服务写回启用列表。
        // 只有用户自己开着总开关时才自愈 —— 他主动关掉的功能不该被我们偷偷打开。
        maybeSelfHealAccessibility()
    }

    /**
     * 授权自愈：系统把无障碍关掉后，用户回到应用这一刻是最佳的修复时机。
     *
     * 没有 `WRITE_SECURE_SETTINGS` 时这里什么也不做（不做假装修复），
     * 由设置页提示用户去系统设置手动开。
     */
    private fun maybeSelfHealAccessibility() {
        val container = (application as AfuApplication).container
        lifecycleScope.launch {
            val settings = runCatching { container.autoLedgerSettings.settings.first() }.getOrNull()
                ?: return@launch
            if (!settings.enabled) return@launch
            if (AccessibilitySelfHealer.isEnabled(this@MainActivity)) return@launch
            val ok = AccessibilitySelfHealer.tryReenable(this@MainActivity)
            if (ok) {
                // 修好了就把「已失效」告警撤掉，别让用户以为还是坏的
                AutoLedgerKeepAlive.clearRevokedNotification(this@MainActivity)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.DEBUG) FrameStats.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge：内容画到系统栏之后，安全区由 Scaffold 的 WindowInsets 处理。
        // 状态栏图标的明暗由 AfuTheme 的 SystemBarsEffect 按主题切换。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as AfuApplication).container

        // 首帧主题正确：DataStore 是本地文件，同步读一次首值的代价是个位数毫秒，
        // 换来的是「不先渲染默认主题再切走」。否则整棵树要重组两次，还会闪一下。
        val initialTheme = runBlocking { container.themePreferences.settings.first() }

        maybeRequestNotificationPermission()

        setContent {
            val themeSettings by container.themePreferences.settings
                .collectAsStateWithLifecycle(initialValue = initialTheme)

            // 拖动「卡片不透明度」时的临时值：让滑块拖动过程即时可见，
            // 又不至于每帧写 DataStore。
            val cardOpacityPreview = remember { mutableStateOf<Float?>(null) }

            AfuTheme(settings = themeSettings) {
                // 卡片不透明度完全由用户在设置里调：想要图就调低，想要清晰就调高
                CompositionLocalProvider(
                    LocalSurfaceAlpha provides themeSettings.cardOpacity,
                    LocalCardOpacityPreview provides cardOpacityPreview,
                ) {
                    AfuApp(container = container)
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}
