# Delta：performance

## MODIFIED Requirements

### Requirement: 交互响应预算
系统 SHALL 在Android 16 真机（Android 16 / HyperOS）上满足下列响应预算，且这些数值 MUST 可被命令行复现测量：

- 冷启动到首帧可见 ≤ 800ms
- 自动记账确认悬浮窗从「识别命中」到「可见」≤ 400ms
- 主界面列表滚动期间，帧耗时超过 32ms 的比例 < 5%
- **设置页上下滑动期间，帧耗时超过 32ms 的比例 < 5%**
- **点击「记一笔」到对话框可用 ≤ 350ms，且全程不被任何底层页面遮挡**

设置页的滑动预算与首页列表同级：设置页包含更多常驻交互控件（色板、开关、滑块、分类与账户选择），它的卡顿更容易被用户感知。

#### Scenario: 悬浮窗在预算内可见
- GIVEN 无障碍服务已连接且完成预热，自动记账总开关开启
- WHEN 识别流程产出一笔有效支付草稿
- THEN 从命中日志时间戳到悬浮窗可见的时间差 ≤ 400ms
- AND 该时间差被写入识别日志供查询

#### Scenario: 冷启动预算
- GIVEN 应用已安装在Android 16 真机 上，进程未运行
- WHEN 执行 `adb shell am start -W -n com.afu.ledger/.MainActivity`
- THEN `TotalTime` ≤ 800ms

#### Scenario: 滚动不产生可见掉帧
- GIVEN 首页存在至少 50 条交易记录
- WHEN 用户连续快速滚动列表 10 秒
- THEN `dumpsys gfxinfo` 中超过 32ms 的帧占比 < 5%

#### Scenario: 设置页上下滑动不卡顿
- GIVEN 用户停留在设置页，自动记账区块可见（含账户与分类选择）
- WHEN 用户连续上下滑动手势 10 秒
- THEN `dumpsys gfxinfo com.afu.ledger` 中超过 32ms 的帧占比 < 5%
- AND 滑块拖动时视觉跟随不滞后

#### Scenario: 记账对话框不被遮挡
- GIVEN 用户在任意底部标签页
- WHEN 用户点击「记一笔」并打开记账表单
- THEN 表单以对话框形式完整绘制在最上层
- AND 底层标签页内容与悬浮按钮只作为变暗的背景出现，不可交互
- AND 对话框在 350ms 内可用

## ADDED Requirements

### Requirement: 常驻标签页的后台开销上限
系统 SHALL 让不可见的底部标签页不产生周期性重组或无效绘制，MUST NOT 因为「页面常驻」而让不可见页面持续刷新。

不可见标签页 MAY 保留组合树以换取切换速度，但 MUST 满足：没有定时器在不可见时继续驱动状态变更、没有无限动画在不可见时持续运行。

#### Scenario: 不可见页面不触发重组
- GIVEN 用户停留在首页标签页
- WHEN 观察 30 秒内的重组与绘制次数
- THEN 设置页与资产页不产生因定时刷新导致的周期性重组
- AND 应用不因此持续重绘

#### Scenario: 回到标签页时立即显示最新状态
- GIVEN 用户在资产页新建了一个账户
- WHEN 用户切回首页
- THEN 首页显示的账户相关数据是最新的
- AND 不出现明显的「先显示旧值再跳变」
