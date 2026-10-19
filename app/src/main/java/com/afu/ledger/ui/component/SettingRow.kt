package com.afu.ledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.Spacing
import com.afu.ledger.ui.theme.dashedHLine

/**
 * 圆形图标容器：设置行、账户行、分类格共用。
 *
 * 统一尺寸与底色算法，避免「有的行图标 20dp、有的 22dp，有的圆、有的方」。
 */
@Composable
fun AfuIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = tint.copy(alpha = 0.14f),
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    val style = LocalThemeStyle.current
    Box(
        modifier = modifier
            .size(size)
            .background(background, shape)
            .then(
                // 描边主题下给图标底加一圈细线：贴纸感，也让浅色底在浅背景上立得住
                if (style.outlineWidth > 0.dp) {
                    Modifier.border(
                        width = 1.2.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 分组标题：小号、加字距，和内容拉开层级。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * 行式设置项。
 *
 * 之前设置页里「管理分类」「从相册选择」都是裸文字按钮，一排小字堆在一起
 * 既没有点击暗示、也看不出彼此关系。行式的价值在于：左侧图标给出「这是什么」，
 * 中间标题给出「做什么」，右侧摘要给出「现在是什么状态」，一眼扫完。
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AfuIconBadge(icon = icon, tint = iconTint)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        detail?.let {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 带开关的设置行。开关行没有箭头，避免「点了会进下一页」的误判。 */
@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailing = {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/** 组内分隔线：比卡片更轻，只用来分行。 */
@Composable
fun SettingDivider(modifier: Modifier = Modifier) {
    // 统一用实线：虚线分隔在滚动时每帧都要重新切段，几十行叠在一起会明显拖慢绘制
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = Spacing.lg)
            .height(if (LocalThemeStyle.current.outlineWidth > 0.dp) 1.dp else 0.6.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)),
    )
}

/** 一组设置：一张卡片包住若干行，行间细分隔线。 */
@Composable
fun SettingGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AfuCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
    }
}
