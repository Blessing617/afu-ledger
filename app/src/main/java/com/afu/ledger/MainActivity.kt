package com.afu.ledger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afu.ledger.perf.FrameStats
import com.afu.ledger.ui.AfuApp
import com.afu.ledger.ui.component.LocalSurfaceAlpha
import com.afu.ledger.ui.theme.AfuTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    /** 通知权限只影响「常驻通知是否可见」，拒绝后自动记账仍然可用。 */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果由设置页读取系统状态 */ }

    override fun onResume() {
        super.onResume()
        // 帧指标只能由应用自己量：HyperOS 上 dumpsys gfxinfo / SurfaceFlinger --latency 都取不到数据
        if (BuildConfig.DEBUG) FrameStats.start()
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.DEBUG) FrameStats.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AfuApplication).container

        // 首帧主题正确：DataStore 是本地文件，同步读一次首值的代价是个位数毫秒，
        // 换来的是「不先渲染默认主题再切走」。否则整棵树要重组两次，还会闪一下。
        val initialTheme = runBlocking { container.themePreferences.settings.first() }

        maybeRequestNotificationPermission()

        setContent {
            val themeSettings by container.themePreferences.settings
                .collectAsStateWithLifecycle(initialValue = initialTheme)

            AfuTheme(settings = themeSettings) {
                // 卡片底色随背景可见度联动：图越明显卡片越透，但留 0.72 的下限保证可读
                val surfaceAlpha = (0.96f - themeSettings.backgroundVisibility * 0.24f)
                    .coerceIn(0.72f, 0.96f)
                CompositionLocalProvider(LocalSurfaceAlpha provides surfaceAlpha) {
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
