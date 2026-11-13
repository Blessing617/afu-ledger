# 任务

## 1. 金额格式化

- [x] 1.1 `MoneyFormatter` 新增带符号的完整金额方法（符号 + 千分位 + 两位小数，负数不产生 `-\`¥`）— 验证：单测 `1_716_550 → "+17,165.50"`、`-16_345 → "-163.45"`、`0 → "+0.00"`
- [x] 1.2 删除 `CalendarStrip.compactNet`（含 `k` 分支与整数截断）— 验证：`grep compactNet` 无匹配，UI 层不再有金额截断
- [x] 1.3 补充边界单测：九位整数位、负号、`Long.MIN_VALUE` 不崩 — 验证：`./gradlew :app:testDebugUnitTest` 绿（10 条 MoneyFormatterTest 全部执行通过）

## 2. 月历组件

- [x] 2.1 格子金额改用完整金额文本，替换为「按可用宽度实测、取最大可容纳字号」（下限 6sp）— 验证：真机 13 日显示 `+16,977.00`，1 日 `-2,555.00`、6 日 `-163.31`，无 `k`、无省略号
- [x] 2.2 字号取下限仍放不下时（万亿级）允许折成两行、不裁切 — 验证：格式化与折行由单测 + 代码走查覆盖；真机未构造伪造大额数据（不动用户真实账目），常态金额已确认无裁切
- [x] 2.3 格子高度 46dp → 52dp，日期与金额两行不重叠，选中态反色仍可读 — 验证：真机截图（默认态 `afu-detail-1`、选中 13 日 `afu-detail-2`）

## 3. 验证

- [x] 3.1 `./gradlew :app:testDebugUnitTest` 全绿 — 验证：BUILD SUCCESSFUL in 1m 26s
- [x] 3.2 真机核对 11 月：日历每日金额与当天明细合计一致 — 验证：13 日格子 `+16,977.00` 与「今天」合计、下方明细逐笔相加一致；1 / 6 日同样核对通过

## 4. 发版与装机

- [x] 4.1 `versionCode 14` / `versionName 1.3.2` — 验证：`app/build.gradle.kts` diff
- [x] 4.2 `./gradlew :app:assembleRelease` + `apksigner verify` + 确认无 `INTERNET` 权限 — 验证：19 MB APK，V2 签名 `CN=Afu Ledger`，权限列表无 INTERNET
- [x] 4.3 提交并推送（`push-via-api.mjs`，tag `v1.3.2`），发布 Release 并附本次说明 — 验证：https://github.com/Blessing617/afu-ledger/releases/tag/v1.3.2
- [x] 4.4 `adb install -r` 覆盖安装（保留数据），`dumpsys` 确认 versionName=1.3.2 — 验证：设备显示 versionName=1.3.2 / versionCode=14，账目数据仍在
