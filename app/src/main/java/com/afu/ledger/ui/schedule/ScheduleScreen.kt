package com.afu.ledger.ui.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.domain.schedule.ScheduleBehavior
import com.afu.ledger.domain.schedule.ScheduleCalculator
import com.afu.ledger.domain.schedule.ScheduleFrequency
import com.afu.ledger.domain.schedule.ScheduleRule
import com.afu.ledger.schedule.PendingOccurrenceStore
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.IconChoiceRow
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 定时记账：规则列表 + 待确认期次 + 新建/编辑表单。
 *
 * 表单刻意用系统日期/时间选择器与普通输入框：记账面板那套自绘键盘适合「快速记一笔」，
 * 而这里要填的字段多、还需要数字与日期，自绘键盘反而更慢。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleListViewModel.UiState,
    onBack: () -> Unit,
    onCreate: (ScheduleRule) -> Unit,
    onUpdate: (ScheduleRule) -> Unit,
    onToggle: (ScheduleRule, Boolean) -> Unit,
    onDelete: (ScheduleRule) -> Unit,
    onConfirmPending: (PendingOccurrenceStore.Occurrence) -> Unit,
    onIgnorePending: (PendingOccurrenceStore.Occurrence) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ScheduleRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("定时记账") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建规则")
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
            if (uiState.pending.isNotEmpty()) {
                item(key = "pending") {
                    PendingOccurrencesCard(
                        occurrences = uiState.pending,
                        onConfirm = onConfirmPending,
                        onIgnore = onIgnorePending,
                    )
                }
            }

            if (uiState.loading) {
                item { Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (uiState.rules.isEmpty()) {
                item(key = "empty") {
                    EmptyScheduleHint()
                }
            } else {
                uiState.rules.forEach { rule ->
                    item(key = "rule-${rule.id}") {
                        RuleCard(
                            rule = rule,
                            categoryName = uiState.categoryName(rule.categoryId),
                            accountName = uiState.accountName(rule.accountId),
                            onToggle = { onToggle(rule, it) },
                            onClick = { editing = rule },
                            onDelete = { onDelete(rule) },
                        )
                    }
                }
            }
        }
    }

    val editingRule = editing
    if (creating || editingRule != null) {
        ScheduleEditorDialog(
            initial = editingRule,
            accounts = uiState.accounts,
            categories = uiState.categories,
            onDismiss = { creating = false; editing = null },
            onSave = { rule ->
                if (editingRule == null) onCreate(rule) else onUpdate(rule)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun EmptyScheduleHint() {
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text("还没有定时记账", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "房租、话费、订阅这类每月固定支出，设一次就会到点自动记一笔。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PendingOccurrencesCard(
    occurrences: List<PendingOccurrenceStore.Occurrence>,
    onConfirm: (PendingOccurrenceStore.Occurrence) -> Unit,
    onIgnore: (PendingOccurrenceStore.Occurrence) -> Unit,
) {
    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "待确认 · ${occurrences.size} 期",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            occurrences.forEach { occurrence ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (if (occurrence.type == TransType.EXPENSE) "-" else "+") +
                                MoneyFormatter.formatWithSymbol(Money(occurrence.amountCents)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = billTime(occurrence.occurredAt) +
                                occurrence.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onIgnore(occurrence) }) { Text("忽略") }
                    TextButton(onClick = { onConfirm(occurrence) }) { Text("入账") }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: ScheduleRule,
    categoryName: String?,
    accountName: String?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    AfuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EventRepeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.displayName(categoryName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(scheduleSummary(rule))
                        append(" · ")
                        append(if (rule.behavior == ScheduleBehavior.AUTO_POST) "自动入账" else "提醒确认")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(nextRunLabel(rule))
                        accountName?.let { append(" · $it") }
                        rule.pausedReason?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.pausedReason != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (rule.type == TransType.EXPENSE) "-" else "+") +
                        MoneyFormatter.formatWithSymbol(rule.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rule.type == TransType.EXPENSE) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除规则",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Switch(checked = rule.enabled, onCheckedChange = onToggle)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条规则？") },
            text = { Text("已经生成的账单会保留，只是以后不再自动记账。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ScheduleEditorDialog(
    initial: ScheduleRule?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (ScheduleRule) -> Unit,
) {
    val context = LocalContext.current

    var amountText by remember {
        mutableStateOf(
            initial?.amount?.let { MoneyFormatter.formatYuan(it.cents).replace(",", "") } ?: "",
        )
    }
    var type by remember { mutableStateOf(initial?.type ?: TransType.EXPENSE) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var accountId by remember { mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: ScheduleFrequency.MONTHLY) }
    var intervalText by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: emptySet<DayOfWeek>()) }
    var dayText by remember { mutableStateOf((initial?.dayOfMonth ?: 1).toString()) }
    var monthText by remember { mutableStateOf((initial?.monthOfYear ?: 1).toString()) }
    var timeOfDay by remember { mutableStateOf(initial?.timeOfDay ?: LocalTime.of(9, 0)) }
    var startDate by remember { mutableStateOf(initial?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(initial?.endDate) }
    var maxText by remember { mutableStateOf(initial?.maxOccurrences?.toString() ?: "") }
    var behavior by remember { mutableStateOf(initial?.behavior ?: ScheduleBehavior.AUTO_POST) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val typeCategories = categories.filter { it.type == type }
    val amount = Money.parseYuan(amountText)
    val dayOfMonth = dayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
    val monthOfYear = monthText.toIntOrNull()?.coerceIn(1, 12) ?: 1
    val interval = intervalText.toIntOrNull()?.coerceIn(1, 999) ?: 1
    val maxOccurrences = maxText.toIntOrNull()?.takeIf { it > 0 }
    val canSave = amount != null && amount.isPositive() &&
        categoryId != null && typeCategories.any { it.id == categoryId } &&
        accountId != null &&
        (frequency != ScheduleFrequency.WEEKLY || weekdays.isNotEmpty()) &&
        (endDate == null || !endDate!!.isBefore(startDate))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Text(
                text = if (initial == null) "新建定时记账" else "编辑定时记账",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("金额") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            Text("类型", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = type == TransType.EXPENSE,
                    onClick = { type = TransType.EXPENSE; categoryId = null },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = type == TransType.INCOME,
                    onClick = { type = TransType.INCOME; categoryId = null },
                    label = { Text("收入") },
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            Text("分类", style = MaterialTheme.typography.labelLarge)
            IconChoiceRow(
                items = typeCategories,
                selectedId = categoryId,
                idOf = { it.id },
                labelOf = { it.name },
                iconOf = { categoryIcon(it.iconKey) },
                colorOf = { Color(it.colorArgb) },
                onSelect = { categoryId = it.id },
            )

            Spacer(Modifier.height(Spacing.sm))
            Text("账户", style = MaterialTheme.typography.labelLarge)
            IconChoiceRow(
                items = accounts,
                selectedId = accountId,
                idOf = { it.id },
                labelOf = { it.name },
                iconOf = { accountIcon(it.iconKey) },
                colorOf = { Color(it.colorArgb) },
                onSelect = { accountId = it.id },
                emptyText = "先到资产页创建一个账户",
            )

            Spacer(Modifier.height(Spacing.sm))
            Text("重复", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrequencyChip("每天", ScheduleFrequency.DAILY, frequency) { frequency = it }
                FrequencyChip("每周", ScheduleFrequency.WEEKLY, frequency) { frequency = it }
                FrequencyChip("每月", ScheduleFrequency.MONTHLY, frequency) { frequency = it }
                FrequencyChip("每年", ScheduleFrequency.YEARLY, frequency) { frequency = it }
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit) },
                    label = { Text("间隔") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = when (frequency) {
                        ScheduleFrequency.DAILY -> "天一次"
                        ScheduleFrequency.WEEKLY -> "周一次"
                        ScheduleFrequency.MONTHLY -> "月一次"
                        ScheduleFrequency.YEARLY -> "年一次"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (frequency) {
                ScheduleFrequency.WEEKLY -> {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("每周哪几天", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val selected = day in weekdays
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    weekdays = if (selected) weekdays - day else weekdays + day
                                },
                                label = { Text(weekdayLabel(day)) },
                            )
                        }
                    }
                }

                ScheduleFrequency.MONTHLY -> {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = dayText,
                            onValueChange = { dayText = it.filter(Char::isDigit) },
                            label = { Text("每月几号") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(150.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = "遇到小月自动记在月末",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ScheduleFrequency.YEARLY -> {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = monthText,
                            onValueChange = { monthText = it.filter(Char::isDigit) },
                            label = { Text("月") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(90.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        OutlinedTextField(
                            value = dayText,
                            onValueChange = { dayText = it.filter(Char::isDigit) },
                            label = { Text("日") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(90.dp),
                        )
                    }
                }

                ScheduleFrequency.DAILY -> Unit
            }

            Spacer(Modifier.height(Spacing.sm))
            Text("时间与开始日期", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButtonText(
                    text = "%02d:%02d".format(timeOfDay.hour, timeOfDay.minute),
                ) {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> timeOfDay = LocalTime.of(hour, minute) },
                        timeOfDay.hour,
                        timeOfDay.minute,
                        true,
                    ).show()
                }
                OutlinedButtonText(text = startDate.format(DATE_FORMAT)) {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            startDate = LocalDate.of(year, month + 1, day)
                        },
                        startDate.year,
                        startDate.monthValue - 1,
                        startDate.dayOfMonth,
                    ).show()
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButtonText(
                    text = endDate?.format(DATE_FORMAT) ?: "不限结束日期",
                ) {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            endDate = LocalDate.of(year, month + 1, day)
                        },
                        endDate?.year ?: startDate.year,
                        (endDate?.monthValue ?: startDate.monthValue) - 1,
                        endDate?.dayOfMonth ?: startDate.dayOfMonth,
                    ).show()
                }
                if (endDate != null) {
                    TextButton(onClick = { endDate = null }) { Text("清除") }
                }
            }
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it.filter(Char::isDigit) },
                label = { Text("最多执行几期（留空表示不限）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            Text("到期时", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = behavior == ScheduleBehavior.AUTO_POST,
                    onClick = { behavior = ScheduleBehavior.AUTO_POST },
                    label = { Text("自动入账") },
                )
                FilterChip(
                    selected = behavior == ScheduleBehavior.NEED_CONFIRM,
                    onClick = { behavior = ScheduleBehavior.NEED_CONFIRM },
                    label = { Text("先提醒我") },
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（也是规则名称）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(Spacing.sm))
                TextButton(
                    enabled = canSave,
                    onClick = {
                        val savedAmount = amount ?: return@TextButton
                        onSave(
                            ScheduleRule(
                                id = initial?.id ?: 0L,
                                accountId = accountId ?: return@TextButton,
                                categoryId = categoryId ?: return@TextButton,
                                type = type,
                                amount = savedAmount,
                                note = note.trim(),
                                frequency = frequency,
                                interval = interval,
                                weekdays = weekdays,
                                dayOfMonth = dayOfMonth,
                                monthOfYear = monthOfYear,
                                timeOfDay = timeOfDay,
                                startDate = startDate,
                                endDate = endDate,
                                maxOccurrences = maxOccurrences,
                                behavior = behavior,
                                enabled = initial?.enabled ?: true,
                                lastGeneratedDate = initial?.lastGeneratedDate,
                                generatedCount = initial?.generatedCount ?: 0,
                                pausedReason = null,
                                createdAt = initial?.createdAt ?: 0L,
                            ),
                        )
                    },
                ) { Text("保存") }
            }
        }
        }
    }
}

