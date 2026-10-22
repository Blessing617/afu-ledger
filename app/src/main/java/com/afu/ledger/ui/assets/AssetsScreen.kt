package com.afu.ledger.ui.assets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.afu.ledger.domain.model.AccountType
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AfuIconBadge
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.SettingRow
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import kotlin.math.roundToInt

/**
 * 资产页。
 *
 * 结构：净资产卡 → 账户列表（一张卡里的行）→ 新增账户入口。
 * 之前每个账户一张独立卡片、下面一大片空白，视觉上像「没加载完」；
 * 现在账户收进同一张卡，信息密度上来了，页面也不再空。
 */
@Composable
fun AssetsScreen(
    uiState: AssetsUiState,
    onAccountClick: (AccountWithBalance) -> Unit,
    onAddAccount: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onMoveAccount: (Int, Int) -> Unit,
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
            item(key = "net-worth") { NetWorthCard(uiState, onTogglePrivacy) }

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
                        ReorderableAccountList(
                            items = uiState.active,
                            hidden = uiState.amountHidden,
                            onClick = onAccountClick,
                            onMove = onMoveAccount,
                        )
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
        }

        // 新增账户入口放在内容里：净资产卡右上角 + 列表末尾各一个。
        // 之前是常驻悬浮按钮，滚动时一直压在列表上，也容易和底部中央的记账按钮混淆。
    }
}

/**
 * 账户列表：长按拖动排序。
 *
 * 不用第三方 reorderable 库：账户通常只有几个，`forEach + detectDragGesturesAfterLongPress`
 * 就够用，而且能精确控制「抬起、让位、落位」三件事。
 *
 * 行高用第一行测量值：所有行结构相同（同 padding、同图标尺寸），等高是稳定假设；
 * 拿不到测量值时拖动只跟手不换位，松手也不会误排。
 */
@Composable
private fun ReorderableAccountList(
    items: List<AccountWithBalance>,
    hidden: Boolean,
    onClick: (AccountWithBalance) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) SettingDivider()

            val from = draggingIndex
            val target = if (from == null || rowHeightPx <= 0f) {
                from
            } else {
                (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, items.lastIndex)
            }

            // 让位方向：往下拖时，被掠过的行向上让；往上拖时相反
            val neighborOffset = when {
                from == null || target == null -> 0f
                index in (from + 1)..target -> -rowHeightPx
                index in target until from -> rowHeightPx
                else -> 0f
            }
            val animatedNeighbor by animateFloatAsState(
                targetValue = if (from == null) 0f else neighborOffset,
                animationSpec = AfuMotion.settle(),
                label = "account-shift",
            )
            val translation = if (index == from) dragOffset else animatedNeighbor
            val lifted = index == from

            AccountRow(
                item = item,
                hidden = hidden,
                onClick = { onClick(item) },
                modifier = Modifier
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer {
                        translationY = translation
                        val scale = if (lifted) 1.02f else 1f
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = if (lifted) 12f else 0f
                    }
                    .onGloballyPositioned { coordinates ->
                        if (rowHeightPx <= 0f) rowHeightPx = coordinates.size.height.toFloat()
                    }
                    .pointerInput(item.account.id, index, items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                            },
                            onDragEnd = {
                                val start = draggingIndex
                                if (start != null && rowHeightPx > 0f) {
                                    val to = (start + (dragOffset / rowHeightPx).roundToInt())
                                        .coerceIn(0, items.lastIndex)
                                    if (to != start) onMove(start, to)
                                }
                                draggingIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                        )
                    },
            )
        }
    }
}

/**
 * 隐私遮罩金额：货币符号 + 一排圆点。
 *
 * 为什么用绘制而不是 `*` 字形：
 * - 星号在大多数字体里位置偏上，34sp 下和基线错位，看起来像两行
 * - 字形宽度受字体影响，圆点可以直接按字号算大小与间距，想大就大、对齐稳定
 * - 圆点垂直居中、等距，观感更圆润，也符合「清爽可爱」的基调
 *
 * 具体账户行只画 3 个点、不重复货币符号，用户要的就是「意思一下」。
 */
