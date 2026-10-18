# 设计：记账转场遮挡、识别延迟与设置页卡顿

## 一、根因定位（读代码得出，待真机基线数据补充）

### 问题 1：记账表单被标签页遮挡

`ui/AfuApp.kt` 的布局是：

```
Box(fillMaxSize) {
    TabPage("home", ...)      // zIndex = if (visible) 1f else 0f
    TabPage("assets", ...)
    TabPage("settings", ...)
    NavHost(...)              // 没有设 zIndex → 默认 0f
}
```

`zIndex` 只在同一父级内比较。当前可见标签页是 `1f`，`NavHost` 是 `0f`，
**所以标签页内容画在二级页面之上**。点「记一笔」后：

- `NavHost` 里的 `record` 页从底部滑入（`slideInVertically + fadeIn`，300ms）
- 但首页的三个元素（空状态卡片「还没有记账」、`ExtendedFloatingActionButton`、底部导航栏）zIndex 更高，盖在表单上
- 表单本身是 `fadeIn`，转场前半段是半透明的，底下的首页内容直接透出来

用户看到的「动画错误、被还没有记账挡住」= 上述三者叠加。这是**确定的层级 bug，不是时序抖动**。

> 补充：底部导航属于 `Scaffold.bottomBar`，在 `NavHost` 之外的同级 Box 中，也参与了同样的遮挡。

### 问题 2：微信 OCR 慢

一次识别的完整链路（`AutoLedgerAccessibilityService.onAccessibilityEvent`）：

| 阶段 | 现状 | 代价 |
|---|---|---|
| 事件去抖 | `delay(600)` 固定值 | 每次至少 600ms |
| 截图 | `takeScreenshot` → `wrapHardwareBuffer` → `copy(ARGB_8888)` → `getPixels(IntArray)` | 全屏 1080×2400 两次全量拷贝 |
| det 预处理 | `OcrImageOps.detInput`：**纯 Kotlin 逐像素双线性插值** | 输出约 960×432 = 41.5 万像素 × 4 采样 × 3 通道 ≈ 500 万次浮点运算，单线程 |
| det 推理 | ORT，intraOp=2 | 约 200ms（README 实测口径） |
| rec 推理 | **每个文本框串行**一次 `runRec` | 页面 20 行就是 20 次串行推理 |

再加上：
- **没有支付页预判**：聊天页也要跑完整个 OCR 才判定「不是支付页」，白付全部成本
- **首次启用**：解压 16MB 模型 + 创建两个 ORT session，全部在 `Dispatchers.IO`，但用户此时正在微信里等
- **截图帧可能被丢弃**：`onAccessibilityEvent` 每次都 `pendingJob?.cancel()`，若截图已发生但新事件又来，这帧的 OCR 结果就没了

### 问题 3：支付宝也不够快

支付宝走 `collectNodeSnapshot` → 节点文本，本身很便宜。但：

- 同样吃 **600ms 固定去抖**（`DEBOUNCE_MILLIS`，对所有页面一视同仁）
- 已确认：`ScreenshotThrottle` **只作用于截图通道**（`OcrTextChannel.read` 内部才调
  `shouldCapture`），节点路径本来就不经过它 —— 所以支付宝的慢不是节流造成的，
  而是**去抖 + 每次事件都重新采集与判定**。这一点消除了一个错误假设，缩小了改动面。
- `collectNodeSnapshot` 每次事件都重新遍历 `rootInActiveWindow` 与所有 `windows`，
  事件密集时存在重复采集

### 问题 4：设置页滑动卡顿

`ui/settings/AutoLedgerSection.kt`：

```kotlin
LaunchedEffect(Unit) {
    while (true) {
        state = computeKeepAliveState()
        delay(5_000)
    }
}
```

`state` 是 `AutoLedgerSection` 内部的状态，**每次变更会让整个区块重组** —— 这个区块包含：

- 2 个 `Switch`
- `accounts` 的 `FlowRow`（N 个 FilterChip）
- `categories` 的 `FlowRow`（14+ 个 FilterChip）
- 识别日志的若干行 `Text`

每 5 秒全量重算这些 `FilterChip`（`FilterChip` 内部有动画与 ripple 状态），叠加：
- 三个标签页常驻组合，alpha=0 的页面仍在组合树中
- 背景模糊（`Modifier.blur(20.dp)`，API 31+）
- 半透明卡片（`LocalSurfaceAlpha`）

在滑动时正好撞上 5 秒刷新，就会掉帧。

## 二、方案取舍

### 问题 1：zIndex 显式分层

