# Delta：performance

## ADDED Requirements

### Requirement: 记账面板的响应与帧预算
系统 SHALL 让记账面板的打开与输入保持「点下去就有反应」，并且不因键盘与分类格增加而掉帧。

预算：

- 点击「记一笔」到面板可交互 ≤ 300ms
- 数字键盘单次点击到金额数字更新 ≤ 1 帧（不排队、不延迟）
- 连续点击键盘 40 次期间，超过 32ms 的帧占比 < 5%
- 打开面板时 MUST NOT 触发首页列表的重新组合

#### Scenario: 面板打开在预算内
- GIVEN 用户在首页，应用已运行
- WHEN 点击中央记账按钮
- THEN 面板在 300ms 内可交互
- AND 首页列表不重新组合

#### Scenario: 连续输入不掉帧
- GIVEN 记账面板已打开
- WHEN 连续快速点击数字键 40 次
- THEN `dumpsys gfxinfo` 中超过 32ms 的帧占比 < 5%
- AND 金额数字与点击同步更新

### Requirement: 主题切换的性能边界
系统 SHALL 在切换主题风格或主题色时，以「一次重组」完成换色换形。

主题切换 MUST：

- 在 200ms 内完成（视觉上完成切换）
- MUST NOT 使用会持续重组整棵树的逐帧颜色动画
- MUST NOT 造成白屏、闪烁或重建页面
- 切换后 MUST NOT 留下持续运行的动画

#### Scenario: 切换主题不卡顿
- GIVEN 用户在外观与主题页
- WHEN 连续切换 4 个主题风格
- THEN 每次切换在 200ms 内完成
- AND 不出现白屏
- AND 超过 32ms 的帧占比 < 5%

#### Scenario: 切换后无残留动画
- GIVEN 用户刚完成一次主题切换
- WHEN 静置 10 秒
- THEN 没有任何动画在持续运行

### Requirement: 装饰层与插画的开销上限
系统 SHALL 让主题装饰与吉祥物插画不成为常驻开销。

要求：

- 装饰由矢量一次性绘制，MUST NOT 每帧重新构建路径对象
- 装饰 MUST NOT 使用无限循环动画
- 页面不可见时 MUST NOT 产生绘制指令
- 装饰图层 MUST NOT 因为滚动而重新组合

#### Scenario: 滚动时装饰不重组
- GIVEN 用户在大肥鱼主题下快速滚动明细页
- WHEN 观察重组与绘制
- THEN 背景装饰不参与重组
- AND 列表滚动帧耗时超 32ms 的占比 < 5%

#### Scenario: 不可见页面不绘制装饰
- GIVEN 用户停留在资产页
- WHEN 观察绘制调用
- THEN 首页与明细页的装饰层不产生绘制指令

## MODIFIED Requirements

### Requirement: 交互响应预算
系统 SHALL 在Android 16 真机（Android 16 / HyperOS）上满足下列响应预算，且这些数值 MUST 可被命令行复现测量：

- 冷启动到首帧可见 ≤ 800ms
- 自动记账确认悬浮窗从「识别命中」到「可见」≤ 400ms
- 主界面列表滚动期间，帧耗时超过 32ms 的比例 < 5%
- **设置页上下滑动期间，帧耗时超过 32ms 的比例 < 5%**
- **标签切换转场期间，帧耗时超过 32ms 的比例 < 5%**
- **明细页上下滑动期间，帧耗时超过 32ms 的比例 < 5%**
- **点击「记一笔」到记账面板可用 ≤ 300ms，且全程不被任何底层页面遮挡**
- **记账面板内连续点击数字键盘 40 次，帧耗时超过 32ms 的比例 < 5%**

设置页的滑动预算与首页列表同级：设置页包含更多常驻交互控件（开关、滑块、分类与账户选择），它的卡顿更容易被用户感知。

明细页包含月历与长列表，两部分的滚动都必须满足同一预算。

**测量必须以 release 构建进行。** debuggable 构建的 ART 运行校验会让帧耗时显著虚高
（实测同一场景 debug p99 65ms vs release p99 12ms），用 debug 数据下结论会得出错误方向。

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

#### Scenario: 明细页滚动不卡顿
- GIVEN 用户停留在明细页，月历与分组列表均可见
- WHEN 用户连续滑动手势 10 秒
- THEN `dumpsys gfxinfo com.afu.ledger` 中超过 32ms 的帧占比 < 5%
- AND 月历月份切换时列表不白屏

#### Scenario: 记账面板不被遮挡且在预算内
- GIVEN 用户在任意底部标签页
- WHEN 用户点击中央记账按钮
- THEN 面板完整绘制在最上层，遮罩下的页面不可交互
- AND 面板在 300ms 内可用

#### Scenario: release 构建下测量
- GIVEN 需要验证上述任一预算
- WHEN 采集 `dumpsys gfxinfo` 或识别日志
- THEN 使用的 APK 为 release 构建（非 debuggable）
- AND 报告里注明构建类型与设备型号
