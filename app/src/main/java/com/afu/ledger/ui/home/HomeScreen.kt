package com.afu.ledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AmountText
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter

/** 首页：月度汇总 + 按日分组的交易列表。 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddTransaction: () -> Unit,
    onCreateAccount: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { MonthSummaryCard(uiState) }

            if (uiState.loaded && uiState.groups.isEmpty()) {
                item {
                    if (uiState.hasAccounts) {
                        EmptyState(
                            title = "还没有记账",
                            description = "点右下角的「记一笔」，开始记录第一笔收支吧",
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
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("记一笔")
        }
    }
}

@Composable
private fun MonthSummaryCard(uiState: HomeUiState) {
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("本月支出", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = MoneyFormatter.formatWithSymbol(uiState.monthExpense),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("本月收入", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = MoneyFormatter.formatWithSymbol(uiState.monthIncome),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF2E9E5B),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(group: TransactionGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(item.categoryColorArgb).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(item.categoryIcon),
                    contentDescription = null,
                    tint = Color(item.categoryColorArgb),
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
