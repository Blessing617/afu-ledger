# 设计：截图 OCR 自动记账（补上微信）

## 技术方案总览

关键取舍一句话：**OCR 不改识别链路，只换文本来源。**

现有链路是：

```
无障碍事件 → 采集节点文本 → PageSnapshot(packageName, texts) → PaymentPageDetector
          → PaymentDraft → DetectionDeduper → PaymentConfirmOverlay → RecordDetectedPaymentUseCase
```

本次在「采集文本」这一层加一个实现：

```
微信前台 + 页面稳定 → 节流判定 → takeScreenshot() → Bitmap → 离线 OCR
                   → OcrTextSanitizer（纠错） → PageSnapshot → 同上，全链路复用
```

`PaymentPageDetector`、`AmountParser`、`MerchantExtractor`、`DetectionDeduper`、`PaymentConfirmOverlay`、`RecordDetectedPaymentUseCase` **一行都不改**。OCR 唯一的产物就是一份 `PageSnapshot`，与无障碍采集的产物完全等价。

这样做的好处：所有「多金额歧义放弃」「排除词」「未来时态不记账」的既有安全规则自动对 OCR 生效，不需要在两套通道里各写一遍，也就不会出现两套规则漂移。

## 决策：OCR 引擎选型

### 对比

| 方案 | 包体增量 | 中文/符号准确率 | 集成成本 | 离线纯净度 | 结论 |
|---|---|---|---|---|---|
| **`onnxruntime-mobile` + PP-OCR 移动版（`.ort` 格式）** | **实测 +19.8 MB** | 高（实测微信页文案可读） | 中高：需自己实现 DB 后处理与 CTC 解码，另需一次性模型转换 | 纯离线，模型走 assets | **采用** |
| `onnxruntime-android`（full）+ `.onnx` 模型 | **实测 +49.7 MB** | 高 | 中高（同上，但不用转格式） | 纯离线 | 落选：仅 so 就 34 MB |
| Tesseract4Android + `chi_sim` | 约 4–6 MB（fast）/ 16 MB（best） | 中低：小数点、负号、`¥` 易丢，反白字与小字差 | 低（Maven 依赖 + 训练数据） | 纯离线 | 备选，作为体积兜底 |
| ML Kit 文本识别（中文，bundled） | 约 20 MB+ | 高 | 低 | 依赖 Google 生态与条款 | 否决 |
| 云端 OCR（任何一家） | 0 | 最高 | 低 | **违反「不申请 INTERNET」** | 否决 |
| Paddle-Lite + `.nb` 模型 | 约 15–20 MB | 高 | 高：AAR 分发不稳定，需自行编译或吃旧版 | 纯离线 | 否决（可维护性差） |

### 为什么选 PP-OCR（ONNX Runtime 路线）

1. **准确率是这次唯一的硬指标**。截图里要读的是「¥25.50」这种带货币符号、可能带负号、两位小数的字符串，Tesseract 在中文训练集上对小数点与 `¥` 的召回不稳定，纠错层要写得很厚才能用——而纠错越厚，「拿不准就放弃」的边界就越模糊。
2. **不引入 Google 依赖**。项目定位是「本地优先、不申请 INTERNET」，ML Kit 虽然 bundled 可离线，但它的分发条款与国内构建链路都不适合一个自用 + 开源的作品集项目。
3. **ONNX Runtime 是唯一需要的外部运行时**，Apache-2.0，Maven Central 可得，可被镜像加速；模型文件（det + rec + 字典）直接放 assets，无运行时下载。
4. 成本可控：det 后处理（DB）与 rec 解码（CTC）加一起约 200–300 行 Kotlin，是真要自己写，但边界清晰、可单测。

### 落地时被现实推翻的三件事（重要）

设计阶段以为「装 `onnxruntime-android` + 直接放 `.onnx`」就够了。实测发现不是：

