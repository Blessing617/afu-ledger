# Delta：performance

## Purpose

定义阿福记账的交互响应预算与测量口径，确保「点开就有反应」，避免功能增加后体感持续劣化。

## ADDED Requirements

### Requirement: 交互响应预算
系统 SHALL 在小米 14 Pro（Android 16 / HyperOS）上满足下列响应预算，且这些数值 MUST 可被命令行复现测量：

- 冷启动到首帧可见 ≤ 800ms
- 自动记账确认悬浮窗从「识别命中」到「可见」≤ 400ms
- 主界面列表滚动期间，帧耗时超过 32ms 的比例 < 5%

#### Scenario: 悬浮窗在预算内可见
- GIVEN 无障碍服务已连接且完成预热，自动记账总开关开启
- WHEN 识别流程产出一笔有效支付草稿
- THEN 从命中日志时间戳到悬浮窗可见的时间差 ≤ 400ms
- AND 该时间差被写入识别日志供查询

#### Scenario: 冷启动预算
- GIVEN 应用已安装在小米 14 Pro 上，进程未运行
- WHEN 执行 `adb shell am start -W -n com.afu.ledger/.MainActivity`
- THEN `TotalTime` ≤ 800ms

#### Scenario: 滚动不产生可见掉帧
- GIVEN 首页存在至少 50 条交易记录
- WHEN 用户连续快速滚动列表 10 秒
- THEN `dumpsys gfxinfo` 中超过 32ms 的帧占比 < 5%

### Requirement: 悬浮窗预热复用
系统 SHALL 在无障碍服务连接后预先完成悬浮窗所需的 Compose 初始化，使「首次弹出」与「后续弹出」的耗时处于同一量级；MUST NOT 在每次弹出时重新创建组合环境。

#### Scenario: 首次弹出不再有冷启动惩罚
- GIVEN 无障碍服务刚连接完成，尚未弹出过任何悬浮窗
- WHEN 首次识别命中
- THEN 悬浮窗可见耗时与第二次弹出相差不超过 100ms

#### Scenario: 预热失败不影响功能
- GIVEN 预热过程中发生异常（如窗口权限被撤销）
- WHEN 用户完成一笔支付并命中
- THEN 仍然尝试正常弹出悬浮窗
- AND 失败时写入识别日志，不静默吞掉

### Requirement: 首帧主题正确
系统 SHALL 在冷启动的首帧就使用用户已保存的主题色、深色模式与背景图设置，MUST NOT 先渲染默认主题再切换。

#### Scenario: 冷启动不闪默认主题
- GIVEN 用户已把主题色设为「樱花粉」并开启深色模式
- WHEN 冷启动应用
- THEN 首帧即为樱花粉 + 深色
- AND 不出现默认紫色主题的中间帧

#### Scenario: 背景图首帧即就位
- GIVEN 用户已设置自定义背景图
- WHEN 冷启动应用
- THEN 首页首帧就带背景图，而不是先显示纯色再淡入
