# Delta：auto-ledger

## ADDED Requirements

### Requirement: 服务可靠运行与失效可见
系统 SHALL 尽最大可能让无障碍服务保持可用；当它确实失效时，系统 MUST 让用户**能发现**，而不是静默失效。

实现 MUST 满足：

- **服务连接过程 MUST NOT 被阻塞**：`onServiceConnected` 中 MUST NOT 同步执行创建窗口、
  初始化 UI 组合环境等重活。这类工作 MUST 放到主线程的后续循环执行，
  以避免触发系统的绑定超时（`wait for adding window timeout`）导致绑定被放弃。
- 自动记账总开关开启且用户已授权时，以**前台服务 + 常驻通知**维持进程优先级；
  通知 MUST 为低打扰（不发声、不弹横幅）
- 持续记录「最近一次收到受支持应用无障碍事件的时间」，并在设置页展示
- 超过设定阈值（30 分钟）无事件时，设置页 MUST 显示「疑似停止工作」状态，
  并提供跳转无障碍设置的一键入口
- 系统 MUST 实时监听无障碍授权的启停变化（`AccessibilityManager`），
  授权被撤销时发出可见告警，MUST NOT 等到用户下次进入设置页才发现
- 设置页 MUST 区分「从未授权」与「授权后被撤销」两种状态，后者 SHOULD 说明最可能的原因

系统 SHALL 提供降低被系统强制停止概率的手段：电池优化豁免的申请入口、
自启动引导、以及最近任务锁定引导。

系统 MAY 提供基于 Shizuku 的自动恢复（周期性巡检并把服务写回启用列表），
但该能力 MUST 在未安装 Shizuku 时安全降级，MUST NOT 影响其它功能。

上述机制 MUST NOT 申请 `INTERNET` 或任何网络能力；常驻通知 MUST NOT 承载任何网络内容。

#### Scenario: 服务连接不被自身阻塞
- GIVEN 无障碍服务刚被系统绑定
- WHEN `onServiceConnected` 返回
- THEN 悬浮窗预热等 UI 初始化在后台继续，不阻塞连接过程
- AND 系统不产生「wait for adding window timeout」
- AND 服务在启用列表中稳定保持

#### Scenario: 授权被撤销时立即可见
- GIVEN 应用进程存活，无障碍服务此前已授权
- WHEN 系统撤销了本应用的无障碍权限
- THEN 应用发出告警通知，说明自动记账已停止工作
- AND 通知可一键跳转到系统无障碍设置

#### Scenario: 设置页区分两种「未开启」
- GIVEN 无障碍服务当前不可用
- WHEN 用户打开设置页的自动记账区块
- THEN 若此前从未授权过，显示「去开启」引导
- AND 若此前授权过，显示「被系统关闭了」并说明可能是被从最近任务划掉

#### Scenario: 常驻通知可关闭且说明后果
- GIVEN 用户关闭了本应用的通知权限
- WHEN 进入设置页的自动记账区块
- THEN 提示通知关闭会导致失效时收不到提醒
- AND 自动记账的其他功能不被禁用

#### Scenario: 没有 Shizuku 时安全降级
- GIVEN 设备未安装 Shizuku 或未授予其权限
- WHEN 应用启动并执行保活相关逻辑
- THEN 所有 Shizuku 相关调用被跳过
- AND 自动记账与其它功能不受影响

#### Scenario: 仍然没有网络能力
- GIVEN 本变更实施完成
- WHEN 检查合并后的 AndroidManifest.xml
- THEN 不包含 `android.permission.INTERNET`
- AND 不包含任何 `ACCESS_NETWORK_STATE`、`CHANGE_NETWORK_STATE` 等网络相关权限
