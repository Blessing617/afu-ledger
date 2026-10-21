package com.afu.ledger.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

/** 按住退格多久算「长按清空」。 */
private const val LONG_PRESS_CLEAR_MS = 420L

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
 * 退格的两种删法：**点一下删一个字符，按住不放清空整笔金额**。
 * 「按住连续删」在真机上像匀速倒带，删到一半还得盯着看删到哪了；一步清空更符合直觉。
 */
@Composable
fun AmountKeypad(
    canSave: Boolean,
    saveLabel: String,
    onKey: (String) -> Unit,
    onClear: () -> Unit = {},
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
                    val isBackspace = key == AMOUNT_KEY_BACKSPACE
                    KeypadKey(
                        label = key,
                        kind = if (isBackspace) KeyKind.FUNCTION else KeyKind.NORMAL,
                        enabled = true,
                        onClick = { onKey(key) },
                        onLongPress = if (isBackspace) onClear else null,
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
    onLongPress: (() -> Unit)? = null,
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
    val source = remember { MutableInteractionSource() }
    val haptic = rememberTapHaptic()
    val scope = rememberCoroutineScope()
    val longPressHaptic = rememberLongPressHaptic()

    val tapModifier = if (onLongPress != null && enabled) {
        // 短按删一个；按住超过阈值清空。阈值内抬手都算点按。
        Modifier.pointerInput(enabled) {
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    haptic()
                    val interaction = PressInteraction.Press(Offset.Zero)
                    scope.launch { source.emit(interaction) }

                    var longPressFired = false
                    val longPressJob = launch {
                        delay(LONG_PRESS_CLEAR_MS)
                        longPressFired = true
                        longPressHaptic()
                        onLongPress()
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    longPressJob.cancel()
                    scope.launch { source.emit(PressInteraction.Release(interaction)) }
                    if (!longPressFired) onClick()
                }
            }
        }
    } else {
        Modifier.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = {
                haptic()
                onClick()
            },
        )
    }

    Box(
        modifier = modifier
            .pressScale(source, pressedScale = if (kind == KeyKind.PRIMARY) 0.97f else 0.93f)
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
