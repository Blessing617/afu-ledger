package com.afu.ledger.auto.keepalive

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.afu.ledger.auto.AutoLedgerAccessibilityService

/**
 * 无障碍授权被系统关掉后的**自愈**。
 *
 * ## 为什么需要它
 *
 * HyperOS 在超级省电、清理后台、系统更新等场景会把本服务从无障碍启用列表里移除。
 * 用户看到的就是「昨天还好好的，今天不记账了」——而他自己什么都没动。
 *
 * 调研里四个实现（钱迹、iCost、李跳跳、AutoAccounting）**没有一家用 JobScheduler 周期巡检**；
 * 钱迹的做法最直接：拿到 `WRITE_SECURE_SETTINGS` 之后，
 * 在「打开应用 / 回到前台」这些时机把服务写回启用列表。
 * 因为真正被杀的是**无障碍开关**，不是我们的进程，在用户回到应用那一刻修才有效。
 *
 * ## 权限从哪来
 *
 * `WRITE_SECURE_SETTINGS` 是受保护权限，只能由用户用一条 adb 命令授予：
 *
 * ```
 * adb shell pm grant com.afu.ledger android.permission.WRITE_SECURE_SETTINGS
 * ```
 *
 * 没有这个权限时**不做任何假装**：只提示用户去系统设置手动开，并把命令抄在设置页里。
 */
object AccessibilitySelfHealer {

    private const val TAG = "AutoLedger"

    /** 本服务的完整组件名，写进 `enabled_accessibility_services` 用的形式。 */
    fun componentName(context: Context): String =
        ComponentName(context.packageName, AutoLedgerAccessibilityService::class.java.name)
            .flattenToString()

    /** 是否已被授予写安全设置的权限。 */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** 本服务当前是否在系统的启用列表里。 */
    fun isEnabled(context: Context): Boolean {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()
        if (raw.isEmpty()) return false
        val mine = componentName(context)
        return raw.split(':').any { it.equals(mine, ignoreCase = true) }
    }

    /**
     * 尝试把本服务写回启用列表。
     *
     * @return true 表示现在确实已启用（或本来就启用）；false 表示没权限或写失败。
     *   **调用方必须按返回值如实展示状态**，不能假设成功。
     */
    fun tryReenable(context: Context): Boolean {
        if (isEnabled(context)) return true
        if (!canWriteSecureSettings(context)) return false

        val mine = componentName(context)
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()

        // 必须保留别人的服务（例如用户还开着李跳跳），只追加自己
        val services = current.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(mine, ignoreCase = true) }
            .toMutableList()
        services.add(mine)

        val ok = runCatching {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                services.joinToString(":"),
            )
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1,
            )
        }.onFailure { Log.w(TAG, "自愈写设置失败: ${it.message}") }.isSuccess

        if (!ok) return false
        val restored = isEnabled(context)
        Log.d(TAG, "自愈结果=$restored")
        return restored
    }

    /** 设置页要展示的那条 adb 命令。 */
    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
}
