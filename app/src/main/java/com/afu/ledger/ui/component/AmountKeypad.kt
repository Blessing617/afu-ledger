package com.afu.ledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.Spacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 退格键的 key 值。 */
const val AMOUNT_KEY_BACKSPACE = "⌫"

/** 主操作键的 key 值（记账面板是「记账 / 保存」，预算面板是「保存」）。 */
const val AMOUNT_KEY_SAVE = "保存"

/** 长按连删：第一次重复前的等待，与逐次加速的步长 / 最短间隔（毫秒）。 */
private const val REPEAT_FIRST_DELAY_MS = 380L
private const val REPEAT_ACCELERATE_MS = 12L
private const val REPEAT_MIN_INTERVAL_MS = 45L

private val KeypadRows = listOf(
    listOf("7", "8", "9"),
    listOf("4", "5", "6"),
    listOf("1", "2", "3"),
    listOf(".", "0", AMOUNT_KEY_BACKSPACE),
)

/**
 * 应用内数字键盘（记账面板与预算面板共用）。
 *
 * 为什么没有 `00`、日期、备注键：`00` 只省一次点击却占一格；日期/备注在金额下方已有入口，
 * 键盘上再来一份就是重复元素。退格放右下角（与系统数字键盘一致），主按钮单独一行满宽胶囊。
 *
 * 退格支持长按连删：按住后先等一个长按阈值，再以逐渐加快的节奏重复，松手立即停。
 */
@Composable
fun AmountKeypad(
    canSave: Boolean,
    saveLabel: String,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeypadRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    KeypadKey(
                        label = key,
                        kind = if (key == AMOUNT_KEY_BACKSPACE) KeyKind.FUNCTION else KeyKind.NORMAL,
                        enabled = true,
                        onClick = { onKey(key) },
                        repeatOnLongPress = key == AMOUNT_KEY_BACKSPACE,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        KeypadKey(
            label = saveLabel,
            kind = KeyKind.PRIMARY,
            enabled = canSave,
            onClick = { onKey(AMOUNT_KEY_SAVE) },
            height = 54.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class KeyKind { NORMAL, FUNCTION, PRIMARY }

@Composable
private fun KeypadKey(
    label: String,
    kind: KeyKind,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    repeatOnLongPress: Boolean = false,
) {
    val background = when (kind) {
        KeyKind.PRIMARY -> if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        }

        KeyKind.FUNCTION -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        KeyKind.NORMAL -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val contentColor = when (kind) {
        KeyKind.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        KeyKind.FUNCTION -> MaterialTheme.colorScheme.primary
        KeyKind.NORMAL -> MaterialTheme.colorScheme.onSurface
    }
    val style = LocalThemeStyle.current

    val tapModifier = if (repeatOnLongPress && enabled) {
        // 自己处理「按下后持续删除」：
        // - 不能用 detectTapGestures：系统长按判定后 onPress 会被取消，真机表现是只删一个字符
        // - 也不能等「没有 pointer 事件」才删：长按期间系统仍会持续派发 MOVE 事件（adb 与真机都会），
        //   永远等不到「无事件」的间隙
        // 正确做法是独立计时器重复回调，同时等手指抬起再停。
        Modifier.pointerInput(enabled) {
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onClick()
                    val repeat = launch {
                        var interval = REPEAT_FIRST_DELAY_MS
                        while (true) {
                            delay(interval)
                            onClick()
                            interval = (interval - REPEAT_ACCELERATE_MS)
                                .coerceAtLeast(REPEAT_MIN_INTERVAL_MS)
                        }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) break
                        }
                    } finally {
                        repeat.cancel()
                    }
                }
            }
        }
    } else {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(if (style.outlineWidth > 0.dp) 16.dp else 14.dp))
            .background(background)
            .then(
                // 描边主题下给按键一圈细线，像一颗颗贴纸键
                if (style.outlineWidth > 0.dp && kind != KeyKind.PRIMARY) {
                    Modifier.border(
                        width = 1.2.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(16.dp),
                    )
                } else {
                    Modifier
                },
            )
            .then(tapModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (kind == KeyKind.PRIMARY) 16.sp else 20.sp,
            fontWeight = if (kind == KeyKind.PRIMARY) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
        )
    }
}