1. **full 包的 so 有 34 MB**，release APK 从 2.2 MB 涨到 51.9 MB（+49.7 MB），远超预算。
   → 改用 `onnxruntime-mobile`，同样 `arm64-v8a` 的 so 只有 **3.7 MB**。
2. **`onnxruntime-mobile` 不认 `.onnx`**，报 `ONNX format model is not supported in this build`。
   → 必须用 `python -m onnxruntime.tools.convert_onnx_models_to_ort --target_platform arm` 转成 `.ort`。
3. **转换工具的 Fixed 优化会融合出 mobile 包没有的算子**：
   - det 报 `Could not find an implementation for BatchNormalization(9)`
   - rec 报 `Could not find an implementation for QuickGelu(1)`
   → 两个解法叠加：① 转换改用 `--optimization_style Runtime`（不做那个融合）；
   ② 转换前用脚本把 BN 展开成等价的 `Mul + Add`（推理期 BN 只是仿射变换，参数是常量）。
   BN 展开后做了数值一致性验证：同一输入下与原模型**最大逐元素差异 1.5e-7**，属于浮点误差。

   整条重建流程（下载 → BN 展开 → 转 ORT）固化成脚本 **[tools/ocr/prepare_ocr_models.py](../../../tools/ocr/prepare_ocr_models.py)**，
   一条命令就能重新生成 `assets/ocr/` 下的三个文件。**模型资产不能手搓，必须由这个脚本产出**，
   仓库里的 `.ort` 就是脚本的产物（已逐字节比对确认）。

模型转换后的资产：

| 文件 | 大小 | 说明 |
|---|---|---|
| `assets/ocr/det.ort` | 4.89 MB | PP-OCRv4 det mobile，BN 已展开 |
| `assets/ocr/rec.ort` | 11.0 MB | PP-OCRv4 rec mobile（中英数字字典） |
| `assets/ocr/ppocr_keys_v1.txt` | 26 KB | 6623 字字典 |

### 实测数据（Android 16 真机 / Android 16）

| 指标 | 实测值 |
|---|---|
| release APK 体积（含 OCR） | **22.0 MB**（改动前基线 2.2 MB，**增量 19.8 MB**） |
| `libonnxruntime.so`（arm64-v8a） | 3.7 MB |
| 单次「截图 → OCR → 文本行」耗时 | **912 ms**（启用 `session.enable_saved_runtime_optimizations` 前为 1067 ms） |
| 识别质量 | 「¥25.50」「瑞幸咖啡」「支付成功」「-42.00」全部正确 |
| 30 秒持续滚动 | 490 个无障碍事件 → **0 次截图**（事件持续变化时去抖一直推迟，不截图） |
| 2.2 秒间隔滚动 8 次 | 6 次判定 → **6 次全部被节流拦截**（8 秒最小间隔生效） |

耗时 912 ms 略高于设计目标 800 ms，但这是**整条链路**（截图转换 + det + 20 行 rec）的端到端时间，且被节流限制为「切页后最多一次」，可以接受。

### 一条需要写清楚的语义

「页面稳定」用的是事件去抖：**只要事件还在连续到达，就一直推迟判定**。所以在微信里持续滚动时不会截图 —— 这是省电上的最优行为（滚动中不可能是支付结果页）。代价是若某个页面永远在动（长时间动画），就永远不判定；这类页面不是支付页，可以接受。

### 被否决路线的补充说明

- **Tesseract 作为备选保留**：如果实测 PP-OCR 路径的包体或集成难度超出预期，退路是 Tesseract + `chi_sim` + **固定 ROI 裁剪**（只识别屏幕中上部金额区域），把「整页识别」降级为「局部识别」来弥补准确率。**这是兜底方案，不并行实现**。本次实测包体增量 19.8 MB 已在预算内，因此不启用。
- **不做的组合**：不上「双引擎投票」。两套模型加起来 30 MB+，收益远低于「拿不准就放弃 + 用户点确认」的成本。

## 决策：ABI 裁剪

