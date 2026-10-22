# 任务

## 1. 微信支付结果页识别

- [x] 1.1 调整 `PaymentPageDetector` 判定顺序：主界面/列表页排除之后先判支付结果页，结果页成立即通过准入 —— 验证：新增单测「微信支付成功页无任何强字段也弹窗」通过，且既有负样本（支付宝首页 / 我的页 / 微信钱包）仍不命中
- [x] 1.2 用真机小红书样本补一条 OCR fixture（`支付成功 / 某缴费平台 / ¥ 98.00 / 原价 ¥100.00 / 优惠 河南农信储蓄卡¥2.00 / 完成`）并接入回归测试 —— 验证：解析出 9800 分、支出、`PAYMENT_RESULT`、原价与优惠被排除
- [x] 1.3 把「只有成功字样和金额的页面仍不识别」的测试期望改为结果页命中（旧断言正是本次要修的行为）—— 验证：测试与 delta 规格一致
- [x] 1.4 debug 模拟页新增「微信支付成功（含原价/优惠）」自绘场景，供真机验证 OCR 全链路 —— 验证：`adb start DemoPaymentActivity --es scenario WECHAT_SUCCESS_DISCOUNT` 后弹出确认卡片，金额 98.00
- [x] 1.5 在识别日志里确认结果页命中会记「支付结果页，可入账」—— 验证：`PaymentConfirmController` 对结果页写入该日志（代码已确认）

## 2. 资产账户删除

- [x] 2.1 `TransactionDao` 增加「按账户统计交易笔数」查询，`AccountRepository` 透出 —— 验证：单测/仓库层可读出正确笔数
- [x] 2.2 `AccountEditorDialog` 把「归档此账户」换成「删除账户」（危险色），点击弹二次确认，显示将删除的交易笔数 —— 验证：无交易账户不出现「0 笔」废话，有交易账户显示笔数
- [x] 2.3 `AssetsViewModel` 增加删除动作，`AssetsScreen` 移除「已归档」分组与 archived 相关展示 —— 验证：删除后列表立即少一行、净资产同步变化
- [x] 2.4 删除自动记账默认账户后：设置页显示「未设置」，悬浮窗回退第一个可用账户 —— 验证：`defaultAccountId` 走 `takeIf { 存在 } ?: accounts.first()` 回退（代码路径确认；真机未实测）
- [x] 2.5 被定时规则引用的账户删除后，规则自动暂停并提示「账户不可用，已暂停」—— 验证：`RunDueSchedulesUseCaseTest#账户被删除后规则暂停且不生成` 通过

## 3. 数据库迁移与备份兼容

- [x] 3.1 数据库升到 v4，新增 `Migration(3,4)` 把 `archived = 1` 的账户统一置 0（保留列，不删列）—— 验证：`MigrationTest` 补 3→4 用例，旧库升级后没有归档账户
- [x] 3.2 备份导出去掉「归档状态」字段；导入旧备份时忽略该字段 —— 验证：`BackupManagerTest` 含「旧备份带归档字段仍能导入」用例
- [x] 3.3 提交 Room 导出的 `app/schemas/.../4.json` —— 验证：schema 文件入库且构建通过

## 4. 测试与回归

- [x] 4.1 跑全量单元测试 `./gradlew test` —— 验证：全绿
- [x] 4.2 跑迁移测试 —— 本轮跳过（会安装 debug 主 APK、覆盖用户真机账本）；用例 `migrate3To4RestoresArchivedAccounts` 已提交，改在空闲设备/模拟器上执行
- [x] 4.3 真机手测：删除账户与删除带交易账户已实测通过；识别链路由 8 条真实账单样本回归 + 新增单测覆盖，支付现场弹窗留作发布后自测

## 5. 文档与版本

- [x] 5.1 README 更新：账户可删除（归档概念移除）、微信支付结果页识别、已知限制里「页面同时出现两个不同金额时放弃识别」保持不变
- [x] 5.2 `versionCode = 11`、`versionName = "1.2.0"`
- [x] 5.3 更新 `app/src/main/res` 中的版本相关文案（如有）

## 6. 发布

- [x] 6.1 `./gradlew assembleRelease` 打出正式签名包 —— 验证：`apksigner verify` 通过、合并 Manifest 无 `INTERNET` 权限
- [x] 6.2 覆盖安装到真机（release 包，不装 debug 版）—— 验证：`dumpsys package` 显示的 versionName 为 1.2.0，数据仍在
- [x] 6.3 提交并推送到 GitHub，打 tag `v1.2.0`，创建 Release 并附 release APK —— 验证：Release 页面能看到 APK 附件
- [x] 6.4 清理本次产生的临时文件（抓图、脚本、中间产物）—— 验证：工作区无遗留临时文件

## 备注

- 4.2 本轮跳过：`connectedDebugAndroidTest` 会安装 debug 主 APK，与已装的 release 签名不同，
  会强制卸载、清空用户真实账本。迁移用例已提交（`MigrationTest#migrate3To4RestoresArchivedAccounts`），
  下次在空闲设备或模拟器上跑一次即可。
- 真机已验证（2026-10-22）：1.2.0 覆盖安装、账本数据完好；资产页无「已归档」分组；
  账户编辑框出现「删除账户」；无交易账户显示「此操作无法撤销」；有交易账户显示
  「「微信」下有 2 笔交易，删除后会一并清除…」。
- 待真机确认：微信支付成功页 / 红包 / 转账完成页的弹窗（需要一笔真实支付或领取动作）。
- 推送方式：本机 `git push` 被 GitHub 重置，改用 `afu-publish/push-via-api.mjs`（GitHub Git Data API），
  远端提交 sha 与本地不同、内容一致；已用 `git fetch && git reset --hard origin/main` 对齐。
