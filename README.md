# 阿福记账 (Afu Ledger)

一个自己用的 Android 记账 App，**本地优先、无网络权限、圆润可换肤**，同时作为个人 Android 工程能力作品集开源。

<p>
  <img src="docs/screenshots/home.png" width="30%" />
  <img src="docs/screenshots/record.png" width="30%" />
  <img src="docs/screenshots/settings.png" width="30%" />
</p>

## 特性

- **手动记账**：支出 / 收入、分类、账户、备注、日期选择，键盘「完成」一键保存
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

## 路线图

- [x] v0.1.0：手动记账 + 资产 + 换肤
- [ ] 定时 / 周期记账
- [ ] 截图 OCR 记账
- [ ] 无障碍自动记账（微信 / 支付宝）
- [ ] 统计图表

## 许可证

[MIT](LICENSE)
