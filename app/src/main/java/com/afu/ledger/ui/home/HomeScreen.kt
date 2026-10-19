package com.afu.ledger.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.TransactionRow
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.MascotAvatar
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter

/**
 * 首页：**今天**。
 *
 * ## 信息分层
 *
 * 打开记账应用最常见的目的是两件事：看今天花了多少、记一笔。所以首页只回答
 * 「今天怎么样」，把月度汇总、月历、历史列表全部交给「明细」页：
 *
 * ```
 * 首页（今天）  →  明细（按月 / 按日）  →  报表（趋势与排行）
 * ```
 *
 * 这样首屏没有需要「滑动才知道是什么」的东西，层级也是单向的：
 * 只看今天的用户停在第一层，想翻历史的点一下就走第二层，不会来回横跳。
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddTransaction: () -> Unit,
    onCreateAccount: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onConfirmPending: (String) -> Unit = {},
    onIgnorePending: (String) -> Unit = {},
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
        item(key = "today") {
            TodayCard(uiState = uiState, onOpenHistory = onOpenHistory)
        }

        if (uiState.pendingBills.isNotEmpty()) {
            item(key = "pending") {
                PendingBillsCard(
                    bills = uiState.pendingBills,
                    onConfirm = onConfirmPending,
                    onIgnore = onIgnorePending,
                )
            }
        }

        if (!uiState.hasAccounts) {
            item(key = "no-account") {
                EmptyState(
                    title = "先创建一个账户",
                    description = "记账需要一个资产账户来归属金额",
                    action = { AfuButton(text = "创建账户", onClick = onCreateAccount) },
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        } else if (uiState.loaded && uiState.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "今天还没有花钱",
                    description = "花了就记一笔，攒起来才看得见",
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        } else {
            item(key = "list-title") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "今天的 ${uiState.items.size} 笔",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onOpenHistory)
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "全部明细",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            item(key = "today-list") {
                AfuCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    uiState.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            SettingDivider()
                        }
                        TransactionRow(
                            item = item,
                            onClick = { onTransactionClick(item.transaction.id) },
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

/**
 * 今日卡：**整体居中**。
 *
 * 居中不是为了对称，是因为这张卡只有「今天花了多少」一个主角：
 * 左对齐时右边会空出一大块，视觉重心偏在左上；居中后数字成为画面中心，
 * 吉祥物与金额各占一头，像一张小票/贴纸。
 */
@Composable
private fun TodayCard(uiState: HomeUiState, onOpenHistory: () -> Unit) {
    val style = LocalThemeStyle.current
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (style.showsMascot) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MascotAvatar(size = 26.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = greetingText(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
            }
            Text(
                text = uiState.todayLabel.ifBlank { "今天" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = expenseText(uiState.todayExpense),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "今日支出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Text(
                    text = "收入 + " + MoneyFormatter.formatWithSymbol(uiState.todayIncome),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .clickable(onClick = onOpenHistory)
                    .padding(horizontal = Spacing.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "看看这个月",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 待确认账单：识别到了、但悬浮窗没弹出来（部分系统会在支付类应用上拦截）。
 *
 * 做成紧凑条而不是大卡片：它是一条待办提示，不该和「今天花了多少」抢视觉重量。
 */
@Composable
private fun PendingBillsCard(
    bills: List<PendingBillView>,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
) {
    AfuCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.SouthEast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "${bills.size} 笔待确认",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "弹窗被系统拦截了",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        bills.forEach { bill ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (if (bill.directionLabel == "收入") "+" else "-") +
                        MoneyFormatter.formatWithSymbol(bill.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = bill.merchant.orEmpty().ifBlank { bill.source },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "忽略",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onIgnore(bill.fingerprint) }
                        .padding(horizontal = Spacing.sm, vertical = 4.dp),
                )
                AfuButton(
                    text = "记一笔",
                    onClick = { onConfirm(bill.fingerprint) },
                )
            }
            Text(
                text = bill.billTimeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 支出金额文案：非零时带负号，零时只显示 ¥0.00（「-¥0.00」很怪）。 */
private fun expenseText(money: Money): String {
    val text = MoneyFormatter.formatWithSymbol(money)
    return if (money.cents == 0L) text else "-$text"
}

/** 大肥鱼主题下的一句问候：让首页像「有人在家」，而不是一块仪表盘。 */
private fun greetingText(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 5 -> "这么晚还没睡呀～"
        hour < 11 -> "早上好呀，今天也要好好记账"
        hour < 14 -> "中午好呀，吃饭了喵？"
        hour < 19 -> "下午好呀，今天花了多少呀"
        else -> "晚上好呀，今天过得怎么样"
    }
}
