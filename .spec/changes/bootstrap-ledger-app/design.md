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
- targetSdk 36 / compileSdk 36
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
ui/            Compose 屏幕与组件（只读状态、发事件）
viewmodel/     StateFlow<UiState> + 事件处理
data/
  local/       Room Entity / DAO / Database
  repository/  聚合 DAO，暴露 Flow
  prefs/       DataStore 配置
design/        Theme、Shapes、颜色、背景容器
```

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
