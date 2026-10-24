# 任务

## 1. 记账面板失效

- [x] 1.1 `RecordViewModel` 把 `finished` 改成一次性事件（Channel），并加 `saving` 防重 — 验证：真机保存后事件只触发一次关闭
- [x] 1.2 `RecordViewModel.reset()`：清空金额、备注、分类/账户、错误与日期（**保留账户与分类列表**，见下） — 验证：保存后再开面板金额为 0.00
- [x] 1.3 `RecordRoute` 在面板关闭时调用 `reset()`，`RecordScreen` 改为收集完成事件 — 验证：真机保存一笔后再次点「记一笔」正常打开、金额为空、账户齐全
- [x] 1.4 主按钮在提交中禁用并忽略重复点击 — 验证：真机连点两次只产生一笔（账本从 1 笔变 2 笔，不是 3 笔）

  > 实现过程中发现：`reset()` 若连账户/分类列表一起清空，Room 的 Flow 不会再发射，
  > 第二次打开面板会显示「还没有可用账户」。已改为只清用户输入，并在打开时补一次 `refresh()`。

## 2. 重复入账

- [x] 2.1 指纹退化规则（按天去重）：**本次不做**。用户判断副作用大于收益，指纹仍按「账单时间到分钟」退化 — 验证：`PaymentDraft.fingerprint()` 未改
- [x] 2.2 `PaymentConfirmController.confirm()` 加进行中闸门，连点只入账一次 — 验证：代码闸门 + `hide()` 复位
- [x] 2.3 日志条目补上方向、时间来源、指纹字段 — 验证：`AutoLedgerLogger.logDraft` 调用点已切换

## 3. 弹层遮罩

- [x] 3.1 抽出 `DialogScrimPolicy()`（关闭系统 dim + 可选不自动弹键盘），预算、定时记账、备注浮层统一使用 — 验证：三处都已调用
- [x] 3.2 真机截图对比「新建预算」与「新建定时记账」背后遮罩 — 验证：同一点像素 **RGB(160,165,172) 完全一致**

## 4. 资产转移

- [x] 4.1 数据库 v5：`transactions.transferAccountId` + 内置 `TRANSFER` 分类（迁移里插入） — 验证：真机 1.2.0 → 1.3.0 覆盖安装后账本完整、净资产不变
- [x] 4.2 `TransType.TRANSFER`；`Transaction.transferAccountId` — 验证：编译 + 映射
- [x] 4.3 `AccountDao.observeBalances()` 支持转入侧（JOIN 条件 + CASE 分支） — 验证：真机转账后工资卡 −100、消费卡 +100、净资产不变
- [x] 4.4 记账面板三档「支出 / 收入 / 转账」，转账隐藏分类、显示转出/转入账户、账户相同时禁用保存 — 验证：真机截图 + `canSave` 逻辑
- [x] 4.5 交易行渲染「工资卡 → 消费卡」，明细日结余与月结余跳过转移 — 验证：真机首页显示「工资卡 → 消费卡 · 账户间转移 · ¥100.00」，今日支出不变
- [x] 4.6 报表、预算、首页统计排除转移 — 验证：`ReportAggregatorTest` 新增用例通过
- [x] 4.7 分类列表与分类管理不显示「转账」分类 — 验证：记账面板分类格只有 9 个支出分类
- [x] 4.8 备份 v3：导出/导入带 `transferAccountId`，旧备份仍可导入 — 验证：`BackupManagerTest.transferSurvivesExportImport` 已写（instrumentation，见下）
- [x] 4.9 删除账户时转移一并删除，另一端余额回退 — 验证：`TransferBalanceTest.deletingAccountRemovesIncomingTransfer` 已写
- [x] 4.10 编辑/删除转移后两端余额正确 — 验证：真机删除测试转移后账本恢复原状

  > 4.8 / 4.9 的用例写在 `androidTest`，本次**没有在真机上执行**：debug 包与 release 包签名不同，
  > 跑 instrumentation 必须先卸载正式包，会丢掉用户账本。迁移本身已由真机覆盖安装实际验证。

## 5. 识别日志落盘

- [x] 5.1 `RecognitionLog` 文件持久化（200 条 / 7 天，IO 线程异步写，超限截断重写） — 验证：代码 + 单测 `RecognitionLogTest` 通过
- [x] 5.2 启动时读回日志，设置页能看到并清空 — 验证：构造注入 `context` 后 init 读回；设置页文案已更新
- [x] 5.3 日志条目补上方向、时间来源、指纹 — 验证：`formatEntry` 显示方向/时间回退/指纹后 12 位

## 6. 文档与发布

- [x] 6.1 更新 README（资产转移、去重规则、日志落盘、已知边界、路线图） — 验证：通读
- [x] 6.2 `testDebugUnitTest` 全绿 — 验证：BUILD SUCCESSFUL，含新增的报表转移用例
- [x] 6.3 打 release 包并 `adb install -r` 到真机 — 验证：versionCode 12 / versionName 1.3.0
- [x] 6.4 真机回归：记一笔 → 再记一笔、连点保存、转账、删除转账、预算 vs 定时记账遮罩 — 验证：全程截图
- [x] 6.5 推送 GitHub 并发布 release（附 APK）— 验证：https://github.com/Blessing617/afu-ledger/releases/tag/v1.3.0
