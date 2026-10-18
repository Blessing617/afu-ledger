package com.afu.ledger.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标的完整集合。
 *
 * ## 为什么要「多」
 *
 * 原先只有 12 个，覆盖不到「宠物 / 咖啡 / 健身 / 房租 / 通勤」这类高频支出，
 * 用户建完分类只能从「更多」里挑，最后全场都是省略号图标 —— 首页看起来像没做图标。
 *
 * 图标全部取自 Material Icons（已随 APK 打包，无网络依赖）。
 * 键名是持久化在数据库里的，**不要改名**，只能新增。
 */
fun categoryIcon(iconKey: String): ImageVector = when (iconKey) {
    "restaurant" -> Icons.Outlined.Restaurant
    "fastfood" -> Icons.Outlined.Fastfood
    "local_cafe" -> Icons.Outlined.LocalCafe
    "bakery_dining" -> Icons.Outlined.BakeryDining
    "cake" -> Icons.Outlined.Cake
    "directions_bus" -> Icons.Outlined.DirectionsBus
    "train" -> Icons.Outlined.Train
    "directions_car" -> Icons.Outlined.DirectionsCar
    "flight" -> Icons.Outlined.Flight
    "shopping_bag" -> Icons.Outlined.ShoppingBag
    "shopping_cart" -> Icons.Outlined.ShoppingCart
    "home" -> Icons.Outlined.Home
    "hotel" -> Icons.Outlined.Hotel
    "local_laundry_service" -> Icons.Outlined.LocalLaundryService
    "sports_esports" -> Icons.Outlined.SportsEsports
    "movie" -> Icons.Outlined.Movie
    "sports_basketball" -> Icons.Outlined.SportsBasketball
    "fitness_center" -> Icons.Outlined.FitnessCenter
    "medical_services" -> Icons.Outlined.MedicalServices
    "local_hospital" -> Icons.Outlined.LocalHospital
    "school" -> Icons.Outlined.School
    "menu_book" -> Icons.Outlined.MenuBook
    "redeem" -> Icons.Outlined.Redeem
    "card_giftcard" -> Icons.Outlined.CardGiftcard
    "celebration" -> Icons.Outlined.Celebration
    "payments" -> Icons.Outlined.Payments
    "attach_money" -> Icons.Outlined.AttachMoney
    "currency_exchange" -> Icons.Outlined.CurrencyExchange
    "work" -> Icons.Outlined.Work
    "emoji_events" -> Icons.Outlined.EmojiEvents
    "trending_up" -> Icons.Outlined.TrendingUp
    "pets" -> Icons.Outlined.Pets
    "phone_iphone" -> Icons.Outlined.PhoneIphone
    "account_balance_wallet" -> Icons.Outlined.AccountBalanceWallet
    "credit_card" -> Icons.Outlined.CreditCard
    "savings" -> Icons.Outlined.Savings
    else -> Icons.Outlined.MoreHoriz
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
