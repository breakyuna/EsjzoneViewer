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

应用通过 OkHttp 访问可选的 Esjzone 站点，使用 Jsoup 和 Xsoup 解析 HTML 页面，使用 Room 保存本地状态，使用 Coil 加载和缓存图片。页面导航由 Voyager 管理。

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
├── database/                    # Room 数据库、DAO、Cache 和 SearchHistory
├── network/                     # OkHttp 客户端、认证、Cookie、URL、XPath 和请求功能
├── novellibrary/                # 小说、章节、用户、分类和正文组件模型
├── ui/
│   ├── app/                     # 应用根导航
│   ├── component/               # 可复用 Compose 组件
│   ├── navigation/              # Navigator 与 CompositionLocal
│   ├── page/                   # 小说详情、章节阅读、搜索结果等页面
│   ├── screen/                 # 加载、登录和主界面
│   ├── tab/                    # 主页、分类、搜索、个人中心 Tab
│   └── theme/                  # Catppuccin 主题实现
└── util/                       # 通用工具函数
```

## 关键调用链

### 启动与认证

`MainActivity` 初始化 `GeneralDatabase` 和 `ImageLoader`，随后进入 `LoadingScreen`。加载页从 Room 恢复 `ews_key`、`ews_token`、站点、主题和成人内容设置，并通过 `EsjzoneClient.isAuthorized` 判断是否进入 `MainScreen` 或 `LoginScreen`。

### 主界面

`MainScreen` 提供四个 Tab：

- `HomeTab`：主页数据、推荐和最新更新。
- `CategoryTab`：分类和分类小说列表。
- `SearchTab`：关键词搜索和搜索历史。
- `ProfileTab`：用户信息、收藏、历史、设置和关于页面入口。

具体作品通过 `NovelPage` 展示详情，章节通过 `ChapterPage` 获取并解析后阅读。

### 网络与解析

- 所有站点基地址应通过 `GlobalSettings.domain` 和 `EsjzoneUrls` 获取，不要在新功能中散落硬编码域名。
- 登录流程使用站点返回的认证 token，并通过 `AuthorizationCookieJar` 携带会话 Cookie。
- 网络请求和网页解析应放在后台协程中，避免阻塞 Compose 主线程。
- 页面字段变化时，优先同步检查 `EsjzoneXPaths.kt`、`network/features/` 和 `novellibrary/` 中对应模型。
- 站点可能将 HTML 片段嵌入 JSON 或页面字段，修改解析逻辑时要保留空字段、异常 HTML 和相对 URL 的处理。

### 本地数据

`GeneralDatabase` 当前包含两类实体：

- `Cache`：会话 Cookie、站点、主题和成人内容显示选项。
- `SearchHistory`：搜索关键词及最近使用时间。

数据库访问应使用 IO 调度器。新增缓存键时要同时考虑首次安装、旧数据缺失和非法值恢复。

## 修改规范

- 用户可见文本优先放在 `app/src/main/res/values/strings.xml`，并同步维护 `values-zh-rCN/strings.xml`。
- Compose 页面应复用现有组件和 Material 3 组件，保持加载、成功和空数据状态完整。
- 涉及成人内容的列表、分类和主页分区必须遵守 `GlobalSettings.adult` 的显示状态。
- 章节阅读内容可能包含正文样式、注音和远程图片，修改 `Component` 或 `ChapterPage` 时要避免破坏这些内容。
- 改动站点 URL、XPath 或登录流程时，同时更新 `NETWORK.md` 中对应的技术说明。
- 不要把网络请求、数据库操作或大型列表计算直接放进 Compose 重组过程；使用现有的协程和 `StateScreenModel` 模式。
- 保持现有 GPL-3.0 许可证文件和第三方开源库归属信息。

## 文档与截图资源

`NETWORK.md` 当前引用 `screenshots/docs/1.png` 和 `screenshots/docs/2.png`。修改或清理截图资源前，必须先用全仓库静态搜索确认引用关系，并同步更新引用方；不能仅因为资源未被 README 使用就删除它们。

## 验证清单

完成修改后至少检查：

1. 除 ChatGPT work 场景外，优先通过 Gradle 构建与编译测试验证代码正确性。
2. `git diff --check` 无空白错误。
3. 检查新增或删除的类名、资源名、路径和文档链接是否仍有有效引用。
4. 检查 Kotlin、XML、JSON、Markdown 文件是否存在语法错误或未闭合结构。
5. 检查新增用户可见文本是否同时存在英文和简体中文资源，或明确说明不需要本地化。
6. 检查敏感信息和无关文件没有进入提交范围。
