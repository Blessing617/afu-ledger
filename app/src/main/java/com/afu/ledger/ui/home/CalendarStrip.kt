package com.afu.ledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
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

/** 格子高度。两行文本（日期 + 完整金额）要放得下，极端大额折行时也不溢出。 */
private val CellHeight = 52.dp

/**
 * 首页与明细页共用的月历。
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

    AfuCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
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
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一月", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onMonthChange(yearMonth.plusMonths(1)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下一月", modifier = Modifier.size(18.dp))
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
                        Spacer(modifier = Modifier.weight(1f).height(CellHeight))
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
    val hasAmount = summary != null && summary.hasData
    val amountColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
        summary != null && summary.netCents < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        else -> Color(0xFF2E9E5B)
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .height(CellHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = dayColor,
            )
            if (hasAmount) {
                CalendarDayAmount(
                    cents = summary.netCents,
                    color = amountColor,
                )
            }
        }
    }
}

/**
 * 格子里的当日净额：**完整数字**，靠字号适配宽度。
 *
 * 这里以前是 `compactNet`：≥10000 元缩成「k」且用整数除法（17,165.50 → +1k），
 * ≥100 元直接截断小数（163.45 → -163）。用户看到日历和明细对不上，以为记账丢了数据。
 *
 * 现在的规则与资产页一致：数字保持完整（符号 + 千分位 + 两位小数），
 * 由布局负责放下它 —— 先实测能容纳的最大字号（10.5sp → 6sp），
 * 只有缩到下限仍放不下（万亿级）才允许在两行内折行，绝不裁切、绝不省略号。
 */
@Composable
private fun CalendarDayAmount(cents: Long, color: Color) {
    val text = MoneyFormatter.formatSignedYuan(cents)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
    // 逗号后插入零宽空格：正常字号下没有任何视觉差异；
    // 只有缩到最小字号仍放不下时，折行才会落在逗号后，而不是把数字切断成两截。
    val breakable = remember(text) { text.replace(",", ",\u200B") }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val fontSize = remember(text, maxWidthPx, baseStyle) {
            fittedFontSize(measurer, text, baseStyle, maxWidthPx)
        }
        Text(
            text = breakable,
            modifier = Modifier.fillMaxWidth(),
            style = baseStyle,
            fontSize = fontSize,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

private const val AmountMaxFontSize = 10.5f
private const val AmountMinFontSize = 6f

/**
 * 逐档实测能完整放下的字号。
 *
 * 档位只有十来档，直接量比估算字符宽度可靠：中文逗号、负号、冒号字宽都不同，
 * 按字符数硬编码会在窄屏（360dp）上算错一档，正是这次要修的问题。
 */
private fun fittedFontSize(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
): TextUnit {
    if (maxWidthPx <= 0) return AmountMaxFontSize.sp
    var size = AmountMaxFontSize
    while (size > AmountMinFontSize) {
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = style.copy(fontSize = size.sp),
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        if (!layout.didOverflowWidth) return size.sp
        size -= 0.5f
    }
    return AmountMinFontSize.sp
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

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
