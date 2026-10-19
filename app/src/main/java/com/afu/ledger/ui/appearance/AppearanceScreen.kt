package com.afu.ledger.ui.appearance

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.afu.ledger.data.prefs.DarkModeSetting
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.Money
import com.afu.ledger.ui.component.AfuSlider
import com.afu.ledger.ui.component.BackgroundLayer
import com.afu.ledger.ui.component.LocalCardOpacityPreview
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.SettingRow
import com.afu.ledger.ui.component.SettingSwitchRow
import com.afu.ledger.ui.component.drawWithScrim
import com.afu.ledger.ui.theme.AfuTheme
import com.afu.ledger.ui.theme.CardStyle
import com.afu.ledger.ui.theme.Decoration
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.MascotAvatar
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.ui.theme.ThemeColorPresets
import com.afu.ledger.ui.theme.ThemeStyle
import com.afu.ledger.ui.theme.ThemeStyles
import com.afu.ledger.ui.theme.themeStyle
import com.afu.ledger.util.MoneyFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 外观与主题。
 *
 * 从设置首页拆出来的一整页：风格、色相、深色模式、背景图四件事放在一起，
 * 因为它们互相影响（同一张背景图在不同风格下的可读性不同），
 * 分在设置页各处时用户要来回滚动才能比较效果。
 *
 * 顶部是一个**实时预览**：改任何一项，预览立刻变。用户不用退出页面才知道换完什么样。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    settings: ThemeSettings,
    onStyleChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onDarkModeChange: (DarkModeSetting) -> Unit,
    onBackgroundPick: (String) -> Unit,
    onBackgroundReset: () -> Unit,
    onBackgroundVisibilityChange: (Float) -> Unit,
    onBackgroundBlurChange: (Boolean) -> Unit,
    onBackgroundScrimChange: (Boolean) -> Unit,
    onCardOpacityChange: (Float) -> Unit,
    onBack: () -> Unit,
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

    // 外观页自身不透背景：页面叠在标签页之上，透出下层或背景图都会让文字发花。
    // 「调参数能看见效果」由缩略图承担 —— 它已经把可见度/模糊/遮罩都作用上去了。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("外观与主题") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "preview") {
                ThemePreviewCard(settings = settings)
            }

            item(key = "styles-header") {
                Text(
                    text = "风格",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
                )
            }

            item(key = "styles") {
                StyleGrid(
                    currentStyleId = settings.themeStyleId,
                    onStyleChange = onStyleChange,
                )
            }

            item(key = "colors-header") {
                Text(
                    text = "主题色",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
                )
            }

            item(key = "colors") {
                ColorGrid(
                    selectedId = settings.themeColorId,
                    onSelect = onThemeColorChange,
                )
            }

            item(key = "dark") {
                DarkModeSelector(
                    mode = settings.darkMode,
                    onChange = onDarkModeChange,
                )
            }

            item(key = "background") {
                BackgroundSection(
                    settings = settings,
                    onPick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onReset = onBackgroundReset,
                    onVisibilityChange = onBackgroundVisibilityChange,
                    onBlurChange = onBackgroundBlurChange,
                    onScrimChange = onBackgroundScrimChange,
                    onCardOpacityChange = onCardOpacityChange,
                )
            }
        }
        }
    }
}

/**
 * 实时预览。
 *
 * 用当前设置重新渲染一个微型界面（底色、卡片、金额、分类图标、吉祥物），
 * 让「换风格」这件事在点之前就有反馈，点之后立刻可见。
 */