@Composable
private fun MaskedAmount(
    money: Money,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight = FontWeight.Bold,
    showCurrency: Boolean = true,
    dotCount: Int = 4,
    modifier: Modifier = Modifier,
) {
    val dotSize = (fontSize.value * 0.26f).dp
    val dotGap = (fontSize.value * 0.18f).dp
    val lineHeight = (fontSize.value * 1.25f).dp

    Row(
        modifier = modifier.defaultMinSize(minHeight = lineHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCurrency) {
            if (money.cents < 0) {
                Text(
                    text = "−",
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = color,
                )
            }
            Text(
                text = "¥",
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
            )
            Spacer(Modifier.width((fontSize.value * 0.12f).dp))
        }
        repeat(dotCount) { index ->
            if (index > 0) Spacer(Modifier.width(dotGap))
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** 完整金额可能很长（万亿级 21 个字符），按长度降字号，绝不裁切。 */
private fun amountFontSize(text: String): TextUnit = when {
    text.length <= 13 -> 16.sp
    text.length <= 17 -> 14.sp
    else -> 12.sp
}

@Composable
private fun NetWorthCard(uiState: AssetsUiState, onTogglePrivacy: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary

    // 金额一律完整显示；大额靠字号与折行解决，不用「万 / 亿」缩写。
    // 点按不再「展开更多数字」，而是切换隐私遮罩（用户语义：遮挡，而不是查看）。
    val netWorthFull = MoneyFormatter.formatWithSymbol(uiState.netWorth)
    val netWorthFont = netWorthFontSize(netWorthFull)

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
                        if (uiState.amountHidden) {
                            MaskedAmount(
                                money = uiState.netWorth,
                                fontSize = 30.sp,
                                color = primary,
                                modifier = Modifier.clickable(onClick = onTogglePrivacy),
                            )
                        } else {
                            Text(
                                text = netWorthFull,
                                // 完整数字可能很长：按长度降字号，允许两行，绝不裁切
                                fontSize = netWorthFont,
                                lineHeight = netWorthFont * 1.15f,
                                fontWeight = FontWeight.Bold,
                                color = primary,
                                maxLines = 2,
                                modifier = Modifier.clickable(onClick = onTogglePrivacy),
                            )
                        }
                        Text(
                            text = if (uiState.amountHidden) "点一下显示金额" else "点一下隐藏金额",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable(onClick = onTogglePrivacy),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    MiniMetric(
                        label = "总资产",
                        money = uiState.totalAssets,
                        hidden = uiState.amountHidden,
                        modifier = Modifier.weight(1f),
                    )
                    MiniMetric(
                        label = "总负债",
                        money = uiState.totalLiabilities,
                        hidden = uiState.amountHidden,
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 净资产主数字的字号：越长越小，保证在卡片宽度内完整可读（折行时也不撑破卡片）。 */
private fun netWorthFontSize(text: String): TextUnit = when {
    text.length <= 15 -> 34.sp
    text.length <= 20 -> 26.sp
    else -> 20.sp
}

@Composable
private fun MiniMetric(
    label: String,
    money: Money,
    hidden: Boolean,
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
        if (hidden) {
            MaskedAmount(
                money = money,
                fontSize = 15.sp,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            val text = MoneyFormatter.formatWithSymbol(money)
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontSize = amountFontSize(text),
                color = valueColor,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AccountRow(
    item: AccountWithBalance,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(item.account.colorArgb)
    val balanceText = MoneyFormatter.formatWithSymbol(item.balance)
    val balanceColor = if (item.balance.cents < 0) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AfuIconBadge(
            icon = accountIcon(item.account.iconKey),
            size = 40.dp,
            iconSize = 21.dp,
            tint = accent,
            background = accent.copy(alpha = 0.16f),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.account.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = accountTypeLabel(item.account.type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        if (hidden) {
            MaskedAmount(
                money = item.balance,
                fontSize = 15.sp,
                color = balanceColor,
                fontWeight = FontWeight.SemiBold,
                showCurrency = false,
                dotCount = 3,
            )
        } else {
            Text(
                text = balanceText,
                style = MaterialTheme.typography.titleMedium,
                fontSize = amountFontSize(balanceText),
                fontWeight = FontWeight.SemiBold,
                color = balanceColor,
                maxLines = 2,
            )
        }
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.WECHAT -> "微信"
    AccountType.ALIPAY -> "支付宝"
    AccountType.BANK -> "银行卡"
    AccountType.CASH -> "现金"
    AccountType.CREDIT -> "信用卡"
}
