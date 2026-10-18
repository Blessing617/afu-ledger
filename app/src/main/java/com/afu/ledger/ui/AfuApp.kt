package com.afu.ledger.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.afu.ledger.data.AppContainer
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.ui.assets.AccountEditorDialog
import com.afu.ledger.ui.assets.AssetsScreen
import com.afu.ledger.ui.assets.AssetsViewModel
import com.afu.ledger.ui.category.CategoryScreen
import com.afu.ledger.ui.category.CategoryViewModel
import com.afu.ledger.ui.component.BackgroundLayer
import com.afu.ledger.ui.home.HomeScreen
import com.afu.ledger.ui.home.HomeViewModel
import com.afu.ledger.ui.record.RecordScreen
import com.afu.ledger.ui.record.RecordViewModel
import com.afu.ledger.ui.settings.SettingsScreen
import com.afu.ledger.ui.settings.SettingsViewModel
import com.afu.ledger.ui.settings.rememberAutoLedgerServiceEnabled

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab("home", "记账", Icons.Outlined.EditNote),
    BottomTab("assets", "资产", Icons.Outlined.AccountBalanceWallet),
    BottomTab("settings", "设置", Icons.Outlined.Settings),
)

private fun tabIndex(route: String?): Int {
    val index = bottomTabs.indexOfFirst { it.route == route }
    return if (index >= 0) index else 0
}

