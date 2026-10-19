package com.afu.ledger.auto.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.IconChoiceRow
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.component.rememberMotionEnabled
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.Spacing

/**
 * 识别成功后的确认卡片。确认前不会写入任何交易。
 *
 * ## 为什么账户与分类要带图标
 *
 * 这张卡片弹出时用户正在支付页面，注意力很短。之前账户与分类都是纯文字 chip：
 * 「WeChat」「餐饮」「交通」…… 要逐字读才能确认选对了。加上图标后，
 * 「圆碗 = 餐饮」「蓝底钱包 = 微信」可以扫一眼完成校对。
 */
@Composable
fun PaymentConfirmContent(
    amountText: String,
    merchant: String?,
    sourceLabel: String,
    /** 识别出的收支方向；用户在卡片上可以改。 */
    directionLabel: String,
    /** 账单时间（页面上的交易时间），不是识别时刻。 */
    billTimeText: String,
    accounts: List<Account>,
    categories: List<Category>,
    initialAccountId: Long,
    initialCategoryId: Long,
    errorText: String?,
    onDirectionChange: () -> Unit,
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
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "· $sourceLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onIgnore) { Text("忽略") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 方向可点：识别错了方向比识别错金额更隐蔽，给一个当场纠正的入口
            Row(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onDirectionChange)
                    .padding(horizontal = Spacing.sm, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = directionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "切换收支方向",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = amountText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(
            text = merchant?.takeIf { it.isNotBlank() } ?: "未识别到商户",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            // 明确显示账单时间：用户能一眼看出「这是昨天那笔」而不是「刚记的」
            text = "账单时间 $billTimeText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.sm))
        // 卡片只有 320dp 宽，图标条横向滚动
        Column {
            Text(
                text = "账户",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            IconChoiceRow(
                items = accounts,
                selectedId = accountId,
                idOf = { it.id },
                labelOf = { it.name },
                iconOf = { accountIcon(it.iconKey) },
                colorOf = { Color(it.colorArgb) },
                onSelect = { accountId = it.id },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Column {
            Text(
                text = "分类",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            IconChoiceRow(
                items = categories,
                selectedId = categoryId,
                idOf = { it.id },
                labelOf = { it.name },
                iconOf = { categoryIcon(it.iconKey) },
                colorOf = { Color(it.colorArgb) },
                onSelect = { categoryId = it.id },
            )
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(Spacing.md))
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
        Spacer(Modifier.height(Spacing.md))
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

    // 不要用 `by` 解包：那会在组合阶段读状态，动画每帧都会让整张卡片重组。
    // 保持 State 引用、在 graphicsLayer 的 lambda 里读，动画就只在 Draw 阶段生效。
    //
    // 用 spring 而不是 tween：悬浮窗是从下方「弹出来」的，弹簧的落位感
    // 比匀速位移自然；同时叠加一点缩放，让它像从底部生长出来而不是平移过来。
    val progress = animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (motion) AfuMotion.settle() else snap(),
        label = "overlayProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
            .graphicsLayer {
                val p = progress.value
                translationY = (1f - p) * OVERLAY_RISE_DP.toPx()
                val scale = OVERLAY_SCALE_FROM + (1f - OVERLAY_SCALE_FROM) * p
                scaleX = scale
                scaleY = scale
                alpha = p
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

private val CARD_WIDTH = 320.dp

/** 悬浮窗出现时的上浮距离。 */
private val OVERLAY_RISE_DP = 56.dp

/** 悬浮窗出现时的起始缩放：略小，配合上浮形成「从底部生长」的观感。 */
private const val OVERLAY_SCALE_FROM = 0.92f
