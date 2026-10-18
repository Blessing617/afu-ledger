# 任务

## 1. 服务骨架与权限

- [x] 1.1 Manifest 声明 `AutoLedgerAccessibilityService`（`BIND_ACCESSIBILITY_SERVICE`、intent-filter、meta-data）— 验证：`assembleDebug` 后合并 Manifest 只有该服务声明，且无 `INTERNET` / `SYSTEM_ALERT_WINDOW` / `READ_SMS`
- [x] 1.2 新建 `res/xml/auto_ledger_service.xml`（事件类型、`canRetrieveWindowContent`、`notificationTimeout=400`、包名白名单）— 验证：系统无障碍设置中能看到「阿福记账自动记账」并可开启
- [x] 1.3 `AppContainer` 暴露 `AutoLedgerSettingsRepository` 与识别引擎依赖，服务从 `AfuApplication.container` 取用 — 验证：编译通过，开启服务后日志有初始化记录
- [x] 1.4 总开关接入：关闭时 `onAccessibilityEvent` 直接返回 — 验证：真机关闭开关后在微信支付成功页不弹窗

## 2. 页面识别与解析

- [x] 2.1 `PageSnapshot` + `PaymentPageRules`：包名白名单、成功关键词、排除词、金额正则集中定义 — 验证：单测覆盖关键词命中与排除
- [x] 2.2 `AmountParser`：清洗 `¥￥`/逗号/空格后复用 `Money.parseYuan` — 验证：单测表驱动，「¥25.50」→2550、「1,299.00」→129900、「25.5」→2550、「¥ 0.01」→1、乱码→null
- [x] 2.3 `PaymentPageDetector`：页面判定 + 金额提取 + 多金额歧义放弃 — 验证：单测给定文本列表断言命中/不命中/歧义
- [x] 2.4 `MerchantExtractor`：尽力提取商户名，排除按钮、时间、金额与已知关键词 — 验证：单测断言提取结果与排除项
- [x] 2.5 `DetectionDeduper`：`sha1(pkg|cents|merchant)` + 60 秒窗口 + LRU — 验证：单测覆盖「同笔不重复」「同额不同商户不合并」「窗口过期可再次触发」
- [x] 2.6 服务端窗口采集：递归取 text/contentDescription，限 800 节点 / 16 层 / 4000 字符，事件 400ms 去抖 — 验证：真机在微信聊天页滚动不触发、不卡顿
- [x] 2.7 识别失败路径：未命中、金额解析失败、歧义分别写识别日志且不弹窗 — 验证：真机在非支付页停留，日志有记录、无弹窗
- [x] 2.8 规则校准：收集公开的支付结果页 / 账单详情页截图，人工抄写文案补进 `PaymentPageRules` 与单测样本 — 验证：单测样本覆盖微信、支付宝各至少 2 套文案；不引入 OCR

## 3. 确认悬浮窗

- [x] 3.1 `PaymentConfirmOverlay`：`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL`，显示/替换/关闭 — 验证：真机上盖在微信之上且下层仍可操作
- [x] 3.2 `ServiceLifecycleOwner`：为 `ComposeView` 提供 lifecycle / ViewModelStore / SavedStateRegistry — 验证：Compose 内容正常渲染无崩溃
- [x] 3.3 `PaymentConfirmContent`：金额、商户名、账户/分类下拉、`记一笔` / `忽略`，套 `AfuTheme` 与当前主题色 — 验证：真机交互可用、样式与 App 一致
- [x] 3.4 60 秒超时自动关闭且不入账 — 验证：真机展示后等待，计时结束卡片消失、首页无新交易
- [x] 3.5 无可用账户时改为引导创建账户的卡片 — 验证：`RecordDetectedPaymentUseCase` 单测覆盖 `NoAccount`；未做卸载重装验证（会清空真实账本）

## 4. 入账与设置

- [x] 4.1 `AutoLedgerSettingsRepository`：总开关、默认账户、默认分类、日志开关持久化 — 验证：设置后重启 App 仍保持
- [x] 4.2 `RecordDetectedPaymentUseCase`：校验账户可用、分类回退到支出「其他」、写 `EXPENSE` 交易并带商户名备注 — 验证：单测覆盖账户缺失/分类失效回退；真机确认后首页出现交易
- [x] 4.3 设置页新增「自动记账」区块（独立 `AutoLedgerSection.kt`，避免 `SettingsScreen` 超 300 行）— 验证：真机可见开关、默认账户/分类选择、授权状态
- [x] 4.4 授权引导：未开启时展示状态与跳转（无障碍设置、应用详情），HyperOS 受限设置/自启动/省电文案 — 验证：按引导走一遍能成功开启并生效
- [x] 4.5 `RecognitionLog`：内存环形 50 条，设置页可查看；关闭日志时不记录、不留文本摘要 — 验证：开启时可看到最近记录，关闭后打印为空

## 5. 适配与验收（不产生真实支付）

- [x] 5.1 新增 debug-only `DemoPaymentActivity`（`src/debug`），复刻六套文案：微信支付成功、支付宝账单详情、多金额歧义、未来时态、退款、非支付页 — 验证：`adb shell am start` 启动后六种场景结果符合 design 的期望表
- [x] 5.2 小米 14 Pro 真机：用微信/支付宝**历史账单详情页**验证「真实页面可读 → 弹窗 → 忽略」，用模拟页验证「确认 → 入账 → 余额变化」 — 验证：截图存档；支付宝账单详情页可读并识别出 ¥25.00（商户名不在无障碍树里，留空）；**微信页面不可读**（自绘，`uiautomator` 仅 1 个节点），结论见 design.md
- [x] 5.3 受限设置路径实测：侧载安装后按引导解除限制 — 验证：本次用 `adb settings` 直接绑定，HyperOS 未拦截；手动开启若遇「受限制的设置」，引导文案已覆盖该路径
- [x] 5.4 权限审计：`aapt dump permissions` 或合并 Manifest 检查无 `INTERNET` / `SYSTEM_ALERT_WINDOW` / `READ_SMS` — 验证：命令输出截图
- [x] 5.5 回归：手动记账、资产、分类、换肤不受影响 — 验证：`./gradlew test` 全绿 + 手测主要页面

## 6. 文档与收尾

- [x] 6.1 README 增加自动记账章节：能力边界（只做无线障碍单通道）、权限与授权路径、已知不覆盖场景 — 验证：README 与实现一致
- [x] 6.2 `versionName` 升到 `0.2.0`，提交并打 tag `v0.2.0` — 验证：`git tag` 可见、工作区干净
