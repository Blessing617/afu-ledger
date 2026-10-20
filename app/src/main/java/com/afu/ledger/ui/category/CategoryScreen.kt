package com.afu.ledger.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.component.AfuCard
import com.afu.ledger.ui.component.AfuIconBadge
import com.afu.ledger.ui.component.AvailableCategoryIcons
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.categoryIcon
import com.afu.ledger.ui.theme.CapsuleShape
import com.afu.ledger.ui.theme.InputShape
import com.afu.ledger.ui.theme.Spacing

private val categoryColors = listOf(
    0xFFF06292, 0xFF4FC3F7, 0xFF4DB6AC, 0xFF9575CD,
    0xFFFF8A65, 0xFF8BC34A, 0xFFFFCA28, 0xFF90A4AE,
)

/**
 * 分类管理。
 *
 * 与设置页同一套行式结构：分组标题 → 一张卡片 → 若干行。
 * 每行左侧是分类自己的图标与底色（与首页、记账面板完全一致），
 * 这样用户在管理页看到的图标就是他在列表里看到的那个。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    uiState: CategoryUiState,
    onCreate: (TransType, String, String, Long) -> Unit,
    onRename: (Category, String) -> Unit,
    onDelete: (Category) -> Unit,
    onMessageConsumed: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Category?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf(TransType.EXPENSE) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item(key = "expense-title") { GroupTitle("支出分类") }
                item(key = "expense-list") {
                    CategoryGroup(
                        categories = uiState.expense,
                        onClick = {
                            editing = it
                            showEditor = true
                        },
                        onDelete = onDelete,
                    )
                }
                item(key = "income-title") { GroupTitle("收入分类") }
                item(key = "income-list") {
                    CategoryGroup(
                        categories = uiState.income,
                        onClick = {
                            editing = it
                            showEditor = true
                        },
                        onDelete = onDelete,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.xl, bottom = Spacing.xl)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        editing = null
                        createType = TransType.EXPENSE
                        showEditor = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新增分类",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    if (showEditor) {
        CategoryEditorDialog(
            existing = editing,
            initialType = createType,
            onDismiss = { showEditor = false },
            onConfirm = { type, name, iconKey, color ->
                val current = editing
                if (current == null) {
                    onCreate(type, name, iconKey, color)
                } else {
                    onRename(current.copy(iconKey = iconKey, colorArgb = color), name)
                }
                showEditor = false
            },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.xs),
    )
}

@Composable
private fun CategoryGroup(
    categories: List<Category>,
    onClick: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    if (categories.isEmpty()) return
    AfuCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        categories.forEachIndexed { index, category ->
            if (index > 0) SettingDivider()
            CategoryRow(
                category = category,
                onClick = { onClick(category) },
                onDelete = { onDelete(category) },
            )
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit, onDelete: () -> Unit) {
    val accent = Color(category.colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AfuIconBadge(
            icon = categoryIcon(category.iconKey),
            size = 40.dp,
            iconSize = 21.dp,
            tint = accent,
            background = accent.copy(alpha = 0.16f),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (category.preset) {
            Text(
                text = "预置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .padding(horizontal = Spacing.sm, vertical = 2.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    existing: Category?,
    initialType: TransType,
    onDismiss: () -> Unit,
    onConfirm: (TransType, String, String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: AvailableCategoryIcons.first()) }
    var colorArgb by remember { mutableLongStateOf(existing?.colorArgb ?: categoryColors.first()) }
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
                        text = if (existing == null) "新增分类" else "编辑分类",
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
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    shape = InputShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (existing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(TransType.EXPENSE to "支出", TransType.INCOME to "收入")
                            .forEach { (item, label) ->
                                val selected = type == item
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CapsuleShape)
                                        .background(
                                            if (selected) accent.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        )
                                        .clickable { type = item }
                                        .padding(vertical = Spacing.sm),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) accent
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AvailableCategoryIcons.forEach { key ->
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

                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    categoryColors.forEach { color ->
                        val selected = color == colorArgb
                        Box(
                            modifier = Modifier
                                .size(30.dp)
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
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(type, name, iconKey, colorArgb) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
