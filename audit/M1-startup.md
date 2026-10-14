# M1 应用启动与生命周期

审计文件：`MainActivity.kt`、`StartupCoordinator.kt`、`EsjzoneApplication.kt`、`app/AppContainer.kt`、`app/PresentationAccess.kt`、`ui/app/App.kt`、`ui/navigation/AppNavigation.kt`、`ui/screen/LoadingScreen.kt`、`update/ReleaseUpdateChecker.kt`

## 调用链概要

- `EsjzoneApplication.onCreate` → `AppContainer(this)`（Room 惰性构建）+ `autoBackup.startScheduling()`
- `MainActivity.onCreate` → `setContent`（读取 `PresentationAccess.settings.*`）→ `initScope.launch { initializeOnce }` → `StartupCoordinator`（Mutex 幂等）→ `AppContainer.initializeAsync()`（IO：DB 清理 + Client/DownloadStore/HomeDataCache 并行初始化）→ `migrateFromLegacy`
- `StartupState.Ready` → `App()` → `AppNavigation()`（同步 `restoreAuthorization`）→ `LoadingScreen`（IO：Room 旧会话导入 + 导航到 Main/Login）
- `MainScreen` 后台 `checkAuthorization`

## 发现

### M1-1（P2）旋转屏幕后外部"打开小说"Intent 会重复入栈
- 证据：`MainActivity.kt:117,171-182`（onCreate/onNewIntent 均调用 `handleNovelIntent` → `setPendingNovelUrl`）；`AppNavigation.kt:570-577` 消费后置 null。
- 触发路径：从下载完成通知（`NovelDownloadWorker.kt:308,403` 构造带 `EXTRA_NOVEL_URL` 的 Intent）打开小说页 → 用户导航到其他页面 → 旋转屏幕/进程内 Activity 重建 → `onCreate` 再次从 `intent` 读出 URL → `LaunchedEffect(pendingNovelUrl, authorization)` 再次 `pushIfNotCurrent(NovelPage)`，在用户当前页面之上重复压栈。
- 影响：导航栈出现重复小说页，返回行为混乱。
- 最小修复：`handleNovelIntent` 中用 `savedInstanceState == null` 守卫（仅全新启动消费 intent），或消费后 `intent.removeExtra(EXTRA_NOVEL_URL)` 并 `setIntent(intent)`。

### M1-2（P3）重试按钮存在竞态窗口
- 证据：`MainActivity.kt:159-162`：`startupCoordinator.retry()`（置 Starting）与 `initScope.launch { initializeOnce }` 分两步执行；`StartupCoordinator.retry()` 无条件覆写状态。
- 触发路径：初始化进行中（例如首次 `initializeAsync` 慢）用户点不到重试（只有 Failed 才显示），实际窗口很小；但若上一次失败后快速连点两次重试，第二次 `retry()` 会把状态从 Failed→Starting 覆盖第一次重试刚设置的 Ready 之前的中间态，UI 回到转圈但初始化其实已完成，需等 mutex 释放后由后一个调用看到 Ready 才恢复。不会死锁，只会多一次转圈。
- 最小修复：`retry()` 内仅当当前状态为 `Failed` 时才置 `Starting`；或把 retry+launch 合并进 `initializeOnce` 的一个原子入口。

### M1-3（P3）`SettingsStateBoundary` 双重检查锁定读取未标注 @Volatile
- 证据：`PresentationAccess.kt:80,94-124`：`boundRepository` 无 `@Volatile`，外层 `if (boundRepository === repository) return` 在锁外读取。
- 触发路径：理论上另一线程正在绑定新 repository 时读到过期值，重复进入 synchronized 块——内层还有一次检查，最坏结果是重复启动一次 collect（同一 repository 时不会，因为内层检查拦住）。实际影响仅理论级，因为 repository 在进程内唯一。
- 最小修复：`@Volatile private var boundRepository`。

## 结论

启动链路整体设计良好：容器进程级单例、Mutex 幂等初始化、Ready 前不渲染业务 UI、授权检查不阻塞主界面、弱网不清会话。无 P0/P1。
