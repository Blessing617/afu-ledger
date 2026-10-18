package com.afu.ledger.ui.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AmountText
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.component.effectiveSurfaceAlpha
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

/**
 * 首页：月度概览 + 月历 + 按日分组的交易列表。
 *
 * 布局顺序是有意的：先「这个月怎么样」，再「哪天花了钱」，最后才是明细。
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddTransaction: () -> Unit,
    onCreateAccount: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onOpenReport: () -> Unit,
    onSelectDate: (LocalDate?) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "summary") { MonthSummaryCard(uiState, onOpenReport) }

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
                    ActiveFilterChip(
                        label = "${date.monthValue} 月 ${date.dayOfMonth} 日",
                        onClear = { onSelectDate(null) },
                    )
                }
            }

            if (uiState.loaded && uiState.groups.isEmpty()) {
                item(key = "empty") {
                    if (uiState.hasAccounts) {
                        EmptyState(
                            title = if (uiState.selectedDate != null) "这天没有记账" else "还没有记账",
                            description = if (uiState.selectedDate != null) {
                                "换个日期看看，或者点右下角记一笔"
                            } else {
                                "点右下角的「记一笔」，开始记录第一笔收支吧"
                            },
                        )
                    } else {
                        EmptyState(
                            title = "先创建一个账户",
                            description = "记账需要一个资产账户来归属金额",
                            action = { AfuButton(text = "创建账户", onClick = onCreateAccount) },
                        )
                    }
                }
            }

            uiState.groups.forEach { group ->
                item(key = "day-${group.dayKey}") { DayHeader(group) }
                items(group.items, key = { it.transaction.id }) { item ->
                    TransactionRow(item = item, onClick = { onTransactionClick(item.transaction.id) })
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddTransaction,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CapsuleShape,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("记一笔")
        }
    }
}

/**
 * 月度概览卡：结余 + 收支两行 + 报表入口。
 *
 * 结余用大字号放在最上面，比「支出 / 收入」两块并排更符合「我最近到底还剩多少」的直觉。
 */
@Composable
private fun MonthSummaryCard(uiState: HomeUiState, onOpenReport: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val balance = uiState.monthIncome.cents - uiState.monthExpense.cents

    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = 0.18f), primary.copy(alpha = 0.03f)),
                    ),
                )
                .padding(Spacing.lg),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${uiState.summaryTitle}结余",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .clickable(onClick = onOpenReport)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Insights,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "报表",
                            style = MaterialTheme.typography.labelMedium,
                            color = primary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = MoneyFormatter.formatWithSymbol(Money(balance)),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                )
                Spacer(Modifier.height(Spacing.md))
                SummaryRow("支出", uiState.monthExpense.cents, Color(0xFFFFB74D), "−")
                Spacer(Modifier.height(Spacing.xs))
                SummaryRow("收入", uiState.monthIncome.cents, Color(0xFF2E9E5B), "+")
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, cents: Long, accent: Color, sign: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .height(8.dp)
                .width(if (label == "支出") 56.dp else 40.dp)
                .clip(CapsuleShape)
                .background(accent),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$sign ${MoneyFormatter.formatWithSymbol(Money(cents))}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ActiveFilterChip(label: String, onClear: () -> Unit) {
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

@Composable
private fun DayHeader(group: TransactionGroup) {
    // 分组标题原先是没有底色的裸文字，直接压在背景图上。
    // 深色模式下 onSurfaceVariant 是浅灰，遇到背景图的亮区会糊成一片；
    // 这里给它一个与卡片同源的半透明胶囊底，保证任何背景与主题下都可读。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = effectiveSurfaceAlpha()),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(group.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = (if (group.netAmount.cents >= 0) "+" else "-") +
                MoneyFormatter.formatYuan(group.netAmount.abs().cents),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionRow(item: TransactionItem, onClick: () -> Unit) {
    AfuCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(item.categoryColorArgb).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(item.categoryIcon),
                    contentDescription = null,
                    tint = Color(item.categoryColorArgb),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = buildString {
                    append(item.accountName)
                    if (item.transaction.note.isNotBlank()) {
                        append(" · ")
                        append(item.transaction.note)
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AmountText(
                money = item.transaction.amount,
                isExpense = item.transaction.type == TransType.EXPENSE,
            )
        }
    }
}