`onnxruntime-mobile` 的 AAR 同样含多 ABI。目标机型是Android 16 真机（`arm64-v8a`），做法：

```kotlin
defaultConfig {
    ndk { abiFilters += "arm64-v8a" }
}
```

代价：其他 ABI 设备上 OCR 不可用（无障碍通道仍然可用）。因为这是**自用 App + 单一目标机型**，这个取舍划算。发布说明里要写清楚。

模型文件用 `androidResources { noCompress.add("ort") }`，避免 aapt 对已压缩格式再压一遍，也避免首次解压耗时异常。

模型资产版本由 `OcrModelInstaller.MODEL_VERSION` 控制：换模型必须递增版本号，解压目录里会写一个 `.ready` 标记（内容为版本号），只比文件大小不够 —— 换模型后字节数可能相近，会被误判成「已就绪」而继续用旧模型（实测踩过这个坑）。

## 决策：截图通路

`AccessibilityService.takeScreenshot()` 需要：

- 服务 XML 声明 `android:canTakeScreenshot="true"`（**现有配置已声明**，无需改动）
- 系统 API ≥ 30（Android 11）。项目 `minSdk = 26`，所以运行时要判断 `Build.VERSION.SDK_INT >= 30`，低于则整条 OCR 通道禁用，设置页显示「系统版本不支持」

调用形态：

```kotlin
takeScreenshot(
    Display.DEFAULT_DISPLAY,
    executor,
    object : TakeScreenshotCallback {
        override fun onSuccess(result: ScreenshotResult) { /* result.hardwareBuffer + colorSpace */ }
        override fun onFailure(errorCode: Int) { /* 映射到失败原因 */ }
    },
)
```

要点：

- `ScreenshotResult.hardwareBuffer` 包出来的是 **`Bitmap.Config.HARDWARE`**，不能直接读像素。要先 `Bitmap.wrapHardwareBuffer(...)` 再 `copy(Bitmap.Config.ARGB_8888, false)`，然后**立刻 `close()` 掉 HardwareBuffer 并 `recycle()` 掉硬件位图**，否则内存暴涨
- 失败码要映射成可读原因：`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`（系统侧也有间隔限制，正好与我们的节流叠加）、`ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS`、`ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY`、`ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR`
- 只处理 `Display.DEFAULT_DISPLAY`，不处理分屏与副屏
- 截图前判断 `PowerManager.isInteractive` 与 `KeyguardManager.isKeyguardLocked`
- **悬浮窗正在显示时不截图**：省电，同时避免把自家确认卡片 OCR 进文本里

## 决策：节流策略（本次的耗电核心）

三段式：**稳 → 冷 → 退**。

| 参数 | 值 | 说明 |
|---|---|---|
| `STABLE_DELAY` | 600 ms | 页面事件静默这么久才认为「页面稳定」，可以截 |
| `MIN_INTERVAL_CONTENT` | 8 000 ms | 内容变化事件之间的最小截图间隔 |
| `MIN_INTERVAL_WINDOW` | 1 500 ms | 窗口状态变化（真正切页）的最小间隔 |
| `MISS_BACKOFF_1` | 连续 3 次未命中 → 间隔 ×4 | 聊天页里一直滑，别一直截 |
| `MISS_BACKOFF_2` | 连续 5 次未命中 → 间隔 60 s | 上限 |
| `OCR_TIMEOUT` | 2 500 ms | 单次 OCR 超时即放弃本次 |
| 退避重置 | 收到 `WINDOW_STATE_CHANGED` | 切页代表用户有新动作，重新给机会 |

为什么窗口状态变化要单独给短间隔：微信「支付成功」页是**新页面覆盖**上来的，会发 `WINDOW_STATE_CHANGED`。如果和内容变化共用 8 秒节流，用户在 8 秒内刷过聊天再付款就会**漏记**——漏记比多截一次图糟糕得多。

判定顺序（短路，先便宜后昂贵）：

