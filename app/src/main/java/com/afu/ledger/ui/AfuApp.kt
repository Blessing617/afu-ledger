package com.afu.ledger.ui

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.afu.ledger.AfuApplication
import com.afu.ledger.data.AppContainer
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.ui.appearance.AppearanceScreen
import com.afu.ledger.ui.assets.AccountEditorDialog
import com.afu.ledger.ui.assets.AssetsScreen
import com.afu.ledger.ui.assets.AssetsViewModel
import com.afu.ledger.ui.category.CategoryScreen
import com.afu.ledger.ui.category.CategoryViewModel
import com.afu.ledger.ui.component.AfuMotion
import com.afu.ledger.ui.component.BackgroundLayer
import com.afu.ledger.ui.component.rememberMotionEnabled
import com.afu.ledger.ui.history.HistoryScreen
import com.afu.ledger.ui.home.HomeScreen
import com.afu.ledger.ui.home.HomeViewModel
import com.afu.ledger.ui.record.RecordScreen
import com.afu.ledger.ui.record.RecordViewModel
import com.afu.ledger.ui.report.ReportScreen
import com.afu.ledger.ui.report.ReportViewModel
import com.afu.ledger.ui.schedule.ScheduleListViewModel
import com.afu.ledger.ui.schedule.ScheduleScreen
import com.afu.ledger.ui.settings.SettingsScreen
import com.afu.ledger.ui.settings.SettingsViewModel
import com.afu.ledger.ui.settings.rememberAutoLedgerServiceEnabled
import com.afu.ledger.ui.theme.LocalThemeStyle
import com.afu.ledger.ui.theme.MascotAvatar
import com.afu.ledger.ui.theme.ThemeBackdrop

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

/**
 * 四个标签 + 中央记账按钮。
 *
 * 中央不占标签位：「记一笔」是动作而不是目的地，把它做成 tab 会让「当前在哪个 tab」
 * 这个概念失效（点完记一笔之后该回到哪？）。做成独立按钮就没有这个歧义。
 */
private val bottomTabs = listOf(
    BottomTab("home", "记账", Icons.Rounded.EditNote),
    BottomTab("history", "明细", Icons.Rounded.ReceiptLong),
    BottomTab("assets", "资产", Icons.Rounded.AccountBalanceWallet),
    BottomTab("settings", "我的", Icons.Rounded.Person),
)

private fun tabIndex(route: String?): Int {
    val index = bottomTabs.indexOfFirst { it.route == route }
    return if (index >= 0) index else 0
}

