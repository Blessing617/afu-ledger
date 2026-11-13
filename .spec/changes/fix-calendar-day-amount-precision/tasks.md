# 任务

## 1. 金额格式化

- [x] 1.1 `MoneyFormatter` 新增带符号的完整金额方法（符号 + 千分位 + 两位小数，负数不产生 `-\`¥`）— 验证：单测 `1_716_550 → "+17,165.50"`、`-16_345 → "-163.45"`、`0 → "+0.00"`
- [x] 1.2 删除 `CalendarStrip.compactNet`（含 `k` 分支与整数截断）— 验证：`grep compactNet` 无匹配，且 UI 层不再出现 `/ 100` 之类的金额截断
- [x] 1.3 补充边界单测：最大合法整数位（9 位）、负号、`Long.MIN_VALUE` 不崩 — 验证：`./gradlew :app:testDebugUnitTest` 绿

## 2. 月历组件

- [ ] 2.1 格子金额改用完整金额文本，替换为「按可用宽度测量、取最大可容纳字号」（下限 6sp）— 验证：真机 13 日显示 `+17,165.50`，无 `k`、无省略号
- [ ] 2.2 字号取下限仍放不下时（万亿级）允许折成两行、不裁切 — 验证：临时构造大额数据截图核对，事后删除临时数据
- [ ] 2.3 格子高度 46dp → 52dp，日期与金额两行不重叠，选中态反色仍可读 — 验证：真机截图（默认态 / 选中态）

## 3. 验证

- [x] 3.1 `./gradlew :app:testDebugUnitTest` 全绿 — 验证：BUILD SUCCESSFUL
- [ ] 3.2 真机核对整个 11 月：日历每日金额与当天明细合计一致 — 验证：截图 + 手算 2~3 天

## 4. 发版与装机

- [x] 4.1 `versionCode 14` / `versionName 1.3.2` — 验证：`app/build.gradle.kts` diff
- [ ] 4.2 `./gradlew :app:assembleRelease` + `apksigner verify` + 确认无 `INTERNET` 权限 — 验证：命令输出
- [ ] 4.3 提交并推送（`push-via-api.mjs`，tag `v1.3.2`），发布 Release 并附本次说明 — 验证：release 页面 URL 可访问
- [ ] 4.4 `adb install -r` 覆盖安装（保留数据），`dumpsys` 确认 versionName=1.3.2 — 验证：设备上版本号与账目数据都在