package com.afu.ledger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.CardShape

/**
 * 空状态：图标 + 标题 + 说明 + 可选操作。
 *
 * 卡片只包住内容本身（不铺满整行），这样背景图在两侧能露出来；
 * 同时保持不透明底色，保证任何背景下文字都可读。
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.SentimentSatisfiedAlt,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, CardShape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}
