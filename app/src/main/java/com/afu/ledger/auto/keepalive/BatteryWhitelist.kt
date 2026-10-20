package com.afu.ledger.auto.keepalive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 电池优化豁免（Doze 白名单）的申请与检查。
 *
 * ## 为什么这一项比「反复引导重新授权」有用得多
 *
 * 无障碍服务被踢出启用列表的**唯一原因**是应用被 force-stop
 * （AOSP `AccessibilityManagerService.onPackagesForceStoppedLocked`：
 * force-stop 一个无障碍应用会连带把它的无障碍权限从安全设置里移除）。
 *
 * 而在定制 ROM 上，触发 force-stop 的主要来源就是**省电策略**：
 * 被省电策略「限制」的应用在系统眼里等同于被 force-stop，
 * 服务因此被移出列表。所以真正该做的是**别让自己进入受限状态**，
 * 而不是等权限没了再去求用户重新点一遍。
 *
 * 标准 Android 提供 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：
 * 申请后应用进入 Doze 白名单，系统不再对它施加常规的后台限制。
 * 这是纯应用层能拿到的最强豁免，且**申请一次即可**，不会反复打扰用户。
 *
 * 不申请 `INTERNET`、不引入任何网络能力，只是免除省电限制。
 */
object BatteryWhitelist {

    /** 是否已在白名单里。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 弹出系统的「忽略电池优化」确认框。
     *
     * 用 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 直接针对本应用申请，
     * 用户点一次「允许」即可；比让用户自己去设置里找要短得多。
     *
     * 部分 ROM 屏蔽了这个 Intent，失败时返回 false，调用方应退回到
     * [batteryOptimizationSettingsIntent] 让用户手动打开。
     */
    fun requestIgnore(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (isIgnoringBatteryOptimizations(context)) return true
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 电池优化列表页（申请入口不可用时的退路）。 */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 小米系「自启动」设置页。
     *
     * HyperOS / MIUI 的自启开关不在标准 Settings 里，各家 Activity 名也不同，
     * 这里按已知的几个候选依次尝试；全部失败就由调用方退回到「应用信息」页。
     */
    fun miuiAutoStartIntents(): List<Intent> = listOf(
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        Intent().setClassName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
        ),
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
