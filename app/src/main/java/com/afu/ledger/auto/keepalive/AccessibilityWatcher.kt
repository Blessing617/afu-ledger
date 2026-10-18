package com.afu.ledger.auto.keepalive

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.afu.ledger.auto.AutoLedgerAccessibilityService
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 自动记账服务的健康状态。
 *
 * 三种状态必须分开，因为**恢复方式不同**：
 */
enum class AutoLedgerHealth {
    /** 服务已启用且正常收事件。 */
    HEALTHY,

    /**
     * 系统里还启着，但长时间收不到任何事件。
     *
     * 典型是 HyperOS 冻结了进程：绑定记录还在（`dumpsys` 能看到），
     * 但应用侧代码不执行，事件派发不进来。
     * 恢复方式：重新开关一次无障碍服务（会触发系统重新 bind 并解冻）。
     */
    SUSPECT_FROZEN,

    /**
     * 系统里**已经不在启用列表**了。
     *
     * 这是最严重的一种，也是 HyperOS 最常见的：系统对应用执行了 **force-stop**，
     * 而 Android 在 force-stop 一个无障碍应用时会把它的无障碍权限一并剥离
     * （`AccessibilityManagerService.onPackagesForceStoppedLocked`）。
     * HyperOS 在「进入超级省电」「锁屏清理」等场景会主动这么做。
     *
     * 此时前台服务救不了 —— 权限都没了，就没有服务可保活。
     * 唯一出路是让用户重新授权，所以必须**主动、显式地告诉用户**。
     */
    PERMISSION_REVOKED,
}

/**
 * 无障碍授权的实时监听。
 *
 * 为什么需要它：设置页原本只在 `ON_RESUME` 时查一次授权状态，
 * 而真实场景是「用户在微信里准备付款 → 系统此时把权限剥了 → 用户以为自动记账在工作」。
 * 等到用户切回记账 App 才发现，那笔账早就漏了。
 *
 * 这里用 `AccessibilityManager` 的两个信号尽早发现：
 *
 * 1. `addAccessibilityStateChangeListener`：**全局**无障碍开关变化（用户关掉所有服务）
 * 2. `addAccessibilityServicesStateChangeListener`：**具体服务**启停（API 33+，能精确到我们这
 *    一个服务被移除）
 *
 * 两者都在系统侧回调，不需要轮询；低于 API 33 时退化为只有第 1 个信号 + 定时自查。
 */
object AccessibilityWatcher {

    private val listeners = CopyOnWriteArraySet<(AutoLedgerHealth) -> Unit>()

    @Volatile
    private var lastKnown: AutoLedgerHealth? = null

    private var manager: AccessibilityManager? = null

    /** 应用级 context，供系统回调里重算状态用（系统回调不带 context）。 */
    @Volatile
    private var appContext: Context? = null

    private val stateListener = AccessibilityManager.AccessibilityStateChangeListener { notifyIfChanged() }

    private val servicesListener: Any? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // API 33+：服务级变化，能精确到「我们这一个服务被移除」。
            // 这个 SAM 是单参数的 (manager) -> Unit。
            AccessibilityManager.AccessibilityServicesStateChangeListener { notifyIfChanged() }
        } else {
            null
        }

    private fun addServicesListener(m: AccessibilityManager) {
        val listener = servicesListener ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                m.addAccessibilityServicesStateChangeListener(
                    listener as AccessibilityManager.AccessibilityServicesStateChangeListener,
                )
            }
        }
    }

    private fun removeServicesListener(m: AccessibilityManager) {
        val listener = servicesListener ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                m.removeAccessibilityServicesStateChangeListener(
                    listener as AccessibilityManager.AccessibilityServicesStateChangeListener,
                )
            }
        }
    }

    /** 幂等；重复调用安全。 */
    @Synchronized
    fun start(context: Context) {
        if (manager != null) return
        val ctx = context.applicationContext
        val m = ctx.getSystemService(AccessibilityManager::class.java) ?: return
        manager = m
        appContext = ctx
        runCatching { m.addAccessibilityStateChangeListener(stateListener) }
        addServicesListener(m)
        lastKnown = current(ctx)
        val known = lastKnown
        if (known != null) listeners.forEach { listener -> runCatching { listener(known) } }
    }

    @Synchronized
    fun stop() {
        val m = manager ?: return
        runCatching { m.removeAccessibilityStateChangeListener(stateListener) }
        removeServicesListener(m)
        manager = null
        appContext = null
        lastKnown = null
    }

    fun addListener(listener: (AutoLedgerHealth) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (AutoLedgerHealth) -> Unit) {
        listeners.remove(listener)
    }

    /** 主动查一次当前健康状态（不依赖监听器）。 */
    fun current(context: Context): AutoLedgerHealth {
        if (!isServiceEnabled(context)) return AutoLedgerHealth.PERMISSION_REVOKED
        return if (AutoLedgerKeepAlive.isSuspectFrozen()) {
            AutoLedgerHealth.SUSPECT_FROZEN
        } else {
            AutoLedgerHealth.HEALTHY
        }
    }

    /** 由外部（例如收到事件、或设置页定时刷新）触发的重算。 */
    fun refresh(context: Context) {
        notifyIfChanged(force = true)
    }

    private fun notifyIfChanged(force: Boolean = false) {
        val ctx = appContext ?: return
        val now = runCatching { current(ctx) }.getOrNull() ?: return
        if (!force && now == lastKnown) return
        lastKnown = now
        listeners.forEach { listener -> runCatching { listener(now) } }
    }

    /** 服务是否在系统的启用列表里。公开给巡检 Worker 用。 */
    fun isServiceEnabled(context: Context): Boolean {
        val m = context.getSystemService(AccessibilityManager::class.java) ?: return false
        if (!m.isEnabled) return false
        val expected = ComponentName(context, AutoLedgerAccessibilityService::class.java)
        return m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
            }
    }
}
