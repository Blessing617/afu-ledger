# 设计：HyperOS 上的自动记账常驻

## 一、根因分析（先证伪，再实施）

### 现象三要素

1. 进程还活着（`dumpsys` 能看到）
2. 无障碍服务**仍显示已绑定**（不是被解绑）
3. 但事件不再送达；**重新开关一次服务即恢复**

### 最可能的机制

这是「**进程被冻结**」的典型特征，而不是「服务被停用」：

- Android 会把进入 cached 状态的进程放进 **cgroup freezer**（`SIGSTOP`），进程不参与 CPU 调度，但进程表项与 system_server 侧的绑定记录都还在。
- HyperOS 在此之上还有更激进的应用冻结策略（「应用冻结」「深度睡眠」），触发阈值比 AOSP 更低。
- `AccessibilityService` 由系统绑定，**bind 关系在 system_server 侧**；进程被 SIGSTOP 后，事件仍会派发到该进程的 binder，但应用侧代码无法执行，表现为「绑定在、回调不来」。
- 重新开关服务 → 系统重新 bind 并**解冻进程** → 立即恢复。

> 该假设必须在实施前用真机观测确认（见 tasks 第 1 组）：
> `dumpsys activity processes | grep afu.ledger` 观察 `frozen=` 字段，
> 以及服务内维护的「最近事件时间戳」与「心跳时间戳」是否对不上。

### 需要排除的其他可能

| 可能原因 | 排除方法 |
|---|---|
| 无障碍授权被系统撤销 | `settings get secure enabled_accessibility_services` 查看是否仍含本服务 |
| 服务主动崩溃被系统重启 | `logcat` 搜索 `ActivityManager.*has died` / `ANR` |
| 事件被节流逻辑自己吞掉 | 识别日志里应能看到「event pkg=」的 debug 记录 |
| 用户操作路径不在受支持应用 | 复现时确认前台包名 |

## 一之二、实测更正：根因不是冻结

上面的假设在实施阶段被**证伪**。实测数据：

| 测试 | 结果 |
|---|---|
| 正常使用 90 秒不操作 | 服务一直在，**没有**自行失效 |
| `adb shell am force-stop com.afu.ledger` | 服务**立即**从启用列表消失 |
| 最近任务「锁定」后手动划掉 | **仍然失效** |
| 电池优化白名单 | 拦不住 force-stop |
| 把 targetSdk 降到 33（对齐李跳跳） | 同样失效，**targetSdk 不是原因** |
| 对李跳跳执行 force-stop | 它**同样**从列表消失，说明它也没有保活黑科技 |

**两层真实原因：**

**（1）平台行为，应用层无法阻止**
`AccessibilityManagerService.onPackagesForceStoppedLocked` 在 force-stop 时会把该包
从 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 移除。定制 ROM 把「最近任务划掉」
等同于 force-stop。所以「划掉后失效」只能通过「别划掉」或 Shizuku 写回来规避。

**（2）本应用自己的 bug ——「开关开着却显示未开启」的真正原因**
`AutoLedgerAccessibilityService.onServiceConnected()` 同步调用了
`PaymentConfirmOverlay.warmUp()`。后者会创建 `ComposeView`、初始化组合环境并
`addView` 一个窗口。这段重活阻塞了服务连接，系统侧打出：

```
W AccessibilityManagerService:  wait for adding window timeout: ~20000
```

20 秒后系统放弃绑定。于是开关明明开着，设置页却显示未开启，自动记账完全不工作。
这个现象与被 force-stop 高度相似，是前几轮一直找错方向的原因。

**修复**：把 `warmUp()` 挪到 `Handler(Looper.getMainLooper()).post { ... }`，
让 `onServiceConnected` 立刻返回。实测修复后服务稳定保持 60 秒以上，
`enabled=true` 时事件正常送达。

## 二、方案对比

