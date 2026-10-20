# 任务

## 1. 签名与迁移（升级不丢数据）

- [x] 1.1 生成项目专用 keystore 到 `D:\Tools\Android\keys\afu-ledger.jks`（alias `afu-ledger`），不进仓库 — 验证：`keytool -list` 能看到条目，SHA-256 `60:EC:...:2D:C1`
- [x] 1.2 加 `keystore.properties.example`，`.gitignore` 排除 `keystore.properties`；`app/build.gradle.kts` 读取它，缺失时回退 debug 签名并打印警告 — 验证：`assembleRelease` 成功，`apksigner verify` 指纹与 keystore 一致
- [x] 1.3 加 `room-testing` 依赖与 `MigrationTest`（1→2），跑真机测试 — 验证：`am instrument` 通过（MigrationTest 1 项）
- [x] 1.4 在 `AppDatabase` 加注释锁死「不许 fallbackToDestructiveMigration」，确认 v1/v2 schema JSON 已入库 — 验证：`git ls-files app/schemas` 有输出

## 2. 备份导出 / 导入

- [x] 2.1 接入 kotlinx.serialization（插件 + json 依赖）；新增 `data/backup/BackupModels.kt` — 验证：`compileDebugKotlin` 通过
- [x] 2.2 实现 `BackupManager`：分批读表 → 流式写 JSON；导入校验版本 → 单事务替换 — 验证：androidTest 6 项全过
- [x] 2.3 设置页加「数据管理」区块：条数、数据库大小、导出 / 导入 / 清空三项 — 验证：真机导出到下载目录，JSON 可读
- [x] 2.4 清空数据：删全表 + 重插内置分类，二次确认 — 验证：androidTest `clearAllReseedsPresetCategories` 通过
- [x] 2.5 端到端验证：release 包导入 13 笔演示数据成功；覆盖安装同签名 release 后数据与今日支出保持 — 验证：首页显示 `-¥38.50`，数据管理显示 13 笔
- [x] 2.6 单测：往返无损（含 1201 笔跨页）、坏文件回滚、版本过高拒绝、悬空引用拒绝 — 验证：`am instrument` 10 项全过

## 3. 版本与构建产物

- [x] 3.1 `versionCode 8` / `versionName 1.0.0` — 验证：`dumpsys package` 读数正确
- [x] 3.2 `assembleRelease` — 验证：`apksigner verify --print-certs` 指纹与 keystore 一致
- [x] 3.3 权限核对：`aapt2 dump permissions` 无 `INTERNET` — 验证：输出无 INTERNET
- [x] 3.4 把 APK 体积（19,098,659 字节 ≈ 18.2 MB）与冷启动耗时（205–334ms）写进 README

## 4. 仓库开源化

- [x] 4.1 真实账单样本集移出仓库并备份到 `_tmp/bill-samples-local/`（仓库外）；`.gitignore` 增加 `test/bill-samples/` — 验证：`git ls-files test/bill-samples` 为空
- [x] 4.2 用 `git filter-branch` 从历史移除 `test/bill-samples`、`docs/screenshots`、`build`，并把作者/提交者邮箱改成 GitHub noreply — 验证：`git log --format='%ae' | sort -u` 只剩 noreply
- [x] 4.3 `git reflog expire` + `gc --prune=now`，`.git` 瘦身 — 验证：`.git` < 5 MB
- [x] 4.4 全仓扫描个人信息：邮箱、手机号、QQ、真实商户名、设备序列号 — 验证：grep 无命中（`docs/`、README、`test/` 重点看）
- [x] 4.5 LICENSE 换成 PolyForm Noncommercial 1.0.0；README 写明非商用条款 — 验证：GitHub 识别为 `Other`
- [x] 4.6 新增 `THIRD-PARTY-NOTICES.md` — 验证：列全依赖与许可，Smiley Sans OFL 指向仓库内文件

## 5. README 与截图

- [x] 5.1 截图前清空背景图与深色模式，恢复默认主题 — 验证：截图背景为默认渐变
- [x] 5.2 截 8 张图（首页、记账面板、明细、报表、资产、定时、设置、数据管理）缩放到宽 720 — 验证：`docs/screenshots/` 8 个文件，每张 < 300 KB
- [x] 5.3 重写 README：首屏截图、功能清单、架构、构建、备份与升级、许可边界 — 验证：本地渲染无死链
- [x] 5.4 README 删除设备型号等个人信息，路线图按实际完成情况修正 — 验证：grep 无型号 / 邮箱
- [x] 5.5 交付前清空设备数据，恢复「认真开始用」的初始状态 — 验证：首页显示空状态、数据管理 0 笔

## 6. GitHub 发布

- [x] 6.1 用现有凭据创建公开仓库 `Blessing617/afu-ledger` — 验证：API 返回 201
- [x] 6.2 推送 main，确认匿名可访问、README 图片可见 — 验证：无 token 访问 raw README 能拿到
- [x] 6.3 打 tag `v1.0.0` 并创建 Release，附 release APK — 验证：Release 页附件可下载且 `apksigner verify` 通过
- [x] 6.4 Release 说明写清安装、升级、无障碍重新授权与备份提醒 — 验证：逐条核对
