package com.afu.ledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column


import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * 轻量信息芯片：日期、备注、时间都用它。
 *
 * 记账面板与定时记账 / 预算面板 MUST 用同一份实现 —— 之前定时记账用的是另一种圆角胶囊，
 * 放在一起看就是「两个 App 的控件」。
 */
@Composable
fun MetaChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}

/** 与记账面板同款的日期选择：自绘月历面板（与明细页月历同一套视觉）。 */
@Composable
fun DatePickerSheet(
    initialSelectedMillis: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneOffset.UTC
    var selected by remember {
        mutableStateOf(
            Instant.ofEpochMilli(initialSelectedMillis).atZone(zone).toLocalDate(),
        )
    }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val today = LocalDate.now()
    val style = LocalThemeStyle.current
    val swallow = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ModalScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = style.containerCorner,
                            topEnd = style.containerCorner,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = swallow,
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一月")
                    }
                    Text(
                        text = "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "下一月")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 周一打头：lead 是本月 1 号之前要空出的格子数
                val lead = (month.atDay(1).dayOfWeek.value + 6) % 7
                val days = month.lengthOfMonth()
                (0 until 6).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { weekday ->
                            val day = week * 7 + weekday - lead + 1
                            if (day in 1..days) {
                                val date = month.atDay(day)
                                val isSelected = date == selected
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .then(
                                            if (isToday && !isSelected) {
                                                Modifier.border(
                                                    1.2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable { selected = date },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f).size(40.dp))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            selected = today
                            month = YearMonth.from(today)
                        },
                    ) { Text("今天") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            onPick(selected.toEpochDay() * 86_400_000L)
                            onDismiss()
                        },
                    ) { Text("确定", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** 与整体风格一致的时间选择：时 / 分两列滚轮，而不是 Material 默认表盘。 */
@Composable
fun TimePickerSheet(
    initialHour: Int,
    initialMinute: Int,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    val style = LocalThemeStyle.current
    val swallow = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ModalScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = style.containerCorner,
                            topEnd = style.containerCorner,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = swallow,
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            ) {
                Text(
                    text = "选择时间",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberWheel(range = 0..23, selected = hour, onSelected = { hour = it })
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                    NumberWheel(range = 0..59, selected = minute, onSelected = { minute = it })
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            onPick(hour, minute)
                            onDismiss()
                        },
                    ) { Text("确定", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** 滚轮：上下滑动选数字，中心项高亮；滚停后回调当前值。 */
@Composable
private fun NumberWheel(
    range: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val values = range.toList()
    // 前后各垫两个空白项，让首尾数字也能滚到中心。
    // 这样「第一个可见项索引」恰好就是中心数字的索引，不用再做偏移换算，
    // 初始位置也不会因为 padding 与索引的口径不同而错位。
    val wheelItems: List<Int?> = remember(values) {
        List<Int?>(2) { null } + values + List<Int?>(2) { null }
    }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selected.coerceIn(0, values.lastIndex),
    )
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val itemHeight = 40.dp

    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val center = state.firstVisibleItemIndex.coerceIn(0, values.lastIndex)
            onSelected(values[center])
        }
    }

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(itemHeight * 5),
        contentAlignment = Alignment.Center,
    ) {
        // 中心高亮圈：放在列表下层，文字始终可读
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(CapsuleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(wheelItems.size) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    wheelItems[index]?.let { value ->
                        Text(
                            text = "%02d".format(value),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 数字输入弹层：点步进器的数值打开，直接用**系统数字键盘**输入。
 *
 * 不做数字网格：1–99 铺成一片既密又难认，31 天也要扫两遍才找到。
 * 键盘输入一次到位；输入超范围时确定按钮不可用。
 */
@Composable
fun NumberInputSheet(
    title: String,
    initial: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialText = initial.toString()
    // 打开就全选：用户点数值是为了改成别的数，而不是在原来的值后面续写
    var input by remember {
        mutableStateOf(
            TextFieldValue(initialText, selection = TextRange(0, initialText.length)),
        )
    }
    val parsed = input.text.toIntOrNull()
    val valid = parsed != null && parsed in range
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val style = LocalThemeStyle.current
    val swallow = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        // 打开即聚焦并弹键盘：用户点数值的目的就是输入
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ModalScrim)
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = style.containerCorner,
                            topEnd = style.containerCorner,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = swallow,
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                BasicTextField(
                    value = input,
                    onValueChange = { candidate ->
                        // 只接受数字，最多 4 位（999 期）；范围由 valid 校验
                        if (candidate.text.length <= 4 && candidate.text.all { it.isDigit() }) {
                            input = candidate
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Text(
                    text = "范围 ${range.first}–${range.last}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        enabled = valid,
                        onClick = {
                            parsed?.let(onConfirm)
                            onDismiss()
                        },
                    ) { Text("确定", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
