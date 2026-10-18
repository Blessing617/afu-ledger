# Delta：auto-ledger

## Purpose

在用户用微信或支付宝完成付款后，从支付结果页读取金额并请求确认，确认后自动入账，避免手动记账的遗漏与重复劳动；识别与入账全程离线，不申请网络权限。

## ADDED Requirements

### Requirement: 无障碍服务与授权引导
系统 SHALL 提供一个可在设置中开关的无障碍服务，仅用于读取受支持支付应用的支付结果页；未授权时系统 MUST 明确展示未开启状态，而不是静默失效。

#### Scenario: 总开关关闭时不识别
- GIVEN 自动记账总开关处于关闭状态
- WHEN 用户在微信完成一笔付款并停留在结果页
- THEN 不出现任何确认悬浮窗
- AND 不产生交易

#### Scenario: 首次进入引导授权
- GIVEN 用户从未开启过本应用的无障碍服务
- WHEN 用户进入设置页的「自动记账」区块
- THEN 显示无障碍服务未开启的状态
- AND 提供跳转系统无障碍设置的入口
- AND 提示 HyperOS 上还需允许「受限制的设置」并关闭省电限制

#### Scenario: 授权后恢复识别
- GIVEN 用户已按引导开启无障碍服务
- WHEN 用户再次完成一笔微信付款
- THEN 正常出现确认悬浮窗

### Requirement: 支付结果页识别与金额提取
系统 SHALL 仅当当前页面被判定为受支持应用（微信、支付宝）的支付成功结果页时触发识别，并从页面节点文本提取金额与可选的商户名。

#### Scenario: 识别微信支付金额
- GIVEN 无障碍服务已开启，当前页面为微信「支付成功」结果页
- WHEN 页面文本中出现「¥25.50」
- THEN 解析出金额 2550 分
- AND 触发确认悬浮窗

#### Scenario: 非支付页面不触发
- GIVEN 用户停留在微信聊天列表或扫码页
- WHEN 页面滚动或刷新触发无障碍事件
- THEN 不出现确认悬浮窗

#### Scenario: 金额无法解析时静默跳过
- GIVEN 页面被判为支付结果页，但没有任何可解析的金额文本
- WHEN 识别流程结束
- THEN 不出现确认悬浮窗
- AND 不产生交易
- AND 在开启识别日志时记录一条「金额解析失败」

#### Scenario: 金额候选冲突时放弃
- GIVEN 支付结果页同时出现两个互不相同的金额文本
- WHEN 识别流程执行
- THEN 不出现确认悬浮窗
- AND 在开启识别日志时记录「金额歧义」

#### Scenario: 预约扣款类文案不触发
- GIVEN 页面文本为「将于明日扣款 19.90 元」这类未来时态提示
- WHEN 识别流程执行
- THEN 不出现确认悬浮窗

### Requirement: 确认悬浮窗
系统 SHALL 在识别成功后于当前页面之上展示确认卡片，包含金额、商户名（若有）、账户与分类选择，以及「记一笔」与「忽略」操作；用户确认前 MUST NOT 写入任何交易。

#### Scenario: 确认后入账
- GIVEN 悬浮窗展示金额 25.50、默认账户「现金」、默认分类「餐饮」
- WHEN 用户点击「记一笔」
- THEN 新增一笔 2550 分的支出交易
- AND 该交易的账户为「现金」、分类为「餐饮」、备注为识别到的商户名

#### Scenario: 当场修改分类
- GIVEN 悬浮窗已展示默认分类「餐饮」
- WHEN 用户改为「交通」并点击「记一笔」
- THEN 入账交易的分类为「交通」

#### Scenario: 忽略或超时丢弃
- GIVEN 悬浮窗已展示
- WHEN 用户点击「忽略」，或悬浮窗在 60 秒内未被操作
- THEN 悬浮窗消失
- AND 不产生任何交易

#### Scenario: 无可用账户时引导创建
- GIVEN 系统中没有任何未归档的资产账户
- WHEN 识别成功需要展示悬浮窗
- THEN 提示先创建资产账户，并提供跳转入口
- AND 不产生交易

### Requirement: 去重
系统 SHALL 对同一笔支付只请求一次确认；重复的页面事件或服务重复回调 MUST NOT 产生第二次确认或第二笔交易。

#### Scenario: 页面反复刷新不重复弹出
- GIVEN 用户停留在支付成功页，页面事件被多次触发
- WHEN 同一金额在同一时间窗内被再次识别
- THEN 不出现第二个悬浮窗
- AND 最多只产生一笔交易

#### Scenario: 相同金额的不同商户视为两笔
- GIVEN 60 秒内先后识别到两家不同商户、金额同为 25.50 的两笔支付
- WHEN 两次识别完成
- THEN 各自弹出一次确认，互不去重

### Requirement: 自动记账设置
系统 SHALL 在设置页提供自动记账区块，包含总开关、默认账户、默认分类与识别日志开关，并持久保存。

#### Scenario: 修改默认账户
- GIVEN 用户把默认账户改为「招行卡」
- WHEN 下一次悬浮窗展示
- THEN 账户预选为「招行卡」

#### Scenario: 默认分类失效时回退
- GIVEN 用户设置的默认分类已被删除
- WHEN 悬浮窗展示
- THEN 分类回退为支出分类「其他」

#### Scenario: 识别日志可查
- GIVEN 用户开启了识别日志
- WHEN 识别成功或失败发生
- THEN 设置页可查看最近若干条识别结果与失败原因
- AND 日志仅保存在本机

### Requirement: 不联网与本地隐私
系统 MUST NOT 申请 `INTERNET` 权限；自动记账的识别与入账 MUST 全部在本机完成。

#### Scenario: 合并后的 Manifest 无网络权限
- GIVEN 构建 debug APK
- WHEN 检查合并后的 AndroidManifest.xml
- THEN 不包含 `android.permission.INTERNET`
- AND 不包含 `android.permission.SYSTEM_ALERT_WINDOW`
- AND 不包含 `android.permission.READ_SMS`

#### Scenario: 不保存原始页面文本
- GIVEN 识别日志处于关闭状态
- WHEN 完成一次识别
- THEN 数据库与 DataStore 中不保留该页面的原始文本快照
- AND 仅保留交易本身与去重所需的最小指纹

### Requirement: 小米 14 Pro / Android 16 适配
系统 SHALL 在小米 14 Pro（Android 16 / HyperOS）上端到端可用：受限设置可解除、服务能保持运行、悬浮窗能正常显示。

#### Scenario: 受限设置引导
- GIVEN 用户在 HyperOS 上开启无障碍服务时遇到「受限制的设置」拦截
- WHEN 用户查看自动记账的授权引导
- THEN 引导中给出「应用信息 → 允许受限制的设置」的具体路径

#### Scenario: 真机端到端
- GIVEN 小米 14 Pro 已按引导完成授权
- WHEN 用户在微信、支付宝完成一笔支付并停留在结果页
- THEN 两次都出现确认悬浮窗
- AND 确认后首页出现对应交易，账户余额正确变化
