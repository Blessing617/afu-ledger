package com.afu.ledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.Spacing

/**
 * 带图标的横向单选条。
 *
 * 设置页原先把「默认账户 / 默认分类」做成一排纯文字 FilterChip：
 * 用户看到的是「WeChat」「餐饮」这些字，要靠读才知道是哪个账户/分类；
 * 而同一个账户在资产页、分类在记账页都是有图标的 —— 两处认知对不上。
 *
 * 这里统一成「圆形图标 + 名称」，与记账面板里的选择器同构。
 */
@Composable
fun <T> IconChoiceRow(
    items: List<T>,
    selectedId: Long?,
    idOf: (T) -> Long,
    labelOf: (T) -> String,
    iconOf: (T) -> ImageVector,
    colorOf: (T) -> Color,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "暂无可选项",
) {
    if (items.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items.forEach { item ->
            val selected = idOf(item) == selectedId
            val accent = colorOf(item)
            ChoiceCell(
                label = labelOf(item),
                icon = iconOf(item),
                accent = accent,
                selected = selected,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun ChoiceCell(
    label: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, accent.copy(alpha = 0.7f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