@Composable
fun AfuApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // 有二级页面盖在上面时，标签页要「后退一层」（缩小 + 变暗）
    val isOverlay = backStackEntry?.destination?.route
        ?.let { it != TAB_HOST } ?: false
    var currentTab by rememberSaveable { mutableStateOf(bottomTabs.first().route) }

    // 记账面板：null = 不显示；-1L = 新增；>0 = 编辑该 id。
    var recordTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    // 系统关闭动画（动画缩放 0 / 减弱动效）时退化为瞬时切换，而不是「变慢」
    val motionEnabled = rememberMotionEnabled()

    val themeSettings by container.themePreferences.settings
        .collectAsStateWithLifecycle(initialValue = ThemeSettings())

    // 首页与明细页共用同一个 ViewModel：订阅同一份交易流，产出两份互不影响的状态。
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val ledgerViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    transactionRepository = container.transactionRepository,
                    categoryRepository = container.categoryRepository,
                    accountRepository = container.accountRepository,
                    pendingBillStore = container.pendingBills,
                    processedBillStore = container.processedBills,
                    settingsRepository = container.autoLedgerSettings,
                    recordDetectedPayment = container.recordDetectedPayment,
                    appContext = appContext,
                )
            }
        },
    )
    val ledgerState by ledgerViewModel.uiState.collectAsStateWithLifecycle()

    val settingsViewModel: SettingsViewModel = viewModel(
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 底色画在最外层：Scaffold 用透明容器，主题装饰才不会被它盖住
            .background(
                if (themeSettings.backgroundUri == null) {
                    MaterialTheme.colorScheme.background
                } else {
                    Color.Transparent
                },
            ),
    ) {
        // 自定义背景图：可见度/模糊/遮罩可调，加载完成淡入
        themeSettings.backgroundUri?.let { uri ->
            BackgroundLayer(
                uri = uri,
                visibility = themeSettings.backgroundVisibility,
                blur = themeSettings.backgroundBlur,
                scrim = themeSettings.backgroundScrim,
            )
        }
        // 主题装饰（气泡 / 花瓣 / 星点）：静态绘制，不参与滚动。
        // 用户设了自定义背景图时不画装饰 —— 两层叠加会把画面糊成一团。
        if (themeSettings.backgroundUri == null) {
            ThemeBackdrop()
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                AfuBottomBar(
                    current = currentTab,
                    onSelect = { currentTab = it },
                    onAdd = { recordTarget = -1L },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                TabPage(route = "home", current = currentTab, backgrounded = isOverlay) {
                    HomeScreen(
                        uiState = ledgerState.home,
                        onAddTransaction = { recordTarget = -1L },
                        onCreateAccount = { currentTab = "assets" },
                        onTransactionClick = { id -> recordTarget = id },
                        onOpenHistory = { currentTab = "history" },
                        onConfirmPending = ledgerViewModel::confirmPending,
                        onIgnorePending = ledgerViewModel::ignorePending,
                    )
                }
                TabPage(route = "history", current = currentTab, backgrounded = isOverlay) {
                    HistoryScreen(
                        uiState = ledgerState.history,
                        onMonthChange = ledgerViewModel::changeMonth,
                        onSelectDate = ledgerViewModel::selectDate,
                        onTransactionClick = { id -> recordTarget = id },
                        onOpenReport = { navController.navigate("report") },
                    )
                }
                TabPage(route = "assets", current = currentTab, backgrounded = isOverlay) {
                    AssetsRoute(container = container)
                }
                TabPage(route = "settings", current = currentTab, backgrounded = isOverlay) {
                    SettingsRoute(
                        container = container,
                        viewModel = settingsViewModel,
                        onOpenAppearance = { navController.navigate("appearance") },
                        onManageCategories = { navController.navigate("categories") },
                        onOpenSchedules = { navController.navigate("schedules") },
                    )
                }
            }
        }

        // 二级页面：仍由 NavHost 管理，只负责「层级进入/返回」的转场。
        //
        // zIndex 必须显式高于 TabPage：TabPage 可见时是 1f，而 NavHost 默认是 0f，
        // 于是转场时表单反而被首页的卡片与悬浮按钮盖住。
        NavHost(
            navController = navController,
            startDestination = TAB_HOST,
            modifier = Modifier.zIndex(NAV_HOST_Z_INDEX),
            // 二级页整屏从底部滑入；底层标签页同时缩小、压暗（见 TabPage 的 recede），
            // 形成「抽屉推上来、底下的卡片往后退」的纵深，而不是两页硬叠。
            enterTransition = {
                if (!motionEnabled) {
                    EnterTransition.None
                } else {
                    slideInVertically(
                        animationSpec = tween(
                            durationMillis = AfuMotion.DURATION_ENTER_MS,
                            easing = AfuMotion.EmphasizedDecelerate,
                        ),
                    ) { full -> full } +
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = AfuMotion.DURATION_ENTER_MS / 2,
                                easing = AfuMotion.EmphasizedDecelerate,
                            ),
                        )
                }
            },
            exitTransition = {
                // 下层内容由 TabPage 自己表达后退，NavHost 不再额外位移
                ExitTransition.None
            },
            popEnterTransition = {
                EnterTransition.None
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
                    ) { full -> full } +
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = AfuMotion.DURATION_EXIT_MS,
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
            composable("appearance") {
                AppearanceRoute(
                    container = container,
                    viewModel = settingsViewModel,
                    navController = navController,
                )
            }
            composable("schedules") {
                ScheduleRoute(container = container, onBack = { navController.popBackStack() })
            }
        }

        recordTarget?.let { target ->
            RecordRoute(
                container = container,
                editingTransactionId = if (target > 0L) target else null,
                onBack = { recordTarget = null },
            )
        }
    }
}

