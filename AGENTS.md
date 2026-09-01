# Esjzone 项目协作指南

本文档用于说明项目结构、关键约束和常用修改边界，适用于在本仓库中进行代码维护的自动化代理和开发者。

## 项目概览

- 项目类型：Android 应用。
- 根项目名：`EsjzoneApplication`。
- 应用模块：`app`。
- 包名与命名空间：`com.breakyuna.esjzone`。
- UI 技术：Kotlin、Jetpack Compose、Material 3。
- 最低 Android 版本：API 29；编译和目标 SDK：34。
- Java/Kotlin 编译目标：17。

应用通过 OkHttp 访问可选的 Esjzone 站点，使用 Jsoup 和 Xsoup 解析 HTML 页面，使用 Room 保存本地状态，使用 Coil 加载和缓存图片。页面导航由 Voyager 管理，导航状态集中在 `ui/navigation/`。

## 强制约束

1. 除 ChatGPT work 场景外，以构建编译和验证通过为第一优先级，确保代码具备可构建性和正确性。
2. 不要在日志、异常信息、测试数据或文档中写入真实密码、会话 Cookie、`ews_key`、`ews_token` 或其他账户凭据。
3. 不要未经明确要求修改 `applicationId`、包名、数据库名称、远程仓库地址或发布签名配置。
4. 修改应保持最小范围，不要顺带重写无关代码或删除看似未使用但可能由资源、反射或网页解析流程依赖的内容。

## 目录职责

```text
app/src/main/java/com/breakyuna/esjzone/
├── MainActivity.kt              # 初始化图片加载器、Room 和 Compose 根入口
├── GlobalSettings.kt            # 当前站点、主题和成人内容显示状态
├── database/                    # Room 数据库、DAO、缓存、书签、阅读历史和本地优先书架
├── network/                     # OkHttp 客户端、认证、Cookie、URL、XPath 和请求功能
├── novellibrary/                # 小说、章节、用户、分类和正文组件模型
├── ui/
│   ├── app/                     # 应用根导航
│   ├── component/               # 可复用 Compose 组件
│   ├── navigation/              # Navigator 与 CompositionLocal
│   ├── page/                   # 小说详情、章节阅读、搜索结果等页面
│   ├── screen/                 # 加载、登录和主界面
│   ├── tab/                    # 主页、历史、分类、收藏、搜索入口、个人中心 Tab
│   └── theme/                  # Catppuccin 主题实现
└── util/                       # 通用工具函数
```

## 关键调用链

### 启动与认证

`MainActivity` 先显示启动状态，再由进程级协程与互斥锁初始化使用 applicationContext 的 `GeneralDatabase`、`ImageLoader` 和下载存储；失败提供重试，Activity 重建复用已初始化资源。启动阶段恢复站点与主题。`LoadingScreen` 恢复会话及成人内容设置，并通过 `checkAuthorization` 的三态结果决定导航：明确失效才清除会话，网络状态未知时保留本地会话；进入主界面前后台调度书架同步，UI 不等待同步完成。

### 主界面

`MainScreen` 提供四个底部 Tab；分类和搜索从主页/页面入口打开：

- `HomeTab`：主页数据、推荐和最新更新，也提供分类入口及右上角关键词搜索和搜索历史入口。
- `HistoryTab`：观看记录；单击浏览历史，双击直接继续最近一次阅读。
- `FavoriteTab`：本地优先书架，支持云端补充、按最近阅读排序、编辑与批量删除。
- `ProfileTab`：用户信息、设置和关于页面入口。

具体作品通过 `NovelPage` 展示详情，章节通过 `ChapterPage` 获取并解析后阅读。

### 网络与解析

- 所有站点基地址应通过 `GlobalSettings.domain` 和 `EsjzoneUrls` 获取，不要在新功能中散落硬编码域名。
- 登录流程使用站点返回的认证 token，并通过 `AuthorizationCookieJar` 携带会话 Cookie。
- 网络请求和网页解析应放在后台协程中，避免阻塞 Compose 主线程。
- 页面字段变化时，优先同步检查 `EsjzoneXPaths.kt`、`network/features/` 和 `novellibrary/` 中对应模型。
- 站点可能将 HTML 片段嵌入 JSON 或页面字段，修改解析逻辑时要保留空字段、异常 HTML 和相对 URL 的处理。

### 本地数据与任务边界

`GeneralDatabase` 当前包含五类实体：`Cache`（会话 Cookie、站点、主题和成人内容显示选项）、`SearchHistory`（搜索关键词）、`Bookmark`（章节书签）、`LocalReadingActivity`（设备本地阅读位置）和 `BookshelfEntry`（本地优先书架及同步意图）。本地阅读历史不上传；书架以本地状态和删除意图为准，远端同步失败时保留本地数据与待处理状态。

数据库访问应使用 IO 调度器。新增缓存键时要同时考虑首次安装、旧数据缺失和非法值恢复。离线下载任务必须使用入队时保存的站点域名和自己的请求基址，不得改写 `GlobalSettings.domain` 或其他前台全局站点状态；下载文件、导出和数据库书架数据的生命周期分别管理。

## 修改规范

- 用户可见文本优先放在 `app/src/main/res/values/strings.xml`，并同步维护 `values-zh-rCN/strings.xml`。
- Compose 页面应复用现有组件和 Material 3 组件，保持加载、成功和空数据状态完整。
- 涉及成人内容的列表、分类和主页分区必须遵守 `GlobalSettings.adult` 的显示状态。
- 设计、添加或修改功能与解析时，必须先查阅 `NETWORK/` 目录下的网站逆向分析文档（如 `API_ENDPOINTS.md`、`AUTH.md`、`DATA_MODELS.md`、`FEATURE_MATRIX.md`、`HTML_PARSERS.md`、`SITE_MAP.md`），基于已验证的实际 DOM 结构与接口规范进行设计与实现。
- 章节阅读内容可能包含正文样式、注音和远程图片，修改 `Component` 或 `ChapterPage` 时要避免破坏这些内容。
- 改动站点 URL、XPath 或登录流程时，同步更新 `NETWORK/` 目录下对应的技术说明文档。
- 不要把网络请求、数据库操作或大型列表计算直接放进 Compose 重组过程；使用现有的协程和 `StateScreenModel` 模式。
- 保持现有 GPL-3.0 许可证文件和第三方开源库归属信息。

## 文档与截图资源

`NETWORK/` 中的文档或技术说明可能引用相关截图资源。修改或清理截图资源前，必须先用全仓库静态搜索确认引用关系，并同步更新引用方；不能仅因为资源未被 README 使用就删除它们。

## 验证清单

完成修改后至少检查：

1. 除 ChatGPT work 场景外，优先通过 Gradle 构建与编译测试验证代码正确性。
2. `git diff --check` 无空白错误。
3. 检查新增或删除的类名、资源名、路径和文档链接是否仍有有效引用。
4. 检查 Kotlin、XML、JSON、Markdown 文件是否存在语法错误或未闭合结构。
5. 检查新增用户可见文本是否同时存在英文和简体中文资源，或明确说明不需要本地化。
6. 检查敏感信息和无关文件没有进入提交范围。
