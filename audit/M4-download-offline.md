# M4 离线下载与文件一致性

审计文件：`offline/NovelDownloadWorker.kt`、`offline/NovelDownloadStore.kt`、`offline/NovelExporter.kt`、`offline/DownloadManifestMerge.kt`、`offline/DownloadedImageIndex.kt`、`ui/page/DownloadPage.kt`、`ui/page/NovelPage.kt`（下载动作区）

## 调用链概要

- 入队：`NovelDownloadManager.enqueue`（unique work + KEEP，域名/并发/选集压缩进 inputData 与 tag）→ `NovelDownloadWorker.doWork`（前台通知、入队时域名恢复会话、详情刷新、`NovelDownloadStore.download` 并发下载 + 每 8 章 checkpoint manifest）。
- 写入：所有文件经临时文件 + `Files.move(ATOMIC_MOVE)`；`writeGuard`（deletionGenerations）保证用户删除后进行中的写入抛 `CancellationException`；100MB 最低空闲检查。
- 读者自动保存：`ChapterPageModel.queueChapterPersistence` → 进程级 scope + 信号量 2 → `saveChapter`（图片优先复用 Coil 磁盘缓存，失败回退网络）。
- 删除/导出：`delete`/`deleteAll` 同步取消 WorkManager 任务并提升 generation；导出走 staging 目录 + 逐快照 ZIP。

## 发现

### M4-1（P3）`updateCommonPassword(novel: DetailedNovel)` 会为未下载小说创建目录
- 证据：`offline/NovelDownloadStore.kt:388-390`：`directoryFor(novel.url, create = true)`；而 URL 重载版本（`:411-413`）使用 `create = false`。
- 触发路径：对从未下载的小说调用对象重载（详情页密码对话框路径）→ 落一个空目录 + manifest.json。
- 影响：下载列表按"有章节文件"过滤，空目录不显示；仅残留空目录，无害但会在 storage 分析中可见。
- 最小修复：对象重载改 `create = false`，与 URL 重载一致。

### M4-2（P3）后台下载失败重试不刷新章节目录
- 证据：`NovelDownloadWorker.kt:296`（`Result.retry()`）与 `NovelDownloadStore.kt:826-884`（CHAPTER_MAX_ATTEMPTS=2 后计入 `failedErrors` → 抛异常 → Worker 再重试）。
- 触发路径：站点中途更新目录（新增章节）时，旧 `record.fileName` 仍有效（按 URL 哈希），行为正确；但若小说被站点删除/移动，重试只是重复同一失败，最多 5 次后 `Result.failure()`，无通知。
- 影响：用户看到下载静默失败（无失败通知），需手动进页面重试。
- 最小修复：`Result.failure()` 前发送与密码/验证通知同级的失败通知。

## 结论

下载模块是本项目文件一致性设计最重的部分：写守卫、原子移动、checkpoint 恢复、未发布文件回收（`recoverUnpublishedFiles`）、删除即取消。未发现数据损坏或 P0/P1 路径。M4-1/M4-2 为低影响完善项。
