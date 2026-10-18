package com.afu.ledger

import android.app.Application
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import android.content.pm.PackageManager
import com.afu.ledger.auto.keepalive.ShizukuGuard
import rikka.shizuku.Shizuku
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
import kotlinx.coroutines.withContext

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

        // 冷启动时主动核对一次并记录「曾经开过」。
        //
        // 不能只依赖 UI 侧的 LaunchedEffect：那个只在设置页组合时才跑，
        // 而用户完全可能开了服务后直接去用微信、再也不打开设置页。
        // 这里在进程启动时就把标记落盘，之后即便服务被撤销，
        // 也能正确区分「从没设置过」与「被撤销了」。
        applicationScope.launch {
            val enabled = withContext(Dispatchers.Default) {
                AccessibilityWatcher.isServiceEnabled(this@AfuApplication)
            }
            if (enabled) {
                container.keepAliveState.markEverEnabled()
            } else {
                // 进程起来了但服务已不在：这正是「被撤销」的典型时刻。
                // 主动刷新一次监听器状态，让通知能发出去。
                AccessibilityWatcher.refresh(this@AfuApplication)
            }
        }

        // 有 Shizuku 权限时，让巡检常驻：服务被划掉后会在一个周期内自动写回。
        // 没有权限就什么都不做（巡检里会自行判断），不影响其它功能。
        AccessibilityGuardWorker.schedule(this)

        // Shizuku 一旦连上（或刚授权），立刻自愈一次，不必等下一个巡检周期。
        // 与 Tally 的 MyApplication 做法一致：用 sticky 监听，已在线的也会被回调。
        runCatching {
            Shizuku.addBinderReceivedListenerSticky {
                applicationScope.launch { selfHealIfPossible() }
            }
            Shizuku.addRequestPermissionResultListener { _, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    applicationScope.launch { selfHealIfPossible() }
                }
            }
        }
    }

    /** 有 Shizuku 权限时，立刻把后台豁免与无障碍服务补上。 */
    private suspend fun selfHealIfPossible() {
        if (!ShizukuGuard.hasPermission()) return
        withContext(Dispatchers.IO) {
            ShizukuGuard.grantBackgroundKeepAlive(this@AfuApplication)
            if (!AccessibilityWatcher.isServiceEnabled(this@AfuApplication)) {
                ShizukuGuard.enableService(this@AfuApplication)
            }
        }
        AccessibilityWatcher.refresh(this@AfuApplication)
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
