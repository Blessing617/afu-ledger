package com.afu.ledger.ui

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.afu.ledger.data.AppContainer
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.ui.assets.AccountEditorDialog
import com.afu.ledger.ui.assets.AssetsScreen
import com.afu.ledger.ui.assets.AssetsViewModel
import com.afu.ledger.ui.category.CategoryScreen
import com.afu.ledger.ui.category.CategoryViewModel
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.BackgroundLayer
import com.afu.ledger.ui.component.rememberMotionEnabled
import com.afu.ledger.ui.home.HomeScreen
import com.afu.ledger.ui.home.HomeViewModel
import com.afu.ledger.ui.record.RecordScreen
import com.afu.ledger.ui.record.RecordViewModel
import com.afu.ledger.ui.report.ReportScreen
import com.afu.ledger.ui.report.ReportViewModel
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

    // 记账表单改用对话框（与「新增账户」同一套交互语言），不再是导航目的地。
    // null = 不显示；-1L = 新增；>0 = 编辑该 id。
    var recordTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    // 系统关闭动画（动画缩放 0 / 减弱动效）时退化为瞬时切换，而不是「变慢」
    val motionEnabled = rememberMotionEnabled()

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
                        onAddTransaction = { recordTarget = -1L },
                        onCreateAccount = { currentTab = "assets" },
                        onEditTransaction = { id -> recordTarget = id },
                        onOpenReport = { navController.navigate("report") },
                    )
                }
                TabPage(route = "assets", current = currentTab) {
                    AssetsRoute(container = container)
                }
                TabPage(route = "settings", current = currentTab) {
                    SettingsRoute(container = container, navController = navController)
                }

                // 二级页面：仍由 NavHost 管理，只负责「层级进入/返回」的转场。
                //
                // zIndex 必须显式高于 TabPage：TabPage 可见时是 1f，而 NavHost 默认是 0f，
                // 于是「记一笔」滑入时表单反而被首页的空状态卡片与悬浮按钮盖住。
                // 这是层级 bug，不是时序抖动 —— 转场前几帧表单半透明，底下内容直接透出来。
                NavHost(
                    navController = navController,
                    startDestination = TAB_HOST,
                    modifier = Modifier.zIndex(NAV_HOST_Z_INDEX),
                    // 进入：从下方滑入 + 淡入，用减速曲线（起始快、结尾缓）。
                    // 先前的 tween(300) 是线性缓动，规格明确禁止；线性会让整段位移
                    // 匀速推开，缺少「一下就到位再轻轻收住」的落位感。
                    enterTransition = {
                        if (!motionEnabled) {
                            EnterTransition.None
                        } else {
                            slideInVertically(
                                animationSpec = tween(
                                    durationMillis = AfuMotion.DURATION_ENTER_MS,
                                    easing = AfuMotion.EmphasizedDecelerate,
                                ),
                            ) { full -> full / 4 } +
                                fadeIn(
                                    animationSpec = tween(
                                        durationMillis = AfuMotion.DURATION_ENTER_MS,
                                        easing = AfuMotion.EmphasizedDecelerate,
                                    ),
                                )
                        }
                    },
                    // 被覆盖的页面左右略退，与进入的位移方向一致，形成层次而非硬切
                    exitTransition = {
                        if (!motionEnabled) {
                            ExitTransition.None
                        } else {
                            slideOutVertically(
                                animationSpec = tween(
                                    durationMillis = AfuMotion.DURATION_EXIT_MS,
                                    easing = AfuMotion.EmphasizedAccelerate,
                                ),
                            ) { full -> -full / 12 } +
                                fadeOut(tween(durationMillis = AfuMotion.DURATION_EXIT_MS))
                        }
                    },
                    popEnterTransition = {
                        if (!motionEnabled) {
                            EnterTransition.None
                        } else {
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = AfuMotion.DURATION_EXIT_MS,
                                    easing = AfuMotion.EmphasizedDecelerate,
                                ),
                            )
                        }
                    },
                    popExitTransition = {
                        if (!motionEnabled) {
                            ExitTransition.None
                        } else {
                            slideOutVertically(
                                animationSpec = tween(
                                    durationMillis = AfuMotion.DURATION_ENTER_MS,
                                    easing = AfuMotion.EmphasizedAccelerate,
                                ),
                            ) { full -> full / 4 } +
                                fadeOut(
                                    animationSpec = tween(
                                        durationMillis = AfuMotion.DURATION_ENTER_MS,
                                        easing = AfuMotion.EmphasizedAccelerate,
                                    ),
                                )
                        }
                    },
                ) {
                    composable(TAB_HOST) { /* 占位：标签页在 NavHost 之外 */ }
                    composable("categories") {
                        CategoryRoute(container = container, onBack = { navController.popBackStack() })
                    }
                    composable("report") {
                        ReportRoute(container = container, onBack = { navController.popBackStack() })
                    }
                }

                // 记账对话框与「新增账户」同为居中卡片，层级由 Dialog 自身的窗口保证，
                // 不再依赖 zIndex，也不需要整页滑入的转场。
                recordTarget?.let { target ->
                    RecordRoute(
                        container = container,
                        editingTransactionId = if (target > 0L) target else null,
                        onBack = { recordTarget = null },
                    )
                }
            }
        }
    }
}

/**
 * 一个常驻组合的标签页。
 *
 * ## 转场为什么不是「纯淡入淡出」
 *
 * 只说「切换」的动效会让用户觉得**断**：一页消失、另一页凭空出现。
 * 有连贯感的转场需要两个额外信号（这也是系统级动效看起来「灵动」的原因）：
 *
 * 1. **方向**：新页面从它所在的方向进来（往右切就从右侧滑入），旧页面反向离开。
 *    用户看到的是「这一叠纸被推过去」，而不是「换了一张纸」。
 * 2. **纵深**：离开的页面轻微缩小并后退，进来的页面从稍小恢复到原尺寸。
 *    近大远小给了空间层次，避免两块全屏图片直接交叠。
 *
 * 位移幅度刻意很小（[TAB_SLIDE_FRACTION] 屏宽）：大幅平移在手机上会显得笨重，
 * 且大面积重绘更容易掉帧；「小幅位移 + 缩放 + 交错淡出」观感更接近连续的形变。
 */
