package com.afu.ledger.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.afu.ledger.auto.AutoLedgerSettings
import com.afu.ledger.auto.OcrStatus
import com.afu.ledger.auto.RecognitionEntry
import com.afu.ledger.data.prefs.DarkModeSetting
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.ui.theme.ThemeColorPresets
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: ThemeSettings,
    autoSettings: AutoLedgerSettings,
    serviceEnabled: Boolean,
    accounts: List<Account>,
    expenseCategories: List<Category>,
    logEntries: List<RecognitionEntry>,
    ocrStatus: OcrStatus,
    wasEverEnabled: Boolean,
    onThemeColorChange: (String) -> Unit,
    onDarkModeChange: (DarkModeSetting) -> Unit,
    onBackgroundPick: (String) -> Unit,
    onBackgroundReset: () -> Unit,
    onBackgroundVisibilityChange: (Float) -> Unit,
    onBackgroundBlurChange: (Boolean) -> Unit,
    onBackgroundScrimChange: (Boolean) -> Unit,
    onCardOpacityChange: (Float) -> Unit,
    onManageCategories: () -> Unit,
    onAutoEnabledChange: (Boolean) -> Unit,
    onDefaultAccountChange: (Long?) -> Unit,
    onDefaultCategoryChange: (Long?) -> Unit,
    onRecognitionLogChange: (Boolean) -> Unit,
    onOcrEnabledChange: (Boolean) -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // 持久化读取权限，重启后背景图仍然可用
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onBackgroundPick(uri.toString())
        }
    }

    // 定时器在 LazyColumn 之外只订阅一次：放进 item 会让 5 秒一次的 tick
    // 同时失效所有可见 item，快速滑动时表现为掉帧（官方 Compose 性能指南明确禁止）。
    val keepAliveState by rememberKeepAliveState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 每个区块都带稳定 key + contentType：
        // key 让 LazyColumn 按身份复用 item 而不是按索引；
        // contentType 让滚动时能复用同类型 item 的组合槽位，减少甩动瞬间的重建开销。
        item(key = "theme-color", contentType = "settings-section") {
        SectionCard(icon = Icons.Outlined.Palette, title = "主题色") {
            // 固定 4 列的居中网格。
            //
            // 原先用 FlowRow + 固定 64dp 宽度：8 个色点在某些屏宽下排成 5 + 3，
            // 最后一行贴着左边，看上去像没对齐 —— 这就是「八个颜色不居中很难看」的由来。
            // 换成「按 4 个一行切分 + 行内居中」后，任何屏宽都是整齐的两行。
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ThemeColorPresets.chunked(COLUMNS).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            Spacing.xl,
                            Alignment.CenterHorizontally,
                        ),
                    ) {
                        rowPresets.forEach { preset ->
                            ThemeColorDot(
                                preset = preset,
                                selected = preset.id == settings.themeColorId,
                                onClick = { onThemeColorChange(preset.id) },
                            )
                        }
                    }
                }
            }
        }
        }

        item(key = "dark-mode", contentType = "settings-section") {
        SectionCard(icon = Icons.Outlined.Palette, title = "深色模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DarkModeSetting.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.darkMode == mode,
                        onClick = { onDarkModeChange(mode) },
                        label = { Text(darkModeLabel(mode)) },
                    )
                }
            }
        }
        }

        item(key = "background", contentType = "settings-section") {
        SectionCard(icon = Icons.Outlined.Image, title = "背景图") {
            BackgroundPreview(
                uri = settings.backgroundUri,
                onPick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = if (settings.backgroundUri == null) "当前使用主题色渐变背景" else "已设置自定义背景图",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) { Text(if (settings.backgroundUri == null) "从相册选择" else "更换图片") }
                if (settings.backgroundUri != null) {
                    TextButton(onClick = onBackgroundReset) { Text("恢复默认") }
                }
            }

            if (settings.backgroundUri != null) {
                Spacer(Modifier.height(Spacing.sm))
                // 拖动期间只更新本地状态，松手才写 DataStore：
                // 否则每一帧都触发一次磁盘写入 + 全树重组，滑块会明显发涩
                var sliderValue by remember(settings.backgroundVisibility) {
                    mutableFloatStateOf(settings.backgroundVisibility)
                }
                Text(
                    text = "可见度 ${(sliderValue * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onBackgroundVisibilityChange(sliderValue) },
                    valueRange = 0f..1f,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("模糊背景", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.backgroundBlur,
                        onCheckedChange = onBackgroundBlurChange,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("暗色遮罩", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.backgroundScrim,
                        onCheckedChange = onBackgroundScrimChange,
                    )
                }

                Spacer(Modifier.height(Spacing.md))
                // 内容卡片不透明度：完全由用户决定「要图还是要字」
                var cardValue by remember(settings.cardOpacity) {
                    mutableFloatStateOf(settings.cardOpacity)
                }
                Text(
                    text = "内容卡片不透明度 ${(cardValue * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = cardValue,
                    onValueChange = { cardValue = it },
                    onValueChangeFinished = { onCardOpacityChange(cardValue) },
                    valueRange = ThemeSettings.MIN_CARD_OPACITY..1f,
                )
            }
        }
        }

        item(key = "category", contentType = "settings-section") {
        SectionCard(icon = Icons.Outlined.Category, title = "分类管理") {
            Text(
                text = "新增、重命名或删除收支分类",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onManageCategories) { Text("管理分类") }
        }
        }

        item(key = "auto-ledger", contentType = "settings-section") {
            AutoLedgerSection(
                settings = autoSettings,
                serviceEnabled = serviceEnabled,
                accounts = accounts,
                categories = expenseCategories,
                logEntries = logEntries,
                ocrStatus = ocrStatus,
                keepAliveState = keepAliveState,
                wasEverEnabled = wasEverEnabled,
                onEnabledChange = onAutoEnabledChange,
                onDefaultAccountChange = onDefaultAccountChange,
                onDefaultCategoryChange = onDefaultCategoryChange,
                onRecognitionLogChange = onRecognitionLogChange,
                onOcrEnabledChange = onOcrEnabledChange,
                onClearLog = onClearLog,
            )
        }
    }
}

private fun darkModeLabel(mode: DarkModeSetting): String = when (mode) {
    DarkModeSetting.SYSTEM -> "跟随系统"
    DarkModeSetting.LIGHT -> "浅色"
    DarkModeSetting.DARK -> "深色"
}

/** 主题色板一行放几个。固定值才能保证「两行各四个、整体居中」。 */
private const val COLUMNS = 4

/** 一个主题色点：色块 + 名字，整块可点。 */
@Composable
private fun ThemeColorDot(
    preset: com.afu.ledger.ui.theme.ThemeColorPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(44.dp)
                .clip(CircleShape)
                .background(preset.seed)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                ),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * 背景预览：选图前先看见「会变成什么样」。
 *
 * 之前只有一行「当前使用主题色渐变背景」的文字 + 两个文字按钮，
 * 用户选完图才知道效果。现在缩略图就是入口，点图即换图。
 */
@Composable
private fun BackgroundPreview(uri: String?, onPick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.06f)),
                ),
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "背景预览",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "点这里选一张图",
                style = MaterialTheme.typography.bodyMedium,
                color = primary,
            )
        }
    }
}
