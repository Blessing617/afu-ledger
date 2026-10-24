# 设计：修 bug + 资产转移

## 1. 「记一笔」无响应的根因

`RecordRoute` 用 `viewModel(key = "record-new", ...)` 拿 `RecordViewModel`，owner 是 Activity 的
`ViewModelStore` —— 面板关掉只是 `recordTarget = null` 不再组合，**ViewModel 本身不销毁**。

`RecordViewModel.save()` 成功后写 `_uiState.finished = true`；`RecordScreen` 里
`LaunchedEffect(uiState.finished) { if (finished) requestClose() }` 以初始值 `true` 启动，
于是面板刚组合就立刻播放关闭动画 → 用户看到的就是「点了没反应」。

真机复核：保存一笔后点中央按钮无任何变化；`am force-stop` 后重开，同一按钮正常弹出空白面板。

同一根因还带出两个次生问题：金额/备注/账户留着上一次的值；连点主按钮可能提交两次。

**修法**：`finished` 从「状态」改成「一次性事件」。

- `RecordViewModel` 用 `Channel<Boolean>(BUFFERED)` 或 `MutableSharedFlow(extraBufferCapacity = 1)`
  发「已完成」，UI 侧用 `LaunchedEffect(Unit) { viewModel.events.collect { requestClose() } }`。
  事件消费过一次就没了，重复打开不会再次触发。
- 面板每次打开时 `reset()`：清空金额、备注、错误、分类/账户选择，日期回到今天。
  实现上放在 `RecordRoute` 的 `DisposableEffect(Unit) { onDispose { viewModel.reset() } }`，
  或 `RecordScreen` 首次组合时 `LaunchedEffect(Unit) { onOpen() }`。选前者：关闭即重置，
  下次组合直接就是干净状态，不用多一次状态跳变。
- 提交防重：`save()` 开头判 `if (saving) return`，`saving` 用 `AtomicBoolean` 或
  `_uiState.update { it.copy(saving = true) }`，主按钮同时置灰。

不采用「每次打开用新 key」：不同 key 的 ViewModel 会永久留在 Activity 的 store 里，
开十次面板就是十个实例，内存只涨不落。

## 2. 同一笔被记成多笔

两个叠加问题：

1. **指纹退化到分钟。** `PaymentDraft.fingerprint()` 在没有交易单号时用
   「包名|金额|方向|账单时间到分钟」。微信支付成功页既没有单号也没有账单时间，
   `occurredAt` 回退成识别时刻 —— 每次识别都是新指纹，于是反复弹窗、反复入账。
   修法：加一个 `occurredAtSource` 判断，`FALLBACK` 时指纹只到「日」。
2. **确认按钮没有闸门。** `confirm()` 是 `scope.launch`，成功才 `hide()`，
   suspend 期间可以再点一次，两次 `execute` 就是两笔。修法：`inFlight` 标志 + 按钮禁用。

`ProcessedBillStore` 的 TTL 语义不变（已入账 7 天、弹窗展示过 10 分钟），
变的只是「同一笔在指纹层面能不能被认出来」。

## 3. 遮罩深浅不一致

`BudgetScreen` 的编辑器是 `Dialog`，`ScheduleScreen` 的也是 `Dialog`，但只有后者调了
`window.setDimAmount(0f)`，前者留着系统默认 dim（约 0.6 的黑），再叠自己的 `ModalScrim`（32%）。

修法：把「关系统 dim」抽成一个小组件（如 `DialogScrimPolicy()`），预算、定时记账、
备注浮层都用它，新加弹层时不容易再忘。

## 4. 资产转移的数据模型

**结论：单条记录 + 一个可空外键列。**

```
transactions
  + transferAccountId INTEGER NULL  → 转入账户
  type = 'TRANSFER'
  accountId = 转出账户
  categoryId = 转账用的固定分类（见下）
```

对照开源实现：

| 项目 | 建模 |
|---|---|
| Actual Budget | 转账是一条交易，payee 指向另一个账户，报表不统计 |
| My Expenses | `TransferTransaction`：一条记录带 from/to 两个账户 |
| Firefly III | 一个 journal 下两条 transaction，互相指向（双分录） |

选单条的理由在本项目更明显：编辑与删除天然原子；余额查询只要多一个 `UNION` 分支；
报表和预算本来就是 `type = 'EXPENSE'` 过滤，`TRANSFER` 自动被排除，改动面最小。

双分录（Firefly III 路线）在这里代价更大：列表会出现两行、删除要保证成对、备份要维护配对关系。

### categoryId 怎么办

`transactions.categoryId` 是 NOT NULL 且有外键。转移不需要消费分类，但列不能为空，
所以引入一个**内置隐藏分类**「转账」：

- 迁移时插入一条 `type = 'EXPENSE'`? 不行，分类的 `type` 决定它出现在哪个选单里。
  更干净的做法是给分类表加 `isSystem` 标记，`observeAll` 里过滤掉，
  只在转移记录里引用它。
- 分类列表（记账面板、分类管理、报表占比）MUST 过滤掉系统分类。

### 余额查询

```sql
SELECT a.id, ...,
  a.initialBalanceCents
  + IFNULL(SUM(CASE
      WHEN t.type = 'INCOME' THEN t.amountCents
      WHEN t.type = 'TRANSFER' AND t.transferAccountId = a.id THEN t.amountCents
      WHEN t.type = 'EXPENSE' THEN -t.amountCents
      WHEN t.type = 'TRANSFER' THEN -t.amountCents
      ELSE 0 END), 0) AS balanceCents
FROM accounts a
LEFT JOIN transactions t ON t.accountId = a.id OR t.transferAccountId = a.id
GROUP BY a.id
```

注意 `LEFT JOIN` 条件要带 `transferAccountId`，否则转入方一侧完全看不到这笔。
一笔转移会 join 出两行（对转出方一行、对转入方一行），所以 CASE 里必须以
`a.id` 判断自己站在哪一侧，而不是只看 `t.accountId`。

### 迁移

- v4 → v5：`ALTER TABLE transactions ADD COLUMN transferAccountId INTEGER`、
  分类表加 `isSystem INTEGER NOT NULL DEFAULT 0`、插入系统分类「转账」。
- 用 `AutoMigration` 覆盖加列；插入系统分类用 `Migration`（值修正），两者可同时注册。
- `app/schemas/` 补 v5 JSON，`MigrationTest` 加一条 4→5。

### 报表 / 预算 / 首页

- 报表：`ReportAggregator` 已按 `EXPENSE` 聚合，`TRANSFER` 天然被排除；
  但「单笔排行」和「分类占比」要确认不会把转移列表算进去（用 type 过滤即可）。
- 预算：`sumExpense` 的 SQL 已限定 `type='EXPENSE'`，不用改。
- 首页与明细：`TransactionItem` 增加 `transferToAccountName`，标题渲染成「工资卡 → 消费卡」，
  日结余/月结余的 fold 里跳过 `TRANSFER`。

### 备份

`BackupModels` 的交易 DTO 加 `transferAccountId`、`type` 里允许 `TRANSFER`，
格式版本升到 2；导入时版本 1 没有该字段 → 当普通收支处理。

## 5. 识别日志落盘

`RecognitionLog` 现在是纯内存 `MutableStateFlow`，还要依赖开关才记。改成：
内存里的 `StateFlow`（给界面）+ 一个私有文件 `recognition_log.jsonl`（给排查），
写入走 `Dispatchers.IO` 的独立协程，环形保留 200 条 / 7 天，启动时读回。

不改 DataStore：日志是追加型、量大，Preferences 不是合适的容器。
