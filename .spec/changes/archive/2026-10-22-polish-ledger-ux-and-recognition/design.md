# 设计：记账体验打磨与支付页识别恢复

## 1. 已确认的根因

| 现象 | 根因（代码位置） |
|---|---|
| 编辑预算改金额后保存按钮永久灰掉 | `BudgetScreen.kt` 用 `MoneyFormatter.formatYuan()` 预填金额（`3,000.00`），`Money.parseYuan()` 只接受纯数字与小数点，带逗号 → `null` → `amountValid=false`。定时记账编辑器早就用 `.replace(",", "")` 绕过，预算没绕。 |
| `446.22` 只能输 5 个数字 | `appendAmountKey()` 把「整数位 + 小数位」当一个 7 位总数限制。 |
| 记一笔易误关 | 遮罩 `clickable { requestClose() }`，且整块遮罩都能点。 |
| 键盘多余 / 重复 | `KeypadRows` 里有 `00`、`日期`、`备注`；日期与备注同时在金额下方的 `MetaChip` 行出现。 |
| 退格不能连删 | 每个键只是 `clickable`，没有长按重复。 |
| 弹出不够 Q 弹 | 面板进场用 `AfuMotion.sheet()`（damping 0.8，几乎不过冲）。 |
| 支付过程中识别不到 | `PaymentPageDetector.isPaymentResultPage()` 命中即 `Missed`，结果页永远不弹。 |
| 资产显示 `4.7w`/`4.70万` | 账户行、总资产、总负债都用 `formatCompactWithSymbol()`；大额显示问题被当成格式问题处理。 |
| 资产不能排序 | `sortOrder` 字段存在但新建时不赋值、无重排接口、无拖动 UI。 |

## 2. 金额输入与展示

### 2.1 解析（`Money.parseYuan`）

依次做：`trim()` → 去掉首尾空格 → 去掉 `,`（仅允许出现在整数部分，出现位置非法则判非法）→ 去掉前导 `+`
→ 原有符号与小数位校验 → 上限校验。

上限用 `MAX_CENTS = 999_999_999_99L`（999,999,999.99 元）。`integerValue * 100 + fractionValue`
在乘之前先用 `integerPart.length > 9` 或 `> MAX_INTEGER` 拦截，避免 Long 乘法溢出。
溢出必须返回 `null`，不能回绕成负数——那会变成一笔「反向」的账。

### 2.2 位数限制

新增共享常量（放在 `Money` 或 `AmountInput` 中）：

- `MAX_INTEGER_DIGITS = 9`
- `MAX_FRACTION_DIGITS = 2`

`appendAmountKey()` 重写为：按小数点切分后分别判断整数位与小数位；`00` 键删掉后不再需要特殊处理。
记账面板、账户初始余额、预算、定时记账四处复用同一套规则，输入框侧用 `take`/过滤兜底。

### 2.3 展示

- `MoneyFormatter.formatCompact*` **退役**：不再用于任何生产界面（保留函数会被下次改动又捡起来用，
  所以直接删除，测试一并移除或改为断言「不产生缩写」）。
- 资产页账户行、总资产、总负债、净资产统一改用 `formatWithSymbol()`。
- 大额的显示问题**由布局解决**：
  - 净资产按字符长度分档降字号（≤15 字符 34sp、≤20 字符 26sp、更长 20sp），允许折行，
    `maxLines` 放宽到 2，MUST NOT 裁切
  - 总资产 / 总负债 / 账户行金额同样按长度降字号；账户行让名称 `weight(1f)` 先让位，金额不换行裁切
  - 极端长度（万亿级 21 字符）在真机上验证不裁切、不折断
- 展示不使用 `w/k/m`，也不再用中文单位。

### 2.4 资产页隐私遮罩

- 状态存放在现有 DataStore（与主题偏好同一处），键 `assetsAmountHidden`，默认 `false`，
  因此「重启后仍隐藏」天然成立，且不引入数据库迁移。
- 资产页在进入时读取一次，作为 `State<Boolean>` 传给净资产卡、总资产 / 总负债、账户行；
  统一用 `MaskedAmount` 组件：`¥` + 4 个绘制圆点（负数带 `−`），账户行 3 个圆点不带货币符号。
