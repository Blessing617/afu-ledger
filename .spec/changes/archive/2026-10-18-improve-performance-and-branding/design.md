# 设计：性能与外观优化方案

## 一、性能根因诊断（先量后改）

实测基线（小米 14 Pro / Android 16）：

| 指标 | 当前值 | 目标 |
|---|---|---|
| 冷启动 `TotalTime`（3 次：695 / 703 / 739） | **703ms（中位）** | ≤ 800ms（已达标） |
| 悬浮窗出现延迟（命中 → 可见） | **188ms**（组合占 174ms） | ≤ 400ms（已达标） |
| 滚动帧耗时（Choreographer 自测，700 帧） | 均值 14.1ms / p90 24ms / >32ms **3.3%** | >32ms 帧 < 5%（已达标） |

**结论：三项渲染侧预算在改造前就已达标。**
因此「体感卡顿」的主要来源不在 UI 渲染，而在识别链路的固有延迟：
去抖（600ms 稳定判定）+ 截图与 OCR（数百 ms）+ 弹窗（188ms）。
渲染侧的优化目标调整为「消除可感知的退化点」（首帧主题闪变、背景过度绘制），
而不是追求数字上的大幅下降；预热复用仍做，但预期收益是 188ms → 更低，属于锦上添花。

**工具限制（实测踩到）**：
- `dumpsys gfxinfo com.afu.ledger` 恒为 `Total frames rendered: 0`，直方图全 0；
- `dumpsys SurfaceFlinger --latency <layer>` 除首行 refresh period 外全 0。
- 结论：HyperOS 上无法用系统命令取帧数据。改用**应用内 Choreographer 帧计时**（仅 debug 构建）作为唯一可信测量手段。

已定位的三个确定性瓶颈（读代码得出，非猜测）：

1. **悬浮窗每次重建组合环境**
   `PaymentConfirmOverlay.show()` 里 `ComposeView(context)` + `setContent(...)` + `addView` 全流程走一遍。
   首次弹出要付 Compose 运行时初始化 + 首次组合 + 字体/资源加载的代价。
   → 改为**服务连接时预热**：提前创建 ComposeView 并 attach 到 WindowManager 但**不可见**（宽高 0 或 alpha 0、置于屏幕外），命中时只更新内容与可见性。

2. **首帧主题错误再纠正**
   `MainActivity` 用 `collectAsStateWithLifecycle(initialValue = ThemeSettings())`，首帧永远是默认主题，
   等 DataStore 读完再重组一次（还是全树重组，因为 `AfuTheme` 在根）。
   → 冷启动前**同步读一次首值**：`runBlocking { themePreferences.settings.first() }` 在 `setContent` 之前取，
   作为 State 初值。DataStore 读取是本地文件，同步一次代价可接受（个位数毫秒），换来首帧正确。

3. **背景层放大过度绘制**
   全屏 `AsyncImage` + 全屏 88% 遮罩 + Scaffold 背景，三层叠满整屏；且 `AsyncImage` 未约束尺寸，
   可能按原图（1440×3200）解码。
   → 背景层单独封装、按屏幕尺寸请求解码（`size(Size.ORIGINAL)` 改为按需尺寸）、
   遮罩改为**只在需要时绘制**（可见度高时用卡片不透明底色兜底，而不是全屏遮罩）。
   → 列表项卡片已经是 `surface` 不透明色，所以遮罩可以显著降低甚至取消，靠卡片兜底可读性。

## 二、悬浮窗预热方案

```
服务连接 (onServiceConnected)
  └─ 预创建 ComposeView + LifecycleOwner
     └─ addView(params: 透明、不可触、屏幕外或 1x1)
        └─ setContent(空内容)
命中 (handleHit)
  └─ 设置组合内容 + 更新 params 为可见 + 移除 FLAG_NOT_TOUCHABLE
  └─ 记录 hit→visible 时间差写入识别日志
```

取舍：

| 方案 | 结论 |
|---|---|
| 每次新建 ComposeView（现状） | 简单，但首次弹出慢 |
| 预热复用单个 ComposeView | **选它**：稳态延迟最低，代价是常驻一个空窗口 |
| 改用 View 手写布局 | 延迟更低，但要重写整套 UI，性价比低 |
| Dialog / Activity 承载 | 需要 SYSTEM_ALERT_WINDOW 或跳 Activity，违背现有权限设计 |

预热窗口的代价与风险：
- 常驻一个 1×1 透明窗口，内存开销约等于一个空 ComposeView（可接受）
- 必须确保它**不拦截触摸、不参与截图**（现有逻辑已排除自身窗口，需补充验证）
- 服务断开时必须 `removeView`，否则泄漏窗口

## 三、图标方案

采用 **Adaptive Icon**：

```
res/mipmap-anydpi-v26/ic_launcher.xml
  <adaptive-icon>
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
  </adaptive-icon>
```

- 前景用 **VectorDrawable** 绘制大肥鱼（矢量可无损缩放，避免多套 PNG）
- 前景内容限制在 66dp 安全区内（108dp 画布，四周各留 18dp）
- 背景用主题色（花瓣粉或云白紫），保证任何遮罩下都有对比
- 同时提供 `mipmap-anydpi-v26/ic_launcher_round.xml` 与旧版本回退图（minSdk 26 → 只需 anydpi-v26 + 一张 fallback PNG）

素材来源二选一（待用户确认）：
- A. 用户提供 PNG（建议 432×432 以上，透明背景）→ 转为 foreground
- B. 由实现方用 VectorDrawable 画一版「圆滚滚鲸鱼娘」形象，后续可替换

## 四、不做的事

- 不引入 Baseline Profile（要额外构建与设备验证，收益需单独量化）
- 不做 Compose 全量重构（当前模块划分已合理）
- 不为性能牺牲功能（例如关掉背景图）
