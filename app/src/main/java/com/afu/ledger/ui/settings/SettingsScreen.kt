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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SectionCard(icon = Icons.Outlined.Palette, title = "主题色") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ThemeColorPresets.forEach { preset ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp).clickable { onThemeColorChange(preset.id) },
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(40.dp)
                                .clip(CircleShape)
                                .background(preset.seed)
                                .border(
                                    width = if (preset.id == settings.themeColorId) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (preset.id == settings.themeColorId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

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

        SectionCard(icon = Icons.Outlined.Image, title = "背景图") {
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
                ) { Text("从相册选择") }
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

        SectionCard(icon = Icons.Outlined.Category, title = "分类管理") {
            Text(
                text = "新增、重命名或删除收支分类",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onManageCategories) { Text("管理分类") }
        }

        // 自动记账区块最重（多组 FlowRow + 日志列表）：
        // 延迟到首帧之后再组合，避免每次切到设置页时卡一下
        var heavyReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { heavyReady = true }
        if (heavyReady) {
            AutoLedgerSection(
                settings = autoSettings,
                serviceEnabled = serviceEnabled,
                accounts = accounts,
                categories = expenseCategories,
                logEntries = logEntries,
                ocrStatus = ocrStatus,
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