- 圆点由 Compose 绘制（`Box + CircleShape`），直径取字号的 0.26 倍、间距 0.18 倍，
  不依赖 `*`/`•` 字形——字形宽度和基线位置受字体影响，34sp 下星号会明显偏上，看起来像两行。
- 遮罩不带小数点，切换时字符数固定，不跳布局；点按净资产数字 = 切换并写 DataStore。
- 遮罩只作用于资产页；首页、明细、报表照常显示真实金额。
- 负债在遮罩下仍保留负号与 error 色，避免被误读成资产。

## 3. 资产排序

### 3.1 数据

- `AccountDao` 增加 `@Query("UPDATE accounts SET sortOrder = :order WHERE id = :id")`，
  仓库层用 `@Transaction` 一次性写入整段顺序（避免逐条更新时界面抖动）。
- 新建账户：`sortOrder = (当前最大 sortOrder + 1)`，查询用 `SELECT IFNULL(MAX(sortOrder), -1) + 1`。
- 取消归档：同样分配末尾值，保证「回到列表时排在末尾」。
- 备份已经带 `sortOrder`，无需改格式。

### 3.2 交互

不引入第三方 reorderable 库（项目没有该依赖，且账户数量少）。资产页账户列表目前在一张 `AfuCard` 里，
保持卡片容器，内部用 `Column` + `detectDragGesturesAfterLongPress`：

- 长按行 → `draggingId` 置位，行 `graphicsLayer` 抬起（`scale=1.02`、阴影/透明度变化）
- 拖动时按行高换算目标索引，其余行用 `animateFloatAsState` 向上/下让位（只动小元素，满足性能约束）
- 松手 → 调仓库重排，清空拖动状态
- 长按手势本身会消费垂直拖动，避免与 `LazyColumn` 滚动打架；未长按的滑动仍然滚动列表

### 3.3 顺序的其它消费者

记账面板与定时记账的账户条都来自 `observeActive()`，DAO 已按 `sortOrder, id` 排序，
只要新建与重排都写对了值，两处自动跟随，不需要额外改动。

## 4. 预算编辑面板

### 4.1 组件抽取

从 `RecordScreen.kt` 抽出可复用的：

- `AmountKeypad`（数字键 + 退格 + 主按钮；支持长按连删、无 `00`）
- `AmountDisplay`（大字号金额）
- `PillSegmented`（胶囊分段）
- `BottomSheetShell`（遮罩、下滑关闭、❌、返回键、弹簧进场）

抽出的组件放在 `ui/component/`，`RecordScreen` 改为调用它们，保证两处**是同一份代码**而不是复制粘贴——
复制出来的「同款」下一次改动就会漂移。

### 4.2 预算表单

底部面板，内容区可滚；金额区 + 键盘固定在底部（与记账面板同构）。字段顺序按使用频率：

1. 金额（大字号 + 键盘）
2. 名称（单行文本，点开才弹系统键盘）
3. 周期（胶囊分段）+ 月度起始日（步进器）
4. 分类（圆形图标格，多选）
5. 预警阈值（滑块）
6. 启用（开关）

金额解析走 2.1 的规范化实现，预填文本可以直接带千分位。保存按钮唯一（面板底部），标题栏只留关闭。

### 4.3 校验

沿用现有规则（名称非空、金额 > 0、自定义区间、起始日 1–31 收敛）。保存按钮的 `enabled` 由同一份
`valid` 计算；编辑态下预填值必须自然通过校验，不能出现「没改任何东西却灰着」。

## 5. 定时记账界面

### 5.1 编辑器

用 `SectionCard(title, content)` 分四区：记什么 / 多久一次 / 什么时候结束 / 到期怎么做。
备注移进「记什么」。结束条件改为枚举：

```kotlin
enum class EndMode { NEVER, UNTIL_DATE, AFTER_COUNT }
```

UI 三段式；提交时映射回既有 `endDate` / `maxOccurrences` 字段，**领域层不变**，只改呈现。
顶部只留关闭，底部保留唯一主按钮（现有 `AfuButton`），删除顶部保存。

