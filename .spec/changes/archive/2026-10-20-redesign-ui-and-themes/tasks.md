# 任务

## 1. 主题基础设施

- [x] 1.1 新建 `ui/theme/ThemeStyle.kt`：`ThemeStyle` + 两个内置风格 `plain`（默认）/ `fatfish`（大肥鱼），已下线的 sakura/midnight 回退到默认 — 验证：`ThemeStyleTest` 覆盖 id 唯一、仅两项、未知与旧 id 回退
- [x] 1.2 `ThemeSettings` 增加 `themeStyleId`（默认 `plain`），`ThemePreferences` 读写与回退 — 验证：真机切换后重启保持
- [x] 1.3 `AfuTheme` 接入 `LocalThemeStyle` / `LocalMotionFlavor` / `LocalDarkTheme`，配色与形状从风格派生 — 验证：切换风格后卡片圆角、底色、描边同时变化
- [x] 1.4 `AfuMotion` 增加 `bouncy()` / `sheet()` / 遮罩时长 — 验证：分类格与中央按钮有轻微回弹
- [x] 1.5 `CategoryIcon.kt` 全部改为 `Icons.Rounded.*`，未匹配回退 Rounded.MoreHoriz — 验证：`ThemeStyleTest` 断言全部 key 有映射、未知 key 不崩溃
- [x] 1.6 组件库：`SettingRow` / `SettingSwitchRow` / `SettingGroup` / `SettingDivider` / `AfuIconBadge` / `SectionHeader` / `HelpTip` / `IconChoiceRow` / `AfuSlider` / `stickerEdge` — 验证：设置页、外观页、记账面板均实际使用

## 2. 大肥鱼主题视觉

- [x] 2.1 吉祥物改为本机 `maid-atelier` 立绘：头像 `mascot_avatar.png`（自绘裁切 + 深蓝圆底）与全身 `maid_chibi.webp` — 验证：首页今日卡、空状态、主题卡、底部中央按钮均显示
- [x] 2.2 气泡 + 小鱼装饰（`ThemeDecoration`），固定海水蓝、静态绘制 — 验证：静置 30 秒无周期性重绘
- [x] 2.3 描边贴纸卡片：2dp 描边 + 内层虚线（`stickerEdge`）+ 近不透明表面 — 验证：自定义背景图下正文清晰
- [x] 2.4 虚线分隔线 — 验证：设置页、明细页分组行显示为虚线
- [x] 2.5 图标容器在描边主题下加细描边 — 验证：设置页图标呈贴纸感
- [x] 2.6 底部导航定制：选中项白色圆章 + 描边，中央按钮为吉祥物头像 — 验证：真机截图
- [x] 2.7 首页按时段问候语 — 验证：不同时段文案不同
- [x] 2.8 标题字体：得意黑 Smiley Sans（OFL 1.1），随包附带 `licenses/smiley-sans-OFL.txt` — 验证：标题/金额为该字体，正文为系统字体

## 3. 设置页与外观页

- [x] 3.1 重构 `SettingsScreen`：分组 + 行式；首屏只保留外观入口与两个分组 — 验证：截首屏无平铺色板与滑块
- [x] 3.2 `HelpTip` 改为「?」+ 弹窗（可滚动正文 + 知道了） — 验证：点击不推动列表位置
- [x] 3.3 默认账户 / 默认分类改为带图标的横向选择条 — 验证：每项有圆形图标，选中项有描边
- [x] 3.4 新建 `AppearanceScreen`：风格两卡、色板、深色模式、背景图与参数、实时预览 — 验证：真机切换即时生效
- [x] 3.5 导航注册 `appearance` 路由 — 验证：进入/返回方向正确
- [x] 3.6 设置首页移除色板、深色模式、背景图区块 — 验证：grep 无残留
- [x] 3.7 移除 Shizuku 引导与底部口号小字 — 验证：grep `Shizuku` 在 UI 层无引用
- [x] 3.8 背景预览缩略图应用可见度/模糊/遮罩，带百分比角标 — 验证：拖滑块时缩略图即时变化
- [x] 3.9 `AfuSlider` 自绘滑块替代 Material Slider — 验证：粗轨道 + 白扣子 + 右侧百分比

## 4. 导航与首页/明细

