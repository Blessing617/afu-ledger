# 真机验收记录

设备：Android 16 真机（`（机型信息略）`）／Android 16／1440×3200
构建：`assembleDebug`（0.6.0，debug 签名）
时间：2026-10-19 22:26 – 22:42

## 一、识别准确性

| 样本 | 页面 | OCR 行数 | 结果 |
|---|---|---|---|
| 微信·美团 | 账单详情 | 21 | `hit amount=2390 merchant=美团 tradeNo=4500000576202610184019086744` ✓ |
| 微信·Valve（外币） | 账单详情 | 17 | `hit amount=4200 merchant=Valve` ✓ |
| 支付宝·旧版 | 账单详情 | 节点通道 | 20 条样本单测全部通过（见下） |

修掉的两个真机 bug：

1. **金额被读成「一23.90」**：微信账单页的负号被 OCR 认成汉字「一」。
   修法：`OcrTextSanitizer` 里把「紧跟数字且前面不是数字」的 `一 / — / – / －` 换成 `-`。
   修前 `miss NO_AMOUNT`，修后 `line[6] -23.90` → 命中。
2. **外币页把真实金额自己排掉**：Valve 页排版是 `-42.00 / 6.24美元 / 标价 / 汇率`，
   旧规则按「邻行含价格词就排除」把 `-42.00` 排除了，整页 `NO_AMOUNT`。
   修法：价格标签只在本行与「上一行是标签」时生效；外币单位只对本行生效。

## 二、弹窗时效

```
22:40:58.567  ocr lines=21 in 223ms (capture=52)
22:40:58.571  hit amount=2390 merchant=美团
22:40:58.593  showConfirm accounts=1 categories=10 direction=EXPENSE
22:40:58.734  AutoLedgerOverlay: 可见 attach=true size=1440x1376 首帧=141ms
22:40:58.734  overlay visible in 163ms (compose 141ms)
```

- 截图 + OCR：**223ms**
- 命中 → 卡片可见：**163ms**（要求 ≤350ms）
- 窗口属性：`(0,160)(fillx wrap) gr=BOTTOM CENTER` —— 卡片确实在底部显示，不是 1×1 占位窗口

顺带修掉的一个「卡片闪一下就没」：

微信 WebView 会在卡片出现后 1–3 秒**补发一次 `TYPE_VIEW_CLICKED`**（页面加载收尾），
而旧逻辑「有点击就收起卡片」直接把它关掉了（实测存活 1.9 秒）。
现在卡片弹出后有 **3 秒冷却期**，期间的换页信号被忽略；实测卡片稳定停留。

## 三、功耗：其他应用不再打扰

在桌面连续滑动 6 次（大量系统事件）后统计：

```
AutoLedger 收到的事件条数：0
```

原因：无障碍配置声明了 `packageNames="com.tencent.mm,com.eg.android.AlipayGphone"`，
过滤发生在系统侧，其他应用的事件根本不进本进程。

事件回调也已重写：不再「每个事件 cancel + launch 一个协程」，改成「覆盖待判定参数 +
重置一个复用 Runnable 的延时」，滚动时零对象分配。

## 四、无感化

```
$ adb shell settings get secure accessibility_button_targets
null
```

无障碍按钮目标为空 —— 服务没有请求过无障碍按钮，界面上不会出现那个图标
（已去掉 `flagRequestAccessibilityButton`，并补上 `android:isAccessibilityTool="true"`）。

## 五、单元测试

```
./gradlew :app:testDebugUnitTest   →  BUILD SUCCESSFUL
```

- 全部用例通过（含新增的账单回归测试）
- 回归测试覆盖：25 条支付宝真实账单样本的金额/方向/账单时间、
  2 条负样本（首页与我的页）必须不命中、「账单时间不等于运行时刻」、
  多金额页选中真实金额、退款判为收入、外币页不误排、负号被读成「一」仍能识别、
  只保留上半屏文本仍能识别全部账单

## 六、微信红包收入页（用户现场反馈「没识别」）

用户停在微信红包收款页（`微信红包-来自群友-江浩(DJ)` `+50.00`），实测 `miss NO_AMOUNT`。
OCR 原始输出（真机日志）：

```
line[3]  微信红包-来自群友-江浩(DJ)
line[4]  +50.00
line[5]  当前状态
line[6]  己存入零钱        ← 「已」被 OCR 读成「己」
line[9]  2026年8月30日19:33:55
line[10] 收款时间          ← 值在标签的上面一行
line[11] 交易单号
```

三个叠加成因，全部已修：

1. **`Money.parseYuan` 不认前导 `+`**：`+50.00` 解析直接返回 null。
   → `AmountParser.toMoney` 先去掉 `+`（方向由 `signDirection` 单独判定）。
2. **同一视觉行里挤了两段内容**：`BillFieldParser` 按坐标合并成
   `微信红包-来自群友-江浩(DJ) +50.00`，整行既不是「支出X元」也不是裸金额，匹配不上。
   → `parseTransaction` 在整行之外再按空白切开逐段尝试。
3. **值在标签上面**：`收款时间` 的下一行是「交易单号」，值反而在上一行。
   → `BillFieldParser` 新增 `valueCandidates`（同行右 → 下一行 → 上一行），
   时间解析逐个候选校验到「真的像日期」为止。

回归测试：`微信红包收款页能识别为收入`（用这段真实 OCR 文本），断言
金额 5000 分、方向收入、时间 `2026-08-30T19:33:55`、单号 `1000039901000608306305517330057`。

## 七、真机弹窗复验（多笔账单）

