# Delta：auto-ledger

## ADDED Requirements

### Requirement: 服务可靠常驻与失效可见
系统 SHALL 尽最大可能让无障碍服务在被系统长时间冻结后仍能继续接收事件；当冻结确实发生时，系统 MUST 让用户**能发现**，而不是静默失效。

实现 MUST 满足：

- 自动记账总开关开启且用户已授权时，以**前台服务 + 常驻通知**维持进程优先级；通知 MUST 为低打扰（不发声、不弹横幅）
- 持续记录「最近一次收到受支持应用无障碍事件的时间」，并在设置页展示
- 超过设定阈值无事件时，设置页 MUST 显示「疑似被系统冻结」状态，并提供跳转无障碍设置的一键入口

该机制 MUST NOT 申请 `INTERNET` 或任何网络能力；常驻通知 MUST NOT 承载任何网络内容。

#### Scenario: 日常使用中仍能识别
- GIVEN 自动记账总开关开启、无障碍已授权、前台服务运行中
- WHEN 手机经历日常使用（熄屏、切应用、放置数小时）后再次打开微信账单页
- THEN 打开微信历史账单详情页仍会出现确认悬浮窗
- AND 设置页显示最近事件时间已更新

#### Scenario: 冻结发生时状态可见
- GIVEN 服务因系统冻结已连续 30 分钟未收到任何事件
- WHEN 用户打开设置页的自动记账区块
- THEN 显示「疑似被系统冻结」而不是「运行正常」
- AND 提供跳转无障碍设置的一键入口

#### Scenario: 重新授权后状态自愈
- GIVEN 服务处于疑似冻结状态
- WHEN 用户关闭再开启无障碍服务
- THEN 设置页状态回到正常
- AND 再次触发微信页面可正常弹出

#### Scenario: 常驻通知可关闭且说明后果
- GIVEN 用户关闭了本应用的通知权限
- WHEN 进入设置页的自动记账区块
- THEN 提示「关闭通知会降低后台可靠性」
- AND 自动记账的其他功能不被禁用

#### Scenario: 仍然没有网络能力
- GIVEN 本变更实施完成
- WHEN 检查合并后的 AndroidManifest.xml
- THEN 不包含 `android.permission.INTERNET`
- AND 不包含任何 `ACCESS_NETWORK_STATE`、`CHANGE_NETWORK_STATE` 等网络相关权限
