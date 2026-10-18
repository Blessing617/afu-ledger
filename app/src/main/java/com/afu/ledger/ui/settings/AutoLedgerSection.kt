package com.afu.ledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.afu.ledger.auto.AutoLedgerSettings
import com.afu.ledger.auto.OcrStatus
import com.afu.ledger.auto.RecognitionEntry
import com.afu.ledger.auto.keepalive.AutoLedgerKeepAlive
import com.afu.ledger.auto.keepalive.BatteryWhitelist
import com.afu.ledger.auto.keepalive.ShizukuGuard
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.LocalTabVisible
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** 设置页的自动记账区块：开关、默认值、授权引导与识别日志。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoLedgerSection(
    settings: AutoLedgerSettings,
    serviceEnabled: Boolean,
    accounts: List<Account>,
    categories: List<Category>,
    logEntries: List<RecognitionEntry>,
    ocrStatus: OcrStatus,
    keepAliveState: KeepAliveUiState,
    wasEverEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDefaultAccountChange: (Long?) -> Unit,
    onDefaultCategoryChange: (Long?) -> Unit,
    onRecognitionLogChange: (Boolean) -> Unit,
    onOcrEnabledChange: (Boolean) -> Unit,
    onClearLog: () -> Unit,
) {
    val context = LocalContext.current

    SectionCard(icon = Icons.Outlined.Bolt, title = "自动记账") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "识别微信 / 支付宝支付结果页",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "不联网，金额只在弹出确认并点「记一笔」后才会入账",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = if (serviceEnabled) "无障碍服务：已开启" else "无障碍服务：未开启",
            style = MaterialTheme.typography.labelMedium,
            color = if (serviceEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )

        if (serviceEnabled) {
            // 状态由 SettingsScreen 在 LazyColumn **之外**统一订阅后传进来。
            //
            // 官方 Compose 性能指南明确禁止在 lazy item 内部 collect 共享 Flow / 定时器：
            // 那样每个可见 item 都会建立独立订阅，定时器一 tick 就同时让所有可见 item
            // 失效重组，快速滚动时直接表现为掉帧。这里只接收已算好的值。
            KeepAliveStatusLine(keepAliveState)
        }

        if (!serviceEnabled) {
            // 授权步骤（只在没开时出现）
            KeepAliveSetupGuide(
                wasEverEnabled = wasEverEnabled,
                onOpenAccessibility = { context.startActivity(accessibilitySettingsIntent()) },
                onOpenAppDetails = { context.startActivity(appDetailsIntent(context)) },
            )
        } else {
            // 已授权后，把「保活三件套」里剩下的两件也补齐 —— 这两件只需设置一次，
            // 之后就再也不会被系统 force-stop，用不着反复回来点授权。
            KeepAliveHardening()
        }

        if (accounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "默认账户",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = account.id == settings.defaultAccountId,
                        onClick = { onDefaultAccountChange(account.id) },
                        label = { Text(account.name) },
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            Text(
                text = "默认分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id == settings.defaultCategoryId,
                        onClick = { onDefaultCategoryChange(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "截图 OCR（微信兜底）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "微信不暴露无障碍节点，只能截屏后用本机离线 OCR 识别。" +
                        "截图只存在内存里，识别完立即丢弃，不落盘、不上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.ocrEnabled, onCheckedChange = onOcrEnabledChange)
        }

        if (settings.ocrEnabled) {
            val statusText = when {
                !ocrStatus.available -> "OCR 状态：不可用${ocrStatus.reason?.let { "（$it）" }.orEmpty()}"
                ocrStatus.lastOutcome != null -> "OCR 状态：可用 · 最近一次 ${ocrStatus.lastOutcome}"
                else -> "OCR 状态：可用"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (ocrStatus.available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            if (ocrStatus.available && ocrStatus.lastElapsedMillis > 0) {
                Text(
                    text = "最近一次识别耗时 ${ocrStatus.lastElapsedMillis} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ocrStatus.lastDetail?.let { detail ->
                Text(
                    text = "分段：$detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "只在微信前台且页面稳定后截一次，内容变化至少间隔 8 秒，" +
                    "连续识别不到会自动退避到 60 秒。支付宝不截图。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "识别日志",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = settings.recognitionLogEnabled, onCheckedChange = onRecognitionLogChange)
        }

        if (settings.recognitionLogEnabled) {
            Text(
                text = "只保留最近 ${RecognitionLogLineLimit} 条，进程结束即清空，不落盘。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (logEntries.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                logEntries.take(RecognitionLogLineLimit).forEach { entry ->
                    Text(
                        text = formatEntry(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClearLog) { Text("清空日志") }
            }
        }
    }
}

private const val RecognitionLogLineLimit = 8

/**
 * 授权引导。只在无障碍服务没开时出现。
 *
 * 两种「没开」要分开说，否则用户只会看到一句「未开启」而不知道发生了什么：
 *
 * - **从没开过** → 就是还没设置，引导去开
 * - **开过又没了** → 几乎一定是被系统撤销了。最常见的原因是把它从最近任务划掉
 *   （划掉 = force-stop，Android 会连带撤销无障碍权限，这是平台行为，应用层无法阻止）。
 *   这种情况下直接告诉用户原因，比让他莫名其妙地再点一遍要好。
 */
@Composable
private fun KeepAliveSetupGuide(
    wasEverEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    if (wasEverEnabled) {
        Text(
            text = "自动记账被关闭了",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "你之前开启过，现在系统里已经没有它了。最常见的原因是把它从最近任务里" +
                "划掉 —— 划掉等于强制停止，Android 会连带撤销无障碍权限。" +
                "重新开启后，尽量别划掉它。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(onClick = onOpenAccessibility) {
            Text(if (wasEverEnabled) "重新开启无障碍服务" else "去开启无障碍服务")
        }
        TextButton(onClick = onOpenAppDetails) { Text("应用信息") }
    }
    Text(
        text = "小米 / Redmi：若被提示「受限制的设置」，到应用信息右上角菜单里允许。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 保活加固：**只需设置一次**的几项。
 *
 * ## 为什么不是「失效了再让用户重新授权」
 *
 * 无障碍权限不会自己过期。它消失的**唯一原因**是应用被 force-stop
 * （AOSP `AccessibilityManagerService.onPackagesForceStoppedLocked`：
 * force-stop 一个无障碍应用会连带把它的无障碍权限从安全设置里移除）。
 *
 * 而在小米 / Redmi 上，**从最近任务里把卡片划掉就等于 force-stop**。
 * 这才是「用着用着就不记账了」的真正原因 —— 不是权限过期，是被划掉了。
 * 反复让用户回来重新授权是在治标。
 *
 * 真正的解法是小米官方的「锁定任务」：最近任务里把卡片**往下拉**，
 * 出现锁图标即锁定，之后一键清理也不会杀掉它。这一步是系统 UI 动作，
 * 应用层无法代劳，只能把路径讲到足够具体。
 * 电池优化豁免与自启动在这里一并兜住。
 */
@Composable
private fun KeepAliveHardening() {
    val context = LocalContext.current
    var ignoringBattery by remember {
        mutableStateOf(BatteryWhitelist.isIgnoringBatteryOptimizations(context))
    }
    // 回到应用时复查：用户可能刚从系统页面回来
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoringBattery = BatteryWhitelist.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Spacer(modifier = Modifier.height(Spacing.md))
    Text(
        text = "保持后台运行（只需设置一次）",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // 第 1 条最容易被忽略、也最关键：不锁定就可能被划掉
    Text(
        text = "① 锁定任务：打开最近任务，把本应用的卡片往下拉，出现锁图标即锁定。" +
            "不锁定的话，从这里划掉应用等于强制停止，无障碍权限会被系统一并撤销。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (ignoringBattery) {
        Text(
            text = "② 后台运行：已允许",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Text(
            text = "② 允许后台运行，避免系统按省电策略停掉本应用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(
                onClick = {
                    // 先用系统标准申请；被 ROM 屏蔽时退回到电池优化列表页
                    if (!BatteryWhitelist.requestIgnore(context)) {
                        runCatching {
                            context.startActivity(BatteryWhitelist.batteryOptimizationSettingsIntent())
                        }
                    }
                },
            ) { Text("允许后台运行") }
            TextButton(
                onClick = {
                    // 依次尝试已知的小米自启页；全都打不开就退到应用信息
                    val opened = BatteryWhitelist.miuiAutoStartIntents().any { intent ->
                        runCatching { context.startActivity(intent) }.isSuccess
                    }
                    if (!opened) {
                        runCatching { context.startActivity(appDetailsIntent(context)) }
                    }
                },
            ) { Text("设置自启动") }
        }
    }
    ShizukuSection()
}

/**
 * Shizuku 自动恢复。
 *
 * 这是「划掉也不影响」的关键：系统撤销无障碍权限后，
 * 靠 Shizuku 的 shell 权限周期性把服务写回去（与 Tally 同款做法）。
 *
 * 没装 Shizuku 时这里只显示说明，不阻塞其它功能。
 */
@Composable
private fun ShizukuSection() {
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(shizukuState()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = shizukuState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))
    when (state) {
        ShizukuUiState.UNAVAILABLE -> Text(
            text = "③ 可选：装 Shizuku 并授权后，应用被划掉也能自动恢复服务；" +
                "否则需要你手动重新开启一次无障碍。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ShizukuUiState.NO_PERMISSION -> {
            Text(
                text = "③ 已检测到 Shizuku，授权后可在应用被划掉时自动恢复服务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { ShizukuGuard.requestPermission(SHIZUKU_REQUEST_CODE) }) {
                Text("授权 Shizuku 自动恢复")
            }
        }

        ShizukuUiState.READY -> Text(
            text = "③ 自动恢复：已启用（每 15 分钟巡检一次）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private enum class ShizukuUiState { UNAVAILABLE, NO_PERMISSION, READY }

private fun shizukuState(): ShizukuUiState = when {
    !ShizukuGuard.isAvailable() -> ShizukuUiState.UNAVAILABLE
    !ShizukuGuard.hasPermission() -> ShizukuUiState.NO_PERMISSION
    else -> ShizukuUiState.READY
}

private const val SHIZUKU_REQUEST_CODE = 4001

/** 后台可靠性状态的展示模型。 */
data class KeepAliveUiState(val text: String, val healthy: Boolean)

/**
 * 后台可靠性状态行。纯展示，不持有定时器。
 *
 * 定时器在 [rememberKeepAliveState]（由 SettingsScreen 在 LazyColumn 之外调用），
 * 原因见官方性能指南的「不要在 lazy item 里 collect 共享 Flow」。
 */
@Composable
private fun KeepAliveStatusLine(state: KeepAliveUiState) {
    val context = LocalContext.current
    Text(
        text = state.text,
        style = MaterialTheme.typography.labelMedium,
        color = if (state.healthy) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
    )
    if (!state.healthy) {
        TextButton(onClick = { context.startActivity(accessibilitySettingsIntent()) }) {
            Text("去重新开启无障碍服务")
        }
    }
}

/**
 * 5 秒刷新一次后台可靠性状态。
 *
 * **必须在 LazyColumn 之外调用一次**，把结果作为参数传给 item。
 * 在 item 内部持有定时器会让定时器 tick 同时失效所有可见 item。
 */
@Composable
fun rememberKeepAliveState(): State<KeepAliveUiState> {
    val visible = LocalTabVisible.current
    val state = remember { mutableStateOf(computeKeepAliveState()) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        // 回到前台先立刻刷新一次，避免显示离开前的旧值
        state.value = computeKeepAliveState()
        while (true) {
            delay(5_000)
            state.value = computeKeepAliveState()
        }
    }
    return state
}

private fun computeKeepAliveState(): KeepAliveUiState {
    if (!AutoLedgerKeepAlive.foregroundRunning) {
        return KeepAliveUiState("后台保护未开启 · 通知权限被关闭时会出现，会显著降低可靠性", false)
    }
    val since = AutoLedgerKeepAlive.sinceLastEventMs()
    return when {
        since < 0L -> KeepAliveUiState("后台保护已开启 · 本次启动后还没有收到页面事件", true)
        since > AutoLedgerKeepAlive.SUSPECT_FROZEN_AFTER_MS ->
            KeepAliveUiState("疑似被系统冻结 · 最近事件在 ${since / 60_000} 分钟前，建议重新开启一次无障碍", false)
        else -> KeepAliveUiState("后台保护已开启 · 最近事件在 ${since / 1_000} 秒前", true)
    }
}

private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatEntry(entry: RecognitionEntry): String {
    val time = logTimeFormat.format(Date(entry.at))
    val amount = entry.amountCents
        ?.let { " · ${MoneyFormatter.formatWithSymbol(Money(it))}" }
        .orEmpty()
    val merchant = entry.merchant
        ?.takeIf { it.isNotBlank() }
        ?.let { " · $it" }
        .orEmpty()
    return "$time  ${entry.outcome}$amount$merchant"
}
