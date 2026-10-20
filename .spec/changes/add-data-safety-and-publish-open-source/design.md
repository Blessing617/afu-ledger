# 设计：数据记忆化与开源发布

## 1. 升级不丢数据

### 签名

现状：`app/build.gradle.kts` 里 release 直接用 `signingConfigs.debug`，依赖用户目录下的 `~/.android/debug.keystore`。同一台机器上没问题，但换机器、重装系统、Android Studio 重建 debug keystore 都会导致签名变化 —— 那时再装新包，系统会拒绝覆盖安装，用户只能卸载重装，数据全丢。这正是「不知道更新后数据还在不在」的根源。

方案：

- 生成一把项目专用 keystore：`D:\Tools\Android\keys\afu-ledger.jks`（**不进仓库、不进 C 盘**，alias `afu-ledger`，RSA 2048，有效期 10000 天）。
- 仓库里提供 `keystore.properties.example`，真实的 `keystore.properties` 由 `.gitignore` 排除。
- `app/build.gradle.kts` 逻辑：
  - `keystore.properties` 存在 → 用它配置 `release` 签名；
  - 不存在 → 回退 `debug` 签名，并在构建日志打印 `WARN: 未找到 keystore.properties，release 包使用 debug 签名，不可用于升级已安装的正式包`。
- 首次切换签名时必须先导出数据再卸载重装（README 写清楚）；之后的每次升级都是覆盖安装。

> 为什么不用「保留 debug 签名」：debug keystore 的位置和内容不在项目内，无法在 README 里给出可复现的升级路径，也不是「固定签名」。

### 迁移测试

- `app/build.gradle.kts` 加 `testImplementation(libs.androidx.room.testing)`（`room-testing`）。
- 测试放 `app/src/androidTest/.../MigrationTest.kt`（迁移需要真实 SQLite，用 instrumentation 测）。
- 用 `MigrationTestHelper` 读取已入库的 `app/schemas/**/*.json`，覆盖 1→2。
- 约束：没有 `fallbackToDestructiveMigration`（现状已满足，加一条注释锁死）；`exportSchema = true`（现状已满足）。

## 2. 备份导出 / 导入

### 格式

```json
{
  "format": "afu-ledger-backup",
  "formatVersion": 1,
  "exportedAt": "2026-10-20T23:59:00+08:00",
  "appVersionName": "1.0.0",
  "appVersionCode": 8,
  "schemaVersion": 2,
  "counts": { "accounts": 4, "categories": 20, "transactions": 5000, "scheduleRules": 3 },
  "accounts": [ ... ],
  "categories": [ ... ],
  "transactions": [ ... ],
  "scheduleRules": [ ... ]
}
```

- 用 `kotlinx.serialization`（新增 `kotlin-serialization` 插件 + `kotlinx-serialization-json` 依赖），DTO 与 Room Entity 分离，避免数据库列变化直接破坏备份格式。
- 金额统一写整数分值 `amountCents`（`Money.cents`，Long），不用 Double —— 与数据库一致，零精度损失。
- 时间为 epoch millis（Long），另附 ISO 文本方便人读。

### 流式读写

- 导出：`JsonWriter`（kotlinx 的 `ExperimentalSerializationApi` `encodeToStream`）直接写 `OutputStream`；交易表用 Room 的 `PagingSource`/`LIMIT OFFSET` 分批读（每批 500 行），逐批写入 JSON 数组，写完即丢弃。堆内存恒定在几 MB 量级。
- 导入：**不支持流式解析**（JSON 数组必须完整解析），改为「按批读取 + 单事务写入」：文件解析结果分批入队，每 500 条提交一次到同一个事务。5000 笔交易的文件约 1 MB，解析峰值内存 < 20 MB，可接受；导入完成后立即回收。
- 文件位置：`ActivityResultContracts.CreateDocument("application/json")` 导出、`OpenDocument` 导入 —— 不需要存储权限，也不引 `READ/WRITE_EXTERNAL_STORAGE`。

### 导入的原子性

- `db.runInTransaction { clearAll(); insertAll(batch) }`，任一步抛异常整体回滚。
- 校验顺序：magic 字段 → `formatVersion <= 1` → `schemaVersion <= 当前` → 实体必填字段。全部通过才动数据库。

### 清空数据

- `clearAll()`：删交易 → 账户 → 分类 → 规则（外键顺序），然后重插内置分类（否则首页空状态会引导新建分类，但记账面板需要默认分类）。
- 主题、自动记账开关、OCR 设置属于 DataStore，不在清空范围内。

