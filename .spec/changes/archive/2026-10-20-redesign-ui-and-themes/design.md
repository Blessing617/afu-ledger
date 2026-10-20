# 技术方案：UI 重构与主题风格系统

## 1. 主题分层：风格 × 色相

现状是「一个 `themeColorId` 派生出整套颜色」，没有风格概念。新模型把两者拆开：

```
ThemeSettings
├── themeStyleId : String     ← 风格（新增，DataStore key: theme_style_id）
├── themeColorId : String     ← 色相（保留）
├── darkMode / background* / cardOpacity  （保留）
```

```kotlin
data class ThemeStyle(
    val id: String,
    val label: String,
    val tagline: String,
    /** 该风格推荐的色相顺序；色板对全部风格可用，这里只影响「推荐」与默认值。 */
    val recommendedColors: List<String>,
    val surface: SurfaceSpec,   // 底色、表面色、描边色、描边宽、卡片透明度下限
    val shapes: ShapeSpec,      // 卡片/输入框/按钮圆角
    val decoration: Decoration, // NONE / BUBBLE / PETAL / STAR
    val card: CardStyle,        // PLAIN / OUTLINED / GLASS
    val showsMascot: Boolean,
    val motion: MotionFlavor,   // CALM / BOUNCY
)
```

四个内置风格：`plain`（清爽）、`fatfish`（大肥鱼）、`sakura`（樱花日和）、`midnight`（深夜）。

**为什么不做「任意颜色 + 任意形状」的自由组合**：组合爆炸会让每个页面都要做兼容处理，而风格的价值在于整体一致。色相已经可自由选，形状与装饰由风格统一给定。

下发方式：`CompositionLocalProvider(LocalThemeStyle provides style, LocalMotionFlavor provides style.motion)`。避免每个 composable 都加参数，也避免把风格塞进 `MaterialTheme`（那样要自定义 `CompositionLocal` 复制整套 M3 主题，维护成本高）。

### 切换性能

**不做**「整树颜色过渡动画」（`animateColorAsState` 逐帧改色会让所有读取颜色的 composable 每帧重组，直接违反帧预算）。切换时直接换值，一次重组完成；观感上的「顺滑」交给进入主题页的转场与预览卡自己的动画。

主题页的预览卡可以有自己的 150ms 局部动画（只影响卡片里的几个小元素）。

## 2. 大肥鱼主题的矢量资产

不打包位图。全部用 `Canvas` + `Path` 画：

- `FatFishMark(size, mood)`：圆身 + 尾巴 + 小眼睛 + 腮红 + 头顶小水柱，3 种 mood（开心/困/惊喜）
- `FishBubbleDecor`：背景气泡与小鱼剪影，静态
- `PetalDecor`：樱花花瓣（sakura）
- 空状态插画 = `FatFishMark` + 一句文案

实现要点：

- 路径用 `drawWithCache` 一次性构建，`onDrawBehind` 绘制
- 坐标全部按 `size` 归一化，DPI 无关
- 阴影用描边替代（`Stroke(width)`），避免 `shadow` 的离屏层开销
- 不使用 `infiniteRepeatable`，不启动任何 `rememberInfiniteTransition`

## 3. 导航与页面结构

```
Scaffold(bottomBar = AfuBottomBar)
├── TabPage("home")     首页：今日
├── TabPage("history")  明细：月历 + 分组列表
├── TabPage("assets")   资产
└── TabPage("settings") 我的
```

底部栏：左侧两个标签、中央凸起的圆形「＋」、右侧两个标签。中央按钮用 `Box` 叠加在 `NavigationBar` 之上（不是 `NavigationBarItem`），点击打开记账面板。

二级页面仍走 `NavHost`：`report`、`categories`、`appearance`。

**当前 spec 要求「记账用居中对话框」，本次改为底部面板**，理由：记账是本应用最高频、输入最多的操作，需要键盘空间；底部面板与数字键盘的组合是记账类产品的通行做法（喵喵记账、随手记、MoneyWiz 都是底部输入区）。账户/分类编辑仍然用居中对话框，因为它们是低频、字段少的表单。

## 4. 记账面板

```
RecordSheet (自绘 overlay，不用 ModalBottomSheet)
├── scrim              点击关闭；alpha 动画 200ms
├── Panel              高度 72% 屏高，圆角 28dp 顶部
│   ├── Handle         40×4dp 圆角条，draggable
│   ├── Header         支出/收入 分段 + 关闭
│   ├── AmountDisplay  大字号（40sp），随输入变化
│   ├── CategoryGrid   4 列固定 Column/Row（不用 LazyGrid）
│   ├── AccountStrip   横向可选条：圆形图标 + 名称
│   ├── MetaRow        时间快捷（今天/昨天/日期）、备注
│   └── Keypad         4×4：0-9 . 退格 完成 + 运算符
└── SaveButton         满宽胶囊
```

**不用 `ModalBottomSheet`**：它会自己带一层 scrim + 动画，和现有 `zIndex` 层级、返回键处理叠加后容易出「两层遮罩」「关闭时残影」；自绘 overlay 的层级、返回键、拖拽都由我们自己控制，行为确定。

