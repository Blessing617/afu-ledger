package com.afu.ledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afu.ledger.BuildConfig
import androidx.compose.runtime.CompositionLocalProvider
import com.afu.ledger.ui.component.LocalForceOpaqueCard
import com.afu.ledger.auto.AutoLedgerSettings
import com.afu.ledger.auto.OcrStatus
import com.afu.ledger.auto.RecognitionEntry
import com.afu.ledger.data.backup.DataStats
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.SettingGroup
import com.afu.ledger.ui.component.SettingRow
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.MascotAvatar
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.ui.theme.themeColorPreset
import com.afu.ledger.ui.theme.themeStyle

/**
 * 设置页（「我的」）。
 *
 * ## 结构
 *
 * 顶部是唯一的个性化入口（外观与主题），下面按「记账 / 自动记账 / 数据与关于」分组，
 * 每组是一张卡片 + 若干行。说明性文字全部收进「?」，首屏只留可操作的东西。
 *
 * 之前这里平铺着 8 个色点、深色模式、背景预览、两个滑块和一大段授权说明，
 * 首屏几乎没有「设置」可言 —— 现在这些都搬进了外观与主题页。
 */
@Composable
fun SettingsScreen(
    settings: ThemeSettings,
    autoSettings: AutoLedgerSettings,
    serviceEnabled: Boolean,
    accounts: List<Account>,
    expenseCategories: List<Category>,
    logEntries: List<RecognitionEntry>,
    ocrStatus: OcrStatus,
    wasEverEnabled: Boolean,
    onOpenAppearance: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenBudgets: () -> Unit,
    onAutoEnabledChange: (Boolean) -> Unit,
    onDefaultAccountChange: (Long?) -> Unit,
    onDefaultCategoryChange: (Long?) -> Unit,
    onRecognitionLogChange: (Boolean) -> Unit,
    onOcrEnabledChange: (Boolean) -> Unit,
    onHideKeepAliveNotificationChange: (Boolean) -> Unit,
    onClearLog: () -> Unit,
    dataStats: DataStats,
    dataBusy: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 定时器在 LazyColumn 之外只订阅一次：放进 item 会让 5 秒一次的 tick
    // 同时失效所有可见 item，快速滑动时表现为掉帧（官方 Compose 性能指南明确禁止）。
    val keepAliveState by rememberKeepAliveState()

    CompositionLocalProvider(LocalForceOpaqueCard provides true) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = 140.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "appearance") {
                AppearanceEntry(settings = settings, onClick = onOpenAppearance)
            }

            item(key = "account-header") {
                SectionLabel("记账")
            }
            item(key = "account-group") {
                SettingGroup {
                    SettingRow(
                        icon = Icons.Rounded.Category,
                        title = "分类管理",
                        subtitle = "新增、重命名、换图标与颜色",
                        detail = "${expenseCategories.size} 个支出",
                        onClick = onManageCategories,
                    )
                    SettingDivider()
                    SettingRow(
                        icon = Icons.Rounded.EventRepeat,
                        title = "定时记账",
                        subtitle = "房租、订阅这类固定收支，到点自动记一笔",
                        onClick = onOpenSchedules,
                    )
                    SettingDivider()
                    SettingRow(
                        icon = Icons.Rounded.PieChart,
                        title = "预算管理",
                        subtitle = "给一段时间设个额度，主页看「还能花多少」",
                        onClick = onOpenBudgets,
                    )
                }
            }

            item(key = "auto-header") {
                SectionLabel("自动记账")
            }
            item(key = "auto-group") {
                SettingGroup {
                    AutoLedgerSection(
                        settings = autoSettings,
                        serviceEnabled = serviceEnabled,
                        accounts = accounts,
                        categories = expenseCategories,
                        logEntries = logEntries,
                        ocrStatus = ocrStatus,
                        keepAliveState = keepAliveState,
                        wasEverEnabled = wasEverEnabled,
                        onEnabledChange = onAutoEnabledChange,
                        onDefaultAccountChange = onDefaultAccountChange,
                        onDefaultCategoryChange = onDefaultCategoryChange,
                        onRecognitionLogChange = onRecognitionLogChange,
                        onOcrEnabledChange = onOcrEnabledChange,
                        onHideKeepAliveNotificationChange = onHideKeepAliveNotificationChange,
                        onClearLog = onClearLog,
                    )
                }
            }

            item(key = "data-header") {
                SectionLabel("数据")
            }
            item(key = "data-group") {
                DataManageSection(
                    stats = dataStats,
                    busy = dataBusy,
                    onExportClick = onExportBackup,
                    onImportClick = onImportBackup,
                    onClearAllClick = onClearAllData,
                )
            }

            item(key = "about") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl, bottom = Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.md, bottom = Spacing.xs),
    )
}

/**
 * 外观入口：用当前主题的配色画一张小卡，点进去换。
 *
 * 它同时承担「现在是什么主题」的展示：风格名 + 三个色点，
 * 不用进二级页就知道当前选了什么。
 */
@Composable
private fun AppearanceEntry(settings: ThemeSettings, onClick: () -> Unit) {
    val style = themeStyle(settings.themeStyleId)
    val preset = themeColorPreset(settings.themeColorId)
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val scheme = MaterialTheme.colorScheme

    AfuCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = 0.16f), primary.copy(alpha = 0.03f)),
                    ),
                )
                .clickable(onClick = onClick)
                .padding(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "外观与主题",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${style.label} · ${preset.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (style.showsMascot) {
                        MascotAvatar(size = 30.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    listOf(primary, tertiary, scheme.background).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