```
总开关开？ → OCR 开关开？ → 悬浮窗没显示？ → API ≥ 30？ → 屏幕亮且未锁屏？
→ 包名在 OCR 白名单？ → 距上次截图 ≥ 该事件类型的间隔？ → 页面已稳定？
→ 单飞槽位空闲？ → 截图
```

单飞：同一时刻只允许一个 OCR 任务在跑。新任务到来时若旧任务未完成，**丢弃旧的**（页面已经变了，旧结果没意义），不排队。

`ScreenshotThrottle` 做成纯 Kotlin 状态机（注入时钟），输入是「事件类型 + 包名 + 时间」，输出「是否允许截图 + 是否重置退避」，全部可 JVM 单测。

### 真机联调又踩到的三个坑（节流因此改过一次）

最初只把 `TYPE_WINDOW_STATE_CHANGED` 当「切页」，结果**微信账单详情页压根不弹窗**。日志证明事件其实收到了，但每次判定都是 `THROTTLED`。三个原因叠在一起：

1. **微信在同一个 Activity 内翻页（聊天 → 账单列表 → 账单详情）只发内容变化事件**，不发窗口状态变化事件。于是翻页被当成「同页滚动」，撞上内容变化的 8 秒间隔。
2. **内容变化事件的 `className` 大多是控件类名**（`android.view.ViewGroup`、`LinearLayout`、`ProgressBar`），不是页面类名。拿它当「页面标识」会在同一页面里疯狂误判。
3. **换页信号会被随后的内容事件冲掉**：去抖的实现是「每次事件 cancel 上一个任务、重建一个」，微信切页后会继续刷一串内容事件，最后执行的那次判定拿到的 `pageChanged` 已经是 `false` 了。

对应改法：

| 坑 | 改法 |
|---|---|
| 1 | 服务 XML 订阅 `typeViewClicked`；**点击**是「用户主动换页」最可靠的信号（翻页必然伴随点击） |
| 2 | `className` 只接受非 `android.*` 前缀的类，控件类名一律忽略 |
| 3 | 用 `pageChangePending` 累积标记，在判定执行时才消费并清空，事件怎么合并都不会丢 |

改完后真机验证通过：点开微信账单详情页 → 弹窗金额 `¥42.00`、商户 `Valve` 与页面一致，点「忽略」后首页没有新增交易。

### OCR 白名单

| 包名 | 通道 |
|---|---|
| `com.tencent.mm`（微信） | 节点文本为空时走 OCR |
| `com.eg.android.AlipayGphone`（支付宝） | 只走无障碍，**不截图** |
| debug 构建下的本应用包名 | 只走无障碍（模拟页方便断言） |

判断「节点文本为空」用阈值而不是严格 0：节点数 < 3 或文本总长 < 12 字符即认为「节点不可用」。微信实测是 0，但给一点余量更稳。

## 决策：OCR 纠错与「拿不准就放弃」

纠错分两步，都在 OCR 文本进入 `PageSnapshot` **之前**完成，所以现有 detector 拿到的已经是干净文本。

### 第一步：字符级归一化（`OcrTextSanitizer`）

只改「数字上下文」里的字符，不做全文无脑替换，否则会把商户名「COCO 都可」改成「C0C0 都可」。

| 规则 | 例子 |
|---|---|
| 全角数字 / 全角符号 → 半角 | `２５．５０` → `25.50` |
| 数字串内的 `O`/`o`/`D` → `0` | `-42.OO` → `-42.00` |
| 数字串内的 `l`/`I`/`|` → `1` | `l2.00` → `12.00` |
| 数字串内的 `S` → `5`、`B` → `8`、`Z` → `2` | `4Z.5O` → `42.50` |
| 紧邻数字的 `Y`/`y` → `¥` | `Y25.50` → `¥25.50` |
| 空白 / 不换行空格 / 千分位逗号清理 | `¥ 1,299.00` → `¥1299.00` |

「数字串内」的判定：字符左右 3 个字符内至少出现一个数字。

