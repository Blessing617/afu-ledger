package com.afu.ledger.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.budget.BudgetAlertLevel
import com.afu.ledger.domain.budget.BudgetPeriod
import com.afu.ledger.domain.budget.BudgetProgress
import com.afu.ledger.domain.model.Budget
import com.afu.ledger.domain.model.BudgetPeriodType
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AfuSlider
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.LocalDate

/**
 * 预算管理页。
 *
 * 列表只回答「每个预算还剩多少」，编辑在对话框里完成 —— 预算字段少（金额、周期、
 * 分类、阈值），整页表单会显得空。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    budgets: List<Budget>,
    progressByBudget: Map<Long, BudgetProgress>,
    categories: List<Category>,
    onBack: () -> Unit,
    onSave: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
    onToggle: (Budget, Boolean) -> Unit,
) {
    var editing by remember { mutableStateOf<Budget?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Budget?>(null) }
    val today = LocalDate.now()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建预算")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (budgets.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "还没有预算",
                        description = "给每月设一个额度，主页就能看到「还能花多少」。\n先设个总预算最省事～",
                        icon = Icons.Rounded.PieChart,
                        action = { AfuButton(text = "设一个预算", onClick = { creating = true }) },
                    )
                }
            } else {
                items(budgets.size, key = { budgets[it].id }) { index ->
                    val budget = budgets[index]
                    BudgetEntry(
                        budget = budget,
                        progress = progressByBudget[budget.id],
                        today = today,
                        onEdit = { editing = budget },
                        onToggle = { enabled -> onToggle(budget, enabled) },
                        onDelete = { pendingDelete = budget },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        val initial = editing
        BudgetEditorDialog(
            initial = initial,
            categories = categories.filter { it.type == TransType.EXPENSE },
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { budget ->
                onSave(budget)
                creating = false
                editing = null
            },
        )
    }

    pendingDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${budget.name}」？") },
            text = { Text("只删除预算本身，已经记下的账不会受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(budget)
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BudgetEntry(
    budget: Budget,
    progress: BudgetProgress?,
    today: LocalDate,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val accent = when (progress?.alertLevel ?: BudgetAlertLevel.SAFE) {
        BudgetAlertLevel.SAFE -> Color(0xFF4C9AFF)
        BudgetAlertLevel.NOTICE -> Color(0xFF7C6CF0)
        BudgetAlertLevel.WARNING -> Color(0xFFFF9800)
        BudgetAlertLevel.OVER -> Color(0xFFE5484D)
    }
    AfuCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PieChart,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = budget.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (progress != null) {
                    Text(
                        text = BudgetPeriod.describe(budget, progress.window, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = budget.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除预算",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (progress != null) {
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "已用 ${MoneyFormatter.formatWithSymbol(Money(progress.usedCents))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (progress.remainingCents >= 0) {
                        "剩余 ${MoneyFormatter.formatWithSymbol(Money(progress.remainingCents))}"
                    } else {
                        "超支 ${MoneyFormatter.formatWithSymbol(Money(-progress.remainingCents))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CapsuleShape)
                    .background(accent.copy(alpha = 0.14f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.barFraction)
                        .height(8.dp)
                        .clip(CapsuleShape)
                        .background(accent),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "额度 ${MoneyFormatter.formatWithSymbol(budget.amount)} · ${progress.percentText}" +
                    if (budget.isTotalBudget) " · 全部支出" else " · ${budget.categoryIds.size} 个分类",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 新建 / 编辑预算表单。
 *
 * 交互与记账面板保持一致：金额用数字键盘、周期用胶囊分段、分类用可选圆角标签，
 * 不放描边输入框之外的花哨控件。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorDialog(
    initial: Budget?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Budget) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amountText by remember {
        mutableStateOf(initial?.amount?.let { MoneyFormatter.formatYuan(it.cents) }.orEmpty())
    }
    var periodType by remember { mutableStateOf(initial?.periodType ?: BudgetPeriodType.MONTHLY) }
    var monthStartDay by remember { mutableStateOf((initial?.monthStartDay ?: 1).toString()) }
    var customStart by remember {
        mutableStateOf(initial?.customStartEpochDay?.let { LocalDate.ofEpochDay(it) })
    }
    var customEnd by remember {
        mutableStateOf(initial?.customEndEpochDay?.let { LocalDate.ofEpochDay(it) })
    }
    var selectedCategories by remember { mutableStateOf(initial?.categoryIds?.toSet() ?: emptySet()) }
    var threshold by remember { mutableStateOf((initial?.alertThresholdPercent ?: 80).toFloat()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    val amount = Money.parseYuan(amountText)
    val startDayValue = monthStartDay.toIntOrNull()
    val nameValid = name.isNotBlank()
    val amountValid = amount != null && amount.cents > 0
    val startDayValid = periodType != BudgetPeriodType.MONTHLY ||
        (startDayValue != null && startDayValue in 1..31)
    val customValid = periodType != BudgetPeriodType.CUSTOM ||
        (customStart != null && customEnd != null && !customEnd!!.isBefore(customStart!!))
    val valid = nameValid && amountValid && startDayValid && customValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建预算" else "编辑预算") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：本月生活费") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("额度（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountText.isNotEmpty() && !amountValid,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("周期", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    BudgetPeriodType.entries.forEach { type ->
                        FilterChip(
                            selected = periodType == type,
                            onClick = { periodType = type },
                            label = { Text(type.label()) },
                        )
                    }
                }

                if (periodType == BudgetPeriodType.MONTHLY) {
                    OutlinedTextField(
                        value = monthStartDay,
                        onValueChange = { monthStartDay = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("每月起始日（1–31）") },
                        supportingText = { Text("发薪日不是 1 号就改这里，例如 15") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = monthStartDay.isNotEmpty() && !startDayValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (periodType == BudgetPeriodType.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        DateField(
                            label = "开始",
                            value = customStart,
                            modifier = Modifier.weight(1f),
                            onPick = { picked ->
                                customStart = picked
                                if (customEnd != null && customEnd!!.isBefore(picked)) customEnd = picked
                            },
                        )
                        DateField(
                            label = "结束",
                            value = customEnd,
                            modifier = Modifier.weight(1f),
                            onPick = { customEnd = it },
                        )
                    }
                }

                Text("统计范围", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = if (selectedCategories.isEmpty()) {
                        "不选分类 = 总预算（统计全部支出）"
                    } else {
                        "只统计选中的 ${selectedCategories.size} 个分类"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    categories.forEach { category ->
                        val selected = category.id in selectedCategories
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCategories = if (selected) {
                                    selectedCategories - category.id
                                } else {
                                    selectedCategories + category.id
                                }
                            },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                            label = { Text(category.name) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "预警阈值 ${threshold.toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                AfuSlider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    onValueChangeFinished = {},
                    valueRange = 50f..100f,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "启用",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        Budget(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            amount = amount ?: Money.ZERO,
                            periodType = periodType,
                            monthStartDay = startDayValue ?: 1,
                            customStartEpochDay = customStart?.toEpochDay(),
                            customEndEpochDay = customEnd?.toEpochDay(),
                            categoryIds = selectedCategories.toList(),
                            alertThresholdPercent = threshold.toInt().coerceIn(50, 100),
                            enabled = enabled,
                        ),
                    )
                },
            ) {
                Text(if (initial == null) "创建" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    modifier: Modifier = Modifier,
    onPick: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable {
                val base = value ?: LocalDate.now()
                android.app.DatePickerDialog(
                    context,
                    { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
                    base.year,
                    base.monthValue - 1,
                    base.dayOfMonth,
                ).show()
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value?.let { "${it.monthValue} 月 ${it.dayOfMonth} 日" } ?: "选择日期",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun BudgetPeriodType.label(): String = when (this) {
    BudgetPeriodType.MONTHLY -> "每月"
    BudgetPeriodType.WEEKLY -> "每周"
    BudgetPeriodType.YEARLY -> "每年"
    BudgetPeriodType.CUSTOM -> "自定义"
}
