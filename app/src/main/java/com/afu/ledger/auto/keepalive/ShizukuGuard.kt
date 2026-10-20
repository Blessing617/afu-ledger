package com.afu.ledger.auto.keepalive

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 借 Shizuku 的 shell 权限把被系统移除的无障碍服务**写回去**。
 *
 * ## 为什么必须这么做
 *
 * 在这台机（Xiaomi 14 Pro / HyperOS）上实测确认：
 *
 * - 从最近任务里把应用卡片划掉 → 应用被 force-stop
 * - Android 在被 force-stop 时会把该应用的无障碍权限从
 *   `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 里移除（AOSP 行为，无法绕过）
 * - 即使用户在最近任务里「锁定」了任务，手动划掉**仍然**会触发
 *
 * 所以「设置一次就再也不管」在纯应用层是做不到的。
 * 同赛道的 Tally（`cypressincloud/Tally`）的做法是：
 * 用 Shizuku 拿到 shell 权限，由 `AccessibilityGuardWorker` 周期性巡检，
 * 发现服务不在启用列表里就**静默写回**，用户完全无感。
 *
 * 这里照同一思路实现，差别只在于组件名与包名换成本应用的。
 * **没有安装 / 授权 Shizuku 时，所有方法都安全降级为 false，不影响其它功能。**
 */
object ShizukuGuard {

    /** 本应用的无障碍服务组件（平坦化名，写进 secure settings 的格式）。 */
    private fun componentName(context: Context): String =
        "${context.packageName}/${context.packageName}.auto.AutoLedgerAccessibilityService"

    /** Shizuku 服务是否可达。 */
    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** 是否已授予本应用 Shizuku 权限。 */
    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /** 请求权限（会弹 Shizuku 的授权框）。 */
    fun requestPermission(requestCode: Int) {
        if (isAvailable() && !hasPermission()) {
            runCatching { Shizuku.requestPermission(requestCode) }
        }
    }

    /**
     * 通过 Shizuku 起一个 shell 执行命令，返回 stdout。
     *
     * `Shizuku.newProcess` 是隐藏 API，用反射调用；失败返回 null。
     */
    private fun execCommand(command: String): String? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null,
        ) as? Process ?: return null

        val output = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                output.append(line).append('\n')
                line = reader.readLine()
            }
        }
        process.waitFor()
        output.toString().trim()
    }.getOrNull()

    /**
     * 把本服务写回启用列表。
     *
     * 做法与 Tally 一致：读出当前的列表，**保留别人的服务**（李跳跳等），
     * 只把我们的组件补进去，然后写回。不直接覆盖整个值，
     * 否则会把用户其它无障碍服务一起关掉。
     */
    fun enableService(context: Context): Boolean {
        if (!hasPermission()) return false

        val component = componentName(context)
        val current = execCommand("settings get secure enabled_accessibility_services")
        val services = LinkedHashSet<String>()
        if (!current.isNullOrBlank() && !current.equals("null", ignoreCase = true)) {
            services.addAll(current.split(":").filter { it.isNotBlank() })
        }
        // 已经在了就不重复写
        if (services.any { it.equals(component, ignoreCase = true) }) return true

        services.add(component)
        val updated = services.joinToString(":")

        execCommand("settings put secure enabled_accessibility_services $updated")
        execCommand("settings put secure accessibility_enabled 1")
        return true
    }

    /**
     * 后台保活的系统级设置（与 Tally 的 `grantBackgroundKeepAlive` 等价）。
     *
     * - Doze 白名单：`dumpsys deviceidle whitelist +pkg`
     * - 允许后台：`appops set RUN_IN_BACKGROUND / RUN_ANY_IN_BACKGROUND allow`
     *
     * 这些本身不需要 SYSTEM_ALERT_WINDOW；Tally 顺手给是因为它的悬浮窗需要，
     * 我们用无障碍悬浮窗（TYPE_ACCESSIBILITY_OVERLAY），**不申请该权限**。
     */
    fun grantBackgroundKeepAlive(context: Context) {
        if (!hasPermission()) return
        val pkg = context.packageName
        execCommand("dumpsys deviceidle whitelist +$pkg")
        execCommand("cmd appops set $pkg RUN_IN_BACKGROUND allow")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            execCommand("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
        }
    }
}
