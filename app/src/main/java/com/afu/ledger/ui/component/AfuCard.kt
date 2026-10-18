package com.afu.ledger.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afu.ledger.ui.theme.CardShape

/**
 * 应用统一卡片：大圆角、无边框、无阴影。
 *
 * 不用阴影：卡片是半透明的（不透明度由用户调），阴影会沿着圆角显出一圈色边，
 * 在背景图上尤其明显。层级感交给底色与间距，不靠投影。
 */
@Composable
fun AfuCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalSurfaceAlpha.current),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
