# Esjzone 项目协作指南

适用于在本仓库中进行代码维护的自动化代理和开发者。本文集中说明工作流程、架构边界和验证要求。

## 强制约束

- 保持修改范围最小，不要顺带重写无关代码；删除看似未使用的内容前，确认资源、反射和网页解析流程中的引用。
- 未经明确要求，不得修改 `applicationId`、包名、数据库名称、远程仓库地址或发布签名配置。
- 不得在日志、异常信息、测试数据或文档中写入真实密码、会话 Cookie、`ews_key`、`ews_token` 或其他账户凭据。
- 保持现有 GPL-3.0 许可证文件和第三方开源库归属信息。
- 上传 GitHub 前无需在本地运行测试；日常改动仅需完成静态检查，测试、代码检查与 APK 构建统一由 GitHub Actions 验证。

### 本地凭据

本地可能配置 Release 签名密钥库、Gradle 用户属性、测试账号、API key、Cookie、Token 或其他开发凭据。自动化代理可以让现有本地构建环境透明使用这些配置，但不得主动读取真实密码、Token、Cookie 或私钥内容；不得打印、回显、复制到仓库，或写入代码、文档和终端日志；不得提交、修改或删除本地凭据，也不得为了调试而 `cat` 敏感配置文件。

## 工作流程

1. 确认修改范围及相关调用链。设计、添加或修改功能与解析前，必须先查阅 `NETWORK/` 中相关的网站逆向分析文档，基于已验证的实际 DOM 结构与接口规范实现。
2. 按下述修改边界实现变更。行为修改应补充能检验结果的测试，不添加只重复实现过程的测试。
3. 同步维护受影响的资源、引用和文档。改动站点 URL、CSS selector 或登录流程时，必须更新 `NETWORK/` 中对应的技术说明。
4. 按改动规模执行验证，并如实报告已执行、未执行及未完成的检查。

网站分析文档入口：

- [API_ENDPOINTS.md](NETWORK/API_ENDPOINTS.md)：接口规范。
- [AUTH.md](NETWORK/AUTH.md)：认证流程。
- [DATA_MODELS.md](NETWORK/DATA_MODELS.md)：数据模型。
- [FEATURE_MATRIX.md](NETWORK/FEATURE_MATRIX.md)：功能对照。
- [HTML_PARSERS.md](NETWORK/HTML_PARSERS.md)：页面解析。
- [SITE_MAP.md](NETWORK/SITE_MAP.md)：站点结构。

## 项目结构与调用链

### 技术概览

- 项目类型：Android 应用。
- 根项目名：`EsjzoneApplication`。
- 应用模块：`app`。
- 包名与命名空间：`com.breakyuna.esjzone`。
- UI 技术：Kotlin、Jetpack Compose、Material 3。
- 最低 Android 版本：API 29；编译 SDK：37；目标 SDK：37。
- Java/Kotlin 编译目标：17。

应用通过 OkHttp 访问可选的 Esjzone 站点，使用 Jsoup CSS 选择器解析 HTML 页面，使用 Room 保存本地状态，使用 Coil 加载和缓存图片。页面导航基于 AndroidX Navigation 3，当前仍通过 `LocalAppNavigator` 和 `LocalBaseNavigator` 向迁移中的页面提供导航控制器；导航状态集中在 `ui/navigation/`。

### 目录职责

```text
app/src/main/java/com/breakyuna/esjzone/
├── MainActivity.kt              # 初始化图片加载器、Room 和 Compose 根入口
├── AppLanguage.kt               # 应用语言枚举与持久化编码
├── data/settings/               # SettingsRepository 与 ReaderSettingsDataStore
├── database/                    # Room 数据库、DAO、缓存、书签、阅读历史和本地优先书架
├── network/                     # OkHttp 客户端、认证、Cookie、URL、CSS 解析和请求功能
├── novellibrary/                # 小说、章节、用户、分类和正文组件模型
├── ui/
│   ├── app/                     # 应用根导航
│   ├── component/               # 可复用 Compose 组件
│   ├── navigation/              # Navigator 与 CompositionLocal
│   ├── page/                    # 小说详情、章节阅读、搜索结果等页面
│   ├── screen/                  # 加载、登录和主界面
│   ├── tab/                     # 主页、历史、分类、收藏、搜索入口、个人中心 Tab
│   └── designsystem/            # Material 3 主题、Design Token 和共享视觉组件
└── util/                       # 通用工具函数
```

### 启动与认证

`MainActivity` 先显示启动状态，再由进程级协程与互斥锁初始化使用 applicationContext 的 `GeneralDatabase`、`ImageLoader`、下载存储和设置 DataStore；失败提供重试，Activity 重建复用已初始化资源。启动阶段幂等迁移旧 Room 设置与 Reader SharedPreferences，并由 `SettingsRepository` 恢复站点、主题、语言和成人内容状态。`LoadingScreen` 只恢复本地会话，然后立即进入 `MainScreen`；主界面显示后由生命周期绑定的后台协程执行有界 `checkAuthorization`。明确失效时显示可选的重新登录提示，但不自动清除会话；网络状态未知时保留本地会话。进入主界面后后台调度书架同步，UI 不等待同步完成，本地书架和阅读历史可离线使用。

### 主界面

`MainScreen` 提供四个底部 Tab；分类和搜索从主页/页面入口打开：

