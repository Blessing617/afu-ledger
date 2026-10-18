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
import androidx.compose.runtime.LaunchedEffect
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
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
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
            val keepAlive = rememberKeepAliveState()
            Text(
                text = keepAlive.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (keepAlive.healthy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            if (!keepAlive.healthy) {
                TextButton(onClick = { context.startActivity(accessibilitySettingsIntent()) }) {
                    Text("去重新开启无障碍服务")
                }
            }
        }

        if (!serviceEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { context.startActivity(accessibilitySettingsIntent()) }) {
                    Text("去开启无障碍服务")
                }
                TextButton(onClick = { context.startActivity(appDetailsIntent(context)) }) {
                    Text("应用信息")
                }
            }
            Text(
                text = "小米 / Redmi：若被提示「受限制的设置」，到应用信息右上角菜单里允许；" +
                    "再把自启动打开、省电策略设为「无限制」，否则服务容易被系统停掉。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private data class KeepAliveUiState(val text: String, val healthy: Boolean)

/** 每 5 秒刷新一次后台可靠性状态，避免把「冻结」显示成「正常」。 */
@Composable
private fun rememberKeepAliveState(): KeepAliveUiState {
    var state by remember { mutableStateOf(computeKeepAliveState()) }
    LaunchedEffect(Unit) {
        while (true) {
            state = computeKeepAliveState()
            delay(5_000)
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
