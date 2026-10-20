# bill-sample-corpus

## Purpose

保存从真机采集的**真实账单页面样本**，并把它变成可自动执行的回归测试，
使识别规则的每次改动都能被「这 40 张真账单还认得出来吗」这一问直接检验，
而不是靠人肉在手机上逐页试。

## Requirements

### Requirement: 真实账单样本集
项目 SHALL 在 `test/bill-samples/` 保存从真机采集的账单页面样本，分为 `alipay/` 与 `wechat/` 两组，每组不少于 20 张。

每组 MUST 覆盖下列账单类型（能采到即采，采不到的在清单里标注缺口）：

- 普通支出
- 收入 / 入账（红包、转账收款）
- 退款
- 带优惠或原价的订单（如扫经营码付款）

支付宝样本 MUST 同时保存无障碍树（`dumps/*.xml`），微信样本 MUST 保存截图，
因为微信账单页对无障碍节点不可读，只能靠截图。

样本目录 MUST 附带一份 `README.md`，说明采集设备、系统版本、采集时间、每张样本的账单类型与已知缺口。

#### Scenario: 样本可追溯
- GIVEN 开发者拿到某个样本文件
- WHEN 查看样本目录的 README 与同名清单
- THEN 能知道它来自哪个应用、哪类账单、采集时设备的型号与系统版本

#### Scenario: 缺口被记录而不是被忽略
- GIVEN 支付宝侧采不到「转账」类账单
- WHEN 查看清单
- THEN 该类型被明确标注为缺口
- AND 不会因为缺少该类型而让人误以为已覆盖

### Requirement: 样本到测试 fixture 的导出
项目 SHALL 提供把样本导出成测试 fixture 的脚本，输出到 `app/src/test/resources/bills/`。

fixture MUST 是纯文本行列表 + 期望字段，MUST NOT 依赖图片：

- 支付宝：从无障碍树按**屏幕坐标顺序**导出文本行（与运行时 `NodeTextCollector` 的排序一致）
- 微信：由截图 OCR 得到的文本行导出；导出时 MUST 记录来源为「OCR」，以区别于节点文本

每条 fixture MUST 包含期望值：金额（分）、方向、账单时间、商户名（可空）、交易单号（可空）。

#### Scenario: 支付宝样本导出为文本行
- GIVEN 支付宝样本同时有 XML 与截图
- WHEN 运行导出脚本
- THEN fixture 里是按屏幕坐标排序的文本行
- AND 不包含任何图片二进制

#### Scenario: 导出结果可直接喂给判定器
- GIVEN 已导出 fixture
- WHEN 单测读取该 fixture 构造 `PageSnapshot`
- THEN 可以直接调用 `PaymentPageDetector.detect`

### Requirement: 识别回归测试
项目 SHALL 提供基于样本集的单元测试：逐条喂入 fixture，断言识别结果与期望值一致。

测试 MUST 覆盖：

- 对每条账单样本：金额、方向、账单时间三个字段必须与期望值一致
- 对每条样本：`occurredAt` 必须来自页面上的账单时间，MUST NOT 等于运行测试的当前时间
- 对主界面 / 我的页 / 列表页 / 钱包页这几类**负样本**：必须判为未命中
- 微信红包「+128.50」这类加号金额必须解析成功

改动识别规则后，该测试 MUST 全部通过才能视为完成。

#### Scenario: 加号金额不再漏记
- GIVEN fixture 里有一条微信红包收入样本，金额文本为 `+128.50`
- WHEN 跑识别回归测试
- THEN 解析出 12850 分、方向为收入
- AND 测试通过

#### Scenario: 日期不再取运行时刻
- GIVEN fixture 的账单时间为 `2026-10-18 20:10:02`
- WHEN 跑识别回归测试
- THEN 结果里的账单时间是 2026-10-18 20:10:02
- AND 测试会在「结果等于当前时间」时失败

#### Scenario: 负样本不误触发
- GIVEN fixture 是支付宝首页（含「最近消息 某餐饮店 付款成功 ¥25.00」）的文本行
- WHEN 跑识别回归测试
- THEN 判定为未命中
- AND 不产生任何交易草稿

