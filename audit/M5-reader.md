# M5 阅读器性能与资源

审计文件：`ui/page/ChapterPage.kt`（3158 行）、`ui/page/ChapterPageModel.kt`、`domain/reader/*`、`ui/reader/*`、`ui/page/ReaderToolbarSettings.kt`、`ui/page/ReaderSettingsSheet.kt`

## 调用链概要

- `ChapterPageModel`：session 代际 + `synchronized(lock)` 管理 9 章滑动窗口、预取（prefetchNext/Previous）、失败冷却、验证态（wenku Cloudflare）记忆；分页测量在 `Dispatchers.Default` 用私有 TextMeasurer。
- 位置持久化：`snapshotFlow → debounce(750)` + ON_PAUSE/onDispose 双兜底写入 `LocalReadingHistoryRecorder`（进程级 IO scope）。
- 章节自动保存：进程级 scope + 信号量 2，读者退出不丢已读章节。
- 窗口副作用：`ReaderKeepScreenOn`/`ReaderBrightness` 均按生命周期 apply/restore 并在 onDispose 还原。
- 统计会话：`ReadingStatisticsSession` 跟随 RESUMED/READY 启停，30s checkpoint。

## 发现

### M5-1（P1）wenku 连续阅读切换章节时，旧 reader 销毁浏览器会话与新区章加载存在竞态
- 证据：
  - `ui/page/ChapterPageModel.kt:57-60`：`onCleared()` 调用 `EsjzoneClient.closeWenkuBrowserSession()`。
  - `network/external/WenkuWebViewSession.kt:40-50`：`close()` 递增 `generation` 并销毁 WebView。
  - `network/external/WenkuWebViewSession.kt:138`（navigate）与 `:154`（fetchInPage）：循环内 `token != generation.get()` → 抛 `WenkuBrowserSessionClosedException`。
  - `network/external/WenkuChapterClient.kt:85-90`：该异常被原样抛出；`ChapterPageModel` 的 catch 只识别 Challenge/WebViewUnavailable/密码类，其余按 `State.Error(loadFailureKind())` 处理（`ChapterPageModel.kt:237-246`）。
- 触发路径：阅读 wenku8 外链章节 → 点击"下一章"（`openReader` 就地替换 root Reader key）→ 新 entry 的 `ChapterPageModel` 开始 `getChapterDetail`，若此刻旧 entry 的 ViewModelStore 才被清空（NavDisplay 过渡期间），`closeBrowserSession()` 使进行中的浏览器抓取失效 → 新章节以通用错误态失败，用户必须手动重试。
- 影响：wenku 连续阅读间歇性加载失败（可恢复，但打断阅读流且错误信息无法自解释）。
- 最小修复（二选一）：
  1. 移除 `onCleared` 中的主动关闭，依赖 `WenkuWebViewSession` 自带的 IDLE_MS 空闲销毁；
  2. 或在 `ChapterPageModel` catch 中把 `WenkuBrowserSessionClosedException` 视为可重试错误，自动重试一次再报错。

### M5-2（P3）阅读器设置 250ms 防抖窗口内杀进程会丢失最后一次调整
- 证据：`ui/page/ChapterPage.kt:325-331`（`saveDebounced`）→ `data/settings/ReaderSettingsDataStore.kt:76-88`（`delay(250)` 后保存）。
- 触发路径：拖动字号/亮度滑块后 250ms 内切后台被系统杀死。
- 影响：单次设置调整回退（保存 scope 为进程级，普通退出不受影响）。
- 最小修复：`ChapterPage` 的 ON_PAUSE/onDispose 处对当前 `readerSettings` 补一次 `saveInBackground`。

## 结论

阅读器内存管理（窗口裁剪、代际取消、测量隔离、生命周期还原）是全区块最规范的部分，未发现泄漏或主线程 IO。唯一实际缺陷是 M5-1 的 wenku 会话竞态。
