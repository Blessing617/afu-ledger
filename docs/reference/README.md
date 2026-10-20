# 外部参考

## Pokedex Compose（Google 官方 Hero Benchmark 所用基准项目）

- 上游：<https://android.googlesource.com/platform/external/compose-hero-benchmarks/pokedex-compose/>
- AOSP 镜像：<https://github.com/aosp-mirror-neo/platform_external_compose-hero-benchmarks_pokedex-compose>
- 分支：`androidx-main`
- 许可：Apache 2.0（作者 skydoves / Google）
- 取于：2026-10-18

Google 用这个项目做 Jetpack Compose 的滚动性能基准（Hero benchmarks），
所以它的列表写法可以当作「官方认为够快」的参照。

本目录只保留与本项目性能优化直接相关的三个文件，仅供对照阅读，不参与构建：

| 文件 | 看点 |
|---|---|
| `PokedexHome.kt` | `LazyVerticalGrid` + `items(key=...)`、卡片内独立 `remember` 状态、`ReportDrawnWhen` |
| `Pokemon.kt` | 简单扁平 `@Immutable` data class 作为列表模型 |
| `PokedexSharedElement.kt` | 共享元素转场的封装方式 |

重新获取：

```bash
curl -s "https://api.github.com/repos/aosp-mirror-neo/platform_external_compose-hero-benchmarks_pokedex-compose/contents/app/src/main/kotlin/com/skydoves/pokedex/compose/feature/home/PokedexHome.kt?ref=androidx-main"
```

## 相关 skill

性能审计依据：Google 官方 `android/skills` 仓库的 `jetpack-compose-performance`。
本机位置：`D:\Tools\DSHData\.dsh\skills\jetpack-compose-performance\`
上游：<https://github.com/android/skills>