### 第二步：严格校验（复用 `AmountParser` + 新增上限）

| 校验 | 不通过时 |
|---|---|
| 金额必须带 `¥`/`￥`，或整条就是两位小数（仅 debug 账单规则） | 放弃，记「金额格式不合规」 |
| 小数位 ≤ 2 | 放弃 |
| 页面只出现一个互不相同的金额 | 放弃，记「金额歧义」 |
| 金额 > 0 且 ≤ 100 000 元 | 放弃，记「金额超上限」/「金额为零」 |
| 页面必须命中支付成功 / 账单详情特征词 | 放弃，记「不是支付页面」 |

### 宁可漏记的具体边界

- 只要出现**两个**金额就放弃，哪怕其中一个看起来更像「优惠金额」——不做「取最大/取第一个」这类猜测
- OCR 置信度低于阈值（rec 模型输出分数的行均值 < 0.6）的行**直接丢弃**，不参与金额候选
- 截图与 OCR 之间页面若已切换（包名变了 / 新一轮事件到来），本次结果**作废**
- 任何异常（OCR 崩溃、模型文件缺失、OOM）只记日志，不弹窗

## 数据流

```
AutoLedgerAccessibilityService（瘦身：只收事件 + 转发）
        │
        ▼
AutoRecognitionEngine（编排，非 Service 类）
        ├── 无障碍文本非空 ──► PageSnapshot ─────────────┐
        └── 文本为空 + 在白名单 ──► OcrTextChannel       │
                                      │                 │
                                      ▼                 │
                              ScreenshotThrottle        │
                                      │ 允许            │
                                      ▼                 │
                              ScreenshotCapturer        │
                                      │ Bitmap          │
                                      ▼                 │
                                  OcrEngine             │
                                      │ 行列表 + 置信度 │
                                      ▼                 │
                              OcrTextSanitizer          │
                                      │                 │
                                      ▼                 ▼
                                   PageSnapshot ─► PaymentPageDetector（复用）
                                                        │
                                                        ▼
                                        既有 悬浮窗 / 去重 / 入账
```

`AutoRecognitionEngine` 独立出来是必要的：`AutoLedgerAccessibilityService.kt` 已经 301 行，压在项目「单文件 ≤ 300 行」红线上，再塞 OCR 编排必然爆。Service 保留事件接收、去抖、总开关判断与悬浮窗回调，其余逻辑迁移到 engine。

## 文件改动

新增：