拖拽关闭：`Modifier.draggable(Orientation.Vertical)` 更新 `offsetY`，松手时 `offsetY > height/4` 或速度足够则关闭，否则弹簧回位。

键盘点击 → 只更新 `amountInput` 字符串。为避免整面板重组：

- 金额显示区单独抽 `AmountDisplay(text)`，只依赖字符串
- 分类网格用 `key` 稳定，选中态通过 `selectedId` 参数传入；只有变化的格子重组（Compose 的稳定性足够，前提是 `Category` 是稳定 data class —— 它已经是）
- 键盘按键的按下反馈用 `graphicsLayer` 的 lambda 读 `InteractionSource` 状态，不进组合

## 5. 首页与明细页

`HomeViewModel` 拆成两个状态：

- `today`：今日收支、今日交易、待确认列表（进入首页时算一次；交易变更时更新）
- `history`：月度汇总、月历数据、分组列表（明细页用）

两者共用一个 `TransactionRepository.observeAll()`，在 ViewModel 里分别 `map`。用 `stateIn(WhileSubscribed(5_000))`，不可见页不订阅。

首页列表 `LazyColumn`：今日交易通常 < 20 条，固定 key。

明细页列表：按日分组，`item(key="day-$dayKey")` + `items(items, key=id)`，和现有实现一致。

## 6. 设置页重构

```
Settings (LazyColumn)
├── 头部：当前主题缩略预览 + 「外观与主题」入口（大块，带背景）
├── 分组「记账」
│   ├── RowSetting(分类管理, 图标, 副标题「12 个支出 · 4 个收入」)
│   └── RowSetting(默认账户, 图标, 摘要=账户名)
├── 分组「自动记账」
│   ├── RowSetting(开关)
│   ├── HelpTip 折叠的授权引导
│   └── RowSetting(默认分类, 图标, 摘要=分类名)
├── 分组「数据与关于」
│   └── RowSetting(识别日志, 开关)
└── 版本号
```

`HelpTip`：点击「?」在行下方展开一段说明（`AnimatedVisibility` 高度动画），不是弹窗——弹窗会打断滚动。展开状态用 `rememberSaveable`。

默认账户/默认分类选择器：横向可滚动条，每项「圆形图标 + 名称」，选中项加描边与浅底色。数据量小，不用 Lazy。

## 7. 图标

复用 `material-icons-extended` 的 `Icons.Rounded.*`。新增 `AfuIcons.kt`：

```kotlin
fun categoryIcon(key: String): ImageVector  // 改为 Rounded
fun accountIcon(key: String): ImageVector
```

`CategoryIcon.kt` 里 36 个 key 全部映射到 Rounded 变体；未匹配回退 `Icons.Rounded.MoreHoriz`。

**不引入 Phosphor/Lucide SVG**：需要 SVG→ImageVector 转换或额外 vector drawable 文件，收益只是造型差异，而 Rounded 变体已经能解决「笔画硬、像办公软件」的问题，且零新增依赖、零体积增长。

## 8. 系统栏

`AfuApp` 的 `Scaffold` 显式传 `contentWindowInsets = WindowInsets.safeDrawing`，并给 `LazyColumn` 的 `contentPadding` 保留底部额外 96dp（悬浮按钮/底部栏）。

二级页面（分类、报表、外观）各自在 `Scaffold` 里用 `WindowInsets.safeDrawing`。

`MainActivity` 不做 `enableEdgeToEdge()`（已经是默认行为），但 `themes.xml` 保持不透明 `windowBackground` 以避免冷启动闪黑。

## 9. 动效 token 增补

```kotlin
object AfuMotion {
    fun bouncy(): SpringSpec<Float>   // damping 0.55, stiffness 500 —— 分类格、按钮按下
    fun settle(): SpringSpec<Float>   // 现有
    fun glide(): SpringSpec<Float>    // 现有
    fun sheet(): SpringSpec<Float>    // damping 0.8, stiffness 400 —— 面板上滑/回位
    const val DURATION_SCRIM_MS = 200
}
```

规则：微交互只动画 `scale` / `alpha`，一律在 `graphicsLayer` 的 lambda 里读；整页转场沿用现有实现。

## 10. 风险与对策

| 风险 | 对策 |
|---|---|
| 主题改成 CompositionLocal 后漏改某个页面，混用旧颜色 | 全量 grep `MaterialTheme.colorScheme` 之外的颜色字面量；把「描边色/底色」收进 `LocalThemeStyle` |
| 自绘面板的返回键与拖拽手势冲突 | 面板打开时用 `BackHandler`；拖拽只绑定 handle 与面板顶部区域 |
| 键盘频繁点击导致重组扩散 | 金额区与键盘拆成独立 composable；键盘按钮用稳定的 `Modifier.clickable`，反馈走 `interactionSource` |
| 大肥鱼装饰影响滚动帧率 | 装饰放在列表**之外**的父 `Box` 的 `drawBehind`，不进 Lazy item；静态 |
| 首页今日视图让「翻历史」变难 | 首页底部给「查看明细」入口，明细页默认本月；不增加点击深度（2 次点击到任意历史） |
