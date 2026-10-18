package com.afu.ledger.auto.overlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.rememberMotionEnabled
import com.afu.ledger.ui.theme.Spacing

/** 识别成功后的确认卡片。确认前不会写入任何交易。 */
@Composable
fun PaymentConfirmContent(
    amountText: String,
    merchant: String?,
    accounts: List<Account>,
    categories: List<Category>,
    initialAccountId: Long,
    initialCategoryId: Long,
    errorText: String?,
    onConfirm: (accountId: Long, categoryId: Long) -> Unit,
    onIgnore: () -> Unit,
) {
    var accountId by remember { mutableLongStateOf(initialAccountId) }
    var categoryId by remember { mutableLongStateOf(initialCategoryId) }

    OverlayCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "自动记账",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onIgnore) { Text("忽略") }
        }

        Text(text = amountText, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = merchant?.takeIf { it.isNotBlank() } ?: "未识别到商户",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.md))
        LabeledChips(
            label = "账户",
            items = accounts.map { it.id to it.name },
            selectedId = accountId,
            onSelect = { accountId = it },
        )
        LabeledChips(
            label = "分类",
            items = categories.map { it.id to it.name },
            selectedId = categoryId,
            onSelect = { categoryId = it },
        )

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        AfuButton(
            text = "记一笔",
            onClick = { onConfirm(accountId, categoryId) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 没有可用账户时的引导卡片。 */
@Composable
fun NoAccountCard(
    amountText: String,
    onOpenApp: () -> Unit,
    onIgnore: () -> Unit,
) {
    OverlayCard {
        Text(
            text = "先建一个账户",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "识别到 $amountText，但还没有可用的资产账户，创建后才能入账。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = onIgnore) { Text("忽略") }
            AfuButton(text = "去创建", onClick = onOpenApp)
        }
    }
}

/** 悬浮窗窗口是 MATCH_PARENT 宽，这里把固定宽度的卡片居中。 */
@Composable
private fun OverlayCard(content: @Composable () -> Unit) {
    val motion = rememberMotionEnabled()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val offsetY by animateDpAsState(
        targetValue = if (appeared) 0.dp else 48.dp,
        animationSpec = if (motion) tween(260) else snap(),
        label = "overlaySlide",
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (motion) tween(200) else snap(),
        label = "overlayFade",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
            .graphicsLayer {
                translationY = offsetY.toPx()
                this.alpha = alpha
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(modifier = Modifier.width(CARD_WIDTH)) {
            AfuCard(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun LabeledChips(
    label: String,
    items: List<Pair<Long, String>>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // 默认项可能在列表末尾（分类「其他」），不滚过去用户看不到自己被选了
    val selectedIndex = items.indexOfFirst { it.first == selectedId }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0),
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = items, key = { it.first }) { item ->
            FilterChip(
                selected = item.first == selectedId,
                onClick = { onSelect(item.first) },
                label = { Text(item.second) },
            )
        }
    }
}

private val CARD_WIDTH = 320.dp
