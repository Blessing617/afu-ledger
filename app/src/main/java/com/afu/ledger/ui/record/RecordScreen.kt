package com.afu.ledger.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.component.AMOUNT_KEY_BACKSPACE
import com.afu.ledger.ui.component.ModalScrim
import com.afu.ledger.ui.component.AMOUNT_KEY_SAVE
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.AmountKeypad
import com.afu.ledger.ui.component.DatePickerSheet
import com.afu.ledger.ui.component.MetaChip
import com.afu.ledger.ui.component.accountIcon
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.component.rememberMotionEnabled
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.util.DateFormatter
import kotlinx.coroutines.launch

/**
 * 记账面板。
 *
 * ## 为什么是底部面板 + 自带键盘
 *
 * 记账是本应用最高频的操作，输入只有「金额 + 分类 + 账户 + 时间 + 备注」几项。
 * 用居中对话框 + 系统键盘的问题是：
 *
 * 1. 系统键盘弹起会把对话框顶上去，布局跳动，且数字键盘上还有一堆无关按键；
 * 2. 对话框高度固定，分类一多就得在很窄的区域里滚。
 *
 * 底部面板能一口气放下「金额 + 分类格 + 账户格 + 数字键盘」，弹出系统键盘的次数
 * 只剩「写备注」这一种低频情况。账户/分类编辑仍然用居中对话框 —— 它们字段少、
 * 频次低，对话框更合适。
 *
 * ## 对齐规则
 *
 * 面板里所有元素共用一条 20dp 的左基线：类型切换、金额、日期/备注、分类格、
 * 账户格都从同一条线开始；分类与账户都用 4 列等宽网格，格子中心天然对齐。
 * 这是「看起来整齐」的来源，而不是靠各处试出来的 padding。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val motionEnabled = rememberMotionEnabled()
    val style = LocalThemeStyle.current
    val scope = rememberCoroutineScope()
    // 键盘的长按回调会活很久，必须让它读到「最新」的界面状态
    val liveState by rememberUpdatedState(uiState)
    // 拖拽关闭阈值换算成像素后比较：直接拿 dp 数值和像素值比会让阈值随屏幕密度漂移
    val dismissThresholdPx = with(LocalDensity.current) { 140.dp.toPx() }

    // 1f = 完全在屏幕下方，0f = 就位
    val appear = remember { Animatable(1f) }
    val drag = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun requestClose() {
        if (!closing) closing = true
    }

    LaunchedEffect(Unit) {
        // 进场用更弹的弹簧：落位时过冲约 5%，再回落，形成「Q 弹」手感。
        // 过冲方向由下面的 graphicsLayer 处理 —— 位移只走到就位，绝不继续上移。
        appear.animateTo(
            targetValue = 0f,
            animationSpec = if (motionEnabled) AfuMotion.panelEnter() else snap(),
        )
    }
    LaunchedEffect(closing) {
        if (closing) {
            appear.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200),
            )
            onBack()
        }
    }
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) requestClose()
    }

    BackHandler(enabled = true) { requestClose() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxPanelHeight = maxHeight * 0.94f

        // 遮罩：用主题的 onSurface 而不是纯黑 —— 纯黑在浅色主题上像「断电」，
        // 用深蓝灰会让页面像「被压暗」而不是「被挡住」。
        // 用 graphicsLayer 的 alpha 表达，避免每帧重组。
        //
        // 遮罩**不可点击关闭**：记账时手指常在面板边缘，点一下空白就丢输入是最高频的误触。
        // 关闭只保留「下滑、右上角 ❌、返回键」三种明确动作。
        val scrimColor = ModalScrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                // graphicsLayer 必须在 background 之前：它在修饰符链里更外层，
                // alpha 才能作用于「遮罩这一层」的全部绘制。反过来写的话
                // background 在外层、不受 alpha 影响，遮罩会变成全不透明的一整块。
                .graphicsLayer { alpha = (1f - appear.value).coerceIn(0f, 1f) }
                .background(scrimColor),
        )

        val panelShape = RoundedCornerShape(
            topStart = style.containerCorner,
            topEnd = style.containerCorner,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxPanelHeight)
                .graphicsLayer {
                    // 位移只负责「从屏幕下方滑到就位」：过冲（appear < 0）不再产生位移，
                    // 否则面板底边会离开屏幕下沿、露出底页。
                    translationY = appear.value.coerceAtLeast(0f) * size.height + drag.value
                    val overshoot = (-appear.value).coerceAtLeast(0f)
                    // 过冲转成以底边为锚的轻微缩放：视觉上是「顶部弹一下」，底部不动。
                    val bounceScale = 1f + overshoot * 0.35f
                    scaleX = bounceScale
                    scaleY = bounceScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (style.outlineWidth > 0.dp) {
                        Modifier.border(
                            width = style.outlineWidth,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = panelShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            // 拖拽条：只在这里绑手势，避免与列表滚动/键盘点击打架
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                drag.snapTo((drag.value + delta).coerceAtLeast(0f))
                            }
                        },
                        onDragStopped = {
                            scope.launch {
                                if (drag.value > dismissThresholdPx) {
                                    requestClose()
                                } else {
                                    drag.animateTo(
                                        targetValue = 0f,
                                        animationSpec = if (motionEnabled) AfuMotion.sheet() else snap(),
                                    )
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

            // ——— 以下内容共用 20dp 左基线 ———
            RecordHeader(
                isEditing = uiState.isEditing,
                type = uiState.type,
                onTypeChange = onTypeChange,
                onClose = { requestClose() },
                onDelete = { showDeleteConfirm = true },
            )

            AmountDisplay(
                amountInput = uiState.amountInput,
                type = uiState.type,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                MetaChip(
                    icon = Icons.Rounded.DateRange,
                    label = DateFormatter.friendlyDayLabel(uiState.occurredAt),
                    onClick = { showDatePicker = true },
                )
                MetaChip(
                    icon = Icons.Rounded.EditNote,
                    label = uiState.note.ifBlank { "添加备注" },
                    onClick = { showNoteEditor = true },
                    muted = uiState.note.isBlank(),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            FieldLabel("分类")
            CategoryGrid(
                categories = uiState.categories,
                selectedId = uiState.selectedCategoryId,
                onSelect = onCategorySelect,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = Spacing.xl),
            )

            FieldLabel("账户")
            if (uiState.accounts.isEmpty()) {
                Text(
                    text = "还没有可用账户，请先到「资产」页创建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
                )
            } else {
                AccountGrid(
                    accounts = uiState.accounts,
                    selectedId = uiState.selectedAccountId,
                    onSelect = onAccountSelect,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 2.dp),
                )
            }

            AmountKeypad(
                canSave = uiState.canSave,
                saveLabel = if (uiState.isEditing) "保存" else "记账",
                onKey = { key ->
                    // 手势回调是首次组合时创建的，金额必须读 liveState（最新值）
                    when (key) {
                        AMOUNT_KEY_BACKSPACE -> onAmountChange(liveState.amountInput.dropLast(1))
                        AMOUNT_KEY_SAVE -> if (liveState.canSave) onSave() else Unit
                        else -> onAmountChange(appendAmountKey(liveState.amountInput, key))
                    }
                },
                onClear = { onAmountChange("") },
            )
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initialSelectedMillis = uiState.occurredAt,
            onPick = onDateChange,
            onDismiss = { showDatePicker = false },
        )
    }

    if (showNoteEditor) {
        NoteEditorSheet(
            initial = uiState.note,
            onDismiss = { showNoteEditor = false },
            onConfirm = { note ->
                onNoteChange(note)
                showNoteEditor = false
            },
        )
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
}

/**
 * 备注浮层。
 *
 * 不用 Material 的描边对话框：备注是「顺手补一句」，不该长得像一张表单。
 * 这里做成一页轻量底部浮层：标题 + 单行输入 + 取消/完成，输入框只有浅色底、没有描边，
 * 键盘弹起时整体上移，不会把输入行压在键盘下面。
 */
