# M6 UI、导航与状态

审计文件：`ui/navigation/AppNavigation.kt`、`ui/navigation/AdaptiveShell.kt`、`ui/screen/*`、`ui/page/FavoritePageModel.kt`、`ui/page/SettingsPage.kt`、`ui/page/SettingsPageModel.kt`、`ui/page/CommentSection.kt`（CommentPageModel）、`ui/tab/*`

## 调用链概要

- Navigation 3：可序列化 `AppNavKey` + route token 注册表（32 LRU）+ ViewModelStore/SaveableStateHolder 装饰器；Reader 为 root 级 shell。
- 会话：`AppNavigation` 同步 `restoreAuthorization(domain)` 驱动 root `LocalAuthorization`；`LoadingScreen` 负责旧 Room 会话一次性导入；`MainScreen` 后台 `checkAuthorization`，结果经 epoch/域/账户三重校验后才提示。
- 页面模型统一 `AppStateViewModel` + `viewModelScope`，磁盘/网络均在 `Dispatchers.IO` 显式切换（抽查 20 余处调用点全部合规）。
- 评论提交：attemptId 代际 + 10s 同内容去重 + 刷新后找回（findCreatedComment）。

## 发现

### M6-1（P2）登出后 root 授权状态未清空
- 证据：`ui/navigation/AppNavigation.kt:552-563`（`authorizationState` 仅在 `replaceAll(MainScreen)` 时经 `setAuthorization` 更新）；`ui/page/SettingsPageModel.kt:177`（`clearSession()` 后无导航状态清理）；`ui/navigation/AppNavigation.kt:362-383`（`replaceAll(LoginScreen)` 不清 `authorization`）。
- 触发路径：设置页退出登录 → backStack 清空为 Login → 若此时发生进程内快速重建（配置切换）或用户通过系统返回重新进入，`AppNavigation` 仍以旧 `Authorization` 提供 root `LocalAuthorization` 并记住 Main 路由；旧 cookie 虽已清除（请求会以"无会话"失败），但 UI 层拿到的是已失效会话对象。
- 影响：常规路径不可见（栈内只有 LoginScreen）；边界路径（重建/深链接）会把失效会话当有效会话使用，可能出现"已登录"外观的页面瞬间渲染后再报未授权。
- 最小修复：`replaceAll` 增加 `Authorization("", "")` 分支（或 `setAuthorization(null)` 语义），登出完成时同步清空 root 授权状态。

### M6-2（P3）`pendingNovelUrl` 在配置切换后重复入栈（详见 M1-1）
- 证据：与 M1-1 相同（`MainActivity.kt:117,171-182` + `AppNavigation.kt:570-577`）。
- 影响：从下载通知打开小说后旋转屏幕，会在当前页面之上重复压栈小说页。
- 最小修复：见 M1-1。

## 结论

导航与状态层没有发现主线程磁盘/网络、状态提升错误或重复加载问题。M6-1 为登出路径的状态残留，M6-2 与 M1 模块合并跟踪。
