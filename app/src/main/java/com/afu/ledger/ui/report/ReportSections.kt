package com.afu.ledger.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import kotlin.math.roundToInt

/** 支出趋势：单日最高 / 平均每日 / 笔数 + 折线图。 */
@Composable
internal fun TrendSection(report: MonthlyReport) {
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("支出趋势")
        Spacer(Modifier.height(Spacing.md))

        Text(
            text = if (report.maxDayExpenseCents > 0) {
                "单日最高：${report.maxDayExpenseDay} 日 · " +
                    MoneyFormatter.formatWithSymbol(Money(report.maxDayExpenseCents))
            } else {
                "本月还没有支出"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.sm))
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricColumn(
                label = "平均每日",
                value = MoneyFormatter.formatWithSymbol(Money(report.averageDailyExpenseCents)),
                modifier = Modifier.weight(1f),
            )
            MetricColumn(
                label = "支出笔数",
                value = "${report.expenseCount} 笔",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.md))
        TrendChart(
            points = report.trend,
            lineColor = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("01", "05", "10", "15", "20", "25", "末").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 分类占比：环形图 + 图例。 */
@Composable
internal fun CategoryShareSection(report: MonthlyReport) {
    if (report.slices.isEmpty()) return
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("支出占比")
        Spacer(Modifier.height(Spacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                DonutChart(
                    slices = report.slices,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "合计",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = MoneyFormatter.formatWithSymbol(Money(report.expenseCents)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                report.slices.take(MAX_LEGEND).forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(slice.colorArgb)),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = slice.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(slice.ratio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (report.slices.size > MAX_LEGEND) {
                    Text(
                        text = "还有 ${report.slices.size - MAX_LEGEND} 个分类",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 分类排行：图标 + 名称 + 占比条 + 金额与笔数。 */
@Composable
internal fun CategoryRankSection(report: MonthlyReport) {
    if (report.slices.isEmpty()) return
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("分类排行")
        Spacer(Modifier.height(Spacing.md))
        report.slices.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(iconKey = slice.iconKey, colorArgb = slice.colorArgb)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = slice.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = MoneyFormatter.formatYuan(slice.amountCents),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    RatioBar(ratio = slice.ratio, color = Color(slice.colorArgb))
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "${slice.count} 笔 · 占 ${(slice.ratio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 单笔支出排行。 */
@Composable
internal fun TopTransactionSection(report: MonthlyReport) {
    if (report.topTransactions.isEmpty()) return
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("单笔支出排行")
        Spacer(Modifier.height(Spacing.md))
        report.topTransactions.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(iconKey = item.iconKey, colorArgb = item.colorArgb, size = 32)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = buildString {
                            append("${item.dayOfMonth} 日")
                            if (item.note.isNotBlank()) {
                                append(" · ")
                                append(item.note)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "- " + MoneyFormatter.formatYuan(item.amountCents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private const val MAX_LEGEND = 5
