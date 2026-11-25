# Esjzone 项目协作指南

适用于在本仓库中进行代码维护的自动化代理和开发者，集中说明强制约束、架构边界、协作规范与验证要求。

## 强制约束

- **最小改动**：优先保证核心主流程正确；不做顺手重构、额外抽象、兼容层、兜底机制或无关测试；仅处理真实影响当前功能的边界问题。删除看似未用代码前，先确认资源、反射与网页解析引用。
- **禁止变更项**：未经明确要求，不得修改 `applicationId`、包名、数据库名称、远程仓库地址、发布签名配置，或破坏 GPL-3.0 许可证与第三方库归属信息。
- **凭据隔离**：不得在代码、日志、异常、测试数据、提交记录或文档中写入真实密码、Cookie、`ews_key`、`ews_token` 或其他凭据。本地签名密钥库、Gradle 属性、测试账号等敏感配置可由构建环境透明使用，但不得主动读取、回显、复制、修改、删除或提交。
- **默认仅静态检查**：未经用户明确要求，不得运行任何编译、测试、构建或会触发编译的 Android Lint；改动规模大或已有测试用例均不构成授权，“修复”“改进”“检查更改”亦不视为授权。验证达到“足以证明本次修改正确”即可停止。

## 工作流程与文档入口

1. 修改前确认调用链；涉及站点接口/解析先查 `NETWORK/`，涉及存储、手势、宽屏或安全边界先查 `docs/`。
2. 按下文边界实现最小改动；行为变更补充检验结果的测试（补充测试源码不代表获准运行）。
3. 同步更新受影响的资源、引用与文档（改动站点 URL、CSS selector、更新协议或登录流程时，同步更新 `NETWORK/`）。
4. 默认仅执行文末静态检查，并如实报告已执行、未执行及未完成的检查。

- `NETWORK/`：[API_ENDPOINTS.md](NETWORK/API_ENDPOINTS.md)（接口）、[AUTH.md](NETWORK/AUTH.md)（认证）、[DATA_MODELS.md](NETWORK/DATA_MODELS.md)（模型）、[FEATURE_MATRIX.md](NETWORK/FEATURE_MATRIX.md)（功能对照）、[HTML_PARSERS.md](NETWORK/HTML_PARSERS.md)（解析）、[SITE_MAP.md](NETWORK/SITE_MAP.md)（站点结构）、[APP_UPDATES.md](NETWORK/APP_UPDATES.md)（更新与 APK 下载）。
- `docs/`：[maintenance-notes.md](docs/maintenance-notes.md)（启动/存储/缓存与安全边界）、[text-selection.md](docs/text-selection.md)（正文选择复制）、[reader-gestures.md](docs/reader-gestures.md)（阅读器手势）、[wide-layout.md](docs/wide-layout.md)（宽屏布局）、[cache-analysis.md](docs/cache-analysis.md)（缓存上限与 Debug 日志）。

## 长时间任务等待策略

仅针对已获准启动的长耗时任务（Gradle、GitHub Actions、长脚本、Subagent），遵循“启动 → 单次长阻塞等待 → 完成即处理”：

- 预计耗时数分钟的任务优先单次阻塞等待最长 10 分钟（600000 ms）；提前完成即处理，超时且仍在运行时再检查并继续长等待，勿高频轮询或仅因无新输出而中断。
- 等待 GitHub Actions 优先使用 `gh run watch --exit-status --interval 30`，不要反复调用 `gh run view`。
- 并行子任务尽量统一等待完成事件，避免逐个轮询。

## 架构概览与关键入口