| 方案 | 作用机制 | 预期效果 | 代价 | 结论 |
|---|---|---|---|---|
| **前台服务 + 常驻通知** | `startForeground` 把进程 adj 抬到约 200，脱离 cached 区间 | 显著降低被冻结概率；**不能 100% 免疫** HyperOS 的额外策略 | 常驻通知；`POST_NOTIFICATIONS`；前台服务类型声明；耗电极小 | **采用（主力）** |
| **AlarmManager 心跳** | `setExactAndAllowWhileIdle` 定期唤醒自身做自查 | 冻结时心跳同样不会执行 → **不能自愈**，但可在解冻后立即发现断流并记录 | 需 `SCHEDULE_EXACT_ALARM`；各家 ROM 对精确闹钟也有限制 | **采用（仅用于「发现」）** |
| JobScheduler / WorkManager | 系统批处理调度，最小周期 15 分钟 | 冻结时不执行；延迟不可控；不能解决根因 | 低 | **不采用** |
| 常驻 Partial WakeLock | 阻止 CPU 休眠 | 有效但持续耗电 | 高（续航明显下降） | 不采用 |
| Shizuku / ADB 提权写 secure settings 自动重开 | 绕过用户手动开关 | 有效 | 需额外安装 Shizuku 并授权，超出本应用定位 | 不采用 |
| 双进程 / 互相拉活 | 已被 Android 8+ 封堵 | — | — | 不采用 |

**结论：没有任何纯应用层手段能保证在 HyperOS 上永不被冻结。**
因此目标定为两层：**降低发生概率（前台服务）** + **发生时让用户看得见（心跳 + 状态展示 + 一键跳转）**。

## 三、实施设计

### 3.1 前台服务

```
AutoLedgerAccessibilityService (已存在，系统绑定)
  └─ onServiceConnected:
       ├─ startForeground(NOTIFICATION_ID, buildNotification(), type = SPECIAL_USE)
       ├─ 建立 NotificationChannel（IMPORTANCE_LOW，无声、无横幅）
       └─ 记录 foregroundStartedAt

  └─ onUnbind / onDestroy:
       └─ stopForeground(STOP_FOREGROUND_REMOVE)
```

Manifest 需要的声明：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service android:name=".auto.AutoLedgerAccessibilityService"
         android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="Keeps the on-device payment recognition accessibility service responsive" />