- [x] 4.1 底部导航改为「记账 · 明细 · 资产 · 我的」+ 中央圆形按钮 — 验证：截图四标签齐全
- [x] 4.2 `HomeViewModel` 产出 `LedgerUiState(home, history)` 两份独立状态 — 验证：首页只含今天，明细含本月
- [x] 4.3 重写 `HomeScreen` 为今日视图 — 验证：截图；空状态显示立绘
- [x] 4.4 新建 `HistoryScreen`：月度汇总 + 月历 + 分组列表 + 报表入口 — 验证：点日期可筛选
- [x] 4.5 共用 `TransactionRow` — 验证：两页使用同一 composable
- [x] 4.6 首页「查看明细」切到明细标签 — 验证：真机点击
- [x] 4.7 二级页整屏覆盖：NavHost 移出 Scaffold，底栏不再因二级页隐藏 — 验证：点击分类管理 5 次，janky 0.37%

## 5. 记账面板

- [x] 5.1 自绘底部面板（scrim + 面板 + 拖拽关闭 + 返回键）— 验证：四种关闭路径一致
- [x] 5.2 `AfuKeypad` 数字键盘，按键描边、按下反馈 — 验证：连续 40 次点击 0 janky
- [x] 5.3 金额显示左对齐、格式化、非法输入拦截 — 验证：`AmountInputTest`
- [x] 5.4 分类圆形图标网格（4 列，选中描边 + 放大）— 验证：截图
- [x] 5.5 账户格与分类格同宽同列 — 验证：截图对照左基线
- [x] 5.6 时间快捷（今天/昨天）+ 日期选择器；备注弹窗输入 — 验证：改时间后归入正确日期分组
- [x] 5.7 保存/编辑/删除接通 — 验证：保存后首页立即出现
- [x] 5.8 遮罩改为主题 onSurface 30%（修正 graphicsLayer 顺序）— 验证：面板上方不再纯黑

## 6. 自动记账弹窗

- [x] 6.1 分类与账户改为带图标的横向选择条 — 验证：`DemoPaymentActivity` 触发截图
- [x] 6.2 金额/商户/方向/时间信息重排，主按钮满宽 — 验证：截图

## 7. 系统栏与细节

- [x] 7.1 `AfuApp` Scaffold 应用 `WindowInsets.safeDrawing` — 验证：首屏不被状态栏遮挡
- [x] 7.2 系统栏图标明暗跟随主题（`SystemBarsEffect`）— 验证：浅色下状态栏图标为深色
- [x] 7.3 二级页面安全区：分类/外观用 Scaffold 默认，报表用 `statusBarsPadding` — 验证：三页标题完整可见
- [x] 7.4 资产页去掉常驻悬浮「新增账户」，改为卡内 + 与列表末行 — 验证：滚动时不再有悬浮按钮挡内容

## 8. 验证与回归

- [x] 8.1 `./gradlew :app:testDebugUnitTest` 全绿
- [x] 8.2 `assembleRelease` 通过并安装到真机
- [x] 8.3 冷启动：`am start -W` TotalTime 173 / 183 / 191ms（预算 800ms）
- [x] 8.4 帧预算（release）：设置页滚动 janky 0.11%、明细页 0%、记账面板连点 40 次 0%、二级页跳转 5 次 0.37%
- [x] 8.5 主题切换与面板交互人工验收：两套风格、记账、拖拽关闭
- [x] 8.6 README 更新界面说明与版本号（0.7.0），更新 docs/screenshots
- [x] 8.7 报表页视觉：贴纸卡片、虚线内边、得意黑标题、Rounded 图标、安全区，图表配色跟随分类/主题色 — 验证：真机截图
- [x] 8.8 深色模式验收：深蓝卡片 + 浅蓝描边 + 白字，吉祥物与装饰仍可辨 — 验证：`cmd uimode night yes` 后截图
- [x] 8.9 卡片不透明度拖动实时预览（`LocalCardOpacityPreview`），拖动只更新内存状态、松手才落盘，预览更新节流到约 30fps — 验证：拖动过程卡片即时变化
- [x] 8.10 滚动流畅度优化：卡片完全不透明（免逐帧混合）、内描边与分隔线由虚线改实线、连续拖动从 8.8% janky 降到 0.11%（外观页）/ 0.25%（设置页）— 验证：`dumpsys gfxinfo`
- [x] 8.11 二级页转场改为整屏滑入 + 底层标签页缩小压暗 — 验证：真机进入/返回
