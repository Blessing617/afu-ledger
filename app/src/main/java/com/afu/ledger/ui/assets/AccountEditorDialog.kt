package com.afu.ledger.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.AccountType
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.component.AvailableAccountIcons
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.InputShape
import com.afu.ledger.ui.theme.Spacing

private val accountColors = listOf(
    0xFF7C6BF0, 0xFFF06292, 0xFF4DB6AC, 0xFF4FC3F7,
    0xFF9575CD, 0xFFFF8A65, 0xFF8BC34A, 0xFFFFCA28,
)

/**
 * 新增 / 编辑账户对话框。
 *
 * 与「记一笔」是两种不同复杂度的操作：这里字段少、频率低，用居中对话框；
 * 但视觉语言必须一致 —— 顶部先把「这个账户长什么样」预览出来（图标 + 颜色），
 * 选图标选颜色时预览即时跟着变，而不是选完保存才知道。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditorDialog(
    existing: Account?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: AccountType, initialBalance: Money, colorArgb: Long, iconKey: String) -> Unit,
    onArchiveToggle: ((Boolean) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.WECHAT) }
    var balanceInput by remember {
        mutableStateOf(
            existing?.initialBalance?.let { money ->
                val yuan = money.cents / 100
                val cents = money.cents % 100
                if (cents == 0L) yuan.toString() else "$yuan.${cents.toString().padStart(2, '0')}"
            } ?: "",
        )
    }
    var colorArgb by remember { mutableLongStateOf(existing?.colorArgb ?: accountColors.first()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: AvailableAccountIcons.first()) }

    // 余额为空按 0 处理；填了内容但解析不了（超 9 位整数、多个小数点等）必须拦住，
    // 不能静默退回 0 —— 那会把用户刚输的余额直接抹掉。
    val parsedBalance = if (balanceInput.isBlank()) Money.ZERO else Money.parseYuan(balanceInput)
    val balanceValid = parsedBalance != null
    val submit = {
        val balance = parsedBalance
        if (name.isNotBlank() && balance != null) {
            onConfirm(name, type, balance, colorArgb, iconKey)
        }
    }
    val accent = Color(colorArgb)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(iconKey),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = if (existing == null) "新增账户" else "编辑账户",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = name.ifBlank { "还没起名字" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    shape = InputShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AccountType.entries.forEach { candidate ->
                        val selected = type == candidate
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { type = candidate }
                                .padding(horizontal = Spacing.sm, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = accountTypeText(candidate),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { balanceInput = it },
                    label = { Text("初始余额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = InputShape,
                    isError = balanceInput.isNotBlank() && !balanceValid,
                    supportingText = if (balanceInput.isNotBlank() && !balanceValid) {
                        {
                            Text("金额需为数字，最多 9 位整数、2 位小数")
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    accountColors.forEach { color ->
                        val selected = color == colorArgb
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { colorArgb = color },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "图标",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AvailableAccountIcons.forEach { key ->
                        val selected = key == iconKey
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) accent.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) accent else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = categoryIcon(key),
                                contentDescription = null,
                                tint = if (selected) accent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                if (existing != null && onArchiveToggle != null) {
                    TextButton(onClick = { onArchiveToggle(!existing.archived) }) {
                        Text(if (existing.archived) "取消归档" else "归档此账户")
                    }
                }
            }
        },
        confirmButton = {
            val canSave = name.isNotBlank() && balanceValid
            TextButton(
                onClick = submit,
                enabled = canSave,
            ) {
                Text(
                    text = "保存",
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSave) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun accountTypeText(type: AccountType): String = when (type) {
    AccountType.WECHAT -> "微信"
    AccountType.ALIPAY -> "支付宝"
    AccountType.BANK -> "银行卡"
    AccountType.CASH -> "现金"
    AccountType.CREDIT -> "信用卡"
}