| 方案 | 评价 |
|---|---|
| **给 `NavHost` 设 `zIndex(2f)`，标签页维持 `0f/1f`** | **采用**。改动最小、语义清晰，明确表达「二级页面永远在标签页之上」 |
| 把 `NavHost` 移到 `TabPage` 之前 | 不采用。会让 `NavHost` 的默认层级不稳定，且未来加图层又踩坑 |
| 去掉 `TabPage` 的 zIndex，改用绘制顺序 | 不采用。`TabPage` 的 zIndex 是切换时「新页面盖旧页面」所必需的 |
| 二级页面改用 `Dialog` / `Popup` | 不采用。`Dialog` 会脱离 `Scaffold` 的 inset 处理，还要重做转场 |

同时：底部导航在二级页面存在时应当隐藏（当前 `isOverlay` 已控制 `NavigationBar` 不显示），
但首页的 FAB 仍在 `TabPage` 内，靠 zIndex 修正即可，**额外给 FAB 在二级页面存在时降低层级或不参与绘制**更稳妥。

### 问题 2：OCR 提速

| 措施 | 预期收益 | 风险 | 结论 |
|---|---|---|---|
| **系统缩放替代逐像素双线性** | 最大项。`Bitmap.createScaledBitmap` 走 native（Skia），比 Kotlin 循环快一个量级 | 缩放算法不同（默认双线性/双三次），数值不会逐位一致 → 用容差单测 + 模拟页命中率验证 | **采用** |
| **只归一化不缩放（已由系统缩好）** | 去掉 500 万次浮点运算的大头 | 无 | **采用** |
| 检测输入从 960 降到 640 | 像素少 56%，耗时再降 | 小字号可能漏检 → 必须用模拟页回归 | 先测后定 |
| rec 批量推理 | 减少 ORT 调用开销 | 改动大、可能影响精度 | 不采用（本轮） |
| 减少一次 `IntArray` 拷贝 | 内存与 GC 压力 | 需重构 `OcrImage` | 视 3.3 实测再定 |
| 截图后立即消费，不被取消 | 避免「白截」 | 需要把「已截图任务」与「去抖任务」分离 | **采用** |

> 关键判断：**逐像素 Kotlin 缩放是最大的一块**，因为它每帧都在做全屏尺寸的浮点运算。

### 问题 3：支付宝延迟

| 措施 | 结论 |
|---|---|
| **已判定为支付结果页时用更短去抖** | **采用**。支付宝的结果页文本往往 1–2 个事件就齐全 |
| 节点路径不受截图节流约束 | **无需改动**。已确认 `ScreenshotThrottle` 只在 `OcrTextChannel.read` 内生效，节点路径不经过它 |
| 退避期内用户点击仍放行 | **采用**（只影响微信截图通道；对支付宝无影响） |

### 问题 4：设置页卡顿

| 措施 | 结论 |
|---|---|
| **`KeepAlive` 状态改为只重组的独立 Composable** | **采用**。把 `remember { mutableStateOf }` 下沉到一个只画一行 `Text` 的组件里，让重组范围限定在那一行 |
| **不可见标签页停止定时器** | **采用**。用 `LocalTabVisible`（CompositionLocal）或把 `visible` 传下去，`LaunchedEffect(visible)` 控制 |
| 设置页改用 `LazyColumn` 的稳定 key | **采用**。`items`/`item(key=...)` 让滚动时复用 |
| 重新评估「三页常驻」 | **待定**，见 proposal 待确认项 |
| 去掉背景模糊 | 不采用。是用户可见功能 |

## 三、实施设计

### 3.1 层级

```kotlin
NavHost(
    navController = navController,
    startDestination = TAB_HOST,
    modifier = Modifier.zIndex(NavHostZIndex),   // 2f，高于任何 TabPage
    ...
)
```

并让首页 FAB 在二级页面存在时不可见/不可点（`onAddTransaction` 期间或 `isOverlay` 时）。

### 3.2 OCR 预处理

```kotlin
// 旧：FloatArray = 逐像素双线性缩放到 (targetW, targetH)
// 新：
//  1. 用 Bitmap.createScaledBitmap 把源位图缩到 (targetW, targetH)（native）
//  2. 再逐像素只做 CHW 拆分 + ImageNet 归一化
```

`OcrImage` 保留 `IntArray` 契约不变，新增一个可选的「已缩放到目标尺寸」入口，
使逐像素循环的规模从「全屏」降到「目标尺寸」（本就如此），但去掉插值计算。

### 3.3 去抖与节流

```
onAccessibilityEvent:
  - 若已有「已截图但未识别」的任务 → 不取消它
  - 否则按去抖窗口调度判定
  - 去抖窗口：快速预判（节点文本含支付特征词）→ 短窗口；否则 → 长窗口
```

### 3.4 耗时观测

