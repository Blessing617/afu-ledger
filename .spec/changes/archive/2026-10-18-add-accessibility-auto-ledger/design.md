# 设计：无障碍自动记账技术方案

## 通道选择

只做无障碍单通道，其余通道明确排除（见 proposal 的范围）：

| 方案 | 结论 | 理由 |
|---|---|---|
| 无障碍读屏 | **采用** | 不需要 root，不联网，能在支付结果页拿到金额文本 |
| 通知监听 | 后续 | 需「通知使用权」，且通知常缺商户名，必须与读屏融合才有用 |
| 截图 OCR | 后续 | 需 MediaProjection 或截图权限，性能与复杂度都高；留给自绘页面兜底 |
| 短信 / 银行推送 | 后续 | 只覆盖到账收入，本次只做支出 |
| Xposed / root | 排除 | 与「自己日常用」的定位不符 |

参考实现只借鉴机制：指纹 + 时间窗去重、「未来时态不记账」来自
[blue-whale-keeper](https://github.com/a2244921700-code/blue-whale-keeper)；
以「支付成功页」为触发点的划分来自 [autoexpense-releases](https://github.com/Meggie6363/autoexpense-releases)。

## 权限与 Manifest

```
<!-- 服务声明，不是权限申请 -->
<service
    android:name=".auto.AutoLedgerAccessibilityService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/auto_ledger_service" />
</service>
```

`res/xml/auto_ledger_service.xml` 要点：

- `accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"`
- `accessibilityFeedbackType="feedbackGeneric"`
- `canRetrieveWindowContent="true"`
- `notificationTimeout="400"`（页面渲染是多次事件，合并后再解析）
- 不限定 `packageNames`，改为**运行时白名单过滤**：debug 构建需要放行自家模拟页面，且部分 ROM 对 XML 包名过滤的行为不一致
- 不申请 `SYSTEM_ALERT_WINDOW`、`POST_NOTIFICATIONS`、`INTERNET`、`READ_SMS`

悬浮窗用 `TYPE_ACCESSIBILITY_OVERLAY`，它随无障碍服务授权生效，无需额外权限。

## 组件划分

新增包 `com.afu.ledger.auto`，与 `bookkeeping` 解耦：

```
auto/
├── AutoLedgerAccessibilityService.kt   只做事件去抖 → 快照 → 交给引擎
├── PageSnapshot.kt                     包名 + 文本列表（可测的抽象）
├── PaymentPageRules.kt                 包名白名单、成功关键词、排除词、金额正则（含 debug-only 账单详情页规则）
├── PaymentPageDetector.kt              判定页面 + 提取金额/商户，输出 PaymentDraft?
├── AmountParser.kt                     文本清洗 → Money（复用 Money.parseYuan）
├── MerchantExtractor.kt                商户名（尽力，可空）
├── DetectionDeduper.kt                 指纹 + 60 秒窗口 + LRU
├── PaymentDraft.kt                     识别结果（金额/商户/来源包名/时间）
├── RecordDetectedPaymentUseCase.kt     校验账户分类 → 写 Transaction
├── AutoLedgerSettingsRepository.kt     DataStore 读写（开关/默认账户/默认分类/日志）
├── RecognitionLog.kt                   内存环形日志（最多 50 条）
└── overlay/
    ├── PaymentConfirmOverlay.kt        WindowManager 容器 + 显示/关闭/超时
    ├── PaymentConfirmContent.kt        确认卡片（Compose）
    └── ServiceLifecycleOwner.kt        给 ComposeView 提供 lifecycle/ViewModelStore
```

约束沿用项目现有红线：一个文件一个主要类型、单文件 ≤ 300 行、UI 不碰 DAO。

## 识别流程

1. `onAccessibilityEvent` 收到 `WINDOW_STATE_CHANGED` / `WINDOW_CONTENT_CHANGED`
2. 包名不在白名单 → 直接返回（不读节点）
3. 400ms 去抖（同一包名连续事件只处理最后一次）
4. `rootInActiveWindow` 递归取 `text` + `contentDescription`，上限 800 节点 / 16 层 / 4000 字符，避免卡顿
5. 得到 `PageSnapshot(packageName, texts)`
6. `PaymentPageDetector`：
   - 必须命中至少一个成功关键词（「支付成功」「付款成功」「交易成功」「已支付」等）
   - 必须不含排除词（「待支付」「确认付款」「将于」「退款」「收款」「账单详情」等）
   - 金额候选：`[¥￥]\s*\d{1,3}(,\d{3})*(\.\d{1,2})?` 或 `\d+(\.\d{1,2})?\s*元`；
     账单详情页（debug）额外接受「整条就是两位小数」的裸金额（如 `-25.50`），
     单号（长整数）、时间（`12:30`）、日期都不会被误认
   - **候选金额出现两个及以上互不相同的值 → 判定为歧义，放弃并记日志**（宁可漏记不误记）
7. 金额 → `AmountParser` 清洗（去 `¥￥`、逗号、空格）→ `Money.parseYuan` → 分
8. 商户名：金额节点附近、排除按钮/时间/金额/已知关键词后取第一个 2–20 字文本；取不到则为空
9. `DetectionDeduper` 判重：`sha1(pkg|cents|merchant)` + 60 秒窗口
10. 未重复且总开关开启 → 展示悬浮窗

## 悬浮窗

- `WindowManager.LayoutParams`：`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`，
  **宽度 `MATCH_PARENT`、高度 `WRAP_CONTENT`**，贴屏幕下方、避开手势区
  （实测 `WRAP_CONTENT` 宽度下 ComposeView 会被测成 0×0，窗口不可见；宽度给满后卡片在内容层居中）
- 内容用 `ComposeView` 承载，外面套项目 `AfuTheme`，主题色读当前 DataStore 设置，保证与 App 一致
- Compose 需要 `LifecycleOwner` / `ViewModelStoreOwner`：用 `ServiceLifecycleOwner` 手写实现，不引入第三方 lifecycle-service 库
- 卡片内容：金额大字、商户名（可空）、账户与分类两个下拉、`记一笔` / `忽略`
- 60 秒无操作自动关闭，且不入账
- 同一时刻只允许一个悬浮窗；新识别到来时若指纹不同，替换旧的

## 入账

`RecordDetectedPaymentUseCase`：

1. 取默认账户；账户不存在或已归档 → 返回「需要创建账户」结果，悬浮窗改为跳转引导
2. 取默认分类；分类不存在、已删除或不是支出类型 → 回退到支出分类「其他」
3. 写 `Transaction(type = EXPENSE, amount = 分, accountId, categoryId, note = 商户名, occurredAt = 识别时刻)`
4. 写库成功后才关闭悬浮窗，并用 Toast 提示「已记一笔」；失败则保留悬浮窗并在卡片内显示错误文案

不新增写库路径，复用 `TransactionRepository.add`。

## 配置与日志

DataStore 新键（沿用 `ThemePreferences` 的写法，放到独立的 `AutoLedgerSettingsRepository`）：

- `auto_ledger_enabled: Boolean`（默认 false，需要用户显式开启）
- `auto_ledger_account_id: Long?`
- `auto_ledger_category_id: Long?`
- `auto_ledger_debug_log: Boolean`（默认 false）

识别日志：内存环形缓冲最多 50 条，记录时间、包名、结果（成功/歧义/未命中/解析失败）、金额、商户名、文本摘要（前 200 字）。关闭日志时既不记录也不保留，满足「不保存原始页面文本」。

未持久化到 Room：需要 DB 迁移（v1 → v2），而日志只是排查用途，内存足够；进程重启后清空是可接受的取舍。

## Android 16 / HyperOS 适配

- **Android 13+ 侧载限制**：无障碍服务默认被拦截为「受限制的设置」。引导文案必须给出「应用信息 → 右上角菜单 → 允许受限制的设置」，并提供 `ACTION_APPLICATION_DETAILS_SETTINGS` 跳转
- 服务是否开启的判定：`AccessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)` 里匹配本服务 id，比读 `Settings.Secure` 稳妥
- **HyperOS 保活**：引导文案给出「应用设置 → 自启动」「省电策略 → 无限制」，不做不可靠的厂商 intent 跳转
- 服务被系统停止后，用户重新开启即可恢复；不申请前台服务，避免常驻通知

## 风险与对策

| 风险 | 对策 |
|---|---|
| 微信自绘页面读不到节点 | 不弹窗、记日志；后续 OCR 变更兜底 |
| 微信/支付宝改版改文案 | 规则集中在 `PaymentPageRules`，改一处即可；用文本特征而非 resource-id |
| 多个金额导致记错 | 歧义直接放弃，不猜 |
| HyperOS 杀服务 | 引导 + 真机验证；不承诺「一直活着」 |
| 悬浮窗遮挡支付页操作 | `FLAG_NOT_TOUCH_MODAL`，卡片只占底部区域，可忽略 |
| 账单详情页规则误入正式版，翻旧账被弹窗打扰 | 规则标记 `debugOnly`，release 构建不加载 |
| 服务被系统重绑后内存去重状态重置，可能重复弹窗 | 弹窗前检查「悬浮窗是否已在显示」，已显示则不再叠加 |
| HyperOS 会冻结后台无障碍服务（实测：几十秒后不再收到事件） | 引导用户开自启动、省电策略设为「无限制」；不承诺常驻 |

## 真机验证结论（小米 14 Pro / Android 16 / HyperOS V816）

| 页面 | 无障碍可读性 | 结论 |
|---|---|---|
| 微信（聊天、账单列表、账单详情） | **不可读**：`rootInActiveWindow` 文本为 0，`uiautomator dump` 也只有 1 个节点 | 微信为自绘渲染，本次单通道对微信**无效**，需 OCR 兜底 |
| 支付宝账单详情页 | **可读**：29 条文本，含金额「支出25元」与「账单详情」「交易单号」等特征 | 金额识别正确；店铺名（`qi**店`）不在无障碍树里，商户名留空 |
| 支付宝账单列表页 | 可读 | 命中「退款」筛选词，被排除，不弹窗（符合预期） |
| 内置模拟页 | 可读 | 六个场景全部符合预期 |

附带发现：安装新 APK 后无障碍服务可能不会自动重绑，需要用户重新开启；HyperOS 冻结服务时进程仍在、`dumpsys` 也显示 bound，但事件不再送达。

截图通路已验证：service 配置加上 `android:canTakeScreenshot="true"` 后，`AccessibilityService.takeScreenshot()` 在 HyperOS + Android 16 上成功返回 1440×3200 画面（未声明该能力时返回 `Services don't have the capability of taking the screenshot`）。微信页面文字在截图中清晰可辨，**截图 + 离线 OCR 是微信唯一可行的自动记账路线**，但属于独立变更（成本：离线 OCR 模型 15–30 MB、单次识别数百毫秒、需要做页面触发节流与识别纠错）。

## 测试策略（不产生真实支付）

**不要求真实付款**：支付流程不可自动化、还会真的花钱，验证一律走下面三条数据来源。

### 1. JVM 单测（覆盖解析与判定）

全部不依赖 Android 框架：

- `AmountParser`：表驱动，「¥25.50」→2550、「1,299.00」→129900、「25.5」→2550、「¥ 0.01」→1、空串/乱码→null
- `PaymentPageDetector`：给定 `PageSnapshot` 文本列表，断言命中/不命中/歧义
- `MerchantExtractor`：给定文本列表，断言提取与排除
- `DetectionDeduper`：同指纹窗口内判重、不同商户不合并、窗口过期后可再次触发

### 2. 历史账单详情页（真机，不花钱）

微信/支付宝都有历史账单：`我 → 服务 → 钱包 → 账单`，点开一笔旧交易即为「账单详情页」，页面里同样有金额、商户名、支付方式、交易单号。

- 账单详情页与支付成功页**文案结构不同**，因此规则表里单独维护一组，且**仅在 debug 构建启用**（见下）
- 用它验证：真实页面上的节点可读性、金额与商户名提取、悬浮窗展示、去重、超时
- 测试时确认入账会写入一条历史交易（会与原有账目重复），**验证入账路径用模拟页，用账单页时点「忽略」**；万一点了确认，测试完回首页删掉那条

### 3. 内置模拟支付页（debug-only，覆盖边界）

在 `src/debug` 下加一个 `DemoPaymentActivity`，用 Compose 复刻几套文案，`adb shell am start` 即可触发真实的无障碍事件链路：

| 场景 | 页面文案要点 | 期望 |
|---|---|---|
| 微信支付成功 | 「支付成功」「¥25.50」「瑞幸咖啡」 | 弹窗，金额 2550，商户「瑞幸咖啡」 |
| 支付宝账单详情 | 「账单详情」「-25.50」「地铁」 | 弹窗（账单页规则），金额 2550 |
| 多金额歧义 | 同时出现「¥25.50」与「¥3.00」 | 不弹窗，日志记「金额歧义」 |
| 未来时态 | 「将于明日扣款 19.90 元」 | 不弹窗 |
| 退款 | 「退款成功」「¥25.50」 | 不弹窗 |
| 非支付页 | 聊天列表式文本 | 不弹窗 |

`DemoPaymentActivity` 只存在于 debug 源集，release 包不含它，也不含账单详情页规则。

注意：同一个 Activity 重复 `am start` 只走 `onNewIntent`，Compose 语义树变化不保证发窗口事件。
测试脚本要「先退出、再进入」（`input keyevent 4` 后再 `am start`），否则服务收不到事件、测出来是假阴性。

### 4. 公开截图样本（规则校准）

从网上公开的支付结果页 / 账单详情页截图里人工抄写文案（不引入 OCR），补进 `PaymentPageRules` 的关键词与单测样本，覆盖微信、支付宝至少各 2 个版本时期的文案差异。

### 5. 真机验收口径

小米 14 Pro 上用「历史账单详情页 + 内置模拟页」各走一遍：弹窗出现、金额正确、确认后首页出现交易且余额变化、忽略与超时都不入账。**不要求真实支付一笔。**
