package com.afu.ledger.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.MascotChibi
import com.afu.ledger.ui.theme.Spacing

/**
 * 空状态：插画 + 标题 + 说明 + 可选操作。
 *
 * 「空」是最容易被做成「什么都没有」的状态。实际上它出现的时机恰恰是用户最需要
 * 被引导的时候（还没建账户、今天还没记账），所以这里给插画留了位置：
 * 大肥鱼风格用吉祥物，其余风格用图标。
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.SentimentSatisfiedAlt,
    action: (@Composable () -> Unit)? = null,
) {
    val style = LocalThemeStyle.current
    AfuCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.xl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (style.showsMascot) {
                MascotChibi(height = 132.dp)
            } else {
                AfuIconBadge(
                    icon = icon,
                    size = 56.dp,
                    iconSize = 28.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
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