在 `AutoRecognitionEngine` 与 `OcrTextChannel` 里累积分段耗时，
仅在 `recognitionLogEnabled` 时写入 `RecognitionLog`：

```
outcome = "识别成功"
detail = "去抖 120ms · 截图 85ms · OCR 210ms · 判定 3ms · 命中→可见 96ms"
```

### 3.5 设置页

```kotlin
// 旧：state 提升到 AutoLedgerSection，5 秒刷新重算整块
// 新：
@Composable
private fun KeepAliveStatusLine() {
    var state by remember { mutableStateOf(...) }
    LaunchedEffect(Unit) { while (true) { state = ...; delay(5_000) } }
    Text(...)
}
// AutoLedgerSection 只调用 KeepAliveStatusLine()，不持有该状态
```

## 四、基线与验收

### 4.1 真机实测基线（小米 14 Pro / 23116PN5BC / Android 16，2026-10-18）

设备：1440×3200 @ 560dpi，已装 v0.3.0，无障碍服务已授权。

| 指标 | 基线实测 | 目标 | 备注 |
|---|---|---|---|
| 设置页滑动：janky 帧占比 | **0.24%**（826 帧） | < 5% | p50 7ms / p90 10ms / p95 12ms / p99 20ms |
| 设置页滑动：janky (legacy) | **27.81%**（608/2186） | — | legacy 口径包含「错过 vsync」等，参考价值有限 |
| 微信截图 OCR 单次识别耗时 | **1894 ms** | ≤ 400 ms | 取自设置页「最近一次识别耗时」 |
| 「记一笔」转场遮挡 | **必现** | 无 | 见 4.3 |
| 卡片半透明导致的视觉糊 | **明显** | 无 | 见 4.2 |

**关键发现（推翻了原假设）**：设置页的「卡顿」不是掉帧。

- 渲染层 janky 只有 0.24%，远低于 5% 预算 → 帧本身跑得动
- 真正的问题是**半透明卡片的视觉问题**：卡片底色为
  `MaterialTheme.colorScheme.surface.copy(alpha = LocalSurfaceAlpha.current)`，
  背景图是原分辨率、`ContentScale.Crop` 铺满全屏。卡片半透明时，
  背景图从卡片底下透出来；**滚动时卡片下方的背景内容随之移动**，
  大面积区域每帧重新混合，视觉上表现为「糊、花、卡」。
- 实证：把「内容卡片不透明度」拖到最低（35%）后，背景图上的人像直接压在
  「自动记账」「默认账户」「餐饮」等文字上，几乎不可读 —— 这就是用户体感的来源。

> 结论：**设置页的问题要从「视觉合成」下手，而不是继续优化帧耗时。**
> 这与规格里「背景与文字对比度不足时自动托底：正文不在背景图上直接绘制」的原则
> 方向一致，但当前实现只在「空状态卡片」上做了不透明兜底，普通卡片没有。

### 4.2 卡片半透明的取舍

| 方案 | 评价 |
|---|---|
| **保留不透明度可调，但滑动时把背景层「冻结」（滚动中不重绘背景）** | 可能有效，但 `graphicsLayer` 缓存与滚动联动难以稳定 |
| **给背景图加一层随滚动不动的合成层** | 治本，但需重构 `BackgroundLayer` |
| **卡片默认更不透明，并把「不透明度」滑块的下限提高** | 直接、可解释，牺牲一点背景图可见度 |
| 去掉背景图功能 | 不采用，是用户明确要的功能 |

具体取舍在第 6 组任务里用实测决定。

### 4.3 「记一笔」遮挡

代码层面已确定是 zIndex 层级 bug（见第一节），基线为**必现**，
故不需要录屏逐帧统计，直接以「修复后不再遮挡」为验收。

## 五、实施记录

### 5.1 设置页卡顿的真正根因（推翻了两轮错误假设）

排查过程中被逐一排除的假设：

| 假设 | 排除方式 | 结论 |
|---|---|---|
| 半透明卡片合成太重 | 临时把 `AfuCard` 底色改成完全不透明，同样甩动 → janky 1.37%，与半透明时无差别 | 不是 |
| 背景图原分辨率太重 | 临时禁用 `BackgroundLayer` → janky 2.55%，p99 仍 150ms | 不是 |
| `blockAllInput` 的 pointerInput 循环 | 去掉后反而更差（2.53%） | 不是 |
| 无障碍服务拖慢输入分发 | 关闭无障碍服务后同样 2.54% | 不是 |
| 隐藏标签页在甩动时重组 | 加 `Log.d` 计数，甩动期间 **0 次**重组 | 不是 |
| `LazyColumn` 缺 key | 加 key + contentType 后 3.37%，p99 仍 150ms | 有效但不是主因 |
| `graphicsLayer { alpha = alpha }` 组合期读状态 | 按 Phase Deferral 改为在 lambda 内读 `.value` | 有效但不是主因 |

