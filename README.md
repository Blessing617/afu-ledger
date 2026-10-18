# 阿福记账 (Afu Ledger)

一个自己用的 Android 记账 App，**本地优先、无网络权限、圆润可换肤**，同时作为个人 Android 工程能力作品集开源。

<p>
  <img src="docs/screenshots/home.png" width="30%" />
  <img src="docs/screenshots/record.png" width="30%" />
  <img src="docs/screenshots/settings.png" width="30%" />
</p>

## 特性

- **手动记账**：支出 / 收入、分类、账户、备注、日期选择，键盘「完成」一键保存
- **自动记账**：无障碍识别微信 / 支付宝支付结果页，悬浮窗确认后入账（不联网、可忽略、可超时丢弃）
- **资产账户**：现金 / 银行卡 / 信用卡，余额 = 初始余额 + 收入 − 支出（实时计算，不冗余存储）
- **按日分组**：首页按天倒序展示，显示当日净额与月度汇总
- **圆润视觉**：卡片 20dp / 输入框 16dp / 胶囊按钮，统一 token 管理
- **主题换肤**：8 套主题色，选择后即时生效并持久化
- **深色模式**：跟随系统 / 浅色 / 深色三态
- **背景图**：从相册选择自定义背景，带可读性遮罩
- **分类管理**：内置 14 个预置分类，支持新增 / 重命名 / 删除（被引用的分类禁止删除）
- **完全离线**：不申请 `INTERNET` 权限，所有数据存本地 Room 数据库

## 技术栈

| 维度 | 选择 |
|---|---|
| 语言 | Kotlin 2.3（AGP 9 内置 Kotlin 支持） |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM：Screen → ViewModel → Repository → Room DAO |
| 数据库 | Room + KSP |
| 配置存储 | DataStore Preferences |
| 图片加载 | Coil 3 |
| 构建 | AGP 9.4.1 / Gradle 9.8.1 / compileSdk 37 / minSdk 26 |

## 架构

```
com.afu.ledger
├── auto/               无障碍自动记账（服务、页面规则、金额解析、去重、悬浮窗）
├── data/
│   ├── local/           Room Entity / DAO / Database
│   ├── repository/      聚合 DAO，向 UI 暴露 Flow
│   ├── prefs/           DataStore（主题色、深色模式、背景图）
│   └── AppContainer.kt  手写依赖容器
├── domain/model/        纯 Kotlin 领域模型（Money、Account、Category、Transaction）
├── ui/
│   ├── theme/           颜色、形状、字体、间距 token
│   ├── component/       通用组件（卡片、胶囊按钮、金额文本、空状态）
│   ├── home/            首页
│   ├── record/          记账表单
│   ├── assets/          资产账户
│   ├── category/        分类管理
│   └── settings/        个性化设置
└── util/                金额与日期格式化
```

约定：一个文件一个主要类型，单文件不超过 300 行，UI 不直接访问 DAO。

## 构建

```bash
# 需要 JDK 17+、Android SDK（platform 37、build-tools 37）
export ANDROID_HOME=/path/to/Android/Sdk
export GRADLE_USER_HOME=/path/to/gradle-home   # 避免污染系统盘

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

运行测试：

```bash
./gradlew test                        # JVM 单元测试（金额解析 / 格式化 / 日期分组）
./gradlew connectedDebugAndroidTest   # 真机测试（Room 余额聚合）
```

## 规格驱动开发

本项目使用 `.spec/` 目录做规格驱动开发：

- `.spec/specs/` — 系统**当前**行为（单一真相源）
- `.spec/changes/` — 进行中的变更
- `.spec/changes/archive/` — 已归档变更（含 proposal / design / tasks / delta）

## 自动记账

在「设置 → 自动记账」开启后，无障碍服务会读取微信 / 支付宝的**支付成功结果页**，识别金额与商户名，弹出确认卡片；点「记一笔」才入账，点「忽略」或 60 秒不操作则丢弃。

**只需要无障碍授权**，不申请 `INTERNET`、`SYSTEM_ALERT_WINDOW`、`NOTIFICATION` 或短信权限，识别与入账全在本机完成。

边界要说清楚：

- 只处理**支出**；银行工资到账、红包到账、退款、先用后付都不在范围内
- 只适配微信与支付宝的支付结果页；美团、淘宝等只有支付最终跳到微信/支付宝收银台时才会顺带覆盖
- 页面同时出现两个不同金额时**直接放弃**，宁可漏记也不记错
- 微信部分页面是自绘的，读不到文本节点时不会弹窗（后续可能用 OCR 兜底）

### 不花钱验证

不要求真实付款。debug 构建内置了模拟页面：

```bash
adb shell am start -n com.afu.ledger/com.afu.ledger.debug.DemoPaymentActivity \
  --es scenario WECHAT_SUCCESS   # 还有 ALIPAY_BILL / AMBIGUOUS / FUTURE / REFUND / NON_PAYMENT
```

也可以用微信/支付宝的**历史账单详情页**（`我 → 服务 → 钱包 → 账单`）验证真实页面的可读性——这个页面规则只在 debug 构建启用，发布版不会因为翻旧账弹窗。

小米 / Redmi 上如果被提示「受限制的设置」，要到应用信息右上角菜单里允许，并打开自启动、把省电策略设为「无限制」。

## 路线图

- [x] v0.1.0：手动记账 + 资产 + 换肤
- [x] v0.2.0：无障碍自动记账（微信 / 支付宝支付结果页）
- [ ] 定时 / 周期记账
- [ ] 截图 OCR 记账（补微信自绘页面）
- [ ] 通知监听通道（银行到账、红包、先用后付）
- [ ] 统计图表

## 许可证

[MIT](LICENSE)