/**
 * 底部栏：四个标签 + 中央凸起的记账按钮。
 *
 * 中央按钮用 `offset` 上移 18dp 而不是做一层额外的自定义布局：
 * 它仍然属于 bottomBar 的一部分（Scaffold 据此给内容留出高度），
 * 凸出的那一截压在内容上，是刻意的。
 */
@Composable
private fun AfuBottomBar(
    current: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val style = LocalThemeStyle.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            // 与内容之间一条细线，避免白色底栏在彩色主题上像一块贴上去的白板
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .navigationBarsPadding()
                    // 曲面屏的左右两侧是弯的：「记账」「我的」贴边时文字会落在弯曲区被吃掉。
                    // 整条内缩 20dp，背景仍然铺满，视觉上不影响，但文字离开了危险区。
                    // 底部再留 8dp：曲面屏的下边缘也会吃掉贴底的标签文字，
                    // 配合 80dp 的栏高把文字整体抬起约 12dp。
                    .height(80.dp)
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(bottomTabs[0], current, onSelect)
                TabItem(bottomTabs[1], current, onSelect)
                Spacer(Modifier.width(76.dp))
                TabItem(bottomTabs[2], current, onSelect)
                TabItem(bottomTabs[3], current, onSelect)
            }
        }
        AddButton(
            onClick = onAdd,
            sticker = style.cardStyle == com.afu.ledger.ui.theme.CardStyle.OUTLINED,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    tab: BottomTab,
    current: String,
    onSelect: (String) -> Unit,
) {
    val selected = tab.route == current
    val accent = MaterialTheme.colorScheme.primary
    val style = LocalThemeStyle.current
    // 描边主题（大肥鱼）下，选中态是「白色贴纸圆章」而不是高亮胶囊
    val sticker = style.cardStyle == com.afu.ledger.ui.theme.CardStyle.OUTLINED
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onSelect(tab.route) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (sticker) 38.dp else 40.dp, if (sticker) 38.dp else 26.dp)
                .clip(if (sticker) CircleShape else RoundedCornerShape(13.dp))
                .background(
                    when {
                        sticker && selected -> MaterialTheme.colorScheme.surface
                        selected -> accent.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    },
                )
                .then(
                    if (sticker && selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (sticker) 21.dp else 20.dp),
            )
        }
        Text(
            text = tab.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 中央记账按钮：按下时轻微缩小，体现「按到了」。 */
@Composable
private fun AddButton(
    onClick: () -> Unit,
    sticker: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = if (motionEnabled) AfuMotion.bouncy() else snap(),
        label = "addScale",
    )
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (sticker) 6.dp else 10.dp,
                shape = CircleShape,
                ambientColor = if (sticker) MaterialTheme.colorScheme.outline else primary,
                spotColor = if (sticker) MaterialTheme.colorScheme.outline else primary,
            )
            .clip(CircleShape)
            .background(if (sticker) MaterialTheme.colorScheme.surface else primary)
            .then(
                if (sticker) {
                    Modifier.border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = MaterialTheme.colorScheme.onPrimary),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (sticker) {
            // 大肥鱼主题下，中央按钮就是吉祥物本尊
            MascotAvatar(size = 46.dp)
        } else {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "记一笔",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
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
 * 2. **纵深**：离开的页面轻微缩小并后退，进来的页面从稍大恢复到原尺寸。
 *
 * 位移幅度刻意很小（[TAB_SLIDE_FRACTION] 屏宽）：大幅平移在手机上会显得笨重，
 * 且大面积重绘更容易掉帧。
 */
@Composable
private fun TabPage(
    route: String,
    current: String,
    backgrounded: Boolean = false,
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
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (motionEnabled) AfuMotion.glide() else snap(),
        label = "tabProgress",
    )
    // 二级页面打开时，当前标签页退到后面：缩小 + 压暗，给上层的滑入让出「纵深」。
    // 只在转场期间变化，静止时恒为 0 或 1，不产生持续开销。
    val recede = animateFloatAsState(
        targetValue = if (visible && backgrounded) 1f else 0f,
        animationSpec = if (motionEnabled) AfuMotion.glide() else snap(),
        label = "tabRecede",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer {
                val p = progress.value
                alpha = p
                val r = recede.value
                if (r > 0.001f) {
                    val recedeScale = 1f - 0.06f * r
                    scaleX *= recedeScale
                    scaleY *= recedeScale
                }
                if (p < 0.999f) {
                    translationX = direction * size.width * TAB_SLIDE_FRACTION * (1f - p)
                    val scale = TAB_SCALE_FROM + (1f - TAB_SCALE_FROM) * p
                    scaleX = scale
                    scaleY = scale
                } else {
                    translationX = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
            }
            .drawWithContent {
                if (progress.value > 0.01f) drawContent()
                val r = recede.value
                if (r > 0.01f) {
                    drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f * r))
                }
            }
            .then(if (visible) Modifier else Modifier.pointerInput(Unit) { blockAllInput() }),
    ) {
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
 * **必须 >= 1**：小于 1 会让页面在转场中内缩，四周露出 Scaffold 底色。
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
 */
private const val NAV_HOST_Z_INDEX = 2f

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
private fun SettingsRoute(
    container: AppContainer,
    viewModel: SettingsViewModel,
    onOpenAppearance: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenSchedules: () -> Unit,
) {
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
        onOpenAppearance = onOpenAppearance,
        onManageCategories = onManageCategories,
        onOpenSchedules = { onOpenSchedules() },
        onAutoEnabledChange = viewModel::setAutoEnabled,
        onDefaultAccountChange = viewModel::setDefaultAccount,
        onDefaultCategoryChange = viewModel::setDefaultCategory,
        onRecognitionLogChange = viewModel::setRecognitionLogEnabled,
        onOcrEnabledChange = viewModel::setOcrEnabled,
        onHideKeepAliveNotificationChange = viewModel::setHideKeepAliveNotification,
        onClearLog = viewModel::clearLog,
    )
}

@Composable
private fun AppearanceRoute(
    container: AppContainer,
    viewModel: SettingsViewModel,
    navController: NavHostController,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    AppearanceScreen(
        settings = settings,
        onStyleChange = viewModel::setThemeStyle,
        onThemeColorChange = viewModel::setThemeColor,
        onDarkModeChange = viewModel::setDarkMode,
        onBackgroundPick = viewModel::setBackgroundUri,
        onBackgroundReset = viewModel::resetBackground,
        onBackgroundVisibilityChange = viewModel::setBackgroundVisibility,
        onBackgroundBlurChange = viewModel::setBackgroundBlur,
        onBackgroundScrimChange = viewModel::setBackgroundScrim,
        onCardOpacityChange = viewModel::setCardOpacity,
        onBack = { navController.popBackStack() },
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

/**
 * 定时记账页。
 *
 * 规则一改就立刻重排闹钟（`onScheduleChanged`）：
 * 否则新建的规则要等到明天兜底任务或下次开机才生效，用户会以为它坏了。
 */
@Composable
private fun ScheduleRoute(container: AppContainer, onBack: () -> Unit) {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as AfuApplication
    val viewModel: ScheduleListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ScheduleListViewModel(
                    rules = container.scheduleRuleRepository,
                    accounts = container.accountRepository,
                    categories = container.categoryRepository,
                    transactions = container.transactionRepository,
                    pendingStore = container.pendingOccurrences,
                    onScheduleChanged = {
                        application.requestDueSchedules()
                    },
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScheduleScreen(
        uiState = uiState,
        onBack = onBack,
        onCreate = viewModel::create,
        onUpdate = viewModel::update,
        onToggle = viewModel::setEnabled,
        onDelete = viewModel::delete,
        onConfirmPending = { occurrence ->
            viewModel.confirm(occurrence)
            application.requestDueSchedules()
        },
        onIgnorePending = viewModel::ignore,
    )
}
