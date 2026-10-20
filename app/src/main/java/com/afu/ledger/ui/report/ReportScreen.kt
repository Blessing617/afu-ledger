package com.afu.ledger.ui.report

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.YearMonth

/** 收支报表：一个月花了多少、花在哪、趋势如何。 */
@Composable
fun ReportScreen(
    uiState: ReportUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = uiState.report
    // 必须给整页一层不透明底色：二级页面是叠在标签页之上滑入的，
    // 没有底色时首页的内容会从报表页背后透出来（真机上第一版就是这样）。
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ReportTopBar(onBack = onBack)

        if (report == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在整理…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "month-switcher") {
                MonthSwitcher(
                    yearMonth = report.yearMonth,
                    canGoNext = uiState.canGoNext,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    onCurrentMonth = onCurrentMonth,
                )
            }

            item(key = "balance") { BalanceCard(report) }

            if (report.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        title = "这个月还没有记录",
                        description = "${report.yearMonth.monthValue} 月还没有任何收支，换个有数据的月份看看吧",
                    )
                }
            } else {
                item(key = "trend") { TrendSection(report) }
                item(key = "donut") { CategoryShareSection(report) }
                item(key = "rank") { CategoryRankSection(report) }
                item(key = "top") { TopTransactionSection(report) }
            }
        }
    }
}

@Composable
private fun ReportTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = "收支报表",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MonthSwitcher(
    yearMonth: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrentMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月")
        }
        Text(
            text = "${yearMonth.year} 年 ${yearMonth.monthValue} 月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "下个月",
                tint = if (canGoNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                },
            )
        }
        if (canGoNext) {
            Box(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .clickableCapsule(onCurrentMonth),
            ) {
                Text(
                    text = "本月",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(report: MonthlyReport) {
    val primary = MaterialTheme.colorScheme.primary
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = 0.16f), primary.copy(alpha = 0.04f)),
                    ),
                )
                .padding(Spacing.lg),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "本月结余",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "上月结余 ${MoneyFormatter.formatWithSymbol(Money(report.previousBalanceCents))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = MoneyFormatter.formatWithSymbol(Money(report.balanceCents)),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                )
                Spacer(Modifier.height(Spacing.md))
                AmountRow("本月支出", report.expenseCents, isExpense = true)
                Spacer(Modifier.height(Spacing.xs))
                AmountRow("本月收入", report.incomeCents, isExpense = false)
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, cents: Long, isExpense: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .height(8.dp)
                .width(if (isExpense) 56.dp else 40.dp)
                .clip(CapsuleShape)
                .background(
                    if (isExpense) {
                        Color(0xFFFFB74D)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = (if (isExpense) "- " else "+ ") + MoneyFormatter.formatWithSymbol(Money(cents)),
            style = MaterialTheme.typography.titleMedium,
            color = if (isExpense) {
                MaterialTheme.colorScheme.onSurface
            } else {
                Color(0xFF2E9E5B)
            },
        )
    }
}

/** 胶囊上的点击。 */
private fun Modifier.clickableCapsule(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/** 圆形图标底盘：报表里多处复用同一视觉。 */
@Composable
internal fun CategoryBadge(iconKey: String, colorArgb: Long, size: Int = 36) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(colorArgb).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = com.afu.ledger.ui.component.categoryIcon(iconKey),
            contentDescription = null,
            tint = Color(colorArgb),
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

/** 报表里统一的小标题。 */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 动效常量对外暴露一份，避免各 section 各写一个时长。 */
internal val ReportMotion = AfuMotion