- `app/src/main/java/com/afu/ledger/auto/AutoRecognitionEngine.kt` — 识别编排（无障碍 / OCR 双来源）
- `app/src/main/java/com/afu/ledger/auto/ocr/ScreenshotCapturer.kt` — `takeScreenshot` 封装、API 门禁、错误码映射、Bitmap 转换与释放
- `app/src/main/java/com/afu/ledger/auto/ocr/ScreenshotThrottle.kt` — 节流与退避状态机（纯 Kotlin）
- `app/src/main/java/com/afu/ledger/auto/ocr/OcrEngine.kt` — OCR 引擎接口与结果类型
- `app/src/main/java/com/afu/ledger/auto/ocr/PpOcrEngine.kt` — ONNX Runtime 实现（det + rec 编排）
- `app/src/main/java/com/afu/ledger/auto/ocr/OcrImageOps.kt` — 双线性缩放、归一化、裁切（纯 Kotlin）
- `app/src/main/java/com/afu/ledger/auto/ocr/DbPostProcessor.kt` — det 概率图二值化、连通域、unclip（纯 Kotlin）
- `app/src/main/java/com/afu/ledger/auto/ocr/CtcDecoder.kt` — CTC 贪心解码（纯 Kotlin）
- `app/src/main/java/com/afu/ledger/auto/ocr/OcrAmountGuard.kt` — 金额可信度闸门（纯 Kotlin）
- `app/src/main/java/com/afu/ledger/auto/OcrStatus.kt` — OCR 运行状态（内存 StateFlow）
- `app/src/main/java/com/afu/ledger/auto/AutoLedgerLogger.kt` — 识别日志写入口
- `app/src/main/java/com/afu/ledger/auto/PaymentConfirmController.kt` — 悬浮窗编排（从服务里抽出）
- `app/src/main/java/com/afu/ledger/auto/ocr/OcrModelInstaller.kt` — assets → `filesDir` 解压与版本校验
- `app/src/main/java/com/afu/ledger/auto/ocr/OcrTextSanitizer.kt` — 字符级纠错（纯 Kotlin）
- `app/src/main/java/com/afu/ledger/auto/ocr/OcrLineBuilder.kt` — OCR 框 → 文本行（按 y 排序、去重、合并）
- `app/src/main/java/com/afu/ledger/auto/ocr/OcrTextChannel.kt` — 节流 → 截图 → OCR → 纠错 → `PageSnapshot`
- `app/src/main/assets/ocr/det.ort`、`rec.ort`、`ppocr_keys_v1.txt` — 模型与字典
- `app/src/test/java/com/afu/ledger/auto/ocr/ScreenshotThrottleTest.kt`
- `app/src/test/java/com/afu/ledger/auto/ocr/OcrTextSanitizerTest.kt`
- `app/src/test/java/com/afu/ledger/auto/ocr/OcrLineBuilderTest.kt`
- `app/src/test/java/com/afu/ledger/auto/ocr/OcrAmountGuardTest.kt`
- `app/src/test/java/com/afu/ledger/auto/ocr/OcrSampleDetectorTest.kt` — 用 OCR 真实输出样本喂 detector

修改：

- `app/src/main/java/com/afu/ledger/auto/AutoLedgerAccessibilityService.kt` — 瘦身为事件转发 + 悬浮窗回调
- `app/src/main/java/com/afu/ledger/auto/PaymentPageRules.kt` — 账单规则提到前面（真实账单页也带「支付成功」文案）
- `app/src/main/java/com/afu/ledger/auto/AutoLedgerSettings.kt` — 增加 `ocrEnabled`
- `app/src/main/java/com/afu/ledger/auto/AutoLedgerSettingsRepository.kt` — 持久化 `ocrEnabled`
- `app/src/main/java/com/afu/ledger/auto/DetectionResult.kt` — 增加 OCR 相关失败原因
- `app/src/main/java/com/afu/ledger/data/AppContainer.kt` — 注入 OCR 引擎与文本通道
- `app/src/main/java/com/afu/ledger/ui/settings/SettingsViewModel.kt`、`AutoLedgerSection.kt` — OCR 开关与状态
- `app/src/main/res/values/strings.xml` — 服务描述披露截屏行为
- `app/src/main/res/xml/auto_ledger_service.xml` — 仅核对（`canTakeScreenshot` 已存在）
- `app/build.gradle.kts`、`gradle/libs.versions.toml` — ONNX Runtime 依赖、`abiFilters`、`noCompress`
- `app/src/debug/java/com/afu/ledger/debug/DemoScenario.kt`、`DemoPaymentActivity.kt` — 新增 OCR 噪声场景
- `README.md` — 特性、包体说明、ABI 限制、已知边界

不改：

- `app/src/main/java/com/afu/ledger/auto/PaymentPageDetector.kt`、`AmountParser.kt`、`MerchantExtractor.kt`、`DetectionDeduper.kt`、`PaymentDraft.kt`、`PageSnapshot.kt`、`overlay/*`、`RecordDetectedPaymentUseCase.kt`

## 测试策略（不产生真实支付）

### 1. JVM 单测（无需 Android / 无需模型）

