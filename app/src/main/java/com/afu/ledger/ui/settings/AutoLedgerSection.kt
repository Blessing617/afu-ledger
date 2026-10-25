package com.afu.ledger.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.afu.ledger.auto.AutoLedgerSettings
import com.afu.ledger.auto.OcrStatus
import com.afu.ledger.auto.RecognitionEntry
import com.afu.ledger.auto.RecognitionLog
import com.afu.ledger.auto.keepalive.AutoLedgerKeepAlive
import com.afu.ledger.auto.keepalive.BatteryWhitelist
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.LocalTabVisible
import com.afu.ledger.ui.component.HelpTip
import com.afu.ledger.ui.component.IconChoiceRow
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.SettingRow
import com.afu.ledger.ui.component.SettingSwitchRow
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 设置页的自动记账分组。
 *
 * ## 从「说明书」改成「开关 + 选择 + 帮助」
 *
 * 原先这个区块把所有解释（为什么划掉就失效、OCR 为什么只能截图）
 * 全部常驻成小字，一屏下来几十行灰字，真正的开关被淹没。现在：
 *
 * - 开关与选择器占主位，一行一件事；
 * - 解释性长文本收进「?」，点开才看；
 * - 默认账户与默认分类用带图标的横条，和记账面板里的选择器同构。
 */
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
    onHideKeepAliveNotificationChange: (Boolean) -> Unit,
    onClearLog: () -> Unit,
) {
    val context = LocalContext.current

    SettingSwitchRow(
        icon = Icons.Rounded.Bolt,
        title = "自动记账",
        subtitle = if (settings.enabled) {
            "识别微信 / 支付宝支付结果页，确认后才入账"
        } else {
            "已关闭 · 打开后可配置默认账户、分类与截图 OCR"
        },
        checked = settings.enabled,
        onCheckedChange = onEnabledChange,
    )

    // 打开总开关才展开详情：关闭时整块只剩这一行，避免把设置页铺成一整面说明书。
    // 收起只影响「显示」，不碰已保存的默认账户 / 默认分类。
    AnimatedVisibility(
        visible = settings.enabled,
        enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(240)),
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120)),
    ) {
        Column {
    SettingDivider()

    // 状态行：一行说清「能不能用」
    val statusHealthy = serviceEnabled && keepAliveState.healthy
    SettingRow(
        icon = Icons.Rounded.Security,
        title = if (serviceEnabled) "无障碍服务已开启" else "无障碍服务未开启",
        subtitle = if (serviceEnabled) keepAliveState.text else "开启后才能识别支付页面",
        iconTint = if (statusHealthy) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        onClick = if (serviceEnabled) null else {
            { context.startActivity(accessibilitySettingsIntent()) }
        },
    )
    SettingDivider()

    // 授权与保活：全部收进「?」
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
        HelpTip(
            title = if (serviceEnabled) "后台可靠性说明" else "如何开启服务",
            text = autoLedgerHelpText(
                serviceEnabled = serviceEnabled,
                wasEverEnabled = wasEverEnabled,
                keepAliveState = keepAliveState,
            ),
        )
        if (!serviceEnabled) {
            KeepAliveActions(
                wasEverEnabled = wasEverEnabled,
                onOpenAccessibility = { context.startActivity(accessibilitySettingsIntent()) },
                onOpenAppDetails = { context.startActivity(appDetailsIntent(context)) },
            )
            if (!wasEverEnabled) {
                Spacer(Modifier.height(Spacing.xs))
            }
        } else {
            KeepAliveHardening()
        }
    }

    SettingDivider()

    // 默认账户
    if (accounts.isNotEmpty()) {
        ChoiceBlock(
            title = "默认账户",
            subtitle = accounts.firstOrNull { it.id == settings.defaultAccountId }?.name ?: "未设置",
            icon = Icons.Rounded.Payments,
        ) {
            IconChoiceRow(
                items = accounts,
                selectedId = settings.defaultAccountId,
                idOf = { it.id },
                labelOf = { it.name },
                iconOf = { accountIcon(it.iconKey) },
                colorOf = { Color(it.colorArgb) },
                onSelect = { onDefaultAccountChange(it.id) },
            )
        }
        SettingDivider()
    }

    // 默认分类
    if (categories.isNotEmpty()) {
        ChoiceBlock(
            title = "默认分类",
            subtitle = categories.firstOrNull { it.id == settings.defaultCategoryId }?.name ?: "未设置",
            icon = Icons.Rounded.ReceiptLong,
        ) {
            IconChoiceRow(
                items = categories,
                selectedId = settings.defaultCategoryId,
                idOf = { it.id },
                labelOf = { it.name },
                iconOf = { categoryIcon(it.iconKey) },
                colorOf = { Color(it.colorArgb) },
                onSelect = { onDefaultCategoryChange(it.id) },
            )
        }
        SettingDivider()
    }

    SettingSwitchRow(
        icon = Icons.Rounded.TextFields,
        title = "截图 OCR 兜底",
        subtitle = "微信不暴露无障碍节点时，截屏后本机识别",
        checked = settings.ocrEnabled,
        onCheckedChange = onOcrEnabledChange,
    )
    if (settings.ocrEnabled) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            SettingRow(
                icon = Icons.Rounded.Cloud,
                title = when {
                    !ocrStatus.available -> "OCR 不可用${ocrStatus.reason?.let { "（$it）" }.orEmpty()}"
                    ocrStatus.lastOutcome != null -> "OCR 可用 · 最近 ${ocrStatus.lastOutcome}"
                    else -> "OCR 可用"
                },
                subtitle = if (ocrStatus.lastElapsedMillis > 0) {
                    "最近一次耗时 ${ocrStatus.lastElapsedMillis} ms"
                } else {
                    null
                },
                iconTint = if (ocrStatus.available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                onClick = null,
            )
            HelpTip(
                title = "OCR 与隐私",
                text = "截图只存在内存里，识别完成立即丢弃，不落盘、不上传；" +
                    "只在微信前台且页面稳定后截一次，内容变化至少间隔 8 秒，" +
                    "连续识别不到会自动退避到 60 秒。支付宝不截图。",
            )
        }
    }
    SettingDivider()

    SettingSwitchRow(
        icon = Icons.Rounded.Info,
        title = "识别日志",
        subtitle = "最近 $RecognitionLogLineLimit 条（最多 ${RecognitionLog.MAX_ENTRIES} 条 / 7 天），重启后仍保留",
        checked = settings.recognitionLogEnabled,
        onCheckedChange = onRecognitionLogChange,
    )
    if (settings.recognitionLogEnabled) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
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
                TextButton(
                    onClick = onClearLog,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text("清空日志") }
            }
        }
    }
        }
    }
}

