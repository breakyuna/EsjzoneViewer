# M2 网络与认证

审计文件：`network/EsjzoneClient.kt`、`network/PersistentCookieJar.kt`、`network/AuthorizationCookieJar.kt`、`network/PageCache.kt`、`network/PageResponsePolicy.kt`、`network/features/Login.kt`、`network/features/IsAuthorized.kt`、`network/features/NetworkSafety.kt`、`update/ReleaseUpdateChecker.kt`、`ui/screen/MainScreen.kt`、`ui/screen/LoadingScreen.kt`

## 调用链概要

- 会话存储：`PersistentCookieJar`（EncryptedSharedPreferences + Keystore；无明文回退，旧明文一次性迁移后删除）
- 请求路径：`EsjzoneClient.getPage` → `PageCache`（磁盘 TTL/stale）+ `fetchPageCoalesced`（同 URL 同账户请求合并、6 并发信号量、取消句柄 `pageRequestCancellation`）→ `PageResponsePolicy.validate` 后才落盘
- 登录：`loginWithOutcome` 两段式（getAuthToken → mem_login），独立 `LoginCookieJar` 收集后才 `activateAccountScope` + `rotatePageCacheScope` + `replaceHostCookies`
- 恢复：`restoreAuthorization` → `establishActiveAccount`；`LoadingScreen` 导入 Room 旧会话后删除旧行
- 探活：`checkAuthorization`（8s 总预算、403 重试一次、WAF 不算过期）

## 发现

### M2-1（P2）`checkAuthorizationOnce` 中 OkHttp Response 依赖 `use {}` 关闭，路径正确但日志可能输出敏感 URL
- 证据：`IsAuthorized.kt:98` `response.bodyStringOrEmpty()` → `NetworkSafety.kt:12` `use { ... }`，确实关闭，非缺陷。`IsAuthorized.kt:85` `AppLogger.i("IsAuthorized", "Checking authorization with server at $profileUrl")` 输出完整 URL（仅路径，无 cookie），可接受。合并为一条观察记录：无实际问题。

### M2-2（P2）下载/离线任务恢复会话时的 host 与设置域可能不一致
- 证据：`NovelDownloadWorker.kt:256` `EsjzoneClient.restoreAuthorization(host)` 使用入队时保存的域名，符合 AGENTS.md 要求；`getPage` 的 `AuthorizationCookieJar.isAuthorizationHost` 会拒绝跨域发送 cookie。行为正确，非缺陷。

### M2-3（P2）`EsjzoneClient.initialize` 非幂等路径下 `PageCache.initialize` 会删除旧缓存
- 证据：`EsjzoneClient.kt:87-110` `@Synchronized initialize` 有 `initialized` 守卫，且由 `AppContainer.initializeAsync` 在启动 Mutex 内调用一次。`PageCache.initialize`（`PageCache.kt:106-118`）会删除 `cacheDir/page_cache` 的旧文件——只在进程首次初始化执行，可接受。非缺陷。

### M2-4（P1）`PersistentCookieJar` 构造在 `EsjzoneClient.initialize` 中同步执行，Keystore 不可用时静默降级为内存会话
- 证据：`PersistentCookieJar.kt:609-638`：secure prefs 创建失败时先 `deleteSharedPreferences` 三件套再重建；仍失败则 `securePreferences = null`，会话仅存内存（`memoryIdentity`/`memoryActiveHosts`），`persistLocked` 静默跳过（`runCatching`）。
- 触发路径：Keystore 损坏/从其他设备恢复备份后首次启动。此时用户登录可正常工作（内存身份），但**杀进程后会话丢失**，且用户无感知（日志仅 Log.e）。
- 影响：反复要求重新登录，与"重启后保持登录"的核心预期不符。
- 最小修复：`securePreferences == null` 时在 UI 层暴露一次性提示（如 `EsjzoneClient.sessionPersistenceAvailable` 状态 + MainScreen 横幅），告知会话无法持久化。

### M2-5（P2）`getPage` 合并请求等待 35s 超过 OkHttp callTimeout 30s
- 证据：`EsjzoneClient.kt:256` `existing.get(35, TimeUnit.SECONDS)`；普通请求 callTimeout 30s（`:107`）。
- 触发路径：同 URL 第二请求加入合并后，owner 若接近 30s 才超时失败，等待方最多再等 35s；等待方先超时抛 `SocketTimeoutException("Timed out waiting for coalesced request")`。体验上偶发"慢到 35s 才报错"，但不会永久挂起。
- 最小修复：将合并等待调整为略小于 callTimeout（如 25s）或与 `pageKind` 的预算联动。

### M2-6（P3）`release 更新检查` 的 SharedPreferences 读写在主线程
- 证据：`MainActivity.kt:168` `ReleaseUpdateChecker.checkOnce(appContext)` 在 onCreate 直接调用；`ReleaseUpdateChecker.kt:47-55` 同步 `getSharedPreferences` + `getLong`（首次 `check` 内又在 IO scope 读一次）。
- 触发路径：每次 Activity 创建执行两次磁盘读（小文件，通常 <1ms）。 StrictMode 下可能报磁盘主线程读。
- 最小修复：`checkOnce` 整体移入已有 IO scope，或至少把 SharedPreferences 读取放进 `Dispatchers.IO`。

## 结论

认证与缓存设计是本项目最扎实的部分：cookie 加密存储、按账户分域的 cache key、合并请求、响应验证后才持久化、WAF/403 不误判会话过期、登出不清他人数据。未发现 P0。主要遗留风险是 Keystore 不可用时静默丢失会话持久化（M2-4）。