@Composable
private fun NoteEditorSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val style = LocalThemeStyle.current
    val swallowClicks = remember { MutableInteractionSource() }

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
                        interactionSource = swallowClicks,
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "备注",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = { onConfirm(draft.trim()) },
                    ) { Text("完成", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(Spacing.xs))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = Spacing.md, vertical = 14.dp),
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "例如：同事聚餐",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
    }
}

/** 顶部：类型切换 + 关闭 / 删除。整行与内容同一条左基线。 */
@Composable
private fun RecordHeader(    isEditing: Boolean,
    type: TransType,
    onTypeChange: (TransType) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.xl, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeSwitch(type = type, onTypeChange = onTypeChange, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.xs))
        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除这笔记录",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭")
        }
    }
}

/** 支出 / 收入分段控件：选中态是实心胶囊，不是文字变色。 */
@Composable
private fun TypeSwitch(
    type: TransType,
    onTypeChange: (TransType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(TransType.EXPENSE to "支出", TransType.INCOME to "收入").forEach { (item, label) ->
            val selected = type == item
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CapsuleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onTypeChange(item) }
                    .padding(vertical = Spacing.sm),
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

/**
 * 金额显示：**左对齐**。
 *
 * 之前右对齐是把它当「计算结果」处理；但这个数字是用户正在输入的东西，
 * 左对齐才能和下面的分类、账户、日期形成一条线，视线不用来回跳。
 */
@Composable
private fun AmountDisplay(amountInput: String, type: TransType) {
    val display = amountInput.ifBlank { "0.00" }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = if (type == TransType.EXPENSE) "−" else "+",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = accent,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "¥",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = accent,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = display,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = if (amountInput.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xl, top = Spacing.xs, bottom = 2.dp),
    )
}

/**
 * 分类网格：每行 4 个等宽格子，圆形图标 + 名称。
 *
 * 用固定 Column + Row 而不是 `LazyVerticalGrid`：外层面板已有拖拽与滚动，
 * 再嵌一个可滚动网格容易出现滚动冲突；分类数量在几十个以内，
 * 一次性组合的开销远小于嵌套滚动的复杂度。
 */
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(max = 250.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        categories.chunked(4).forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                rowCategories.forEach { category ->
                    CategoryCell(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - rowCategories.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(category.colorArgb)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = AfuMotion.bouncy(),
        label = "categoryScale",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (selected) accent else accent.copy(alpha = 0.14f))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(category.iconKey),
                contentDescription = null,
                tint = if (selected) Color.White else accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 账户网格：与分类同一套 4 列格子，保证两栏对齐。 */
@Composable
private fun AccountGrid(
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        accounts.chunked(4).forEach { rowAccounts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                rowAccounts.forEach { account ->
                    AccountCell(
                        account = account,
                        selected = account.id == selectedId,
                        onClick = { onSelect(account.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - rowAccounts.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AccountCell(
    account: Account,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(account.colorArgb)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) accent else accent.copy(alpha = 0.14f))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = accountIcon(account.iconKey),
                contentDescription = null,
                tint = if (selected) Color.White else accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = account.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 金额输入规则（纯函数，便于单测）。
 *
 * - 数字：小数位已满 [Money.MAX_FRACTION_DIGITS] 位时忽略；整数位超过 [Money.MAX_INTEGER_DIGITS] 位时忽略
 * - 整数位与小数位**分别**限制，不把两者相加当成总位数 —— 否则 `446.22` 之后就没法继续补整数位了
 * - 小数点：已存在时忽略；空串输入时补 0
 * - 退格由调用方处理
 */
internal fun appendAmountKey(current: String, key: String): String {
    if (key == ".") {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }
    val digits = key.filter { it.isDigit() }
    if (digits.isEmpty()) return current
    val dotIndex = current.indexOf('.')
    if (dotIndex >= 0) {
        val decimals = current.length - dotIndex - 1
        if (decimals + digits.length > Money.MAX_FRACTION_DIGITS) return current
        return current + digits
    }
    val integerDigits = current.count { it.isDigit() }
    if (integerDigits + digits.length > Money.MAX_INTEGER_DIGITS) return current
    // 前导 0 只在一位时保留（"0" + "5" → "5"）
    if (current == "0") return digits.trimStart('0').ifEmpty { "0" }
    return current + digits
}
