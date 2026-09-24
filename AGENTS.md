# Esjzone 项目协作指南

本文档用于说明项目结构、关键约束和常用修改边界，适用于在本仓库中进行代码维护的自动化代理和开发者。

## 项目概览

- 项目类型：Android 应用。
- 根项目名：`EsjzoneApplication`。
- 应用模块：`app`。
- 包名与命名空间：`com.breakyuna.esjzone`。
- UI 技术：Kotlin、Jetpack Compose、Material 3。
- 最低 Android 版本：API 29；编译 SDK：37；目标 SDK：37。
- Java/Kotlin 编译目标：17。

应用通过 OkHttp 访问可选的 Esjzone 站点，使用 Jsoup CSS 选择器解析 HTML 页面，使用 Room 保存本地状态，使用 Coil 加载和缓存图片。页面导航基于 AndroidX Navigation 3，当前仍通过 `LocalAppNavigator` 和 `LocalBaseNavigator` 向迁移中的页面提供导航控制器；导航状态集中在 `ui/navigation/`。

## 强制约束

1. 除 ChatGPT work 场景外，以本地单元测试和 Lint 通过为日常验证优先级；APK 构建由 GitHub Actions 验证。
2. 不要在日志、异常信息、测试数据或文档中写入真实密码、会话 Cookie、`ews_key`、`ews_token` 或其他账户凭据。
3. 不要未经明确要求修改 `applicationId`、包名、数据库名称、远程仓库地址或发布签名配置。
4. 修改应保持最小范围，不要顺带重写无关代码或删除看似未使用但可能由资源、反射或网页解析流程依赖的内容。

