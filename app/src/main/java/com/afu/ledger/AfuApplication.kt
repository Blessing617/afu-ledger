package com.afu.ledger

import android.app.Application
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.afu.ledger.auto.keepalive.AccessibilityGuardWorker
import com.afu.ledger.auto.keepalive.AccessibilityWatcher
import com.afu.ledger.auto.keepalive.AutoLedgerHealth
import com.afu.ledger.auto.keepalive.AutoLedgerKeepAlive
import com.afu.ledger.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AfuApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 首次启动写入预置分类；已有数据时内部会跳过
        applicationScope.launch {
            container.categoryRepository.ensurePresetCategories()
        }

        // 预加载背景图：让首页首次组合直接命中图片缓存，
        // 避免「先空一段时间、再整张图突然出现」
        applicationScope.launch {
            val backgroundUri = container.themePreferences.settings.first().backgroundUri ?: return@launch
            runCatching {
                val request = ImageRequest.Builder(this@AfuApplication)
                    .data(backgroundUri)
                    .build()
                SingletonImageLoader.get(this@AfuApplication).enqueue(request)
            }
        }

        startAccessibilityWatch()
    }

    /**
     * 实时盯着无障碍授权。
     *
     * 这件事**必须在 Application 里做**，不能只在设置页：
     * 用户准备付款时人在微信里，记账 App 根本没有界面在运行。
     * 若只在设置页检测，等用户想起来切回来时权限早就没了，
     * 中间漏掉的每一笔都不会有人知道。
     */
    private fun startAccessibilityWatch() {
        AccessibilityWatcher.start(this)
        AccessibilityWatcher.addListener(::onHealthChanged)

        // 有 Shizuku 权限时，让巡检常驻：服务被划掉后会在一个周期内自动写回。
        // 没有权限就什么都不做（巡检里会自行判断），不影响其它功能。
        AccessibilityGuardWorker.schedule(this)
    }

    private fun onHealthChanged(health: AutoLedgerHealth) {
        when (health) {
            AutoLedgerHealth.HEALTHY ->
                AutoLedgerKeepAlive.clearRevokedNotification(this)

            // 两种失效的恢复路径不同，文案分开
            AutoLedgerHealth.PERMISSION_REVOKED ->
                AutoLedgerKeepAlive.showRevokedNotification(this, frozenInsteadOfRevoked = false)

            AutoLedgerHealth.SUSPECT_FROZEN ->
                AutoLedgerKeepAlive.showRevokedNotification(this, frozenInsteadOfRevoked = true)
        }
    }
}