</service>
```

> 注意：无障碍服务本身也是 `Service`，可以在其内部 `startForeground`。
> `specialUse` 是 Android 14+ 为「无法归入既有类型」的合法用途准备的类型，需要在 manifest 用 `<property>` 说明用途。

### 3.2 事件心跳

- 服务内维护 `lastEventAt: Long`（任何受支持包的事件都更新）
- 每次判定/每 N 分钟（`AlarmManager` 或前台服务内协程定时器）检查 `now - lastEventAt`
- 超过 30 分钟 → 置「疑似冻结」状态并写识别日志
- **注意**：进程被冻结时定时器也停，所以该状态会在解冻后的第一次执行时被补上——这正是我们想要的「事后发现」，不做自愈承诺

### 3.3 状态展示

设置页「自动记账」区块新增一行：

- 正常：`运行中 · 最近事件 2 分钟前`
- 疑似冻结：`疑似被系统冻结 · 最近事件 42 分钟前` + 「去重新授权」按钮（跳 `ACTION_ACCESSIBILITY_SETTINGS`）
- 前台服务未运行：`后台保护未开启` + 说明

状态 MUST 来自真实数据，不允许写死「正常」。

### 3.4 对「本地优先、不联网」的影响评估

| 维度 | 影响 |
|---|---|
| 网络权限 | **零新增**，仍然没有 `INTERNET` |
| 数据出站 | 无。通知内容只有「自动记账运行中 / 最近事件时间」 |
| 用户可见性 | 增加一条常驻通知（低优先级，不发声） |
| 可关闭性 | 用户可在系统里关掉通知权限；关闭后保活能力下降，设置页会提示 |
| 耗电 | 前台服务本身开销极小；未引入 WakeLock |

### 3.5 风险与回退

| 风险 | 对策 |
|---|---|
| 前台服务在 HyperOS 上仍被冻结 | 5–10 分钟真机验收；若不达标，明确告知用户「只能降低概率」，状态展示成为主要价值 |
| `specialUse` 类型被系统或商店质疑 | manifest 写清用途；不上架商店时无影响 |
| 常驻通知打扰 | `IMPORTANCE_LOW` + 无声 + 不可展开 |
| 用户关闭通知导致保活失效 | 设置页显式提示，不静默降级 |
| 预热窗口与前台服务叠加导致内存增长 | 测量 PSS，超过阈值则退回「不预热」 |

## 四、实施记录

已实现（代码位置）：

- `auto/keepalive/AutoLedgerKeepAlive.kt`：通知渠道（IMPORTANCE_LOW）、常驻通知、
  `lastEventAt` 心跳记录、`isSuspectFrozen()` 判定（阈值 30 分钟）
- `AutoLedgerAccessibilityService`：`onServiceConnected` 启动前台服务（API 34+ 用
  `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`）、`onAccessibilityEvent` 刷新心跳、
  `onDestroy` 停止前台服务
- `AndroidManifest.xml`：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、
  `POST_NOTIFICATIONS`；service 声明 `foregroundServiceType="specialUse"` 与用途 `<property>`
- `MainActivity`：Android 13+ 运行时请求通知权限（拒绝不影响功能）
- `AutoLedgerSection`：每 5 秒刷新「后台保护已开启 / 疑似被系统冻结」状态，
  疑似冻结时给出「去重新开启无障碍服务」入口

实测：`keepAlive foreground=true`（startForeground 成功），进程状态在熄屏前为 TOP(2)。

## 五、实施记录（补充）

在原有实现之上新增/修正：

| 文件 | 作用 |
|---|---|
| `AutoLedgerAccessibilityService.kt` | **修复服务连接阻塞**：`warmUp()` 改为 `Handler.post` 异步执行 |
| `auto/keepalive/AccessibilityWatcher.kt` | `AccessibilityManager` 实时监听服务启停；对外暴露 `isServiceEnabled` / `current` / `refresh`；三种健康态 `HEALTHY` / `SUSPECT_FROZEN` / `PERMISSION_REVOKED` |
| `auto/keepalive/KeepAliveStateStore.kt` | 持久化「曾经开过」标记，区分「从没设置」与「被撤销」 |
| `auto/keepalive/BatteryWhitelist.kt` | 电池优化豁免申请 + 小米自启页跳转 |
| `auto/keepalive/ShizukuGuard.kt` | 借 Shizuku shell 权限把服务写回启用列表（只补自己，保留李跳跳等） |
| `auto/keepalive/AccessibilityGuardWorker.kt` | WorkManager 15 分钟周期巡检 |
| `AndroidManifest.xml` | 加 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`ShizukuProvider`、`<queries>`；服务配置加 `flagRequestAccessibilityButton` |
| `AfuApplication.kt` | 冷启动核对并落盘标记；挂 Shizuku binder/permission 监听，连上即自愈 |
| `AutoLedgerSection.kt` | 三态状态行 + 一次性引导（锁定任务 / 后台运行 / 可选 Shizuku）+ 通知权限被关的降级提示 |

### 参考的开源实现

| 项目 | 做法 | 本项目的取舍 |
|---|---|---|
| `cypressincloud/Tally` | Shizuku + `AccessibilityGuardWorker` 15 分钟巡检写回 | **采纳同样的思路**（代码自行实现，组件名/包名换成本项目的） |
| 李跳跳 | 实测无任何保活代码；被 force-stop 后同样失效 | 用于证伪「targetSdk 33 所以不掉」的假设 |
| 小花记账 | ① 关电池优化 ② 自启动 ③ 任务锁定 ④ 建议用 Tasker | 采纳前三条；Tasker 不引入（让用户多装一个 App 不合理） |
| 钱迹 / AutoAccounting | 承认「进程被杀就要重新开」/ 提供 Root、Shizuku 等多种授权 | 与本结论一致 |