### 5.2 列表卡片

`RuleCard` 重排为：

- 左：分类图标；中上：金额（收支色、titleLarge）；中下：`周期描述 · 下次执行`
- 右：启用开关
- 名称作为金额上方的次要行（或与金额同一行的灰字）
- 行为标签、暂停原因：底部一行小字 / 警示行
- 低频信息（已生成期数、上次执行结果）移入编辑器或折叠区

## 6. 动效

- 新增 token `AfuMotion.sheetBouncy()`：`dampingRatio ≈ 0.68`、`stiffness = StiffnessMedium`，
  过冲约 4%–6%。只用于**从屏幕外进入的面板/整页**——过冲方向是屏幕外，不会露底色。
- 面板内部内容不加位移动画，避免「整块晃」。
- 退出继续用 `EmphasizedAccelerate` + `DURATION_EXIT_MS`，与进入不同曲线。
- 低端机与系统「移除动画」退化路径不变。

## 7. 支付结果页识别

### 7.1 判定

`PaymentPageDetector` 在正向准入之后、金额解析之前，把「是否结果页」从 `Missed` 改为**标记**：

```kotlin
val isResultPage = isPaymentResultPage(fields, pageText, tradeNo)
```

结果页仍需过金额解析与方向判定，产出 `PaymentDraft(pageType = PAYMENT_RESULT)`；
`occurredAt` 取当前时刻并标记 `TimeSource.FALLBACK`。结果页不要求商户名。

### 7.2 去重与合并

现有三层去重全部保留：

1. `ProcessedBillStore`：同指纹（含金额、方向、时间窗）不重复弹
2. `DetectionDeduper`：页面级短时去重
3. `PaymentSessionDeduper`：同包名 + 同金额 + 同方向，120 秒；双单号不同则放行

结果页与账单页的指纹天然不同（一个无单号），所以依赖第 3 层。新增一步在 `PaymentConfirmController`：

- 维护「当前未决草稿」的会话键 → `PendingConfirm`
- 同键再次命中（账单页后到）时，**合并**字段（商户、单号、页面时间优先取有值的一方）并刷新悬浮窗，
  不新开窗口、不写第二条
- 用户确认/忽略后，把会话键标记为已处理（带上短 TTL），后续同键命中直接跳过
- 已入账记录不做回写（避免「账单页晚到 → 改历史账」这种难以解释的行为）

### 7.3 参考

- 同类开源实现 `Meggie6363/autoexpense-releases` 明确以「识别微信 / 支付宝**支付成功页面**」为主路径，
  说明结果页识别是行业常规做法；钱迹的「AI 自动记账」同样强调从账单与支付通知中取数。
- 支付宝 / 微信官方的支付成功页**没有公开的无障碍树或官方样本集**，网络上的截图也不带节点结构。
  因此本变更不依赖外部图片资源，判据来自项目已有的真机日志（`docs/` 与回归测试样本），
  并靠单测覆盖结果页的文本形态。

## 8. 发布

- `versionCode 9`、`versionName 1.1.0`
- `./gradlew testDebugUnitTest assembleRelease`
- 真机覆盖安装（同签名），用第 1 组备份对账
- commit 到 main，打 `v1.1.0` tag，GitHub Release 附 APK 与更新说明

## 9. 风险与规避

| 风险 | 规避 |
|---|---|
| 结果页恢复后重复弹窗 | 三层去重 + 未决草稿合并；单测覆盖同笔两页 |
| 大额完整显示挤压布局 | 字号按长度分档 + 允许折行；真机用万亿级数字验证不裁切 |
| 隐私键与主题偏好互相影响 | 新键独立命名与默认值，读写只走资产页，主题逻辑不动 |
| 拖动排序与列表滚动冲突 | 长按后才接管拖动；账户数量少，不用 Lazy |
| 抽组件导致记账面板回归 | 记账面板作为第一个改造对象，改完立即真机验证，再迁移预算 |
| 真机数据丢失 | 第 1 组先导出并校验备份，再动代码；覆盖安装不卸载 |