- **技术栈**：Android 应用（根项目 `EsjzoneApplication`，模块 `:app`、`:baselineprofile`，包名 `com.breakyuna.esjzone`）；API 29+，compileSdk/targetSdk 37，Java/Kotlin 17；Kotlin、Jetpack Compose、Material 3、OkHttp、Jsoup、Room、DataStore、Coil、AndroidX Navigation 3。
- **核心包职责**（`app/src/main/java/com/breakyuna/esjzone/`）：
  - 入口与装配：`MainActivity.kt`、`StartupCoordinator.kt`、`app/`（`AppContainer`、`PresentationAccess`）、`AppLanguage`/`AppPalette`/`AppThemeMode`。
  - 数据与领域：`data/`（`settings/` DataStore、`reader/`、`repository/` 适配器）、`domain/`（`reader/` 模型与会话控制、`repository/` 含 `SettingsRepository`）、`database/`（Room `GeneralDatabase`、DAO、实体）。
  - 网络与外围：`network/`（OkHttp、认证、Cookie、URL、`features/` CSS 解析）、`novellibrary/`（领域与正文模型）、`offline/`、`storage/`、`backup/`、`update/`、`util/`。
  - 界面层：`ui/app/`（根导航）、`ui/navigation/`（`AppNavigator`、`AppNavKey`、`AppStateViewModel`、自适应导航壳）、`ui/designsystem/`（M3 主题、Design Token、`GlobalText`）、`ui/component/`（含 `SelectableProse`）、`ui/product/`、`ui/discovery/`、`ui/reader/`、`ui/screen/`（`LoadingScreen`、`LoginScreen`、`MainScreen`、`StorageSetupScreen`）、`ui/tab/`、`ui/page/`。
- **启动与导航不变量**：
  - `MainActivity` 先展示启动状态，再以进程级协程与互斥锁基于 `applicationContext` 初始化数据库、`ImageLoader`、下载存储与 DataStore（失败可重试，重建复用）；启动时幂等迁移旧 Room 设置与 Reader SharedPreferences。
  - `LoadingScreen` 仅恢复本地会话即进 `MainScreen`；主界面后台执行有界 `checkAuthorization`（失效仅提示重登不自动清会话，网络未知保留会话）并异步同步书架，本地书架与历史可离线使用。
  - 导航由 Navigation 3 管理（保留旧保存路由恢复）：通过参数传递 `AppNavigator`、用可序列化 `AppNavKey` 传参；阅读器（`ChapterPage`）进根栈，其余页面进发起导航的栈。`MainScreen` 含四个底部 Tab（`HomeTab`、`HistoryTab`、`FavoriteTab`、`ProfileTab`），分类与搜索从主页/页面入口进入；会员中心只读页与特殊接口约束见 `NETWORK/` 及 `network/features/` 注释。

## 修改边界

### 网络与解析

- 站点基地址统一通过 `SettingsRepository.domain`（`data/settings/SettingsDataStore`）与 `EsjzoneUrls` 获取，禁止硬编码域名。
- 认证请求使用站点 token 与 `AuthorizationCookieJar`；无登录态请求（如会员回复外链、更新 APK 下载）不得混用带凭据客户端。
- 网络与解析均在后台协程执行；页面字段变动时同步检查 `network/features/` 与 `novellibrary/`，保留空字段、异常 HTML、嵌入 JSON 的 HTML 片段及相对 URL 处理。

### 本地数据与下载

- `GeneralDatabase`（`database/GeneralDatabase.kt`）包含 `Cache`、`SearchHistory`、`Bookmark`、`LocalReadingActivity`、`BookshelfEntry`、`ReadingStat`、`BookshelfGroup`、`BookshelfGroupMember`；改表结构须提供 `Migration` 并同步导出 schema。
- 本地阅读历史不上传；书架以本地状态与删除意图为准，远端同步失败保留本地状态。已登录时，阅读尚未向当前账号请求过的本地/缓存章节会补请求该章节页以更新站点最后观看位置（不上传本地历史本身）。
- 数据库访问使用 IO 调度器；应用设置以 DataStore 为权威来源（旧 Room 仅一次性迁移），新增键须兼容首次安装、缺省与非法值恢复。
- 离线下载任务须使用入队时记录的域名与独立请求基址，不得改写前台 `SettingsRepository.domain`；下载、导出、缓存与书架生命周期独立（并发与代际一致性见 [docs/maintenance-notes.md](docs/maintenance-notes.md)）。

### UI、状态与资源

