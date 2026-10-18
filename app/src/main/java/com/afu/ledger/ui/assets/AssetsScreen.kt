package com.afu.ledger.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.afu.ledger.domain.model.AccountType
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetsScreen(
    uiState: AssetsUiState,
    onAccountClick: (AccountWithBalance) -> Unit,
    onAddAccount: () -> Unit,
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
            item { NetWorthCard(uiState) }

            if (uiState.loaded && uiState.active.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有账户",
                        description = "创建一个现金或银行卡账户，就能开始记账了",
                    )
                }
            }

            items(uiState.active, key = { it.account.id }) { item ->
                AccountRow(item = item, onClick = { onAccountClick(item) })
            }

            if (uiState.archived.isNotEmpty()) {
                item {
                    Text(
                        text = "已归档",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                items(uiState.archived, key = { "archived-${it.account.id}" }) { item ->
                    AccountRow(item = item, onClick = { onAccountClick(item) }, dimmed = true)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddAccount,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("新增账户")
        }
    }
}

@Composable
private fun NetWorthCard(uiState: AssetsUiState) {
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("净资产", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = MoneyFormatter.formatWithSymbol(uiState.netWorth),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(Spacing.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
                Column {
                    Text("总资产", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormatter.formatWithSymbol(uiState.totalAssets), style = MaterialTheme.typography.titleMedium)
                }
                Column {
                    Text("总负债", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        MoneyFormatter.formatWithSymbol(uiState.totalLiabilities),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    item: AccountWithBalance,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    AfuCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(item.account.colorArgb).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(item.account.iconKey),
                    contentDescription = null,
                    tint = Color(item.account.colorArgb),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.account.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = accountTypeLabel(item.account.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = MoneyFormatter.formatWithSymbol(item.balance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.balance.cents < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.BANK -> "银行卡"
    AccountType.CREDIT -> "信用卡"
}
