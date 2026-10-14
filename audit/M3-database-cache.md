# M3 数据库与缓存

审计文件：`database/GeneralDatabase.kt`、`database/dao/*`、`database/BookshelfRepository.kt`、`database/BookshelfSyncRules.kt`、`database/LocalReadingHistoryRecorder.kt`、`database/ReadingStatistics*.kt`、`network/PageCache.kt`、`network/ListPageCache.kt`、`network/NovelDetailCache.kt`、`network/features/HomeDataCache.kt`

## 调用链概要

- Room v11，10 个显式迁移 + `fallbackToDestructiveMigrationOnDowngrade`；DAO 全部 suspend/Flow，写操作带 `operation_version` 乐观锁。
- 书架：本地 intent 立即落库（`setFavorite`/`removeBatch`）→ `scheduleSync` 串行同步 → 远端快照完整解析成功后才导入；tombstone 防止本地删除被远端快照复活。
- PageCache：账户域隔离 key（`accountScope|url`）、写令牌（WriteToken/epoch）、压缩落盘、256MB LRU 淘汰；`ListPageCache` 以 revision 共享快照。
- 阅读历史/统计：进程级 IO scope 写入，Compose 侧 debounce 750ms + ON_PAUSE/onDispose 兜底。

## 发现

### M3-1（P2）`clearUpdateForFingerprint` 缺少 scope 过滤，跨账户清除"有更新"标记
- 证据：`database/dao/BookshelfDao.kt:144-145`：`UPDATE bookshelf SET has_update = 0 WHERE latest_fingerprint = :fingerprint`（无 `scope` 条件）；调用方 `database/BookshelfRepository.kt:139-142`（`markLatestRead`，读者打开最新章时触发）。
- 触发路径：同一设备登录过账户 A、B（或 A 在另一镜像的会话），两边书架都有同一小说且 `has_update=1`。用户在 A 下阅读该最新章 → fingerprint 按章节 URL 计算、与账户无关 → B 的同行 `has_update` 同时被清零；下次同步 `changed` 为 false（fingerprint 未变），圆点不会恢复。
- 影响：另一账户永久丢失该小说的更新提示，直到站点发布真正的新章节。
- 最小修复：查询增加 `scope = :scope`，由 `markLatestRead` 传入当前 `scopeFor(authorization)`。

### M3-2（P3）PageCache 全局 `ioLock` 序列化所有页面读写
- 证据：`network/PageCache.kt:140-155`（readEntry 持锁读文件并可能 `setLastModified` 写）、`network/PageCache.kt:180-208`（write 持锁压缩写 + 原子移动 + 淘汰扫描）。
- 触发路径：大章节 HTML（数百 KB 压缩）与下载任务（并发 5~8 章）、书架元数据补全（并发 6 个详情页）同时进行时，全部页面读写排队。IO 有界，不会死锁，但慢盘设备上会相互放大延迟。
- 影响：弱盘设备上页面加载/下载吞吐下降。
- 最小修复：读路径改为无锁读取（依赖文件原子替换的最终一致性），仅写路径持锁；或将 `setLastModified` 移出锁外。

## 结论

数据库层设计严谨：版本化乐观锁防止并发 intent 被网络响应覆盖、tombstone 防复活、迁移链完整、缓存按账户隔离。未发现 P0/P1。主要遗留为 M3-1 的跨账户更新标记污染。
