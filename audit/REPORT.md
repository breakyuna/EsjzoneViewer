# EsjzoneViewer 静态代码审计总报告

- 审计日期：2026-10-14
- 范围：`app/src/main` 全部 333 个 Kotlin 文件（约 4.9 万行），纯静态审计，未修改源码、未运行构建。
- 方法：按 8 个模块逐层核对实际调用链（启动→导航→页面模型→仓库/网络/存储），仅记录有代码证据与触发路径的问题。
- 模块明细：`M1-startup.md`、`M2-network-auth.md`、`M3-database-cache.md`、`M4-download-offline.md`、`M5-reader.md`、`M6-ui-navigation.md`、`M7-settings-backup.md`、`M8-misc.md`

## 总体评价

代码质量显著高于同类项目：无 `GlobalScope`；磁盘/网络调用全部显式 `Dispatchers.IO`（抽查 20 余处调用点）；Room 写操作带 `operation_version` 乐观锁；下载文件全部临时文件 + 原子移动 + 删除代际守卫；日志全量正则脱敏（Cookie/Token/邮箱）；会话按账户域隔离并加密存储；WAF/403 不会被误判为会话过期。**未发现崩溃、数据丢失或凭据泄露级别的缺陷。** 以下为发布前值得修复的事项。

## 按优先级排列

| # | 级别 | 问题 | 位置 | 触发条件 | 最小修复 |
| --- | --- | --- | --- | --- | --- |
| 1 | P1 | wenku 连续阅读切换章节时，旧 reader 销毁浏览器会话与新区章加载竞态，章节加载失败需手动重试 | `ui/page/ChapterPageModel.kt:57-60`、`network/external/WenkuWebViewSession.kt:40-50,138,154`、`network/external/WenkuChapterClient.kt:85-90` | 阅读 wenku8 外链章 → 下一章（root Reader key 就地替换）→ 旧 entry 的 `onCleared` 在新区章浏览器抓取进行中递增 generation | 移除 `onCleared` 主动关闭（依赖 IDLE_MS 空闲销毁），或捕获 `WenkuBrowserSessionClosedException` 自动重试一次 |
| 2 | P1 | 安全存储不可用时会话仅存内存，每次启动都需重新登录且无任何提示 | `network/PersistentCookieJar.kt:609-638,467-480` | Keystore 损坏、从其他设备恢复备份后首次启动（`deleteSharedPreferences` 三件套后重建仍失败） | 暴露 `sessionPersistenceAvailable` 状态，MainScreen 横幅提示"会话无法保存" |
| 3 | P2 | 旋屏/进程恢复后"打开小说"Intent 重复入栈 | `MainActivity.kt:117,171-182`、`ui/navigation/AppNavigation.kt:570-577` | 下载完成通知打开小说 → 导航到其他页 → 旋屏 → `onCreate` 再次读出同一 URL 并 push | `handleNovelIntent` 增加 `savedInstanceState == null` 守卫，或消费后 `intent.removeExtra` |
| 4 | P2 | `clearUpdateForFingerprint` 无 scope 过滤，跨账户清除书架"有更新"圆点 | `database/dao/BookshelfDao.kt:144-145`、`database/BookshelfRepository.kt:139-142` | 两账户书架含同一小说且都有更新；A 阅读最新章后 B 的圆点永久消失（同步时 fingerprint 未变不会恢复） | SQL 增加 `scope = :scope` 并传入当前书架 scope |
| 5 | P2 | 登出后 root 授权状态未清空 | `ui/navigation/AppNavigation.kt:552-563,362-383`、`ui/page/SettingsPageModel.kt:177` | 登出 → backStack 清空为 Login → 进程内重建/深链接进入 Main/Reader 时仍持有已失效 Authorization | `replaceAll(LoginScreen)` 时同步清空 `authorizationState` |
| 6 | P2 | 合并请求等待 35s 超过普通请求 callTimeout 30s | `network/EsjzoneClient.kt:256,107` | 同 URL 第二请求加入合并后 owner 接近超时才失败 | 合并等待改为 25s 或与 pageKind 预算联动 |
| 7 | P3 | 旧版 `theme` 设置被删除但未迁移 | `data/settings/SettingsDataStore.kt:143-153` | 从保留旧 Room `theme` 的版本升级 | 迁移时映射 `AppThemeMode` 或在发布说明注明 |
| 8 | P3 | 恢复备份时 Room 与下载目录导入非原子 | `backup/LocalBackup.kt:145-179` | 备份中下载部分损坏 | 先校验下载归档再提交 Room 事务 |
| 9 | P3 | 更新检查主线程读 SharedPreferences | `MainActivity.kt:168`、`update/ReleaseUpdateChecker.kt:47-55` | 每次 Activity 创建（含旋屏） | 整体移入 IO scope |
| 10 | P3 | 阅读器设置 250ms 防抖窗口内杀进程丢失调整 | `ui/page/ChapterPage.kt:325-331`、`data/settings/ReaderSettingsDataStore.kt:76-88` | 拖滑块后 250ms 内被杀 | ON_PAUSE/onDispose 补一次立即保存 |
| 11 | P3 | PageCache 全局 `ioLock` 序列化所有页面读写 | `network/PageCache.kt:140-155,180-208` | 大章节 + 下载/元数据补全并发时慢盘设备相互排队 | 读路径无锁化（原子替换保证最终一致） |
| 12 | P3 | `updateCommonPassword(DetailedNovel)` 为未下载小说创建空目录 | `offline/NovelDownloadStore.kt:388-390` | 对未下载小说设置通用密码 | 改 `create = false` |
| 13 | P3 | 后台下载最终失败无通知 | `offline/NovelDownloadWorker.kt:296` | 网络持续失败重试耗尽 | `Result.failure()` 前发失败通知 |
| 14 | P3 | 启动重试按钮存在竞态窗口 | `MainActivity.kt:159-162`、`StartupCoordinator.kt:25-27` | 失败后快速连点重试 | `retry()` 仅当状态为 Failed 时置 Starting |

## 建议修复顺序

1. **#1、#2**（P1）：直接影响核心阅读流与登录保持，修复成本低。
2. **#3、#4、#5**（P2）：均有明确触发路径与最小改动。
3. **#6**（P2）：一行常量调整。
4. P3 项可并入发布前清理批次；#11 涉及缓存重构，建议单独排期并回归 `testDebugUnitTest`。

## 验证建议

- #1：wenku 章节连续阅读 instrumented 测试（已有 `ReaderContentsInstrumentedTest` 可扩展断言切换章节不出现 Error 态）。
- #4：Bookshelf 双 scope 的 JVM 单测（`markLatestRead` 后断言另一 scope 行 `has_update` 不变）。
- #3：`MainActivityLaunchInstrumentedTest` 增加旋屏后返回栈深度断言。
- #2：`PersistentCookieJarIsolationTest` 已有安全存储用例，可补充"secure 不可用"分支。
- 全部修复后按 AGENTS.md 执行 `./gradlew testDebugUnitTest lintDebug --build-cache`。