@Composable
private fun FrequencyChip(
    label: String,
    value: ScheduleFrequency,
    current: ScheduleFrequency,
    onSelect: (ScheduleFrequency) -> Unit,
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun OutlinedButtonText(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
        )
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun billTime(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))

private fun weekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

/** 规则的一句话周期描述，列表与表单共用。 */
fun scheduleSummary(rule: ScheduleRule): String {
    val time = "%02d:%02d".format(rule.timeOfDay.hour, rule.timeOfDay.minute)
    return when (rule.frequency) {
        ScheduleFrequency.DAILY ->
            if (rule.interval == 1) "每天 $time" else "每 ${rule.interval} 天 $time"

        ScheduleFrequency.WEEKLY -> {
            val days = rule.weekdays.sortedBy { it.value }.joinToString("、") { weekdayLabel(it) }
            val interval = if (rule.interval == 1) "每周" else "每 ${rule.interval} 周"
            "$interval$days $time"
        }

        ScheduleFrequency.MONTHLY ->
            if (rule.interval == 1) "每月 ${rule.dayOfMonth} 日 $time"
            else "每 ${rule.interval} 个月 ${rule.dayOfMonth} 日 $time"

        ScheduleFrequency.YEARLY ->
            "每年 ${rule.monthOfYear} 月 ${rule.dayOfMonth} 日 $time"
    }
}

private fun nextRunLabel(rule: ScheduleRule): String {
    if (!rule.enabled) return "已暂停"
    if (rule.reachedEnd) return "已完成"
    val next = ScheduleCalculator.nextOccurrence(rule, LocalDateTime.now()) ?: return "已结束"
    return "下次 " + next.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}