- `HomeTab`：主页数据、推荐和最新更新，也提供分类入口及右上角关键词搜索和搜索历史入口。
- `HistoryTab`：观看记录；单击浏览历史，双击直接继续最近一次阅读。
- `FavoriteTab`：本地优先书架，支持云端补充、按最近阅读排序、编辑与批量删除。
- `ProfileTab`：用户信息、设置和关于页面入口。

具体作品通过 `NovelPage` 展示详情，章节通过 `ChapterPage` 获取并解析后阅读。

## 修改边界

### 网络与解析

- 所有站点基地址应通过 `SettingsRepository.domain`（由 `SettingsDataStore` 提供）和 `EsjzoneUrls` 获取，不要在新功能中散落硬编码域名。
- 登录流程使用站点返回的认证 token，并通过 `AuthorizationCookieJar` 携带会话 Cookie。
- 网络请求和网页解析应放在后台协程中，避免阻塞 Compose 主线程。
- 页面字段变化时，优先同步检查 `network/features/` 的 CSS selector adapter 和 `novellibrary/` 中对应模型。
- 站点可能将 HTML 片段嵌入 JSON 或页面字段，修改解析逻辑时要保留空字段、异常 HTML 和相对 URL 的处理。

### 本地数据与下载

`GeneralDatabase` 的实体包括：`Cache`（会话 Cookie、站点、主题和成人内容显示选项）、`SearchHistory`（搜索关键词）、`Bookmark`（章节书签）、`LocalReadingActivity`（设备本地阅读位置）和 `BookshelfEntry`（本地优先书架及同步意图）。本地阅读历史不上传；书架以本地状态和删除意图为准，远端同步失败时保留本地数据与待处理状态。

数据库访问应使用 IO 调度器。新增缓存键时要同时考虑首次安装、旧数据缺失和非法值恢复；应用设置的权威来源是 DataStore，旧 Room 设置仅用于一次性迁移。离线下载任务必须使用入队时保存的站点域名和自己的请求基址，不得改写 `SettingsRepository.domain` 或其他前台设置状态；下载文件、导出和数据库书架数据的生命周期分别管理。

### UI、状态与资源

- 用户可见文本优先放在 `app/src/main/res/values/strings.xml`，并同步维护 `values-zh-rCN/strings.xml`。
- Compose 页面应复用现有组件和 Material 3 组件，保持加载、成功和空数据状态完整。
- 涉及成人内容的列表、分类和主页分区必须遵守 `SettingsRepository.adult` 的 DataStore 状态。
- 章节阅读内容可能包含正文样式、注音和远程图片，修改 `Component` 或 `ChapterPage` 时要避免破坏这些内容。
- 不要把网络请求、数据库操作或大型列表计算直接放进 Compose 重组过程；页面状态使用 Navigation 3 entry 提供生命周期的 AndroidX `ViewModel`（当前基类为 `AppStateViewModel`），并通过协程执行后台工作。`screenModelScope` 是迁移期间保留的兼容命名，清理前必须先迁移所有调用点。
## 本地验证策略

本地验证用于提前发现格式、凭据或契约错误。Termux 已配置 Android SDK、JDK 和 Gradle，但没有模拟器。上传 GitHub 前日常不要求在本地执行 Gradle 单元测试或 Lint，统一交由 GitHub Actions CI 执行验证。

### 静态检查（本地日常必跑）

```bash
python3 tools/qa/verify_static_contracts.py
git diff --check
```

提交与上传前检查修改范围内的以下内容：

- 新增或删除的类名、资源名、路径和文档链接的引用。
- Kotlin、XML、JSON、Markdown 结构。
- 新增用户可见文本的英文和简体中文资源。
- 敏感信息和无关文件。

静态验证通过即可完成本地常规验证，随后即可提交并上传 GitHub。

### 按需本地测试（选跑）

日常上传无需在本地执行测试。仅当用户明确要求排查本地测试失败或特定构建问题时，才按需单独执行：

```bash
./gradlew testDebugUnitTest lintDebug --build-cache
```

`testDebugUnitTest` 验证 JVM 单元测试和业务回归，`lintDebug` 检查 Android 项目问题。未实际执行时如实标注“未在本地验证”。

### CI：测试、Lint 与 APK 构建

- 自动化单元测试、Android Lint 以及 Debug/Release APK 构建全部交给 GitHub Actions 验证；日常本地验证不要求 `assembleDebug` 或 `assembleRelease`。
- Release Variant、R8、资源压缩、签名和 Baseline Profile 集成由 CI 的 Release 构建检查。
- 日常本地验收不强制运行 `connectedDebugAndroidTest`。保留 Room、DataStore、SharedPreferences 迁移、Cookie / Android Framework 持久化和 MainActivity 启动等 Instrumentation 测试；需要时通过 GitHub Actions 的 Android Device Validation 在模拟器上运行。

### 结果表述

| 实际完成的检查 | 可报告的结果 |
| --- | --- |
| 源码及静态检查 | 静态验证通过 |
| `testDebugUnitTest` | JVM 单元测试通过 |
| `lintDebug` | Android Lint 通过 |
| `assembleDebug` | Debug 构建通过 |
| `assembleRelease` | Release 构建通过 |
| `connectedDebugAndroidTest` | Android Instrumentation 测试通过 |

静态验证通过不能代表 JVM 单元测试、Android Lint 或 APK 构建通过；本地测试与 Lint 通过也不代表 APK 构建通过。未执行的项目标注“未验证”，已启动但未完成的检查不得报告为通过。
