package com.afu.ledger.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/** 分类 iconKey → 图标。未识别的 key 回落到「更多」。 */
fun categoryIcon(iconKey: String): ImageVector = when (iconKey) {
    "restaurant" -> Icons.Outlined.Restaurant
    "directions_bus" -> Icons.Outlined.DirectionsBus
    "shopping_bag" -> Icons.Outlined.ShoppingBag
    "home" -> Icons.Outlined.Home
    "sports_esports" -> Icons.Outlined.SportsEsports
    "medical_services" -> Icons.Outlined.MedicalServices
    "school" -> Icons.Outlined.School
    "redeem" -> Icons.Outlined.Redeem
    "payments" -> Icons.Outlined.Payments
    "emoji_events" -> Icons.Outlined.EmojiEvents
    "trending_up" -> Icons.Outlined.TrendingUp
    "account_balance_wallet" -> Icons.Outlined.AccountBalanceWallet
    "credit_card" -> Icons.Outlined.CreditCard
    "savings" -> Icons.Outlined.Savings
    else -> Icons.Outlined.MoreHoriz
}

/** 账户可选的图标 key。 */
val AvailableAccountIcons: List<String> = listOf(
    "account_balance_wallet", "credit_card", "savings", "payments",
)

/** 可供用户挑选的分类图标 key 列表。 */
val AvailableCategoryIcons: List<String> = listOf(
    "restaurant", "directions_bus", "shopping_bag", "home", "sports_esports",
    "medical_services", "school", "redeem", "payments", "emoji_events",
    "trending_up", "more_horiz",
)
