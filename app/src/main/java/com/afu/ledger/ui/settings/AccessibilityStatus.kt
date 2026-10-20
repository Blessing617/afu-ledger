package com.afu.ledger.ui.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.afu.ledger.auto.AutoLedgerAccessibilityService
import kotlinx.coroutines.delay

/** 判断自动记账的无障碍服务当前是否被系统启用。 */
fun isAutoLedgerServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    if (!manager.isEnabled) {
        if (com.afu.ledger.BuildConfig.DEBUG) android.util.Log.d("AfuA11y", "manager disabled")
        return false
    }

    val expected = ComponentName(context, AutoLedgerAccessibilityService::class.java)
    val list = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val found = list.any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
        ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
    }
    if (com.afu.ledger.BuildConfig.DEBUG) {
        android.util.Log.d(
            "AfuA11y",
            "enabled=${manager.isEnabled} found=$found expected=$expected " +
                "list=${list.map { it.resolveInfo?.serviceInfo?.name }} " +
                "raw=${Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)}",
        )
    }
    return found
}

/**
 * 跟随系统实时刷新「无障碍服务是否已开启」。
 *
 * ## 为什么不能只查 ON_RESUME
 *
 * 真机反馈：在系统无障碍设置里打开开关、返回应用后，设置页仍然显示「未开启 / 去打开」，
 * 要再切一次页面才变绿。两个原因叠加：
 *
 * 1. 系统把服务写进启用列表有延迟，`ON_RESUME` 那一刻可能还查不到；
 * 2. 只在 resume 时查一次，之后就再也不看了。
 *
 * 所以这里三管齐下：
 * - 监听 `AccessibilityManager` 的全局与服务级状态变化（系统主动回调，通常最快）
 * - `ON_RESUME` 时立刻重算，并触发一轮短间隔复检
 * - 处于「未开启」时每 700ms 复查一次（最多约 14 秒），一检到就停
 *
 * 全程不乐观显示：只有真的查到服务在列表里才变绿。
 */
@Composable
fun rememberAutoLedgerServiceEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(isAutoLedgerServiceEnabled(context)) }
    // 每次回到前台 +1，用来重启下面的复检协程（enabled 不变时 LaunchedEffect 不会自己重启）
    var probe by remember { mutableIntStateOf(0) }

    DisposableEffect(context, lifecycleOwner) {
        val manager = context.getSystemService(AccessibilityManager::class.java)

        fun refresh() {
            val now = isAutoLedgerServiceEnabled(context)
            if (now != enabled) enabled = now
        }

        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { refresh() }
        runCatching { manager?.addAccessibilityStateChangeListener(stateListener) }

        val servicesListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityManager.AccessibilityServicesStateChangeListener { refresh() }
        } else {
            null
        }
        if (servicesListener != null) {
            runCatching { manager?.addAccessibilityServicesStateChangeListener(servicesListener) }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
                probe++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { manager?.removeAccessibilityStateChangeListener(stateListener) }
            if (servicesListener != null) {
                runCatching { manager?.removeAccessibilityServicesStateChangeListener(servicesListener) }
            }
        }
    }

    LaunchedEffect(enabled, probe) {
        if (enabled) return@LaunchedEffect
        // 系统启用列表有延迟：短间隔复查，查到就立刻停
        repeat(PROBE_ATTEMPTS) {
            delay(PROBE_INTERVAL_MILLIS)
            if (isAutoLedgerServiceEnabled(context)) {
                enabled = true
                return@LaunchedEffect
            }
        }
    }

    return enabled
}

/**
 * 「未开启」状态下前台复查的次数与间隔。
 *
 * 20 × 700ms ≈ 14 秒：足以覆盖系统启用列表的延迟，又不会在用户根本没打算开启时
 * 一直轮询耗电（0.7 秒一次只是读一个系统列表，开销可忽略，但没必要常驻）。
 */
private const val PROBE_ATTEMPTS = 20
private const val PROBE_INTERVAL_MILLIS = 700L

fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

fun appDetailsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)