装包后连续验证多张账单，识别与弹窗均正常：

```
hit amount=8800  merchant=重庆哎呦哎呦信息科技有限公司  → showConfirm → 可见 176ms
hit amount=2980  merchant=阿佑鲜烫牛內米线            → 识别正确
hit amount=2390  merchant=美团                      → 可见 163ms
```

## 八、新发现待办：返回键不收起卡片

用户按系统返回键从账单详情回到列表时，卡片不会自动收起（要等 60 秒超时或点「忽略」）。
原因：卡片只认「点击事件」和「前台应用变化」，而系统返回键在 WebView 内翻页时
通常既不产生 `TYPE_VIEW_CLICKED`，包名也仍是微信。

影响：卡片会继续占用识别通道（`OVERLAY_SHOWING` 节流），表现为「这段时间识别不工作」。
已在 tasks 里记为待办。


## 九、尚未验证的

- **点「记一笔」的入账交互**：需要手指点在卡片按钮上，adb 坐标试探没点中（点歪会把卡片收起）。
  入账时间用账单时间这一点由 `RecordDetectedPaymentUseCaseTest` 覆盖（断言
  `occurredAt == draft.occurredAt`，不等于识别时刻）。
- **1 小时真实使用后的电池占比**：需要用户日常使用后看系统电池统计。
  目前只证明了「其他应用事件 0 条」这个最大头已经消除。
- **微信红包（收入）真机弹窗**：截图样本与单测已覆盖 `+128.50` → 收入，真机未逐张点。

---

# 第二轮：A + B 类完成后的验证（2026-10-19 23:0x）

## 十、微信侧 fixture（任务 0.6）

用 `tools/grab_wx_ocr.sh` 在真机上逐条点开微信账单，把 App 自己 OCR 出来的
`line[N]` 日志原样落成测试数据（微信账单页对无障碍完全不可读，只能这样采）。

采到 7 条，覆盖要求的两类收入：

| 编号 | 内容 | 识别结果 |
|---|---|---|
| 01 | 京东 -14.90 | ✓ 支出 1490 分 + 单号 |
| 02 | 零钱充值 184.50（无符号、状态词也无方向） | ✓ **正确放弃**（UNKNOWN_DIRECTION，不猜方向） |
| 03 | 重庆哎呦 -88.00 | ✓ 支出 8800 分 |
| 04 | 途窖公寓 -270.64 | ✓ 支出 27064 分 |
| 05 | 途容公寓 -2530.00 | ✓ 支出 253000 分 |
| 06 | **微信红包 +50.00** | ✓ 收入 5000 分、收款时间、交易单号 |
| 07 | **转账-来自初中同学 +50.00** | ✓ 收入 5000 分、**转账单号**、收款时间 |

## 十一、待确认账单（6b.1–6b.3）

真机验证「识别 → 落库」：

```
hit amount=1490 merchant=京东 tradeNo=4500000382202609069095825283
showConfirm accounts=1 categories=10 direction=EXPENSE

$ adb shell run-as com.afu.ledger cat files/datastore/pending_bills.preferences_pb
com.tencent.mm|4500000382202609069095825283 | 1490 | EXPENSE | 京东 | ... | occurredAtFromPage=1
```

指纹里是**正确的微信包名**，金额/方向/商户/账单时间齐全。

> 过程中发现并修掉一个真机问题：debug 版白名单曾放行自家包名，
> 结果本应用自己的 Compose 界面产生的事件被当成「待识别页面」，
> 截全屏 OCR 并把 `com.afu.ledger|单号` 写进了待确认与去重记录。
> 现在运行时的包名白名单**只认微信与支付宝**（debug 也一样）；
> detector 层的 debug 放行只服务单元测试。

6b.2（弹窗失败退化通知）与 6b.3（首页补确认）的代码已完成，
但**没有在真机上触发过「弹窗失败」这条路径**（系统拦悬浮窗的场景无法用 adb 稳定复现），
属于「实现完成、未真机触发验证」。

## 十二、授权自愈（6b.4 / 6b.5）

用 adb 完整复现了 HyperOS 的行为：

```
# 1. 授予受保护权限
$ adb shell pm grant com.afu.ledger android.permission.WRITE_SECURE_SETTINGS
# 2. 把本服务从启用列表踢掉（模拟系统干的）
$ adb shell settings put secure enabled_accessibility_services "hello.litiaotiao.app/.LttService"
$ adb shell settings get secure enabled_accessibility_services
hello.litiaotiao.app/.LttService
# 3. 打开应用
$ adb shell am start -n com.afu.ledger/.MainActivity
# 4. 启用列表被自动写回，且**保留了李跳跳**
$ adb shell settings get secure enabled_accessibility_services
hello.litiaotiao.app/.LttService:com.afu.ledger/com.afu.ledger.auto.AutoLedgerAccessibilityService
```

没授予权限时不做任何假装修复：设置页给出可复制的
`adb shell pm grant com.afu.ledger android.permission.WRITE_SECURE_SETTINGS`。

6b.6（总开关关闭时不自愈）由代码保证：`if (!settings.enabled) return@launch`，
未在真机上逐次点验。

## 十三、单元测试

```
./gradlew :app:testDebugUnitTest   →  BUILD SUCCESSFUL
```

**144 个用例全绿**，其中 `BillRecognitionRegressionTest` 覆盖：
25 条支付宝真实样本 + 7 条微信真实 OCR 样本 + 2 条负样本 +
外币页 / 负号被读成「一」/ 红包页 / 上半屏快筛覆盖率 / 账单时间不等于运行时刻。