- `ScreenshotThrottleTest`：注入假时钟，断言「内容事件 8s 内只放行一次」「窗口事件 1.5s 后可放行」「连续未命中退避到 60s」「窗口变化重置退避」「息屏不放行」
- `OcrTextSanitizerTest`：表驱动，`-42.OO` → `-42.00`、`¥4Z.5O` → 规整后仍不合规、`Y25.50` → `¥25.50`、`２５．５０` → `25.50`、商户名 `COCO都可` 不被改写
- `OcrLineBuilderTest`：乱序框 → 按 y 归一化成行、同一行碎片合并
- `OcrSampleDetectorTest`：把真机 OCR 出来的真实文本列表存成样本，喂 `PaymentPageDetector`，断言命中 / 歧义 / 放弃

### 2. debug 模拟页（不花钱、可重复）

在既有 `DemoPaymentActivity` 上扩场景（`adb shell am start` 触发）：

| 场景 | 页面要点 | 期望 |
|---|---|---|
| `WECHAT_OCR_FULL` | 模拟微信支付成功整屏：状态栏、顶部栏、「支付成功」、「¥25.50」、「瑞幸咖啡」、「完成」 | OCR 命中 2550，弹窗 |
| `OCR_NOISE_O` | 金额渲染成 `-42.OO` | 纠错为 42.00，弹窗 |
| `OCR_NOISE_BAD` | 金额渲染成 `¥4Z.5O` 且无其他金额 | 不弹窗，日志「金额格式不合规」 |
| `OCR_LOW_CONTRAST` | 深底浅字小字号 | 允许失败，但不弹错金额 |
| `OCR_CHAT` | 聊天式文本 + 一个 `¥25.50` | 不弹窗（无支付特征词） |

注意既有坑：同一 Activity 重复 `am start` 只走 `onNewIntent`，测试脚本要先 `input keyevent 4` 退出再进。

### 3. 微信历史账单详情页（真机，不花钱）

`我 → 服务 → 钱包 → 账单 → 点开一笔旧交易`，这就是真实的自绘页面，与支付成功页的渲染方式一致。

- 验证 OCR 能不能读出金额行与「账单详情」特征
- 验证节流：停留 60 秒内不应出现截图风暴（`dumpsys` / 日志计数）
- 该页面规则 `debugOnly`，release 不触发，符合既有约定
- **验证入账走模拟页，账单页只点「忽略」**；万一误点确认，测试后到首页删掉那条

### 4. 支付宝回归

支付宝不截图：确认 OCR 通道对支付宝完全不激活，且原有识别、弹窗、入账行为不变。

### 5. 真机验收口径

Android 16 真机 上：模拟页 5 个场景结果符合期望表；微信历史账单详情页弹窗金额正确；支付宝回归正常；`./gradlew test` 全绿；合并 Manifest 仍无 `INTERNET`。**不要求真实支付一笔。**

## 风险与对策

| 风险 | 对策 |
|---|---|
| OCR 认错金额 | 严格校验 + 单金额 + 置信度阈值 + 用户点「记一笔」才入账；任何一项不满足就放弃 |
| 截图 + OCR 耗电 | 三段节流（稳定 600ms / 内容 8s / 窗口 1.5s）+ 连续未命中退避到 60s + 单飞 + 2.5s 超时；支付宝完全不截图 |
| APK 体积膨胀 | `abiFilters arm64-v8a` + `noCompress onnx`；实测后回填体积数据；必要时退到 Tesseract 方案 |
| OCR 冷启动慢 | 服务连接后预加载模型（`onServiceConnected` 里异步 warm-up），首次识别不吃冷启动损耗 |
| 模型文件被清理 / 解压失败 | 首次使用前校验文件存在与大小，缺失则重新解压；仍失败则通道标记不可用并写日志 |
| 微信支付成功页停留很短，被节流漏掉 | 窗口状态变化走 1.5s 短间隔，且窗口变化会重置退避 |
| 悬浮窗被 OCR 读进去污染文本 | 悬浮窗显示期间不截图；截图前二次确认 |
| HyperOS 冻结服务 | 既有风险，引导不变；表现为不识别而不是错记 |
| `takeScreenshot` 在部分 ROM 不可用 | 失败即降级为「微信不识别」，设置页显示不可用，绝不假成功 |
