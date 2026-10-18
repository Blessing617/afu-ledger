package com.afu.ledger.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.DatePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.InputShape
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(
    uiState: RecordUiState,
    onTypeChange: (TransType) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategorySelect: (Long) -> Unit,
    onAccountSelect: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除")
                        }
                    }
                    IconButton(onClick = onSave, enabled = uiState.canSave) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "保存",
                            tint = if (uiState.canSave) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            TypeSwitcher(type = uiState.type, onTypeChange = onTypeChange)

            OutlinedTextField(
                value = uiState.amountInput,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("金额") },
                prefix = { Text("¥", fontSize = 20.sp) },
                singleLine = true,
                shape = InputShape,
                textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (uiState.canSave) onSave() },
                ),
            )

            SectionTitle("分类")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                uiState.categories.forEach { category ->
                    val selected = category.id == uiState.selectedCategoryId
                    Column(
                        modifier = Modifier.width(64.dp).clickable { onCategorySelect(category.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color(category.colorArgb)
                                    else Color(category.colorArgb).copy(alpha = 0.16f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = categoryIcon(category.iconKey),
                                contentDescription = null,
                                tint = if (selected) Color.White else Color(category.colorArgb),
                            )
                        }
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            SectionTitle("账户")
            if (uiState.accounts.isEmpty()) {
                Text(
                    text = "还没有可用账户，请先到「资产」页创建",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    uiState.accounts.forEach { account ->
                        FilterChip(
                            selected = account.id == uiState.selectedAccountId,
                            onClick = { onAccountSelect(account.id) },
                            label = { Text(account.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（可选）") },
                singleLine = true,
                shape = InputShape,
            )

            TextButton(onClick = { showDatePicker = true }) {
                Text("时间：" + DateFormatter.toLocalDate(uiState.occurredAt).toString())
            }

            uiState.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后账户余额会同步回退，且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(onDateChange)
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TypeSwitcher(type: TransType, onTypeChange: (TransType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = type == TransType.EXPENSE,
            onClick = { onTypeChange(TransType.EXPENSE) },
            label = { Text("支出") },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = type == TransType.INCOME,
            onClick = { onTypeChange(TransType.INCOME) },
            label = { Text("收入") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
