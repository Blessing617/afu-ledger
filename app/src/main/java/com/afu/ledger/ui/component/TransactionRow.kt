package com.afu.ledger.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.home.TransactionItem
import com.afu.ledger.ui.theme.Spacing

/**
 * 一条交易行。首页与明细页共用，保证「同一笔账在两处长得一样」。
 *
 * 行式而不是卡片：卡片每行都要一次圆角裁剪 + 间距，50 条记录视觉上就散成 50 块，
 * 信息密度低。行式把它们收进一张容器卡片，扫一眼能看更多。
 */
@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(item.categoryColorArgb)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AfuIconBadge(
            icon = categoryIcon(item.categoryIcon),
            size = 40.dp,
            iconSize = 21.dp,
            tint = accent,
            background = accent.copy(alpha = 0.16f),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 转移的标题已经写了「转出 → 转入」，副标题只留备注，不再重复账户名
            val subtitle = if (item.isTransfer) {
                item.transaction.note.ifBlank { "账户间转移" }
            } else {
                buildString {
                    append(item.accountName)
                    if (item.transaction.note.isNotBlank()) {
                        append(" · ")
                        append(item.transaction.note)
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        AmountText(
            money = item.transaction.amount,
            isExpense = item.transaction.type == TransType.EXPENSE,
            isTransfer = item.isTransfer,
        )
    }
}

/** 分组标题：日期 + 当日净额，一行胶囊，浮在背景上也读得清。 */
@Composable
fun DayHeader(
    label: String,
    netText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = netText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