private const val RecognitionLogLineLimit = 8

/** 默认账户 / 默认分类的展示块：标题行 + 下方的图标选择条。 */
@Composable
private fun ChoiceBlock(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

private fun autoLedgerHelpText(
    serviceEnabled: Boolean,
    wasEverEnabled: Boolean,
    keepAliveState: KeepAliveUiState,
): String {
    val parts = mutableListOf<String>()
    if (serviceEnabled) {
        parts += "后台保护状态：${keepAliveState.text}。"
    } else if (wasEverEnabled) {
        parts += "你之前开启过，现在系统里已经没有它了。最常见的原因是把它从最近任务里划掉 —— " +
            "划掉等于强制停止，Android 会连带撤销无障碍权限。"
    } else {
        parts += "开启路径：系统设置 → 无障碍 → 已下载的应用 → 阿福记账 → 打开。"
    }
    parts += "无障碍权限不会自己过期，它消失的唯一原因是被 force-stop。" +
        "在小米 / Redmi 上，把应用从最近任务划掉就等于 force-stop，" +
        "所以请把卡片向下拉锁定。"
    parts += "小米 / Redmi 若提示「受限制的设置」，到应用信息右上角菜单里允许。"
    return parts.joinToString("\n\n")
}

/** 未开启时的两个跳转按钮。 */
@Composable
private fun KeepAliveActions(
    wasEverEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(onClick = onOpenAccessibility) {
            Text(if (wasEverEnabled) "重新开启无障碍服务" else "去开启无障碍服务")
        }
        TextButton(onClick = onOpenAppDetails) { Text("应用信息") }
    }
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
 *
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "保持后台运行（只需设置一次）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "① 锁定任务：最近任务里把本应用卡片往下拉，出现锁图标即锁定。",
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
        NotificationPermissionHint()
    }
}


/** 后台可靠性状态的展示模型。 */
data class KeepAliveUiState(val text: String, val healthy: Boolean)

/**
 * 通知权限被关时给出降级说明。
 *
 * 后台可靠性**不依赖**通知权限 —— 无损的是「失效时你能收到提醒」这条能力。
 * 权限被关时前台服务仍会运行（Android 会隐式展示一个低优先级条目），
 * 但我们发出的「已失效」告警会看不见，用户就回到「它什么时候坏的我不知道」。
 * 所以这里要明说，而不是静默降级。
 */
@Composable
private fun NotificationPermissionHint() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) return

    Text(
        text = "提示：通知权限已关闭。自动记账仍可用，但服务失效时你不会收到提醒。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
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
            KeepAliveUiState("疑似被系统冻结 · 最近事件在 ${since / 60_000} 分钟前", false)
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
    // 方向与时间来源只在与默认值不同时提示，避免每行都拖一条尾巴
    val direction = entry.direction?.takeIf { it != "EXPENSE" }?.let { " · $it" }.orEmpty()
    val fallbackTime = entry.timeSource?.takeIf { it == "FALLBACK" }?.let { " · 时间回退" }.orEmpty()
    // 指纹只给后 12 位：两条记录是不是同一笔，比对比全套指纹快得多
    val fingerprint = entry.fingerprint?.takeLast(12)?.let { " · #$it" }.orEmpty()
    // 页面摘要单独一行：误识别时「当时页面上写了什么」比什么都重要
    val headline = entry.pageHeadline
        ?.takeIf { it.isNotBlank() }
        ?.let { "\n    ↳ $it" }
        .orEmpty()
    return "$time  ${entry.outcome}$amount$direction$merchant$fallbackTime$fingerprint$headline"
}
