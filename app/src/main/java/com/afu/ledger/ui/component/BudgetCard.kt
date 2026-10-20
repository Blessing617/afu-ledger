package com.afu.ledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PieChart
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
import androidx.compose.ui.unit.sp
import com.afu.ledger.domain.budget.BudgetAlertLevel
import com.afu.ledger.domain.budget.BudgetPeriod
import com.afu.ledger.domain.budget.BudgetProgress
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.LocalDate

/** 预警档位对应的主色。 */
private fun BudgetAlertLevel.color(): Color = when (this) {
    BudgetAlertLevel.SAFE -> Color(0xFF4C9AFF)
    BudgetAlertLevel.NOTICE -> Color(0xFF7C6CF0)
    BudgetAlertLevel.WARNING -> Color(0xFFFF9800)
    BudgetAlertLevel.OVER -> Color(0xFFE5484D)
}

/**
 * 档位文案。
 *
 * 语气按人设走：正常时轻松、临界时提醒、超支时自嘲 —— 不吓人，但一眼能看懂。
 */
private fun BudgetAlertLevel.message(): String = when (this) {
    BudgetAlertLevel.SAFE -> "余量很足，放心花～"
    BudgetAlertLevel.NOTICE -> "花掉一半多啦，本鲸鱼娘帮你盯着"
    BudgetAlertLevel.WARNING -> "快到上限了！再花就要吃白饭了喵"
    BudgetAlertLevel.OVER -> "超支了…这月白饭配咸菜吧"
}

/**
 * 首页预算卡片。
 *
 * 只回答一个问题：**这一期还能花多少**。所以剩余金额最大、进度条第二、
 * 其余信息（周期、已用、额度）压在底部小字里。
 */
@Composable
fun BudgetCard(
    progress: BudgetProgress,
    today: LocalDate = LocalDate.now(),
    onClick: () -> Unit,
) {
    val level = progress.alertLevel
    val accent = level.color()
    val isOver = progress.remainingCents < 0
    val periodText = BudgetPeriod.describe(progress.budget, progress.window, today)

    AfuCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PieChart,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.budget.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = periodText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .padding(horizontal = Spacing.xs, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "预算",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (isOver) "超支 " else "还能花 ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormatter.formatWithSymbol(
                    Money(
                        if (isOver) -progress.remainingCents else progress.remainingCents
                    )
                ),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // 进度条：超支时封顶满格，但颜色与百分比仍如实反映
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CapsuleShape)
                .background(accent.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.barFraction)
                    .height(8.dp)
                    .clip(CapsuleShape)
                    .background(accent),
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "已用 ${MoneyFormatter.formatWithSymbol(Money(progress.usedCents))} / " +
                    MoneyFormatter.formatWithSymbol(progress.budget.amount) + " · " + progress.percentText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = level.message(),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
        )
    }
}
