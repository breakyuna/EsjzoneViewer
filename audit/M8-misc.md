# M8 其他（更新检查、WebView、日志）

审计文件：`update/ReleaseUpdateChecker.kt`、`update/ReleaseVersion.kt`、`ui/page/InAppBrowserActivity.kt`、`util/AppLogger.kt`、`util/CrashHandler.kt`、`util/CommentRequestTiming.kt`、`ui/reader/ReaderFontStore.kt`、`network/features/RequestAuthToken.kt`、`network/features/Logout.kt`

## 调用链概要

- 更新检查：独立 OkHttp（不带 ESJ cookie）、5/5/10s 超时、禁重试、24h 节流、release URL 白名单校验（https + github.com + 仓库路径）。
- 应用内浏览器：独立 WebView，JS 桥仅做繁简转换（无凭据访问），非 http(s) 主框架 URL 转系统浏览器，下载监听转外部。
- 日志：所有写日志消息经 `sanitize`（Authorization/Cookie/ews_key/ews_token/pwd/email 正则脱敏 + 邮箱替换），文件 2MB 轮转；崩溃报告含脱敏堆栈。
- 字体下载：WorkManager unique work + SHA-256 + 大小校验后发布。

## 发现

### M8-1（P3）更新检查在主线程读 SharedPreferences
- 证据：`MainActivity.kt:168`（onCreate 直接调用 `ReleaseUpdateChecker.checkOnce`）→ `ReleaseUpdateChecker.kt:50-53`（同步 `getSharedPreferences` + `getLong`）。
- 触发路径：每次 Activity 创建（含旋转屏幕）执行两次同步磁盘读；StrictMode 可见。
- 影响：极小（小文件），属规范问题。
- 最小修复：把 `checkOnce` 整体移入其 IO scope。

### M8-2（P3）崩溃报告的"RECENT ACTIVITY"可能包含 URL 查询串中的邮箱
- 证据：`util/AppLogger.kt:206-214`（报告拼接 recent entries）→ `sanitize` 的 `EMAIL_PATTERN` 会替换邮箱文本，但 URL path/query 的其他参数（如 `?email=`）已被 `PLAIN_SECRET_PATTERN` 覆盖；实际残留风险限于未列入黑名单的字段。
- 影响：日志仅存本机 `filesDir/logs`，可被用户导出分享；当前正则覆盖面已含主要凭据字段。
- 最小修复：无需改动；导出对话框已提示含设备信息。如需加固，可在导出前对 query 参数整体脱敏。

## 结论

更新检查、应用内浏览器与日志脱敏设计完备，未发现敏感信息泄露路径。均为 P3 观察项。