### 设置页入口

- `SettingsScreen` 新增 `SectionCard("数据管理")`：交易条数 / 数据库大小（`db.openHelper.readableDatabase.path` 文件长度），三个动作行：导出备份、导入备份、清空全部数据。
- 动作走 `SettingsViewModel`，状态用 `StateFlow<DataManageUiState>`；导出/导入用 Compose 的 `rememberLauncherForActivityResult`。

## 3. 仓库开源化

### 隐私清理

| 项 | 处理 |
|---|---|
| `test/bill-samples/`（84 张真实账单截图 + uiautomator dump，含真实商户、金额、订单号） | 从**工作区**与**git 历史**中移除；本地另存到仓库外（`D:\Tools\DSH-Workspace\_tmp\bill-samples-local\`） |
| `docs/screenshots/*.png`（旧版界面含真实/历史数据） | 历史中移除，用 release 版假数据截图替换 |
| `build/wx_*.png`、`*.db` 等采集产物（已 gitignore，但历史里有） | 历史中移除 |
| 提交者邮箱 `535104678@qq.com`（含 QQ 号） | 全历史改写为 `Blessing617@users.noreply.github.com` |
| README 里的设备型号 / 序列号 | 改成「中端 Snapdragon 机型，Android 16」这类中性描述 |

历史改写用 `git filter-branch`（49 个提交，够用；`filter-repo` 不在 Windows 自带环境里）：

```bash
git filter-branch --force --index-filter \
  'git rm -r --cached --ignore-unmatch test/bill-samples docs/screenshots build' \
  --prune-empty --tag-name-filter cat -- --all
git filter-branch --force --env-filter \
  'export GIT_AUTHOR_EMAIL="Blessing617@users.noreply.github.com"; export GIT_COMMITTER_EMAIL="Blessing617@users.noreply.github.com"' \
  -- --all
```

然后 `git reflog expire --expire=now --all && git gc --prune=now --aggressive`，`.git` 从 34 MB 降到 1 MB 内。

> 重写只影响本地仓库。仓库尚未推送到 GitHub（`git remote -v` 为空），所以不存在「洗不掉的历史」。

### 许可证

MIT 明确允许商业使用，与用户「不给其他人商用」的要求冲突。改用 **PolyForm Noncommercial 1.0.0**：

- 允许：个人学习、研究、自用、修改、分发（同样须非商用）
- 禁止：任何商业用途（含在公司内部使用、用于经营活动）
- 条款文本官方维护：https://polyformproject.org/licenses/noncommercial/1.0.0/

README 首屏写明「代码不含商用授权，商用请先联系作者」。

### 第三方许可

新增 `THIRD-PARTY-NOTICES.md`：

| 组件 | 许可 |
|---|---|
| AndroidX / Jetpack Compose / Room | Apache 2.0 |
| ML Kit 中文文字识别（bundled） | ML Kit Terms（随 Google Play Services 条款，APK 内闭源） |
| Shizuku API / Provider（rikka） | Apache 2.0 |
| Coil 3 | Apache 2.0 |
| kotlinx.serialization | Apache 2.0 |
| 得意黑 Smiley Sans | SIL OFL 1.1（仓库已附 `licenses/smiley-sans-OFL.txt`） |

## 4. 截图

- 用 **release 包** + 假数据（金额如 12.50 / 88.00，商户「楼下便利店」「公司食堂」）。
- 造数据路径：debug 包造 3 天假数据 → 导出备份 → 卸载 → 装 release → 导入。这一步顺带端到端验证备份功能。
- 截图设备分辨率 1440×3200，缩放到宽 720 后放 `docs/screenshots/`（每张 < 300 KB）。
- 需要 8 张：首页（今日）、记账面板、明细页、报表、资产、定时记账、设置页、大肥鱼主题首页。

## 5. 发布

- `versionCode 8 / versionName 1.0.0`。
- `assembleRelease` → `app/build/outputs/apk/release/app-release.apk`，用 `apksigner verify --print-certs` 核对指纹；用 `aapt2 dump permissions` 核对没有 `INTERNET`。
- GitHub 仓库 `Blessing617/afu-ledger`（public，MIT 换成 PolyForm NC）。
- tag `v1.0.0`，Release 附件放 APK（约 22 MB，低于 GitHub 100 MB 限制）。
- Release 页写清：安装方式、升级不会丢数据、首次安装需要重新授权无障碍。
