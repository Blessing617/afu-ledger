package com.afu.ledger.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.afu.ledger.domain.model.Money
import com.afu.ledger.util.MoneyFormatter

/** 收支金额文本：支出用暖色、收入用绿色，符号前置。 */
@Composable
fun AmountText(
    money: Money,
    isExpense: Boolean,
    modifier: Modifier = Modifier,
    showSign: Boolean = true,
) {
    val color: Color = if (isExpense) {
        MaterialTheme.colorScheme.error
    } else {
        Color(0xFF2E9E5B)
    }
    val text = if (showSign) {
        MoneyFormatter.formatSigned(money, isExpense)
    } else {
        MoneyFormatter.formatWithSymbol(money)
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleMedium,
    )
}