@Composable
private fun ThemePreviewCard(settings: ThemeSettings) {
    val style = themeStyle(settings.themeStyleId)
    // 用设置里的深色模式重新包一层主题，预览才是「选完之后的样子」
    AfuTheme(settings = settings) {
        val scheme = MaterialTheme.colorScheme
        val decorationColor = scheme.primary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(style.containerCorner))
                .background(
                    Brush.linearGradient(
                        listOf(
                            scheme.background,
                            lerp(scheme.background, decorationColor, 0.12f),
                        ),
                    ),
                )
                .border(
                    width = if (style.cardStyle == CardStyle.PLAIN) 0.dp else 1.dp,
                    color = scheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(style.containerCorner),
                )
                .padding(Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "预览",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今日支出",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = "-${MoneyFormatter.formatWithSymbol(Money(2680))}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        MiniPill(text = "餐饮", color = scheme.primary)
                        MiniPill(text = "交通", color = scheme.tertiary)
                    }
                }
                PreviewSampleCard(style = style) {
                    if (style.showsMascot) {
                        MascotAvatar(size = 64.dp)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 预览里的一张「卡片」：体现描边 / 圆角 / 底色。 */
@Composable
private fun PreviewSampleCard(style: ThemeStyle, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(style.cardCorner))
            .background(scheme.surface)
            .then(
                if (style.cardStyle == CardStyle.PLAIN) {
                    Modifier
                } else {
                    Modifier.border(
                        width = style.outlineWidth.coerceAtLeast(1.dp),
                        color = scheme.outline.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(style.cardCorner),
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MiniPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** 风格选择：2 列卡片，每张卡是「风格缩略图 + 名字」。 */
@Composable
private fun StyleGrid(
    currentStyleId: String,
    onStyleChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ThemeStyles.chunked(2).forEach { rowStyles ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowStyles.forEach { style ->
                    StyleCard(
                        style = style,
                        selected = style.id == currentStyleId,
                        onClick = { onStyleChange(style.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowStyles.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: ThemeStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminanceIsDark()
    val background = if (isDark) style.darkBackground else style.lightBackground
    val surface = if (isDark) style.darkSurface else style.lightSurface
    val outline = if (isDark) style.darkOutline else style.lightOutline
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(style.containerCorner))
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) primary else outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(style.containerCorner),
            )
            .clickable(onClick = onClick)
            .padding(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f)
                .clip(RoundedCornerShape(style.cardCorner))
                .background(surface)
                .border(
                    width = if (style.cardStyle == CardStyle.PLAIN) 0.dp else style.outlineWidth,
                    color = outline.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(style.cardCorner),
                ),
        ) {
            // 极简的「界面缩略图」：一个色块 + 两个条 + 一个圆
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.85f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(outline.copy(alpha = 0.28f)),
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.22f)),
                        )
                    }
                }
            }
            if (style.showsMascot) {
                MascotAvatar(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                    size = 34.dp,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = style.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (isDark) style.darkOnSurface else style.lightOnSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = style.tagline,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) style.darkOnSurfaceVariant else style.lightOnSurfaceVariant,
        )
    }
}

