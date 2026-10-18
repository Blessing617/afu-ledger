package com.afu.ledger.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/** 某一天的收支概览，日历格子里显示净额。 */
data class DaySummary(
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
) {
    val netCents: Long get() = incomeCents - expenseCents
    val hasData: Boolean get() = expenseCents != 0L || incomeCents != 0L
}

/**
 * 首页月历。
 *
 * 为什么要有它：原先首页只有「按日分组的长列表」，用户想知道「这个月哪天花了钱」
 * 只能一直滑。月历把这个问题变成一眼可见，并且点一下就能筛出那天的账。
 *
 * 实现上刻意保持「纯展示 + 回调」：月份与选中日期都由 ViewModel 持有，
 * 组件内部不存状态 —— 否则切标签页回来时日历会自己跳回今天。
 */
@Composable
fun CalendarStrip(
    yearMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    days: Map<Int, DaySummary>,
    onSelectDate: (LocalDate?) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${yearMonth.year} 年 ${yearMonth.monthValue} 月",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (YearMonth.from(today) != yearMonth) {
                TodayChip(onClick = { onMonthChange(YearMonth.from(today)) })
                Spacer(Modifier.size(Spacing.xs))
            }
            IconButton(
                onClick = { onMonthChange(yearMonth.minusMonths(1)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一月", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onMonthChange(yearMonth.plusMonths(1)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下一月", modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        // 7 列网格。用 Row + weight 而不是 LazyVerticalGrid：
        // 外层已经是 LazyColumn，嵌套可滚动网格会带来测量与滚动冲突。
        val leadingBlanks = yearMonth.atDay(1).dayOfWeek.value - 1
        val cells = leadingBlanks + yearMonth.lengthOfMonth()
        val rows = (cells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cellIndex = row * 7 + column
                    val day = cellIndex - leadingBlanks + 1
                    if (day < 1 || day > yearMonth.lengthOfMonth()) {
                        Spacer(modifier = Modifier.weight(1f).height(46.dp))
                    } else {
                        val date = yearMonth.atDay(day)
                        DayCell(
                            day = day,
                            summary = days[day],
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            selectedColor = primary,
                            onClick = { onSelectDate(if (date == selectedDate) null else date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    summary: DaySummary?,
    isToday: Boolean,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> selectedColor
        isToday -> selectedColor.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val dayColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = dayColor,
            )
            if (summary != null && summary.hasData) {
                Text(
                    text = compactNet(summary.netCents),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        summary.netCents < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        else -> Color(0xFF2E9E5B)
                    },
                )
            }
        }
    }
}

@Composable
private fun TodayChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = "今天",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 格子里的金额要短。
 *
 * 规则：100 元以内保留一位小数（-23.9），100 元以上取整（-139），
 * 一万元以上用 k（-17k）。格子只有半个指甲盖大，字符一多就被裁掉。
 */
private fun compactNet(cents: Long): String {
    val abs = kotlin.math.abs(cents)
    val sign = if (cents < 0) "-" else "+"
    return when {
        abs >= 10_000_00 -> sign + (abs / 10_000_00) + "k"
        abs >= 100_00 -> sign + (abs / 100)
        else -> {
            val yuan = abs / 100.0
            val text = if (yuan == yuan.toLong().toDouble()) {
                yuan.toLong().toString()
            } else {
                String.format(java.util.Locale.CHINA, "%.1f", yuan)
            }
            sign + text
        }
    }
}

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
