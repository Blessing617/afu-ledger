# 任务

## 1. 背景图解码与缓存

- [x] 1.1 新增 `backgroundImageRequest()`：按屏幕像素解码 + 固定 `memoryCacheKey`，`BackgroundLayer` 与 `AfuApplication` 预加载共用 — 验证：`grep -n "ImageRequest.Builder" app/src/main` 只剩这一处
- [x] 1.2 淡入时长 200ms → 140ms — 验证：BackgroundLayer 中 `tween(durationMillis = 140)`
- [x] 1.3 新增 `BackgroundThumbnail`：设置背景时生成 96px 缩略图、恢复默认时删除、启动时补齐缺失 — 验证：`files/background_thumb.jpg` 存在且 < 20KB
- [x] 1.4 `BackgroundLayer` 首帧铺缩略图，原图淡入覆盖 — 验证：`am force-stop` 后冷启动第一张截图已有背景

## 2. 复测与记录

- [x] 2.1 release 构建、300 笔数据 + 照片背景、明细页快速滑动 15 次 — 验证：`dumpsys gfxinfo` Janky 0%，50 分位 11ms / 90 分位 15ms / 99 分位 28ms
- [x] 2.2 冷启动 `am start -W` — 验证：TotalTime 205–334ms，≤ 800ms
- [x] 2.3 首帧截图对比（改前纯色底 / 改后缩略图占位） — 验证：`_tmp/cold1.png` vs `_tmp/cold_thumb1.png`
- [x] 2.4 用户真机复测滚动体感（待用户确认具体页面）
