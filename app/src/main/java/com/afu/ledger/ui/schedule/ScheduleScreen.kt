package com.afu.ledger.ui.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.domain.schedule.ScheduleBehavior
import com.afu.ledger.domain.schedule.ScheduleCalculator
import com.afu.ledger.domain.schedule.ScheduleFrequency
import com.afu.ledger.domain.schedule.ScheduleRule
import com.afu.ledger.schedule.PendingOccurrenceStore
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.IconChoiceRow
import com.afu.ledger.ui.component.rememberMotionEnabled
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * 定时记账：规则列表 + 待确认期次 + 新建/编辑页。
 *
 * 编辑页刻意做成**整页**而不是弹窗：字段有金额、类型、分类、账户、周期、时间、结束条件、
 * 到期行为与备注，塞进一个对话框里只会变成一坨输入框。现在按记账面板的语言重新组织：
 * 大字号金额、胶囊分段、圆形分类格、没有描边输入框。
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
                item(key = "empty") { EmptyScheduleHint() }
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
        ScheduleEditorPage(
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

// ---------------------------------------------------------------------------
// 新建 / 编辑页
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleEditorPage(
    initial: ScheduleRule?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (ScheduleRule) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val motionEnabled = rememberMotionEnabled()
    // 0 = 在屏幕下方，1 = 就位。用 Animatable + graphicsLayer：
    // 位移与透明度都在绘制阶段更新，不触发重组，滚长表单时也不掉帧。
    val appear = remember { Animatable(if (motionEnabled) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            // 整页用 sheet 档弹簧（阻尼 0.8）：只回弹一点点，不会把整块内容晃出底色
            appear.animateTo(1f, AfuMotion.sheet())
        }
    }

    fun dismiss(after: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        if (!motionEnabled) {
            after?.invoke() ?: onDismiss()
            return
        }
        scope.launch {
            appear.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = AfuMotion.DURATION_EXIT_MS,
                    easing = AfuMotion.EmphasizedAccelerate,
                ),
            )
            after?.invoke() ?: onDismiss()
        }
    }

    var amountText by remember {
        mutableStateOf(
            initial?.amount?.let { MoneyFormatter.formatYuan(it.cents).replace(",", "") } ?: "",
        )
    }
    var type by remember { mutableStateOf(initial?.type ?: TransType.EXPENSE) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var accountId by remember { mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: ScheduleFrequency.MONTHLY) }
    var interval by remember { mutableStateOf(initial?.interval ?: 1) }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: emptySet<DayOfWeek>()) }
    var dayOfMonth by remember { mutableStateOf(initial?.dayOfMonth ?: 1) }
    var monthOfYear by remember { mutableStateOf(initial?.monthOfYear ?: 1) }
    var timeOfDay by remember { mutableStateOf(initial?.timeOfDay ?: LocalTime.of(9, 0)) }
    var startDate by remember { mutableStateOf(initial?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(initial?.endDate) }
    var maxOccurrences by remember { mutableStateOf(initial?.maxOccurrences ?: 0) }
    var behavior by remember { mutableStateOf(initial?.behavior ?: ScheduleBehavior.AUTO_POST) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val typeCategories = categories.filter { it.type == type }
    val amount = Money.parseYuan(amountText)
    val canSave = amount != null && amount.isPositive() &&
        categoryId != null && typeCategories.any { it.id == categoryId } &&
        accountId != null &&
        (frequency != ScheduleFrequency.WEEKLY || weekdays.isNotEmpty()) &&
        (endDate == null || !endDate!!.isBefore(startDate))

    val commitSave = {
        val savedAmount = amount
        val savedAccount = accountId
        val savedCategory = categoryId
        if (savedAmount != null && savedAmount.isPositive() &&
            savedAccount != null && savedCategory != null
        ) {
            val rule = ScheduleRule(
                id = initial?.id ?: 0L,
                accountId = savedAccount,
                categoryId = savedCategory,
                type = type,
                amount = savedAmount,
                note = note.trim(),
                frequency = frequency,
                interval = interval.coerceAtLeast(1),
                weekdays = weekdays,
                dayOfMonth = dayOfMonth,
                monthOfYear = monthOfYear,
                timeOfDay = timeOfDay,
                startDate = startDate,
                endDate = endDate,
                maxOccurrences = maxOccurrences.takeIf { it > 0 },
                behavior = behavior,
                enabled = initial?.enabled ?: true,
                lastGeneratedDate = initial?.lastGeneratedDate,
                generatedCount = initial?.generatedCount ?: 0,
                pausedReason = null,
                createdAt = initial?.createdAt ?: 0L,
            )
            // 先播退场，再落库并关闭：否则整页会「啪」地消失，和 App 其它转场不一致
            dismiss { onSave(rule) }
        }
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 从下方轻轻推上来；透明度同步，避免边缘露底
                    val p = appear.value
                    translationY = (1f - p) * size.height * 0.10f
                    alpha = p.coerceIn(0f, 1f)
                    val scale = 0.985f + 0.015f * p
                    scaleX = scale
                    scaleY = scale
                },
            color = MaterialTheme.colorScheme.background,
        ) {
            // 全屏编辑器不需要对话框的灰色遮罩：它会让状态栏变成一条脏灰色。
            // 铺满状态栏后还要把状态栏图标改回深色，否则浅色背景上是白字，看不见。
            val view = LocalView.current
            SideEffect {
                runCatching {
                    val window = (view.parent as? DialogWindowProvider)?.window
                    window?.setDimAmount(0f)
                    if (window != null) {
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                // 顶栏：关闭 + 标题 + 保存
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { dismiss() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                    Text(
                        text = if (initial == null) "新建定时记账" else "编辑定时记账",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = commitSave, enabled = canSave) {
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
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AmountField(
                        amountText = amountText,
                        onAmountChange = { raw ->
                            amountText = raw.filter { it.isDigit() || it == '.' }
                        },
                        type = type,
                        onTypeChange = { type = it; categoryId = null },
                    )

                    FieldLabel("分类")
                    CategoryPicker(
                        categories = typeCategories,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                    )

                    FieldLabel("账户")
                    if (accounts.isEmpty()) {
                        Text(
                            text = "先到资产页创建一个账户，才能设定时记账",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                        )
                    } else {
                        IconChoiceRow(
                            items = accounts,
                            selectedId = accountId,
                            idOf = { it.id },
                            labelOf = { it.name },
                            iconOf = { accountIcon(it.iconKey) },
                            colorOf = { Color(it.colorArgb) },
                            onSelect = { accountId = it.id },
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }

                    FieldLabel("重复")
                    PillRow(
                        options = listOf(
                            ScheduleFrequency.DAILY to "每天",
                            ScheduleFrequency.WEEKLY to "每周",
                            ScheduleFrequency.MONTHLY to "每月",
                            ScheduleFrequency.YEARLY to "每年",
                        ),
                        selected = frequency,
                        onSelect = { frequency = it },
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )

                    Spacer(Modifier.height(Spacing.xs))
                    StepperRow(
                        label = "间隔",
                        value = interval,
                        suffix = when (frequency) {
                            ScheduleFrequency.DAILY -> "天"
                            ScheduleFrequency.WEEKLY -> "周"
                            ScheduleFrequency.MONTHLY -> "个月"
                            ScheduleFrequency.YEARLY -> "年"
                        },
                        range = 1..99,
                        onChange = { interval = it },
                    )

                    when (frequency) {
                        ScheduleFrequency.WEEKLY -> {
                            FieldLabel("每周哪几天")
                            WeekdayRow(
                                selected = weekdays,
                                onToggle = { day ->
                                    weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                },
                            )
                        }

                        ScheduleFrequency.MONTHLY -> {
                            StepperRow(
                                label = "每月几号",
                                value = dayOfMonth,
                                suffix = "日",
                                range = 1..31,
                                onChange = { dayOfMonth = it },
                                hint = "遇到小月自动记在月末",
                            )
                        }

                        ScheduleFrequency.YEARLY -> {
                            StepperRow(
                                label = "几月",
                                value = monthOfYear,
                                suffix = "月",
                                range = 1..12,
                                onChange = { monthOfYear = it },
                            )
                            StepperRow(
                                label = "几号",
                                value = dayOfMonth,
                                suffix = "日",
                                range = 1..31,
                                onChange = { dayOfMonth = it },
                                hint = "2 月 29 日在平年记在 28 日",
                            )
                        }

                        ScheduleFrequency.DAILY -> Unit
                    }

                    FieldLabel("时间与开始日期")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CapsuleAction(
                            icon = Icons.Rounded.Schedule,
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
                        CapsuleAction(
                            icon = Icons.Rounded.CalendarToday,
                            text = startDate.format(DATE_FORMAT),
                        ) {
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> startDate = LocalDate.of(year, month + 1, day) },
                                startDate.year,
                                startDate.monthValue - 1,
                                startDate.dayOfMonth,
                            ).show()
                        }
                    }

                    Spacer(Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CapsuleAction(
                            icon = Icons.Rounded.EventBusy,
                            text = endDate?.format(DATE_FORMAT) ?: "不限结束",
                            muted = endDate == null,
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
                            Text(
                                text = "清除",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { endDate = null }
                                    .padding(horizontal = Spacing.xs, vertical = 4.dp),
                            )
                        }
                    }

                    StepperRow(
                        label = "最多执行",
                        value = maxOccurrences,
                        suffix = "期",
                        range = 0..999,
                        onChange = { maxOccurrences = it },
                        hint = "0 表示不限",
                    )

                    FieldLabel("到期时")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        BehaviorOption(
                            title = "自动入账",
                            description = "到点直接记一笔",
                            selected = behavior == ScheduleBehavior.AUTO_POST,
                            onClick = { behavior = ScheduleBehavior.AUTO_POST },
                            modifier = Modifier.weight(1f),
                        )
                        BehaviorOption(
                            title = "先提醒我",
                            description = "生成待确认，由你点入账",
                            selected = behavior == ScheduleBehavior.NEED_CONFIRM,
                            onClick = { behavior = ScheduleBehavior.NEED_CONFIRM },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FieldLabel("备注（也是规则名称）")
                    NoteField(value = note, onValueChange = { note = it })

                    Spacer(Modifier.height(Spacing.xl))
                }

                // 底部主按钮：始终可见，不用滚到底才能保存
                AfuButton(
                    text = if (initial == null) "创建" else "保存",
                    onClick = commitSave,
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                )
            }
        }
    }
}

/** 金额区：大字号、无边框，点击直接弹系统数字键盘；上面是收支分段。 */
@Composable
private fun AmountField(
    amountText: String,
    onAmountChange: (String) -> Unit,
    type: TransType,
    onTypeChange: (TransType) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = if (type == TransType.EXPENSE) "−" else "+",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "¥",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            BasicTextField(
                value = amountText,
                onValueChange = onAmountChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (amountText.isBlank()) {
                        Text(
                            text = "0.00",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                    }
                    inner()
                },
            )
        }

        // 收支分段：选中态是实心胶囊，和记账面板完全一致
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .width(200.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(TransType.EXPENSE to "支出", TransType.INCOME to "收入").forEach { (item, label) ->
                val selected = type == item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onTypeChange(item) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xl, top = Spacing.md, bottom = Spacing.xs),
    )
}

/** 分类网格：4 列圆形图标，与记账面板同一套视觉。 */
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    if (categories.isEmpty()) {
        Text(
            text = "这个方向下还没有分类，先到「分类管理」加一个",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 236.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        categories.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                rowItems.forEach { category ->
                    val selected = category.id == selectedId
                    val accent = Color(category.colorArgb)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
                            )
                            .clickable { onSelect(category.id) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (selected) 40.dp else 36.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = categoryIcon(category.iconKey),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 通用胶囊单选行。 */
@Composable
private fun <T> PillRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** 数字步进：左右是圆形加减，中间是值。比键盘输入少一次弹窗，也不会输进非法字符。 */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    suffix: String,
    range: IntRange,
    onChange: (Int) -> Unit,
    hint: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(Icons.Rounded.Remove, enabled = value > range.first) {
                onChange((value - 1).coerceIn(range))
            }
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .width(88.dp)
                    .padding(horizontal = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepButton(Icons.Rounded.Add, enabled = value < range.last) {
                onChange((value + 1).coerceIn(range))
            }
        }
        if (hint != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    // 到达范围边界时也保留一个淡底：否则这一侧像「少了个按钮」，布局不对称
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 星期选择：7 个圆形钮，选中填主色。 */
@Composable
private fun WeekdayRow(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                    )
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = weekdayLabel(day),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** 时间 / 日期胶囊按钮。 */
@Composable
private fun CapsuleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 8.dp),
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
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** 到期行为选项：整卡可点，选中用主色描边 + 浅底。 */
@Composable
private fun BehaviorOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
            )
            .then(
                if (selected) {
                    Modifier.padding(0.dp)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 备注：一行下划线输入，不画框。 */
@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) {
                        Text(
                            text = "例如：房租",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        )
    }
}

// ---------------------------------------------------------------------------
// 文案与格式化
// ---------------------------------------------------------------------------

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