@Composable
fun AfuApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val overlayRoute = backStackEntry?.destination?.route
    val isOverlay = overlayRoute != null && overlayRoute != TAB_HOST

    var currentTab by rememberSaveable { mutableStateOf(bottomTabs.first().route) }

    val themeSettings by container.themePreferences.settings
        .collectAsStateWithLifecycle(initialValue = ThemeSettings())

    Box(modifier = Modifier.fillMaxSize()) {
        // 自定义背景图：可见度/模糊/遮罩可调，加载完成淡入
        themeSettings.backgroundUri?.let { uri ->
            BackgroundLayer(
                uri = uri,
                visibility = themeSettings.backgroundVisibility,
                blur = themeSettings.backgroundBlur,
                scrim = themeSettings.backgroundScrim,
            )
        }

        Scaffold(
            containerColor = if (themeSettings.backgroundUri == null) {
                MaterialTheme.colorScheme.background
            } else {
                Color.Transparent
            },
            bottomBar = {
                if (!isOverlay) {
                    NavigationBar(
                        containerColor = if (themeSettings.backgroundUri == null) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        },
                    ) {
                        bottomTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab.route == currentTab,
                                onClick = { currentTab = tab.route },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // 三个标签页常驻组合：切换时只动 alpha，不重建页面。
                // 这是「点设置就卡一下」的根治手段——设置页的 UI 树最重。
                TabPage(route = "home", current = currentTab) {
                    HomeRoute(
                        container = container,
                        navController = navController,
                        onNavigateToAssets = { currentTab = "assets" },
                    )
                }
                TabPage(route = "assets", current = currentTab) {
                    AssetsRoute(container = container)
                }
                TabPage(route = "settings", current = currentTab) {
                    SettingsRoute(container = container, navController = navController)
                }

                // 二级页面：仍由 NavHost 管理，只负责「层级进入/返回」的转场
                NavHost(
                    navController = navController,
                    startDestination = TAB_HOST,
                    enterTransition = {
                        slideInVertically(tween(300)) { full -> full / 3 } + fadeIn(tween(300))
                    },
                    exitTransition = { fadeOut(tween(90)) },
                    popEnterTransition = { fadeIn(tween(210)) },
                    popExitTransition = {
                        slideOutVertically(tween(300)) { full -> full / 3 } + fadeOut(tween(240))
                    },
                ) {
                    composable(TAB_HOST) { /* 占位：标签页在 NavHost 之外 */ }
                    composable(
                        route = "record?transactionId={transactionId}",
                        arguments = listOf(
                            navArgument("transactionId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                        ),
                    ) { entry ->
                        val id = entry.arguments?.getLong("transactionId") ?: -1L
                        RecordRoute(
                            container = container,
                            editingTransactionId = if (id > 0L) id else null,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("categories") {
                        CategoryRoute(container = container, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

/** 一个常驻组合的标签页：不可见时只把 alpha 归零，组合树保留。 */
@Composable
private fun TabPage(
    route: String,
    current: String,
    content: @Composable () -> Unit,
) {
    val visible = route == current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "tabAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer { this.alpha = alpha }
            .then(if (visible) Modifier else Modifier.pointerInput(Unit) { blockAllInput() }),
    ) {
        content()
    }
}

/** 不可见页面吞掉所有触摸，避免事件穿透到下层页面。 */
private suspend fun PointerInputScope.blockAllInput() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

private const val TAB_HOST = "tab-host"


@Composable
private fun HomeRoute(
    container: AppContainer,
    navController: NavHostController,
    onNavigateToAssets: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    transactionRepository = container.transactionRepository,
                    categoryRepository = container.categoryRepository,
                    accountRepository = container.accountRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onAddTransaction = { navController.navigate("record") },
        onCreateAccount = onNavigateToAssets,
        onTransactionClick = { id -> navController.navigate("record?transactionId=$id") },
    )
}

@Composable
private fun AssetsRoute(container: AppContainer) {
    val viewModel: AssetsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AssetsViewModel(container.accountRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<AccountWithBalance?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    AssetsScreen(
        uiState = uiState,
        onAccountClick = { item ->
            editing = item
            showEditor = true
        },
        onAddAccount = {
            editing = null
            showEditor = true
        },
    )

    if (showEditor) {
        AccountEditorDialog(
            existing = editing?.account,
            onDismiss = { showEditor = false },
            onConfirm = { name, type, balance, colorArgb, iconKey ->
                val current = editing
                if (current == null) {
                    viewModel.createAccount(name, type, balance, colorArgb, iconKey)
                } else {
                    viewModel.updateAccount(
                        current.account.copy(
                            name = name,
                            type = type,
                            initialBalance = balance,
                            colorArgb = colorArgb,
                            iconKey = iconKey,
                        ),
                    )
                }
                showEditor = false
            },
            onArchiveToggle = { archived ->
                editing?.let { viewModel.setArchived(it.account.id, archived) }
                showEditor = false
            },
        )
    }
}

@Composable
private fun SettingsRoute(container: AppContainer, navController: NavHostController) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    themePreferences = container.themePreferences,
                    autoLedgerSettings = container.autoLedgerSettings,
                    accountRepository = container.accountRepository,
                    categoryRepository = container.categoryRepository,
                    recognitionLog = container.recognitionLog,
                    ocrStatusHolder = container.ocrStatus,
                )
            }
        },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val autoSettings by viewModel.autoSettings.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
    val ocrStatus by viewModel.ocrStatus.collectAsStateWithLifecycle()
    val serviceEnabled = rememberAutoLedgerServiceEnabled()

    SettingsScreen(
        settings = settings,
        autoSettings = autoSettings,
        serviceEnabled = serviceEnabled,
        accounts = accounts,
        expenseCategories = expenseCategories,
        logEntries = logEntries,
        ocrStatus = ocrStatus,
        onThemeColorChange = viewModel::setThemeColor,
        onDarkModeChange = viewModel::setDarkMode,
        onBackgroundPick = viewModel::setBackgroundUri,
        onBackgroundReset = viewModel::resetBackground,
        onBackgroundVisibilityChange = viewModel::setBackgroundVisibility,
        onBackgroundBlurChange = viewModel::setBackgroundBlur,
        onBackgroundScrimChange = viewModel::setBackgroundScrim,
        onCardOpacityChange = viewModel::setCardOpacity,
        onManageCategories = { navController.navigate("categories") },
        onAutoEnabledChange = viewModel::setAutoEnabled,
        onDefaultAccountChange = viewModel::setDefaultAccount,
        onDefaultCategoryChange = viewModel::setDefaultCategory,
        onRecognitionLogChange = viewModel::setRecognitionLogEnabled,
        onOcrEnabledChange = viewModel::setOcrEnabled,
        onClearLog = viewModel::clearLog,
    )
}

@Composable
private fun RecordRoute(
    container: AppContainer,
    editingTransactionId: Long?,
    onBack: () -> Unit,
) {
    val viewModel: RecordViewModel = viewModel(
        key = "record-${editingTransactionId ?: "new"}",
        factory = viewModelFactory {
            initializer {
                RecordViewModel(
                    transactionRepository = container.transactionRepository,
                    categoryRepository = container.categoryRepository,
                    accountRepository = container.accountRepository,
                    editingTransactionId = editingTransactionId,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RecordScreen(
        uiState = uiState,
        onTypeChange = viewModel::setType,
        onAmountChange = viewModel::setAmountInput,
        onCategorySelect = viewModel::setCategory,
        onAccountSelect = viewModel::setAccount,
        onNoteChange = viewModel::setNote,
        onDateChange = viewModel::setOccurredAt,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onBack,
    )
}

@Composable
private fun CategoryRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: CategoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CategoryViewModel(container.categoryRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CategoryScreen(
        uiState = uiState,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onMessageConsumed = viewModel::consumeMessage,
        onBack = onBack,
    )
}

@Composable
private fun PlaceholderScreen(title: String, hint: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$title · $hint", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