- **简洁高密**：禁止为含义明确的设置项添加描述、副标题、helper text 或解释小字；仅功能存在歧义或需警告时允许辅助说明，优先通过布局、控件和命名自解释。
- **多语言与简繁**：同步维护全部三份 `strings.xml` 且键名完全一致：`app/src/main/res/values/strings.xml`（默认简体中文）、`values-zh-rCN/strings.xml`（简体中文）、`values-en/strings.xml`（英文）。面向用户的 Compose 文本优先使用 `ui/designsystem/` 的 `GlobalText`（常 `as Text`）与 `globalStringResource`，确保全局简繁转换生效；页面保持加载、成功、错误和空态完整。
- **正文与内容约束**：阅读器外只读正文用 `SelectableProse` 仅包裹文字本身，不得包裹可点击或可长按的行（见 [docs/text-selection.md](docs/text-selection.md)）；涉及成人内容的列表/分类/历史/主页须遵守 `SettingsRepository.adult`（隐藏时不得将未知分类当作非成人）；修改 `Component`、`ui/reader/` 或 `ChapterPage` 时勿破坏正文样式、注音、划线锚点与远程图片。
- **状态管理**：禁止在 Compose 重组中做 I/O 或重计算；页面状态使用 Navigation 3 entry 作用域的 `AppStateViewModel` 与协程。

## 提交、PR 与代码审查规范

- **Commit 格式**：采用 Conventional Commits 且描述使用简体中文：`<type>: <简体中文描述>` 或 `<type>(<scope>): <简体中文描述>`。
  - 常用 `type`：`feat`、`fix`、`refactor`、`perf`、`style`、`docs`、`test`、`build`、`ci`、`chore`、`revert`（例：`fix: 修复以换行开头的章节正文丢失`、`feat(reader): 支持章节多段落书签`）。
  - 首行简洁明确（建议 ≤50 字），不加句末句号，保留代码标识符、路径、API 名称和技术术语原文；复杂说明或未验证项写在空行后的正文中；单次提交保持单一职责，不夹带无关修改或敏感文件。
- **分支、PR 与发布**：分支建议 `<type>/<short-topic>`；PR 标题同 Commit 格式，正文按 [.github/pull_request_template.md](.github/pull_request_template.md) 填写并显式标注未验证项；正式发布 Tag 须严格匹配 `gradle.properties` 中的 `v<app_version>`。
- **PR 代码审查**：所有 GitHub PR 审查评论（标题、问题描述、风险分析、修复建议）必须使用简体中文，保留代码标识符、路径、API 名称、技术术语原文及 `P0`/`P1`/`P2` 优先级标记，不得因翻译降低技术准确度或严格度。

## 本地验证策略

Termux 已配置 Android SDK、JDK 与 Gradle（无模拟器）。**默认仅执行静态检查**，不得私自运行 JVM/设备测试、Lint、构建或 Node 等行为测试脚本。

### 静态检查（所有改动默认执行）

```bash
python3 tools/qa/verify_static_contracts.py
git diff --check
```

脚本自动校验已移除旧 API/主题残留、凭据特征、三份 `strings.xml` 键名对称及 `SelectableProse` 与行长按页面互斥；同时人工核对变更范围内的符号/路径/文档链接引用、语法结构、中英文翻译与敏感信息。

### 编译、测试、Lint 与 CI（仅用户明确要求时）

- 修改期间勿逐处跑 Gradle；完成全部改动且静态检查通过后，仅在用户明确授权范围内统一执行。失败时集中修复后可复跑受影响项，通过后无新改动不重复跑，用户要求停止即停。
- 用户明确要求 JVM 单元测试与 Android Lint 时运行：`./gradlew testDebugUnitTest lintDebug --build-cache`（两项均过方可称为“本地测试与 Lint 通过”）。
- APK 构建与设备测试交给 GitHub Actions（`.github/workflows/build.yml`、`release.yml`、`device-validation.yml`）：未经明确要求不得本地执行 `assembleDebug`/`assembleRelease`、编译/运行设备测试或操作 CI。PR 与 push 均跑 R8 Release 构建（PR 仅产未签名 APK 并传 R8 mapping，正式签名仅在非 PR 流程）；保留 Room/DataStore/Prefs 迁移、Cookie 持久化与 `MainActivity` 启动等 Instrumentation 测试。

### 结果表述

| 实际完成的检查 | 可报告的结果 |
| --- | --- |
| 源码及静态检查 | 静态验证通过 |
| `testDebugUnitTest` | JVM 单元测试通过 |
| `lintDebug` | Android Lint 通过 |
| `assembleDebug` | Debug 构建通过 |
| `assembleRelease` | Release 构建通过 |
| `connectedDebugAndroidTest` | Android Instrumentation 测试通过 |

不同层级检查结果不可互代；未执行项须标注“未验证”，已启动未完成项不得报告为通过。
