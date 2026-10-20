package com.afu.ledger.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标的完整集合。
 *
 * ## 为什么是 Rounded 变体
 *
 * 之前混用 Outlined：笔画细、端点方、转角硬，一屏里同时出现线性图标和实心图标时
 * 像「三种不同的应用拼在一起」。Material Icons 的 Rounded 变体笔画圆头圆角，
 * 与应用的圆润造型、大圆角卡片一致，且**不新增任何依赖**（`material-icons-extended`
 * 已在依赖里），也不用引 SVG 转换工具链。
 *
 * 图标全部取自 Material Icons（已随 APK 打包，无网络依赖）。
 * 键名是持久化在数据库里的，**不要改名**，只能新增。
 */
fun categoryIcon(iconKey: String): ImageVector = when (iconKey) {
    "restaurant" -> Icons.Rounded.Restaurant
    "fastfood" -> Icons.Rounded.Fastfood
    "local_cafe" -> Icons.Rounded.LocalCafe
    "bakery_dining" -> Icons.Rounded.BakeryDining
    "cake" -> Icons.Rounded.Cake
    "directions_bus" -> Icons.Rounded.DirectionsBus
    "train" -> Icons.Rounded.Train
    "directions_car" -> Icons.Rounded.DirectionsCar
    "flight" -> Icons.Rounded.Flight
    "shopping_bag" -> Icons.Rounded.ShoppingBag
    "shopping_cart" -> Icons.Rounded.ShoppingCart
    "home" -> Icons.Rounded.Home
    "hotel" -> Icons.Rounded.Hotel
    "local_laundry_service" -> Icons.Rounded.LocalLaundryService
    "sports_esports" -> Icons.Rounded.SportsEsports
    "movie" -> Icons.Rounded.Movie
    "sports_basketball" -> Icons.Rounded.SportsBasketball
    "fitness_center" -> Icons.Rounded.FitnessCenter
    "medical_services" -> Icons.Rounded.MedicalServices
    "local_hospital" -> Icons.Rounded.LocalHospital
    "school" -> Icons.Rounded.School
    "menu_book" -> Icons.Rounded.MenuBook
    "redeem" -> Icons.Rounded.Redeem
    "card_giftcard" -> Icons.Rounded.CardGiftcard
    "celebration" -> Icons.Rounded.Celebration
    "payments" -> Icons.Rounded.Payments
    "attach_money" -> Icons.Rounded.AttachMoney
    "currency_exchange" -> Icons.Rounded.CurrencyExchange
    "work" -> Icons.Rounded.Work
    "emoji_events" -> Icons.Rounded.EmojiEvents
    "trending_up" -> Icons.Rounded.TrendingUp
    "pets" -> Icons.Rounded.Pets
    "phone_iphone" -> Icons.Rounded.PhoneIphone
    "account_balance_wallet" -> Icons.Rounded.AccountBalanceWallet
    "credit_card" -> Icons.Rounded.CreditCard
    "savings" -> Icons.Rounded.Savings
    else -> Icons.Rounded.MoreHoriz
}

/** 账户图标。与分类共用映射，未匹配时回退到钱包。 */
fun accountIcon(iconKey: String): ImageVector = when (iconKey) {
    "credit_card" -> Icons.Rounded.CreditCard
    "savings" -> Icons.Rounded.Savings
    "payments" -> Icons.Rounded.Payments
    else -> Icons.Rounded.AccountBalanceWallet
}

/** 账户可选的图标 key。 */
val AvailableAccountIcons: List<String> = listOf(
    "account_balance_wallet", "credit_card", "savings", "payments",
)

/**
 * 可供用户挑选的分类图标 key 列表。
 *
 * 顺序按「使用频率」排：吃饭、通勤、购物排在最前，挑的时候不用滑。
 */
val AvailableCategoryIcons: List<String> = listOf(
    "restaurant", "fastfood", "local_cafe", "cake",
    "directions_bus", "train", "directions_car", "flight",
    "shopping_bag", "shopping_cart", "home", "hotel",
    "sports_esports", "movie", "sports_basketball", "fitness_center",
    "medical_services", "local_hospital", "school", "menu_book",
    "redeem", "card_giftcard", "celebration", "payments",
    "attach_money", "currency_exchange", "work", "emoji_events",
    "trending_up", "pets", "phone_iphone", "more_horiz",
)
