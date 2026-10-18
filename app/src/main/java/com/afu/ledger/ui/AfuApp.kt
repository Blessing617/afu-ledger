package com.afu.ledger.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import com.afu.ledger.ui.home.HomeScreen
import com.afu.ledger.ui.home.HomeViewModel
import com.afu.ledger.ui.record.RecordScreen
import com.afu.ledger.ui.record.RecordViewModel
import com.afu.ledger.ui.settings.SettingsScreen
import com.afu.ledger.ui.settings.SettingsViewModel

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab("home", "记账", Icons.Outlined.EditNote),
    BottomTab("assets", "资产", Icons.Outlined.AccountBalanceWallet),
    BottomTab("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun AfuApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    val themeSettings by container.themePreferences.settings
        .collectAsStateWithLifecycle(initialValue = ThemeSettings())

    Box(modifier = Modifier.fillMaxSize()) {
        // 自定义背景图：图片 + 可读性遮罩
        themeSettings.backgroundUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)),
            )
        }

        Scaffold(
            containerColor = if (themeSettings.backgroundUri == null) {
                MaterialTheme.colorScheme.background
            } else {
                Color.Transparent
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = if (themeSettings.backgroundUri == null) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        },
                    ) {
                        bottomTabs.forEach { tab ->
                            val selected = backStackEntry?.destination?.hierarchy
                                ?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(innerPadding),
            ) {
                composable("home") {
                    HomeRoute(container = container, navController = navController)
                }
                composable("assets") {
                    AssetsRoute(container = container)
                }
                composable("settings") {
                    SettingsRoute(container = container, navController = navController)
                }
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
                    PlaceholderScreen(title = "分类管理", hint = "即将上线")
                }
            }
        }
    }
}

@Composable
private fun HomeRoute(container: AppContainer, navController: NavHostController) {
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
        onCreateAccount = { navController.navigate("assets") },
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
            initializer { SettingsViewModel(container.themePreferences) }
        },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScreen(
        settings = settings,
        onThemeColorChange = viewModel::setThemeColor,
        onDarkModeChange = viewModel::setDarkMode,
        onBackgroundPick = viewModel::setBackgroundUri,
        onBackgroundReset = { viewModel.setBackgroundUri(null) },
        onManageCategories = { navController.navigate("categories") },
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