/** 色板：4 列居中，选中用外环 + 对勾，不用粗黑描边。 */
@Composable
private fun ColorGrid(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ThemeColorPresets.chunked(4).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    Spacing.xl,
                    Alignment.CenterHorizontally,
                ),
            ) {
                rowPresets.forEach { preset ->
                    ColorDot(
                        label = preset.label,
                        color = preset.seed,
                        selected = preset.id == selectedId,
                        onClick = { onSelect(preset.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorDot(
    label: String,
    color: Color,
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
                .size(44.dp)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun DarkModeSelector(mode: DarkModeSetting, onChange: (DarkModeSetting) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DarkModeSetting.entries.forEach { item ->
            val selected = mode == item
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else Color.Transparent,
                    )
                    .clickable { onChange(item) }
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (item) {
                        DarkModeSetting.SYSTEM -> Icons.Rounded.PhoneIphone
                        DarkModeSetting.LIGHT -> Icons.Rounded.LightMode
                        DarkModeSetting.DARK -> Icons.Rounded.DarkMode
                    },
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = darkModeLabel(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun darkModeLabel(mode: DarkModeSetting): String = when (mode) {
    DarkModeSetting.SYSTEM -> "跟随"
    DarkModeSetting.LIGHT -> "浅色"
    DarkModeSetting.DARK -> "深色"
}

/** 背景图：预览 + 选择行 + 显示参数。 */
@Composable
private fun BackgroundSection(
    settings: ThemeSettings,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onVisibilityChange: (Float) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onScrimChange: (Boolean) -> Unit,
    onCardOpacityChange: (Float) -> Unit,
) {
    // 卡片不透明度的拖动预览：拖动过程中让所有卡片实时变化，松手后再落盘
    val cardOpacityPreview = LocalCardOpacityPreview.current
    val scope = rememberCoroutineScope()
    var lastPreviewAt by remember { mutableStateOf(0L) }
    // 提到最外层：缩略图上的示例卡和下面的滑块共用同一个值
    var cardValue by remember(settings.cardOpacity) {
        mutableFloatStateOf(settings.cardOpacity)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "背景",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
        )

        BackgroundPreview(
            uri = settings.backgroundUri,
            visibility = settings.backgroundVisibility,
            blur = settings.backgroundBlur,
            scrim = settings.backgroundScrim,
            cardOpacity = cardValue,
            onPick = onPick,
        )

        if (settings.backgroundUri != null) {
            var sliderValue by remember(settings.backgroundVisibility) {
                mutableFloatStateOf(settings.backgroundVisibility)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "背景可见度",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(sliderValue * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AfuSlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onVisibilityChange(sliderValue) },
                    valueRange = 0f..1f,
                )
                SettingSwitchRow(
                    icon = Icons.Rounded.Image,
                    title = "模糊背景",
                    checked = settings.backgroundBlur,
                    onCheckedChange = onBlurChange,
                )
                SettingDivider()
                SettingSwitchRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "暗色遮罩",
                    checked = settings.backgroundScrim,
                    onCheckedChange = onScrimChange,
                )
                SettingDivider()
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "内容卡片不透明度",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(cardValue * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AfuSlider(
                    value = cardValue,
                    onValueChange = {
                        cardValue = it
                        // 拖动中：只更新预览状态，不写磁盘。
                        // 节流到约 30fps —— 每帧都改会让所有半透明卡片与背景图重新合成，
                        // 真机上 90 分位帧耗时会翻倍（实测 8.8% 的帧超过 32ms）。
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastPreviewAt >= PREVIEW_THROTTLE_MS) {
                            lastPreviewAt = now
                            cardOpacityPreview?.value = it
                        }
                    },
                    onValueChangeFinished = {
                        // 松手：先把最终值推给预览，再落盘
                        cardOpacityPreview?.value = cardValue
                        lastPreviewAt = android.os.SystemClock.uptimeMillis()
                        onCardOpacityChange(cardValue)
                        scope.launch {
                            delay(350)
                            cardOpacityPreview?.value = null
                        }
                    },
                    valueRange = ThemeSettings.MIN_CARD_OPACITY..1f,
                )
            }
        }

        if (settings.backgroundUri != null) {
            SettingRow(
                icon = Icons.Rounded.Palette,
                title = "恢复默认背景",
                subtitle = "回到主题色渐变，并重置显示参数",
                onClick = onReset,
            )
        }
    }
}

/**
 * 背景预览：**带当前参数的实时缩略图**。
 *
 * 只显示原图是不够的：用户调「可见度」时缩略图不变，等于盲调。
 * 这里把可见度（alpha）、模糊、遮罩都作用到缩略图上，调滑块就能看见结果。
 * 数值口径与 [BackgroundLayer] 保持一致，避免「预览和实际不一样」。
 */
@Composable
private fun BackgroundPreview(
    uri: String?,
    visibility: Float,
    blur: Boolean,
    scrim: Boolean,
    cardOpacity: Float,
    onPick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val style = LocalThemeStyle.current
    val background = MaterialTheme.colorScheme.background
    val isDark = (0.299f * background.red + 0.587f * background.green + 0.114f * background.blue) < 0.5f
    val clamped = visibility.coerceIn(0f, 1f)
    val userScrim = if (scrim) (1f - clamped) * 0.92f else 0f
    val scrimAlpha = if (isDark) {
        maxOf(userScrim, 0.55f * (1f - clamped * 0.4f))
    } else {
        userScrim
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(style.containerCorner))
            // 不透明底：缩略图要「模拟」页面效果，不能和页面自己的背景图叠在一起
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        // 主题渐变垫底：可见度低时露出的是它，与真实页面的表现一致
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.06f)),
                    ),
                ),
        )
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "背景预览",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // 可见度直接作用到缩略图：0 = 几乎看不见，1 = 原图
                    .graphicsLayer { alpha = 0.18f + 0.82f * clamped }
                    .then(
                        if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(10.dp)
                        } else {
                            Modifier
                        },
                    )
                    .drawWithScrim(scrimAlpha, background),
            )
            // 示例卡片：直接叠在背景缩略图上，拖动「内容卡片不透明度」时它实时变透，
            // 这样调参数不再是盲调（以前只有退出去看主界面才知道效果）。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.sm)
                    .size(width = 156.dp, height = 58.dp)
                    .graphicsLayer { alpha = cardOpacity.coerceIn(0.2f, 1f) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "内容卡片",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${(cardOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // 参数标签，调完一眼确认数值
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.sm)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = Spacing.sm, vertical = 2.dp),
            ) {
                Text(
                    text = "${(clamped * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "点这里选一张图",
                    style = MaterialTheme.typography.bodyMedium,
                    color = primary,
                )
            }
        }
    }
}

private fun Color.luminanceIsDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/** 拖动时的预览节流间隔：约 30fps，兼顾「跟手」与「不掉帧」。 */
private const val PREVIEW_THROTTLE_MS = 32L
