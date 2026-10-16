# 应用更新检查与 APK 下载

本功能使用 GitHub API，与 ESJ Zone 网页接口、认证和小说下载相互独立。

## 协议依据

2026-10-15 对照 [GitHub Releases 官方接口文档](https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28) 与 [Release assets 文档](https://docs.github.com/en/rest/releases/assets?apiVersion=2022-11-28) 实现。以下字段来自官方响应结构；本次未执行真实 APK 下载或设备安装验证。

| 请求 | 说明 |
| --- | --- |
| `GET https://api.github.com/repos/breakyuna/EsjzoneViewer/releases?per_page=20` | 保留现有渠道筛选与版本比较规则；忽略草稿 |
| `GET assets[].browser_download_url` | 下载 APK，允许 HTTPS 重定向到 GitHub 附件服务，不携带登录 Cookie 或认证头 |

APK 元数据取自 Release 的 `assets`：`name`、`state`、`size`、`browser_download_url`、可选的 `digest`。只使用状态为 `uploaded`、大小大于零的 APK，地址必须为同仓库、同 Release 标签和附件名称的 HTTPS 下载路径。优先选取发布工作流生成的 `EsjzoneViewer-{tag}.apk`；否则仅在存在唯一有效 APK 时使用，不猜测多个 APK 的架构适配关系。没有可用 APK 时，弹窗仍提供发布页面入口。

## 下载与安装

- 用户点击“下载更新”后立即关闭更新弹窗，完成必要的通知权限请求后启动 `dataSync` 前台服务，默认在后台下载。独立 OkHttp 客户端在 IO 协程中流式写入 `cacheDir/app_updates/update.apk.part`，重新打开的弹窗与通知显示进度并允许取消、重试。
- 下载后核对附件字节数；GitHub 提供 SHA-256 `digest` 时同时校验。完整文件才重命名为 `update.apk`，并检查 APK 包名与当前应用一致、`versionCode` 大于当前构建。安装器负责最终签名和系统兼容性检查。
- 缓存目录无需共享存储权限，并且不参与系统备份。下一次下载清理旧更新文件；取消或失败清理半成品。
- 弹窗显示与任务生命周期分离。查看进度时，返回键或点击弹窗外部只隐藏弹窗，用户可以继续浏览其他页面；通过“关于”的版本栏或下载通知可以重新打开原任务，不会重新请求版本或重复下载。只有“取消下载”、服务停止或下载失败会中止传输。
- Activity 重建和切到其他应用不会主动取消任务。服务采用 `START_NOT_STICKY`，不承诺强制停止或系统终止进程后的自动续传。
- 下载完成且 Activity 处于 `RESUMED` 时重新显示更新弹窗并自动请求安装。应用位于后台时只发出完成通知，用户点击通知或返回应用后再进入安装流程。没有安装来源授权时打开本应用的系统授权页；授权返回后继续安装，拒绝则保留已下载文件与“安装更新”按钮。
- 完成通知只携带版本、发布页面与附件元数据，不包含可能很长的更新说明。应用进程已重建时，核对缓存 APK 的大小、包名与 `versionCode` 后恢复安装入口；缓存文件已清理时允许重新下载。
- Android 13 及以上在首次下载时请求通知权限；拒绝不会阻止下载，但通知栏的进度和完成入口不可见，应用内的进度、取消与安装流程仍可使用。
- 通过仅暴露 `app_updates/` 缓存目录的 FileProvider 生成 `content://` URI，并授予安装器临时读取权限。系统安装界面仍要求用户确认，取消安装后可以再次点击“安装更新”。

Android 协议依据：[canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())、[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)。Manifest 声明 `REQUEST_INSTALL_PACKAGES`，不申请共享存储访问权限。

后台服务依据：[前台服务启动](https://developer.android.com/develop/background-work/services/fgs/launch)、[服务超时处理](https://developer.android.com/develop/background-work/services/fgs/timeout)、[通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)。复用已有 `FOREGROUND_SERVICE` 和 `FOREGROUND_SERVICE_DATA_SYNC` 权限，独立声明更新服务。APK 下载由用户直接发起，使用独立前台服务，避免占用 Android 16 起受配额限制的长时间 WorkManager 任务。

## 源码入口

- `update/ReleaseUpdateChecker.kt`：检查与 APK 元数据读取。
- `update/ReleaseApkAsset.kt`：附件选择与 URL 边界。
- `update/ReleaseApkDownloader.kt`：流式下载、完整性检查与 APK 身份检查。
- `update/AppUpdateDownloadService.kt`：下载服务、进度、取消及完成通知。
- `update/ReleaseUpdateDialog.kt`：进度、授权返回与系统安装入口。
- `res/xml/app_update_paths.xml`：FileProvider 允许分享的目录。