## 目录职责

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
│   ├── page/                   # 小说详情、章节阅读、搜索结果等页面
│   ├── screen/                 # 加载、登录和主界面
│   ├── tab/                    # 主页、历史、分类、收藏、搜索入口、个人中心 Tab
│   └── designsystem/           # Material 3 主题、Design Token 和共享视觉组件
└── util/                       # 通用工具函数
```

## 关键调用链

### 启动与认证

`MainActivity` 先显示启动状态，再由进程级协程与互斥锁初始化使用 applicationContext 的 `GeneralDatabase`、`ImageLoader`、下载存储和设置 DataStore；失败提供重试，Activity 重建复用已初始化资源。启动阶段幂等迁移旧 Room 设置与 Reader SharedPreferences，并由 `SettingsRepository` 恢复站点、主题、语言和成人内容状态。`LoadingScreen` 只恢复本地会话，然后立即进入 `MainScreen`；主界面显示后由生命周期绑定的后台协程执行有界 `checkAuthorization`。明确失效时显示可选的重新登录提示，但不自动清除会话；网络状态未知时保留本地会话。进入主界面后后台调度书架同步，UI 不等待同步完成，本地书架和阅读历史可离线使用。

### 主界面

`MainScreen` 提供四个底部 Tab；分类和搜索从主页/页面入口打开：

- `HomeTab`：主页数据、推荐和最新更新，也提供分类入口及右上角关键词搜索和搜索历史入口。
- `HistoryTab`：观看记录；单击浏览历史，双击直接继续最近一次阅读。
- `FavoriteTab`：本地优先书架，支持云端补充、按最近阅读排序、编辑与批量删除。
- `ProfileTab`：用户信息、设置和关于页面入口。

具体作品通过 `NovelPage` 展示详情，章节通过 `ChapterPage` 获取并解析后阅读。

### 网络与解析

- 所有站点基地址应通过 `SettingsRepository.domain`（由 `SettingsDataStore` 提供）和 `EsjzoneUrls` 获取，不要在新功能中散落硬编码域名。
- 登录流程使用站点返回的认证 token，并通过 `AuthorizationCookieJar` 携带会话 Cookie。
- 网络请求和网页解析应放在后台协程中，避免阻塞 Compose 主线程。
- 页面字段变化时，优先同步检查 `network/features/` 的 CSS selector adapter 和 `novellibrary/` 中对应模型。
- 站点可能将 HTML 片段嵌入 JSON 或页面字段，修改解析逻辑时要保留空字段、异常 HTML 和相对 URL 的处理。

### 本地数据与任务边界

`GeneralDatabase` 当前包含五类实体：`Cache`（会话 Cookie、站点、主题和成人内容显示选项）、`SearchHistory`（搜索关键词）、`Bookmark`（章节书签）、`LocalReadingActivity`（设备本地阅读位置）和 `BookshelfEntry`（本地优先书架及同步意图）。本地阅读历史不上传；书架以本地状态和删除意图为准，远端同步失败时保留本地数据与待处理状态。

数据库访问应使用 IO 调度器。新增缓存键时要同时考虑首次安装、旧数据缺失和非法值恢复；应用设置的权威来源是 DataStore，旧 Room 设置仅用于一次性迁移。离线下载任务必须使用入队时保存的站点域名和自己的请求基址，不得改写 `SettingsRepository.domain` 或其他前台设置状态；下载文件、导出和数据库书架数据的生命周期分别管理。

## 修改规范

- 用户可见文本优先放在 `app/src/main/res/values/strings.xml`，并同步维护 `values-zh-rCN/strings.xml`。
- Compose 页面应复用现有组件和 Material 3 组件，保持加载、成功和空数据状态完整。
- 涉及成人内容的列表、分类和主页分区必须遵守 `SettingsRepository.adult` 的 DataStore 状态。
- 设计、添加或修改功能与解析时，必须先查阅 `NETWORK/` 目录下的网站逆向分析文档（如 `API_ENDPOINTS.md`、`AUTH.md`、`DATA_MODELS.md`、`FEATURE_MATRIX.md`、`HTML_PARSERS.md`、`SITE_MAP.md`），基于已验证的实际 DOM 结构与接口规范进行设计与实现。
- 章节阅读内容可能包含正文样式、注音和远程图片，修改 `Component` 或 `ChapterPage` 时要避免破坏这些内容。
- 改动站点 URL、CSS selector 或登录流程时，同步更新 `NETWORK/` 目录下对应的技术说明文档。
- 不要把网络请求、数据库操作或大型列表计算直接放进 Compose 重组过程；页面状态使用 Navigation 3 entry 提供生命周期的 AndroidX `ViewModel`（当前基类为 `AppStateViewModel`），并通过协程执行后台工作。`screenModelScope` 是迁移期间保留的兼容命名，清理前必须先迁移所有调用点。
- 保持现有 GPL-3.0 许可证文件和第三方开源库归属信息。

## 文档与截图资源

`NETWORK/` 中的文档或技术说明可能引用相关截图资源。修改或清理截图资源前，必须先用全仓库静态搜索确认引用关系，并同步更新引用方；不能仅因为资源未被 README 使用就删除它们。

## 本地验证策略

本地验证的目标是提前发现会使 GitHub Actions 中断的常见错误。Termux 已配置 Android SDK、JDK 和 Gradle。普通 Kotlin、Compose、网络、解析、UI 等代码修改完成后，执行与 CI 对应的测试和 Lint：

```bash
./gradlew testDebugUnitTest lintDebug --build-cache
```

两项全部成功后，才能称为“本地测试与 Lint 通过”。`testDebugUnitTest` 验证 JVM 单元测试和业务回归，`lintDebug` 检查 Android 项目问题；两者也会编译所需的 Debug 代码。行为修改应补充能检验结果的测试，不添加只重复实现过程的测试。纯文档或仅修改本地规则时，可只运行相关静态检查。

APK 构建交给 GitHub Actions。日常本地验证不要求 `assembleDebug` 或 `assembleRelease`；Release Variant、R8、资源压缩、签名和 Baseline Profile 集成由 CI 的 Release 构建检查。如用户明确要求排查构建问题，可按需要单独执行构建任务。

本地 Termux 没有模拟器，日常本地验收不强制运行 `connectedDebugAndroidTest`。保留 Room、DataStore、SharedPreferences 迁移、Cookie / Android Framework 持久化和 MainActivity 启动等 Instrumentation 测试；需要时通过 GitHub Actions 的 Android Device Validation 在模拟器上运行。

辅助检查继续执行：

```bash
python3 tools/qa/verify_static_contracts.py
git diff --check
```

对代码修改，静态验证通过不得代替 Gradle 单元测试或 Lint；静态检查也不能证明 APK 构建成功。还要检查新增或删除的类名、资源名、路径和文档链接的引用，Kotlin、XML、JSON、Markdown 结构，新增用户可见文本的英文和简体中文资源，以及提交范围内的敏感信息和无关文件。

验证结果按实际执行的命令表述：只检查源码称“静态验证通过”；`testDebugUnitTest` 成功称“JVM 单元测试通过”；`lintDebug` 成功称“Android Lint 通过”；`assembleDebug` 成功称“Debug 构建通过”；`assembleRelease` 成功称“Release 构建通过”；`connectedDebugAndroidTest` 成功称“Android Instrumentation 测试通过”。本地测试与 Lint 通过不代表 APK 构建通过；没有实际执行的项目应明确标注未验证。

## 本地凭据

本地可能配置 Release 签名密钥库、Gradle 用户属性、测试账号、API key、Cookie、Token 或其他开发凭据。自动化代理可以让现有本地构建环境透明使用这些配置，但不得主动读取真实密码、Token、Cookie 或私钥内容；不得打印、回显、复制到仓库，或写入代码、文档和终端日志；不得提交、修改或删除本地凭据，也不得为了调试而 `cat` 敏感配置文件。
