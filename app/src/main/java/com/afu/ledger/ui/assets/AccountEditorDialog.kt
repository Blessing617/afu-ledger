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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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

/** 新增 / 编辑账户对话框。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditorDialog(
    existing: Account?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: AccountType, initialBalance: Money, colorArgb: Long, iconKey: String) -> Unit,
    onArchiveToggle: ((Boolean) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }
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

    val submit = {
        val balance = Money.parseYuan(balanceInput.ifBlank { "0" }) ?: Money.ZERO
        if (name.isNotBlank()) {
            onConfirm(name, type, balance, colorArgb, iconKey)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增账户" else "编辑账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    shape = InputShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AccountType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(accountTypeText(candidate)) },
                        )
                    }
                }

                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { balanceInput = it },
                    label = { Text("初始余额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = InputShape,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("颜色", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    accountColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (color == colorArgb) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
                                .clickable { colorArgb = color },
                        )
                    }
                }

                Text("图标", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AvailableAccountIcons.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (key == iconKey) Color(colorArgb).copy(alpha = 0.24f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = categoryIcon(key),
                                contentDescription = null,
                                tint = if (key == iconKey) Color(colorArgb)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (existing != null && onArchiveToggle != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    TextButton(onClick = { onArchiveToggle(!existing.archived) }) {
                        Text(if (existing.archived) "取消归档" else "归档此账户")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun accountTypeText(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.BANK -> "银行卡"
    AccountType.CREDIT -> "信用卡"
}
