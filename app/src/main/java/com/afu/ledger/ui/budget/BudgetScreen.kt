package com.afu.ledger.ui.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.afu.ledger.domain.budget.BudgetAlertLevel
import com.afu.ledger.domain.budget.BudgetPeriod
import com.afu.ledger.domain.budget.BudgetProgress
import com.afu.ledger.domain.model.Budget
import com.afu.ledger.domain.model.BudgetPeriodType
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.component.AMOUNT_KEY_BACKSPACE
import com.afu.ledger.ui.component.AMOUNT_KEY_SAVE
import com.afu.ledger.ui.component.AfuButton
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.AfuSlider
import com.afu.ledger.ui.component.AmountKeypad
import com.afu.ledger.ui.component.EmptyState
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.component.rememberMotionEnabled
import com.afu.ledger.ui.record.appendAmountKey
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.MoneyFormatter
import java.time.LocalDate
import kotlinx.coroutines.launch

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
        BudgetEditorSheet(
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
 * 新建 / 编辑预算面板。
 *
 * 与记账面板同一套交互语言：底部上滑、大字号金额、应用内数字键盘、胶囊分段、
 * 圆形图标分类格、底部唯一胶囊主按钮。遮罩**不可点击关闭** —— 关闭只留 ❌、下滑与返回键。
 *
 * 编辑已有预算时，金额预填会去掉千分位逗号：逗号只适合展示，留在输入框里会让光标
 * 编辑中间的数字非常难受（而且上一版正是「预填带逗号 → 解析失败 → 保存永远灰着」的根因）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorSheet(
    initial: Budget?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Budget) -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    val style = LocalThemeStyle.current
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amountText by remember {
        mutableStateOf(
            initial?.amount?.let { MoneyFormatter.formatYuan(it.cents).replace(",", "") }.orEmpty(),
        )
    }
    var periodType by remember { mutableStateOf(initial?.periodType ?: BudgetPeriodType.MONTHLY) }
    var monthStartDay by remember { mutableStateOf(initial?.monthStartDay ?: 1) }
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
    // 键盘长按回调存活时间很长，读它拿最新金额而不是创建时的快照
    val liveAmount by rememberUpdatedState(amountText)
    val nameValid = name.isNotBlank()
    val amountValid = amount != null && amount.cents > 0
    val startDayValid = periodType != BudgetPeriodType.MONTHLY || monthStartDay in 1..31
    val customValid = periodType != BudgetPeriodType.CUSTOM ||
        (customStart != null && customEnd != null && !customEnd!!.isBefore(customStart!!))
    val valid = nameValid && amountValid && startDayValid && customValid

    var closing by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<Budget?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val appear = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        appear.animateTo(
            targetValue = 0f,
            animationSpec = if (motionEnabled) AfuMotion.sheetBouncy() else snap(),
        )
    }
    LaunchedEffect(closing) {
        if (closing) {
            appear.animateTo(
                targetValue = 1f,
                animationSpec = tween(AfuMotion.DURATION_EXIT_MS, easing = AfuMotion.EmphasizedAccelerate),
            )
            val saved = pendingSave
            if (saved != null) onSave(saved) else onDismiss()
        }
    }

    fun requestClose() {
        if (!closing) closing = true
    }

    fun buildBudget(): Budget = Budget(
        id = initial?.id ?: 0L,
        name = name.trim(),
        amount = amount ?: Money.ZERO,
        periodType = periodType,
        monthStartDay = monthStartDay,
        customStartEpochDay = customStart?.toEpochDay(),
        customEndEpochDay = customEnd?.toEpochDay(),
        categoryIds = selectedCategories.toList(),
        alertThresholdPercent = threshold.toInt().coerceIn(50, 100),
        enabled = enabled,
    )

    fun commitSave() {
        if (!valid) return
        pendingSave = buildBudget()
        requestClose()
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            // 遮罩：不接收点击。误触空白就丢掉刚填的额度是最让人恼火的体验。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.30f * (1f - appear.value.coerceIn(0f, 1f)) }
                    .background(MaterialTheme.colorScheme.onSurface),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .graphicsLayer {
                        translationY = appear.value.coerceAtLeast(0f) * size.height + dragOffset
                        val overshoot = (-appear.value).coerceAtLeast(0f)
                        val bounce = 1f + overshoot * 0.35f
                        scaleX = bounce
                        scaleY = bounce
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(
                        RoundedCornerShape(
                            topStart = style.containerCorner,
                            topEnd = style.containerCorner,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // 拖拽条：只在这里绑手势，避免和正文滚动打架
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                }
                            },
                            onDragStopped = {
                                scope.launch {
                                    if (dragOffset > dismissThresholdPx) {
                                        requestClose()
                                    } else {
                                        dragOffset = 0f
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CapsuleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.xl, end = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (initial == null) "新建预算" else "编辑预算",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // —— 金额：大字号，只用应用内键盘输入 ——
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "额度",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "¥",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = amountText.ifBlank { "0.00" },
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (amountValid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }

                    OutlinedLikeField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "预算名称，例如：本月生活费",
                    )

                    Text("周期", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        BudgetPeriodType.entries.forEach { type ->
                            SegmentPill(
                                text = type.label(),
                                selected = periodType == type,
                                onClick = { periodType = type },
                            )
                        }
                    }

                    when (periodType) {
                        BudgetPeriodType.MONTHLY -> StepperLine(
                            label = "每月起始日",
                            value = monthStartDay,
                            suffix = "日",
                            hint = "发薪日不是 1 号就改这里",
                            onDecrease = { monthStartDay = if (monthStartDay <= 1) 28 else monthStartDay - 1 },
                            onIncrease = { monthStartDay = if (monthStartDay >= 28) 1 else monthStartDay + 1 },
                        )

                        BudgetPeriodType.CUSTOM -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

                        else -> Unit
                    }

                    Text(
                        text = if (selectedCategories.isEmpty()) {
                            "统计范围 · 不选分类 = 总预算（全部支出）"
                        } else {
                            "统计范围 · 已选 ${selectedCategories.size} 个分类"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    CategoryChoiceGrid(
                        categories = categories,
                        selected = selectedCategories,
                        onToggle = { id ->
                            selectedCategories = if (id in selectedCategories) {
                                selectedCategories - id
                            } else {
                                selectedCategories + id
                            }
                        },
                    )

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

                    if (!valid && (amountText.isNotEmpty() || name.isNotEmpty())) {
                        Text(
                            text = when {
                                !nameValid -> "给预算起个名字"
                                !amountValid -> "额度必须大于 0"
                                !startDayValid -> "起始日需在 1–28 之间"
                                else -> "自定义区间结束不能早于开始"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(Spacing.sm))
                }

                AmountKeypad(
                    canSave = valid,
                    saveLabel = if (initial == null) "创建" else "保存",
                    onKey = { key ->
                        // 同上：长按回调是旧闭包，必须读最新的金额
                        when (key) {
                            AMOUNT_KEY_BACKSPACE -> amountText = liveAmount.dropLast(1)
                            AMOUNT_KEY_SAVE -> commitSave()
                            else -> amountText = appendAmountKey(liveAmount, key)
                        }
                    },
                )
            }
        }
    }
}

/** 无描边的单行输入：浅色底 + 圆角，与「记一笔」的备注行同一观感。 */
@Composable
private fun OutlinedLikeField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = Spacing.md, vertical = 13.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 胶囊分段里的一个选项。 */
@Composable
private fun SegmentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 圆形加减步进器：数值类参数不弹键盘。 */
@Composable
private fun StepperLine(
    label: String,
    value: Int,
    suffix: String,
    hint: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            RoundStepButton(icon = Icons.Rounded.Remove, onClick = onDecrease)
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(52.dp),
            )
            RoundStepButton(icon = Icons.Rounded.Add, onClick = onIncrease)
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoundStepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 圆形图标分类格：4 列，选中项有描边与更实的底色。 */
@Composable
private fun CategoryChoiceGrid(
    categories: List<Category>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        maxItemsInEachRow = 4,
    ) {
        categories.forEach { category ->
            val isSelected = category.id in selected
            val accent = Color(category.colorArgb)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggle(category.id) }
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (isSelected) 0.30f else 0.12f))
                        .then(
                            if (isSelected) {
                                Modifier.border(1.6.dp, accent, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(category.iconKey),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
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
