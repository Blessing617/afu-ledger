# 设计：阿福记账第一版技术方案

## 技术选型

| 维度 | 选择 | 理由 |
|---|---|---|
| 语言 | Kotlin 2.x | 现代 Android 标准，协程/Flow 生态成熟 |
| UI | Jetpack Compose + Material 3 | 圆角/主题/动画声明式实现，换肤成本最低 |
| 构建 | Gradle 9.8.1 + AGP 9.4.1 | 本机已验证组合（JDK 25 需 Gradle ≥ 9.1） |
| 数据库 | Room + KSP | 编译期校验 SQL，Flow 原生支持 |
| 状态 | ViewModel + StateFlow | 不引入 Hilt，第一版用手写工厂，降低复杂度 |
| 配置存储 | DataStore (Preferences) | 主题色、背景图 URI、深色模式 |
| 图片加载 | Coil | 背景图与图标显示 |
| 导航 | Navigation Compose | 单 Activity 多页面 |

## 包名与 SDK

- 包名：`com.afu.ledger`（如需更换请在实施前提出）
- minSdk 26（覆盖 Android 8.0+，无障碍与通知 API 均可用）
- compileSdk 37（Compose 1.12 依赖要求）/ targetSdk 36
- 第一版不申请 `INTERNET` 权限，确保「本地优先」可审计

## 数据模型

```
Account      id, name, type(CASH/BANK/CREDIT), initialBalance,
             color, icon, archived, sortOrder, createdAt

Category     id, name, type(INCOME/EXPENSE), icon, color,
             sortOrder, isPreset

Transaction  id, accountId, categoryId, type(INCOME/EXPENSE),
             amount(以「分」存储的 Long), note, occurredAt,
             createdAt, updatedAt
```

关键决策：

1. **金额用 Long 存「分」**，不用 Double/Float，避免浮点误差。
2. **账户余额不落库**，由 `initialBalance + Σ收入 - Σ支出` 查询计算，避免双写不一致。
3. **交易软删除**：增加 `deletedAt` 字段，为后续「回收站」留余地；
   第一版可先做硬删除，但保留字段。
4. 时间统一存 UTC 毫秒，展示时按本地时区格式化。

## 架构分层

```
com.afu.ledger
├── AfuApplication.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── entity/       AccountEntity.kt, CategoryEntity.kt, TransactionEntity.kt
│   │   ├── dao/          AccountDao.kt, CategoryDao.kt, TransactionDao.kt
│   │   └── AppDatabase.kt
│   ├── repository/       AccountRepository.kt, CategoryRepository.kt, TransactionRepository.kt
│   └── prefs/            ThemePreferences.kt
├── domain/
│   ├── model/            Account.kt, Category.kt, Transaction.kt, Money.kt
│   └── usecase/          按需拆分，不预先造空壳
├── ui/
│   ├── theme/            Color.kt, Shape.kt, Type.kt, AfuTheme.kt
│   ├── component/        AfuCard.kt, AfuButton.kt, AmountText.kt, EmptyState.kt …
│   ├── home/             HomeScreen.kt, HomeViewModel.kt, HomeUiState.kt
│   ├── record/           RecordScreen.kt, RecordViewModel.kt, RecordUiState.kt
│   ├── assets/           AssetsScreen.kt, AssetsViewModel.kt, AssetsUiState.kt
│   ├── category/         CategoryScreen.kt, CategoryViewModel.kt
│   └── settings/         SettingsScreen.kt, SettingsViewModel.kt
└── util/                 日期与金额格式化
```

### 代码组织硬性约定（便于审查）

1. **一个文件一个主要类型**；Screen 文件允许包含其私有子 Composable，但不得再放第二个公开 Screen。
2. **单文件不超过 300 行**，超过即按职责拆分（这是本项目的审查红线）。
3. **UI 层不得直接访问 DAO**，一律 `Screen → ViewModel → Repository → DAO`。
4. **按 feature 分包**，每个包自带 `Screen + ViewModel + UiState`，相邻功能不共用 ViewModel。
5. **颜色、圆角、间距不写魔法数字**，统一在 `ui/theme` 中定义 token。
6. 每个 Repository 只负责一个聚合根，跨表逻辑放在 Repository 或 usecase，不放 DAO。
7. Entity 与 domain model 分离，Repository 负责二者映射，UI 只见 domain model。

## 设计系统要点

- 圆角：卡片 20dp、输入框 16dp、按钮为胶囊（`CircleShape` 或 50% 圆角）
- 主题色：提供 6～8 个预设色板，用 `dynamicColorScheme(seed)` 派生 M3 配色
- 背景图：从相册选择后存 URI 持久权限；主界面叠加 60%～80% 的表面色遮罩保证文字对比度
- 深色模式：跟随系统 + 手动三态（跟随/浅色/深色）

## 取舍记录

| 方案 | 未选原因 |
|---|---|
| 引入 Hilt | 第一版类少，手写工厂更透明；后续模块变多再引入 |
| 余额冗余存字段 | 易与交易表不一致，查询计算对个人使用量级足够快 |
| 采用 Tally 源码 | 上游无 LICENSE，无法合法开源；仅参考其无障碍实现思路 |
| 先做自动记账 | 适配周期长，第一版先保证「能记账」的价值兑现 |
