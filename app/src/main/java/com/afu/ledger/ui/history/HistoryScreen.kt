package com.afu.ledger.ui.history

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.DayHeader
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.TransactionRow
import com.afu.ledger.ui.home.CalendarStrip
import com.afu.ledger.ui.home.HistoryUiState
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

/**
 * 明细页：月度概览 + 月历 + 按日分组的历史。
 *
 * 它是首页的「下一层」：首页只回答今天，想翻历史、想看这个月花在哪，就往这里点。
 * 结构与首页一致（概览卡 → 时间工具 → 分组列表），降低两层之间的切换成本。
 */
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate?) -> Unit,
    onTransactionClick: (Long) -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = 140.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "summary") {
            MonthSummaryCard(uiState = uiState, onOpenReport = onOpenReport)
        }

        item(key = "calendar") {
            CalendarStrip(
                yearMonth = uiState.calendarMonth,
                today = LocalDate.now(),
                selectedDate = uiState.selectedDate,
                days = uiState.calendarDays,
                onSelectDate = onSelectDate,
                onMonthChange = onMonthChange,
            )
        }

        uiState.selectedDate?.let { date ->
            item(key = "filter-chip") {
                FilterChip(
                    label = "${date.monthValue} 月 ${date.dayOfMonth} 日",
                    onClear = { onSelectDate(null) },
                )
            }
        }

        if (uiState.loaded && uiState.groups.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = if (uiState.selectedDate != null) "这天没有记账" else "这个月还没有账",
                    description = if (uiState.selectedDate != null) {
                        "换个日期看看，或者点中间的「＋」记一笔"
                    } else {
                        "点中间的「＋」记第一笔吧"
                    },
                )
            }
        }

        uiState.groups.forEach { group ->
            item(key = "day-${group.dayKey}") {
                DayHeader(
                    label = group.label,
                    netText = (if (group.netAmount.cents >= 0) "+" else "-") + netText(group.netAmount),
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            item(key = "day-card-${group.dayKey}") {
                AfuCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    group.items.forEachIndexed { index, item ->
                        if (index > 0) SettingDivider()
                        TransactionRow(
                            item = item,
                            onClick = { onTransactionClick(item.transaction.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 月度概览：结余为主，支出与收入为辅，右上角进报表。
 *
 * 结余用主色大字号，是因为「这个月还剩多少」比「花了多少」更接近用户的实际问题；
 * 支出与收入给等宽的对照行，方便横向比较。
 */
@Composable
private fun MonthSummaryCard(uiState: HistoryUiState, onOpenReport: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${uiState.summaryTitle}结余",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = MoneyFormatter.formatWithSymbol(uiState.monthBalance),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(primary.copy(alpha = 0.12f))
                    .clickable(onClick = onOpenReport)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "报表",
                    style = MaterialTheme.typography.labelLarge,
                    color = primary,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SummaryMetric(
                label = "支出",
                value = MoneyFormatter.formatWithSymbol(uiState.monthExpense),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                label = "收入",
                value = MoneyFormatter.formatWithSymbol(uiState.monthIncome),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CapsuleShape)
                .background(accent),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** 当前筛选的日期胶囊；点一下取消筛选。 */
@Composable
private fun FilterChip(label: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClear)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "只看 $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "全部",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        )
    }
}

/** 净额展示用的辅助：不带货币符号的绝对值。 */
private fun netText(money: Money): String = MoneyFormatter.formatYuan(money.abs().cents)
