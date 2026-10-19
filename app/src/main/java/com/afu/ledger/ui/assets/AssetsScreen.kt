package com.afu.ledger.ui.assets

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.unit.sp
import com.afu.ledger.domain.model.AccountType
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AfuIconBadge
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.SettingRow
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter

/**
 * 资产页。
 *
 * 结构：净资产卡 → 账户列表（一张卡里的行）→ 归档分组。
 * 之前每个账户一张独立卡片、下面一大片空白，视觉上像「没加载完」；
 * 现在账户收进同一张卡，信息密度上来了，页面也不再空。
 */
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
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "net-worth") { NetWorthCard(uiState) }

            if (uiState.loaded && uiState.active.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "还没有账户",
                        description = "创建一个现金或银行卡账户，就能开始记账了",
                        icon = Icons.Rounded.AccountBalanceWallet,
                    )
                }
            }

            if (uiState.active.isNotEmpty()) {
                item(key = "active-list") {
                    AfuCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        uiState.active.forEachIndexed { index, item ->
                            if (index > 0) SettingDivider()
                            AccountRow(item = item, onClick = { onAccountClick(item) })
                        }
                    }
                }
            }

            item(key = "add-account") {
                AfuCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    SettingRow(
                        icon = Icons.Rounded.Add,
                        title = "新增账户",
                        onClick = onAddAccount,
                    )
                }
            }

            if (uiState.archived.isNotEmpty()) {
                item(key = "archived-title") {
                    Text(
                        text = "已归档",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm),
                    )
                }
                item(key = "archived-list") {
                    AfuCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        uiState.archived.forEachIndexed { index, item ->
                            if (index > 0) SettingDivider()
                            AccountRow(
                                item = item,
                                onClick = { onAccountClick(item) },
                                dimmed = true,
                            )
                        }
                    }
                }
            }
        }

        // 新增账户入口放在内容里：净资产卡右上角 + 列表末尾各一个。
        // 之前是常驻悬浮按钮，滚动时一直压在列表上，也容易和底部中央的记账按钮混淆。
    }
}

@Composable
private fun NetWorthCard(uiState: AssetsUiState) {
    val primary = MaterialTheme.colorScheme.primary
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = 0.20f), primary.copy(alpha = 0.03f)),
                    ),
                )
                .padding(Spacing.lg),
        ) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "净资产",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = MoneyFormatter.formatWithSymbol(uiState.netWorth),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    MiniMetric(
                        label = "总资产",
                        value = MoneyFormatter.formatWithSymbol(uiState.totalAssets),
                        modifier = Modifier.weight(1f),
                    )
                    MiniMetric(
                        label = "总负债",
                        value = MoneyFormatter.formatWithSymbol(uiState.totalLiabilities),
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountRow(
    item: AccountWithBalance,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    val accent = Color(item.account.colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AfuIconBadge(
            icon = accountIcon(item.account.iconKey),
            size = 40.dp,
            iconSize = 21.dp,
            tint = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            background = (if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else accent)
                .copy(alpha = 0.16f),
        )
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

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.WECHAT -> "微信"
    AccountType.ALIPAY -> "支付宝"
    AccountType.BANK -> "银行卡"
    AccountType.CASH -> "现金"
    AccountType.CREDIT -> "信用卡"
}