**真正的主因有两个，都在官方文档里写着：**

1. **在 `LazyColumn` 的 item 内部持有定时器**（`references/lazy-layouts.md` 第 5 条）。
   上一版把「5 秒刷新的后台状态」从 `AutoLedgerSection` 下沉到它内部的一个子
   `Composable`，以为能缩小重组范围 —— 但这让定时器跑在了 **lazy item 内部**。
   按官方说明，timers/Flow 在 item 里会让**每次 tick 同时失效所有可见 item**。
   修复：把 `rememberKeepAliveState()` 提升到 `SettingsScreen`（`LazyColumn` 之外）
   订阅一次，算好的值作为参数传进 item。

2. **一直在 debug 构建上做性能测量**。官方 skill 第一条前提：
   > Never profile or benchmark debuggable builds; ART runtime verification
   > severely degrades performance.

   debug 版 ART 不做 AOT、JIT 校验开销大，我之前测到的 p99 150ms 里有很大一块
   是构建本身的代价，不代表用户真实体验。

### 5.2 真机实测（小米 14 Pro，1440×3200）

设置页快速甩动（`input swipe 720 2600 → 720 300 80ms`，往返 6–8 轮）：

| 版本 / 状态 | 帧数 | janky | p50 | p90 | p95 | p99 |
|---|---|---|---|---|---|---|
| debug，修复前 | 534 | 3.37% | — | 24ms | 46ms | **150ms** |
| debug，修复后 | 636 | 2.20% | — | 23ms | 48ms | 150ms |
| **release，修复后（第一轮）** | 1264 | **0.79%** | 5ms | **8ms** | 9ms | **17ms** |
| **release，修复后（第二轮）** | 1596 | **0.44%** | 5ms | **9ms** | 16ms | **17ms** |
| 系统设置页（对照） | 1266 | 0.71% | 6ms | 9ms | 9ms | 19ms |

**结论：设置页甩动已达到与系统设置页同档水平（janky 0.44–0.79% vs 0.71%），
远低于规格要求的 5%。**

### 5.3 微信截图 OCR 耗时

| 阶段 | 耗时 |
|---|---|
| 修复前（v0.3.0，逐像素 Kotlin 插值） | 1894 ms |
| 交给系统缩放（Skia native） | 638 ms |
| 再去掉一次全尺寸 int 数组分配 | **557 ms** |

**仍未达到规格写的 400 ms。** 剩余开销的构成：

- det 推理（输入约 448×960，已缩到原图 9.3% 的像素）
- rec **逐行串行**推理：一屏 5 行文本就要跑 5 次 rec
- 全屏 HardwareBuffer → ARGB_8888 拷贝（这一步无法省，否则读不了像素）

要再往下压，需要改动推理层（批量 rec、或把 det 档位降到 640 并验证精度），
风险与验证成本都比本次「修缺陷」的目标大，因此**移出本次范围**。

### 5.3.1 真机复现的困难（如实记录）

MIUI 会周期性覆盖 `enabled_accessibility_services`：
通过 `adb shell settings put` 写入本服务后，系统的 `AppOps` 会拒绝
`CREATE_ACCESSIBILITY_OVERLAY`，服务随即被解绑，事件不再送达。

- 手动在系统设置里开一次，可以撑一段时间，能测到一组数据
- 但反复测试时很快又被踢掉，**无法稳定跑多轮对比**

因此支付宝的端到端耗时**未能在真机上取得可靠数字**，
改为用 [NodePathPolicyTest] 锁住「节点可读时必须走节点通道、绝不触发截图」这一调度决策。
这在逻辑上保证了支付宝不会付出截图 + OCR 的成本，但**不等于**实测了 ≤250ms。

### 5.4 交互统一

「记一笔」已改为居中 `AlertDialog`，与「新增账户」同一套语言（截图并排确认过）。
对话框是独立窗口，天然解决了原先的 zIndex 遮挡问题。

### 5.5 顺带发现的缺陷（未修，记录在案）

- **初始余额解析失败时静默变 0**：输入「/500」这类非法内容时
  `Money.parseYuan` 返回 null，`AccountEditorDialog` 直接 fallback 到 `Money.ZERO`
  并保存成功，用户看不出钱没被读进去。

## 六、审计依据

本节的修复方向来自 Google 官方 **`android/skills@jetpack-compose-performance`**
（`github.com/android/skills`）的 7 类审计清单，以及官方 Hero Benchmark 所用的
开源基准项目 **Pokedex Compose**（`compose-hero-benchmarks`）的实现范式。