@Composable
private fun TabPage(
    route: String,
    current: String,
    content: @Composable () -> Unit,
) {
    val visible = route == current
    val motionEnabled = rememberMotionEnabled()
    // 方向：目标页在右侧就从右边进，在左侧就从左边进
    val direction = remember(route, current) {
        val target = tabIndex(route)
        val from = tabIndex(current)
        when {
            target > from -> 1f
            target < from -> -1f
            else -> 0f
        }
    }
    // 注意：这里**不要**用 `by` 解包。
    // `val alpha by animateFloatAsState(...)` 会在组合阶段读取状态，
    // 动画每一帧都会让整个 TabPage（含整页内容）重组一次。
    // 保持 State 引用，把 .value 的读取放进 graphicsLayer 的 lambda 里，
    // 状态读取就落在 Draw 阶段，动画期间不触发重组。
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // spring 而非 tween：规格要求「MUST 使用减速曲线或 spring」，且弹簧带落位感
        animationSpec = if (motionEnabled) AfuMotion.glide() else snap(),
        label = "tabProgress",
    )
    // 完全透明的页面不参与绘制。
    //
    // 只把 alpha 设成 0 时，这一层仍然是独立 RenderNode，仍要进合成流程；
    // 三个全屏层 + 背景层 + Scaffold 同时在场时，合成器压力会体现在 sync 阶段。
    // 这里在完全透明时整个跳过 drawContent：组合树保留（切换速度不变），
    // 但不再产生绘制指令。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer {
                val p = progress.value
                alpha = p
                if (p < 0.999f) {
                    // 未完全就位时，位移 + 缩放一起表达「还没到位」
                    translationX = direction * size.width * TAB_SLIDE_FRACTION * (1f - p)
                    val scale = TAB_SCALE_FROM + (1f - TAB_SCALE_FROM) * p
                    scaleX = scale
                    scaleY = scale
                } else {
                    // 就位后复位，保证稳态下这一层可被缓存、不因缩放而重采样
                    translationX = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
            }
            .drawWithContent {
                if (progress.value > 0.01f) drawContent()
            }
            .then(if (visible) Modifier else Modifier.pointerInput(Unit) { blockAllInput() }),
    ) {
        // 让页面里的周期性任务知道自己在不在前台：
        // 组合树保留是为了切换快，但不可见时不该继续跑定时器。
        CompositionLocalProvider(LocalTabVisible provides visible) {
            content()
        }
    }
}

/** 标签切换时的水平位移幅度（屏宽的比例）。小幅度即可表达方向。 */
private const val TAB_SLIDE_FRACTION = 0.06f

/**
 * 标签页未就位时的缩放起点。
 *
 * **必须 >= 1**：小于 1 会让页面在转场中内缩，四周露出 Scaffold 底色（实测能看到一圈灰边）。
 * 从 1.02 收敛到 1.0 同样能表达「从近处落定」的纵深，但永远不会露边。
 */
private const val TAB_SCALE_FROM = 1.02f

/** 当前所在的标签页是否可见。不可见时页面应暂停定时刷新。 */
val LocalTabVisible = staticCompositionLocalOf { true }

/** 不可见页面吞掉所有触摸，避免事件穿透到下层页面。 */
private suspend fun PointerInputScope.blockAllInput() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

private const val TAB_HOST = "tab-host"

/**
 * 二级页面的层级，必须高于 [TabPage] 的任何 zIndex。
 *
 * TabPage 可见时为 1f，不可见时为 0f；NavHost 取 2f 就能保证
 * 「二级页面永远画在标签页之上」，不被空状态卡片、悬浮按钮或底部导航盖住。
 */
private const val NAV_HOST_Z_INDEX = 2f


@Composable
private fun HomeRoute(
    container: AppContainer,
    navController: NavHostController,
    onAddTransaction: () -> Unit,
    onCreateAccount: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onOpenReport: () -> Unit,
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
        onAddTransaction = onAddTransaction,
        onCreateAccount = onCreateAccount,
        onTransactionClick = onEditTransaction,
        onOpenReport = onOpenReport,
        onSelectDate = viewModel::selectDate,
        onMonthChange = viewModel::changeMonth,
    )
}

/** 报表是二级页面：从首页滑入，返回时滑出。 */
@Composable
private fun ReportRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ReportViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ReportViewModel(
                    transactionRepository = container.transactionRepository,
                    categoryRepository = container.categoryRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReportScreen(
        uiState = uiState,
        onBack = onBack,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onCurrentMonth = viewModel::goToCurrentMonth,
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

    // 记住「曾经开过」：用来区分「从没设置」与「被系统撤销」，
    // 后者的引导需要解释原因（多半是被从最近任务划掉了）。
    var wasEverEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(serviceEnabled) {
        if (serviceEnabled) {
            container.keepAliveState.markEverEnabled()
            wasEverEnabled = true
        } else {
            wasEverEnabled = container.keepAliveState.wasEverEnabledNow()
        }
    }

    SettingsScreen(
        settings = settings,
        autoSettings = autoSettings,
        serviceEnabled = serviceEnabled,
        accounts = accounts,
        expenseCategories = expenseCategories,
        logEntries = logEntries,
        ocrStatus = ocrStatus,
        wasEverEnabled = wasEverEnabled,
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
