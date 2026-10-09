# Wenku8 轻小说文库适配：实施计划与跨 session 交接

> 编写日期：2026-10-09（Asia/Shanghai）。
> 本仓库检查基线：`f1e3153a1b7b35063f3e3966921c93f71f23647c`。
> 参考仓库：<https://github.com/dmzz-yyhyy/LightNovelReader>。
> 参考分支：`dev/1.3`；固定提交：`8681711f020af991fe37ca89983cc4531fe94c53`。
> 状态（2026-10-09 更新）：已实施身份／导航、候选搜索／详情／分卷目录、独立会话与本地书架、下载／离线、入口及归属文件。站点协议、完整会话和设备仍待验证；本地可实现的 A–F 改造已集中补齐，已通过最终 JVM／Lint 检查及设备测试源码编译；实际结果见文末。未知站点协议不猜测。

## 1. 给新 session 的任务说明

本文件是完整交接依据，不要求读取原 session。用户要求参考 LightNovelReader 的 Wenku8 实现，为 EsjzoneViewer 增加轻小说文库适配，优先复用当前网络层、数据模型、书架、缓存、下载和阅读器，保持两个数据源相互独立。

当前 session 的授权仅为检查、设计及写入本文件。用户准备在新 session 明确开启修改任务；收到“按本文件开始修改”等指令后，实施下述计划，不必再次询问是否可以修改已明确列出的范围。仅收到文件阅读或审查请求时，不自动进入实现。

用户的原始要求：

1. 分析参考项目的网络请求、登录认证、Cookie、搜索、详情、目录和正文。
2. 尽量复用本项目，不复制无关架构。
3. 允许直接移植许可证兼容的代码，但必须检查实际文件及其依赖许可证。
4. 移植代码保留必要版权声明、标记来源和修改内容，补齐许可证文件。
5. 不复制、硬编码、传播真实账号、密码、Session Token 或 Cookie。
6. 接口、参数及解析规则无法确认时，明确标记待验证，不猜测。
7. 尽量减少对 ESJ 功能的影响，保持各数据源独立。

建议新 session 的启动指令：

```text
请阅读 doc/WENKU8_INTEGRATION_PLAN.md 和 AGENTS.md，按交接计划开始实现 Wenku8 适配。
先复核当前代码与计划基线的差异，按阶段推进；对待验证接口获取证据，不猜测。
完成全部修改后统一执行规定的静态检查、JVM 测试和 Android Lint，报告未验证项。
```

上述启动指令不包含提交、推送、发布或读取本地凭据的授权。APK 构建按仓库指南交给 CI；只有收到相关指令后才提交、推送并监控工作流。

## 2. 已完成的检查及证据边界

- 阅读了根目录 [AGENTS.md](../AGENTS.md) 和六份 `NETWORK/` 入口文档。
- 检查了当前源码的 Wenku 章节路由、Cookie、WebView、正文解析、缓存、下载、详情、书架、历史、仓库适配器及 Navigation 3 恢复流程。
- 通过 GitHub API 确认参考分支和固定提交，检查该提交的 Wenku 实际源文件、相关工具、构建依赖、根许可证和 README 版权说明。
- 核对了直接相关依赖的实际版本许可证，见第 11 节。
- 匿名 GET 以下页面，均得到 HTTP 403，未取得有效业务 DOM：
  - `https://www.wenku8.net/login.php`
  - `https://www.wenku8.net/book/1.htm`
  - `https://www.wenku8.net/novel/0/1/index.htm`
- 上述数字只是只读路径探测样本，不构成作品存在性、章节目录或登录协议的证明。403 也不能在未检查响应的情况下直接断言为某种特定挑战。
- 未执行真实账户登录、登出或云端收藏操作；未主动读取本地账号、Cookie、密码、私钥或签名配置。
- 本次计划整理不修改业务源码，不运行 Gradle、构建或设备测试。文档静态检查结果由本次任务最终回复说明。

证据分级必须延续：

| 标记 | 含义 | 可以据此做什么 |
| --- | --- | --- |
| 当前源码确认 | 本仓库中已追踪到真实实现和调用链 | 确定修改点及回归范围 |
| 参考源码确认／站点待验证 | 固定提交中有实现，但当前站点没有取得有效样本 | 作为协议候选，建立解析设计和合成测试；不可宣称线上可用 |
| 站点验证 | 取得有效页面、表单或响应并核对 | 在 `NETWORK/WENKU8.md` 记录后实施相关协议 |
| 未确认 | 既无直接协议证据，也无有效页面样本 | 不编造参数、selector、Cookie 名单或成功标志 |

## 3. 安全读取参考代码

参考项目的 `Wenku8Api.kt` 包含内置账户 Cookie；`Wenku8WebsiteDataSource.kt` 的注释中也存在其他站点 Cookie。它们不能视为可移植的公共配置。

先前审查时曾误将这些内容带入工具输出；临时审查副本已脱敏，仓库没有写入这些值。本文件不记录任何凭据值。新 session 必须避免重复此问题：

- 不对上述文件直接执行完整 `cat`、不输出含账户映射或 Cookie 请求头的片段。
- 如需再次读取，先在内存中过滤账户 Cookie 映射和注释中的 Cookie 字面量，再输出或保存脱敏内容。
- 不向工具输出完整原网页、Cookie、响应请求头或登录表单字段值；表单检查只记录 action、method、字段名称、类型和已脱敏结构。
- 不运行参考项目请求方法或连通性探测，它们会注入内置 Cookie。
- 不依赖原 session 的临时下载目录；它不是可信、持久的实施输入。
- 测试会话仅使用明确的虚构值，网页样本需移除个人信息及凭据；日志不记录 Cookie、密码、认证请求体或令牌。

## 4. 参考项目的实际实现

参考源码路径以该项目根目录为起点：

| 文件 | 职责 | 采用方式 |
| --- | --- | --- |
| [Wenku8Api.kt][up-api] | Ktor 请求、Cookie 注入、编码、并发、书卡解析、探索入口 | 参考编码和必要解析；排除凭据、日志、在线探测及原框架 |
| [Wenku8SearchProvider.kt][up-search] | 注册书名和作者搜索类型 | 参考协议类型，不移植 SearchProvider 框架 |
| [BookRequestDispatcher.kt][up-dispatch] | 将请求委派到列表中的网站数据源 | 当前只有一个网站实现，无须移植轮换包装 |
| [Wenku8BookDataSource.kt][up-contract] | 原项目书本请求接口 | 参考职责边界，使用当前模型及仓库边界 |
| [Wenku8WebsiteDataSource.kt][up-website] | 详情、分卷目录、正文和搜索 HTML 解析 | 最主要的协议参考，允许移植经审查的解析片段 |
| [utils/network/Jsoup.kt][up-jsoup-helper] | XPath 便捷方法及其他旧网络工具 | 当前 Jsoup 已可完成查询，无须引入旧网络和代理工具 |
| `api/.../content/builder/`、`api/.../util/Cache.kt` | 原项目内容 Builder 和缓存 | 不移植，使用现有 Component、ChapterBody 和缓存 |

### 4.1 网络及编码

- 原项目使用 Ktor 的 OkHttp 引擎，配置压缩、浏览器 User-Agent、请求头、超时及重试。
- 共用请求函数的并发限制为 3；源码另外声明了数据源 permits，不能将各层限制误当成本站正式速率规范。
- 主机列表为 `.cc`、`.net`、`.com`，默认 `.cc`。源码没有证明这些镜像在本项目环境中等价或具备可跨主机共享的会话。
- 原始响应字节按 `GB18030` 解码。注释说明页面可能声明 GBK，但实际包含 GB18030 四字节字符；它是参考项目采用该解码的理由，当前站点仍待取得样本核对。
- 源码使用 `Logging` 的 `HEADERS` 级别。本项目不得照搬可能暴露 Cookie 的日志设置。
- 原项目定期访问 `/login.php` 检查连通性，这不是登录认证的实现，也不是登录态有效性证明。

### 4.2 登录及 Cookie

- 未发现 Wenku 适配器中完整的用户密码提交、登录成功识别、会话持久化、换号和退出流程。
- `ConstantCookiesStorage` 注入的是内置账户信息，不是用户自己登录产生的正常 Cookie 管理方案。
- 不移植 Cookie 映射、固定账户、密码摘要、会话值或其他站点的搜索 Cookie。
- 登录 POST endpoint、字段、编码、验证码、成功／失败标记和退出协议全部列为待验证。

### 4.3 搜索

参考源码使用：

```text
GET /modules/article/search.php
searchtype = articlename | author
searchkey = 关键词按 GB2312 编码后的表单风格百分号编码
page = 页码
```

- 结果页通过书卡 DOM 读取作品；总页数读取 `#pagelink em` 文本的页数部分。
- 单本命中通过详情页面上的“小说目录”链接识别，返回单本结果。
- 遇“两次搜索的间隔时间不得少于 5 秒”提示，参考代码等待 5 秒后重试；各页间也等待 5 秒。
- 参考代码流式遍历全部页；本项目改为当前分页 UI 所需的按页加载，不自动拉完整结果集。
- 不照搬 `href.split('/').getOrNull(3)` 提取 ID；应使用解析后的完整 URL 和路径规则，兼容合法相对链接。
- GB2312 编码和 GB18030 响应解码是两件事，不得混淆；编码一次即可，避免二次编码百分号。
- 零结果、页数缺失、单本命中和限流四种页面需分别识别。没有零结果证据时，不能把缺 selector 一律视为空结果。

### 4.4 详情

路径：`/book/{bookId}.htm`。

参考代码通过 `#content` 下的位置 XPath 提取：标题／括号副标题、封面、作者、简介、标签、文库分类、字数、最后更新、完结状态。

主要候选位置：

- 标题：`#content` 第一层 div 的首个 table 内第一行嵌套 table 中的 `span > b`。
- 元数据：该 table 第二行各 td，分别为文库、作者、状态、更新、长度。
- 封面：第二个 table 的第一列 img。
- 标签／简介：第二个 table 第二列的固定 span 位置。

这些位置仅有参考源码证据。实现前核对当前真实结构，不能仅凭字段名称发明额外 selector。标题先保留原文，不必照搬可能截断正常括号内容的正则。

### 4.5 目录

路径：`/novel/{bookId 整数除以 1000}/{bookId}/index.htm`。

- 遍历 `/html/body/table/tbody/tr`。
- 行的首个 `td` 的 class 为 `vcss` 时，为卷头；读取 `vid` 和文本。
- 其他行遍历 `td > a`；链接是章节文件，按原 DOM 顺序加入当前卷。
- 章节 ID 的位置和路径关系需要校验，不能直接对任意 href 按点号截断。
- 本项目将卷映射为 `ChapterListItem`，章节映射为 `ChapterItem`／`Chapter`，同时生成 `orderedChapters`。

### 4.6 正文

路径：`/novel/{group}/{bookId}/{chapterId}.htm`。

- 标题为 `#title`，正文为 `#content`。
- 参考代码遍历正文直接子节点：文本节点生成段落，`div.divimage` 中的 img 生成图片。
- 前后章由 `#foottext` 的第 3／4 个 a 获取，排除 `index.htm` 或包含 article 的链接。
- 参考代码检测“因版权问题”，返回受限错误；不得尝试规避受限页面。
- 本项目优先沿用共享 `analyseComponents` 和结构化正文，不移植原项目 JSON Builder 或其空行算法。
- 翻章以作品完整目录为准；正文的前后链接仅作合法、同源辅助，不能跳到目录、登录或其他作品。

## 5. 当前项目的架构与真实修改点

源码根目录：`app/src/main/java/com/breakyuna/esjzone/`。下表路径均相对此目录。

| 文件／位置 | 当前确认的行为 | 必须处理的内容 |
| --- | --- | --- |
| `network/features/GetChapterDetail.kt` | 已根据 ChapterSource 分流 ESJ 和 Wenku；先读下载文件 | 保留该统一章节入口，不重建阅读链路 |
| `network/external/WenkuChapterClient.kt` | 独立 CookieJar／OkHttp，DNS 和主机限制，缓存及前台浏览器获取 | 扩展非章节页面请求，保持独立会话与限制 |
| `network/external/WenkuCookieJar.kt` | 加密独立存储 `wenku8_cookies`；浏览器导入时重建固定七天 Cookie | 登录会话不能依赖统一七天期限；补正常清理和同步行为 |
| `network/external/WenkuWebViewSession.kt` | fetch 和导航仅允许 Wenku 章节；成功检测依赖 `#content` | 扩展页面类型和对应验证，不把目录误当正文 |
| `network/external/ExternalChapterHtml.kt` | 原始字节依次检查 HTTP／meta charset，默认 GBK；只保留本站图片 | Wenku 专用 GB18030 处理、图片主机及正文验证 |
| `network/PageResponsePolicy.kt` | `EXTERNAL_CHAPTER` 校验正文，其他类型主要为 ESJ 结构 | Wenku 非章节单独验证，不放松 ESJ 的页面策略 |
| `network/features/GetNovelDetail.kt` | ESJ selector、详情缓存和评论读取 | 在入口按作品 URL 分流，Wenku 不进入 ESJ 解析／会话 |
| `novellibrary/novel/Novel.kt` | `DetailedNovel.id()` 只从 forumUrl 提取 ESJ ID | 返回 Wenku 带来源身份，不伪造 forumUrl |
| `novellibrary/novel/Chapter.kt` | 已有 ChapterSource；`Chapter.novelId()` 只识别 ESJ 路径 | 明确作品身份与章节来源不同，避免改变已有外链归属 |
| `novellibrary/novel/NovelChapterList.kt` | 已支持分卷结构和完整章节顺序 | 复用，Wenku 目录由专用 parser 创建 |
| `novellibrary/component/ChapterItemParser.kt` | ESJ 目录 DOM 解析依赖 forum 链接或 data-title | 不直接拿它解析 Wenku 表格，不为了 Wenku 放松 ESJ selector |
| `domain/repository/Repositories.kt` | 已有 Novel／Reader／Search／Bookshelf 等接口，但部分参数仍为 ESJ Authorization | 增量接入，不重复创建整套 repository 架构 |
| `data/repository/LegacyRepositoryAdapters.kt` | 调用 EsjzoneClient 和原书架 repository | 确保新分流覆盖这些入口；不全面迁移无关页面 |
| `app/AppContainer.kt`、`app/PresentationAccess.kt` | 仓库实例与旧 UI 边界；已有 Wenku 图片 loader | 注册必要 Wenku 访问，不重建容器或依赖注入 |
| `database/BookshelfRepository.kt` | scope 绑定 ESJ 账户；本地意图会调度 ESJ 远端同步；元数据通过 ESJ 详情补充 | Wenku 使用独立本地 scope，收藏／删除／元数据单独分流 |
| `database/dao/LocalReadingActivityDao.kt` | 按 novel_id 或 novel_url 查询、覆盖和删除 | 独立 Wenku ID 必须带来源，防止数字 ID 相同的作品互相覆盖 |
| `database/ReadingStatistics.kt` | 统计身份优先采用 novelId | 同样使用 Wenku 带来源身份，保留 ESJ 统计键 |
| `ui/page/NovelPage.kt` | 创建 CommentPageModel，订阅 ESJ 书架 scope，调用收藏元数据种子 | Wenku 隐藏评论／论坛；不触发相关请求；收藏订阅也要分源 |
| `ui/page/NovelPageModel.kt`、`NovelDetailLoader.kt` | 从统一 getNovelDetail 获取详情，支持下载回退 | 统一分流后复用，不另外复制详情 ViewModel |
| `ui/page/ChapterPageModel.kt: ensureChapterOrder()` | 没有传入目录时拼 `${EsjzoneUrls.Base}/detail/$novelId.html` | 根据所属作品身份／URL获取目录，不把 Wenku 身份拼成 ESJ URL |
| `ui/page/ChapterPage.kt` | 已按章节来源限制评论，但有多处通过 ID 拼 ESJ detail 的回退 | 逐处检查来源，使用统一身份到 URL helper |
| `ui/navigation/AppNavigation.kt` | ReaderRoute.restore 将 ID 拼成 ESJ detail；readerRouteFromToken 按第一个冒号拆分 | 修复 Wenku 恢复及带冒号 ID 的 token 编解码，见第 6.2 节 |
| `ui/page/FavoritePage.kt`、`FavoritePageModel.kt` | 作品／分组 scope 和同步状态都是 ESJ；VM 构造时固定账户 | 来源切换及来源独立 VM／scope，禁用 Wenku 云同步流程 |
| `ui/page/SearchPage.kt`、`ui/tab/SearchTab.kt`、`network/features/Search.kt` | ESJ 专用类型／排序参数和搜索历史 | 保留 ESJ；Wenku 使用独立搜索入口和类型 |
| `ui/page/HistoryPage.kt` | 本地历史打开阅读器，缺封面时请求 getNovelDetail | 通过统一分流回补文库封面，不把文库本地历史上传到 ESJ |
| `offline/NovelDownloadWorker.kt` | 开始下载时恢复 ESJ Authorization，重新获取 ESJ 详情；已保存入队 domain | 重取详情按源分流，Wenku 不恢复 ESJ 会话；冻结任务请求基址 |
| `offline/NovelDownloadStore.kt` | 已下载 Wenku 正文；图片 client 按章节 baseUrl 判断；离线详情重建为扁平目录 | 非章节封面选择也要分源；按需补最小卷信息以保留离线目录 |
| `ui/designsystem/AppImage.kt` | 正文图片已有本站 Wenku loader；普通封面不因此自动使用该通道 | 封面和插图按已验证来源选择客户端，避免 Cookie 发送给其他主机 |
| `backup/LocalBackup.kt`、`backup/AutoBackup.kt` | 历史和下载按现有白名单备份；分组只处理传入 scope | 保持凭据排除；文库分组使用正确 scope；不要无关重做备份格式 |

### 5.1 需要全局追踪的详情请求入口

基线 `rg getNovelDetail` 找到了以下实际调用者，不能只改 NovelPage：

- `NovelPageModel.kt`
- `NovelDetailLoader.kt`
- `ChapterPageModel.kt`
- `HistoryPage.kt`
- `BookshelfRepository.kt`
- `NovelDownloadWorker.kt`
- `LegacyRepositoryAdapters.kt`

在 `GetNovelDetail.kt` 的统一入口分流可覆盖主要调用者；调用者自行构造的 ESJ URL仍需改正，不能仅修改解析器。

## 6. 已指定的集成决策

以下为本计划推荐的首期实现选择，不是当前已有功能或本站接口事实。

### 6.1 范围与目录

首期交付：文库入口、书名／作者搜索、详情、完整分卷目录、正文／插图、独立用户会话入口、本地书架／分组、阅读历史、下载及现有导出。

初期仅接入当前已经允许的 `https://www.wenku8.net`。URL 统一定义在 Wenku URL builder 中，不把 Wenku 主机写入 ESJ domain 设置或 ESJ 镜像名单；不散落域名常量。

建议新增最少的源专用文件：

```text
network/wenku8/Wenku8Urls.kt        URL、作品来源、站内页面路径及身份辅助函数
network/wenku8/Wenku8Parsers.kt     书卡、搜索结果、详情和目录纯解析
network/wenku8/Wenku8Features.kt    请求、分页和映射当前模型的薄适配
ui/page/Wenku8Page.kt              文库入口／搜索及其 ViewModel
ui/page/Wenku8LoginPage.kt         用户操作的登录 WebView（协议验证后）
NETWORK/WENKU8.md                  实际证据、接口、DOM 和待验证项
```

文件名称可因实际代码长度拆分，但不新增插件模块、通用爬虫框架、Ktor、原项目 DI、Result 库、自动代理、数据源轮换或全局数据源大重构。

沿用 `network/external/` 内的已有 Wenku 会话及客户端；扩展其职责，不同时维护两套互不共享 Cookie 的 Wenku 客户端。需要改类名时先检查全部引用，改名本身不是目标。

### 6.2 身份、URL 和导航

- 独立 Wenku 作品本地 ID：`wenku8:{数字 bookId}`。
- 作品规范 URL：`https://www.wenku8.net/book/{bookId}.htm`。
- 章节 URL：目录中解析和校验后的绝对 HTTPS URL。
- ESJ 原有 ID、URL、历史和文件键保持现有规则。
- ESJ 作品中 Wenku 外链章节仍归原 ESJ 作品，以传入的 novelId／novelUrl 和 ESJ 目录为准；不能仅根据章节主机重写所属作品。
- 继续复用现有 canonicalPageKey：它已经对站外地址保留主机及查询信息，不必大改全部缓存／下载键。
- 独立文库身份应同时进入书架 novelId、历史 novelId、统计 bookKey，以及相关比较逻辑；不新增 Room 列即可避免跨源数字冲突。
- 增加一个来源明确的“作品身份转详情 URL”辅助函数：合法 ESJ ID生成原路径；合法 Wenku 身份生成文库路径。网络层不接受带前缀的 ID 作为远端 bookId，发送请求前提取纯数字。

**导航 token 的冒号问题必须同时修复。**当前 `ChapterPage.key` 为 `ChapterPage:{novelId}:{chapterIdentity}`，readerRouteFromToken 在第一个冒号处分割。直接加入 `wenku8:数字` 会错误拆分身份。

最小方案：

1. ChapterPage.key 和 AppNavigation.readerToken 都对 novelId 使用已有 `encodeRouteTokenPart`。
2. readerRouteFromToken 拆分后对 novelId 使用 `decodeRouteTokenPart`；chapterIdentity 保持完整。
3. ESJ 数字 ID 编码后不变，保持现有 token 语义；updateReaderRoute 的 registry key必须与 ChapterPage.key一致。
4. ReaderRoute.restore 通过来源明确的 helper 生成小说 URL，不能再无条件 `/detail/$id.html`。
5. 新 Wenku 搜索／登录页面必须加入 LegacyRoute／token／restore对应分支，页面恢复不只依赖 live registry。含查询或搜索类型的 key需正确编解码。
6. 检查 `/detail/$id.html`、novelId 数值转换以及 novelId 分隔解析的其他使用点，修复本次来源扩展真实触及的回退。

### 6.3 传输、页面验证和编码

- 保留已有 OkHttp 连接、超时、取消、响应大小限制、主机／DNS 限制及 Cloudflare 处理原则。
- 在 Wenku 客户端／WebView 中引入所需页面类别：搜索、详情、目录、正文，以及验证后的用户登录页面。类别仅限 Wenku，不创建全项目万能网页抽象。
- 增加站内请求 URL 检查：HTTPS、准确 host、标准端口、无 URL 用户信息、已确认的路径及参数；不要为了目录支持放宽 ChapterSource 为任意本站路径。
- 允许的业务路径来源于第 4 节；登录 POST和重定向路径需待实际验证后加入。
- 搜索按搜索结果结构判断成功；详情按作品结构；目录按目录表格；正文按有效正文，不能共用一个 `#content` 成功判断。
- 允许合法同源业务重定向（例如搜索命中详情）时，同时记录并验证最终 URL；不能允许任意跨主机跳转携带会话。
- 沿用当前隐藏浏览器先尝试、必要时可见验证的流程；自动完成普通 JS挑战不等于自动识别或代解 CAPTCHA。
- Worker 不启动 WebView。前台可预取验证后的业务页及选中章节；遇盾／会话要求时提供恢复入口。
- GBK／GB2312 类页面声明以及缺少声明的 Wenku 响应，按经过样本核对的 GB18030 策略解码；明确 UTF-8等路径继续保留。
- OkHttp原始字节、浏览器同源 fetch的 TextDecoder及浏览器 DOM捕获需要结果一致。DOM捕获字符串已解码，不再次按 GB18030 转码。
- 登录页、限流页、挑战页、版权受限页和解析失败页面不得写入业务缓存。
- 日志仅记录来源、页面类别、状态码、耗时、失败分类，不记录完整 HTML、Cookie 或请求表单。

### 6.4 会话与缓存

- Wenku不借用 ESJ Authorization／ews 字段，不向 ESJ发送文库 Cookie，亦不把 ESJ会话同步到文库。
- Cookie存储复用加密的 `WenkuCookieJar`，不要另建未加密副本。
- `Set-Cookie` 走正常 CookieJar，保留真实 domain／path／secure／expiry；浏览器 `getCookie` 丢失这些属性，不能凭此虚构永久或统一七天有效期。
- 用户登录首选站点 WebView，用户在站点表单里输入；原生层不读取／保存密码，不注入脚本抓取输入。
- 在核对登录后页面或可信用户标志前，状态是未知，不能凭 HTTP200、HTTP403、cf_clearance或某个 Cookie的存在宣称已登录。
- 网络失败／挑战不清除会话；明确的过期证据才提示重新登录。
- 文库登录／退出／换号需要同时处理 CookieJar、本站 WebView Cookie和浏览器会话；不得对全局 CookieManager使用 removeAllCookies而清除其他站点会话。
- 未确认网站退出 endpoint时，不猜测远端登出；可明确实现“清除本地文库会话”，仅清理本站会话，并记录与远端登出的区别。
- 登录前后、退出或换号要切换缓存命名空间／会话代次，并阻止旧请求重新写入新作用域。复用当前类似 cacheEpoch 的机制，不复制凭据作为缓存键。
- 公开正文缓存已有 `wenku8|{canonical URL}`。是否共享匿名与登录内容需先验证访问差异；保守采用独立 Wenku会话作用域，不把账号内容当无条件公开缓存。
- 新搜索缓存键必须包含类型、完整关键词和页码，保留语义查询参数；无需照搬书卡的 hashCode缓存。
- 用户主动下载的文件是本地内容，不因退出会话或清除临时缓存而删除。

### 6.5 模型、解析与显示

- 搜索书卡映射 `CoveredNovelImpl`，详情映射 `DetailedNovel`，简介使用 `NovelDescription`，目录使用现有卷／章节模型。
- 不创建与 Novel／Chapter平行的持久化模型，只允许请求内部临时 DTO用于“列表／单本／空／限流”等真实结果分类。
- Wenku forumUrl为空，不伪造 ESJ论坛地址；sourceUrl为经过校验的原站地址。
- 缺少的 views／likes等字段按模型允许值传递，但 UI按来源隐藏不适用统计，不显示伪造的浏览或收藏数字。
- 文库分类可映射当前 type，作者／字数／标签／更新按实际页面字段映射。完结等当前模型没有的额外展示不是首期必要扩展，不为此重构全部模型。
- 正文沿用共享组件与 ChapterBody；不改变中文段落、真实空行、注音、图片、划线和阅读器交互语义。
- 不把网站错误文本拼成正文，不能把有正文容器但仅有受限通知的页面缓存为章节。
- 当前 parser会拒绝过短正文、限制图片主机；以真实短章／插图样本核对，不凭假想情况放开限制。
- 相对封面、目录、正文图片用当前页面 URL解析；实际 CDN主机需有证据后才加入允许名单。封面和正文图片客户端选择都要处理，Cookie不得发送给不相关主机。

### 6.6 书架、分组和本地历史

- 文库本地书架 scope固定为 `wenku8:local`，不随 ESJ账号／镜像或 Wenku账号切换；这是本地收藏，首期不做文库云端收藏同步。
- ESJ默认scope和同步状态机保持现有行为；给需要显式选择来源的方法增加有默认值的source参数或对应小分支。
- Wenku setFavorite：收藏直接写本地可见行，保留添加时间，syncState使用现有 SYNCED表示没有待同步意图；取消收藏直接删除本站 scope的行及成员关系，不创建墓碑或 PENDING_REMOVE。
- Wenku批量删除同样本地完成，不调用 ESJ removeBatch的远端意图流程。
- observeEntry、seedRemoteFavorite／元数据种子、补封面、最新章节、更新提示都要按来源分流。不能只在setFavorite加分支而继续订阅ESJ书架scope。
- ESJ startup sync、重试和旧scope认领不能扫描文库行；文库元数据刷新使用 Wenku详情入口。
- 书架页提供来源切换，默认ESJ。列表、分组、选择集合、滚动状态与同步提示按来源处理，不把ESJ同步失败提示显示在文库。
- FavoritePageModel需要来源明确的scope。现有 rememberAppViewModel无key参数；单纯 Compose key(source)不会自动隔离同一ViewModelStore中的同类VM。
- 最小修正可给 rememberAppViewModel补可选key，并同步传入viewModel与remember工厂key；旧调用默认不变。书架为每个来源使用稳定VM key，避免切源继续拿旧scope。
- 本地阅读历史沿用原表与统一页面，Wenku身份带来源。来源不同的同数字作品不能覆盖、匹配、合并或删除对方记录。
- 不为首期文库搜索接入ESJ SearchHistory表：文库搜索输入采用页面状态恢复，首期不新增持久化搜索历史，避免为非核心功能增加Room迁移。
- 分组备份继续沿用显式scope，文库备份／恢复传文库scope。若现有备份入口只知道ESJscope，增加必要来源选择，默认原行为；不把全部账户数据混入备份。
- 自动备份的既定scope含义保持明确；不得静默改为备份所有账户。必要调整仅限文库独立分组支持，且更新对应说明。

### 6.7 下载、离线详情和导出

- 复用WorkManager、NovelDownloadStore、结构化章节文件、增量清单、图片缓存和现有Exporter。
- 入队数据保存来源及本站请求基址；URL可确定来源，但Worker仍需验证输入一致。任务不能改 SettingsRepository.domain。
- Wenku任务不调用ESJ restoreAuthorization尝试恢复本站登录；使用文库自己的客户端会话。
- Worker重取详情通过统一来源入口；其非章节请求也禁止启动WebView。
- 保持原有选章、并发限制、重启后清单进度恢复、部分成功落盘及删除时写入保护。
- 当前正文下载已限制Wenku并发为最多2；不因为参考代码permits而提升下载并发。
- 前台文库详情遇验证要求时，登录／验证后预取详情和目录，继续选章预取，再入队。验证提示要能处理非章节页，不把所有页面包装成伪造Chapter后写正文缓存。
- 后台遇人工验证要求保留已完成文件并通知返回对应文库详情；通知冷启动与来源路由需核对。
- 读离线详情时仍能识别为文库，文库论坛／评论不会出现；恢复完整目录和本地阅读位置。
- 为离线保留卷分组，可在清单最小新增可选卷信息，例如每章的 volumeId／volumeTitle，并在重建时生成ChapterListItem；默认缺字段按现有扁平目录处理，不重置或批量转换已有ESJ下载。
- 离线卷信息只用于本次目录需求，不设计另一个复杂目录存储系统。若采用不同的同等简洁方案，说明选择并验证章节顺序。
- 导出使用现有TXT／EPUB能力及正文模型；不移植原项目epub模块。
- 不改变现有下载文件、导出文件、书架记录的独立生命周期；删除收藏不删除下载。

### 6.8 UI和资源

- 在 HomeTab既有入口区域增加“轻小说文库”，打开Wenku8Page；保留四个底部Tab及原主页行为。
- 文库搜索提供书名／作者两个类型、搜索结果、按页加载、加载／错误／空状态；复用现有搜索栏、书卡、封面和状态组件。
- 文库页面提供紧凑的登录／会话入口，不能显示未经确认的“已登录”。没有证据时展示可操作的会话管理入口。
- 搜索结果打开现有NovelPage；详情的简介、作者、标签、目录、继续阅读、收藏、下载和导出布局复用。
- 文库详情不创建／加载ESJ CommentPageModel，不显示论坛、评论、ESJ云端收藏状态及其他ESJ专用操作；只隐藏UI不足以阻止后台请求。
- ChapterPage当前已按章节来源禁用文库评论，保留该限制；其他ESJ专用动作也必须按来源检查。
- 用户可见文本放入现有资源，至少检查values、values-en及values-zh-rCN的资源对称性，采用当前项目语言约定。
- 不为明确控件添加说明小字、无关设置或登录协议术语。

## 7. 实施阶段与退出条件

按以下顺序实施。全部代码、资源、文档及必要测试修改完成后，才统一运行Gradle检查；中途可以做轻量静态检查。

### 阶段A：基线复核、许可证和网站证据

- 读取AGENTS及本计划；执行git status，保留不属于本任务的工作区变更。
- 复核相对基线新增的代码，尤其客户端／导航／书架，不直接依据旧行号编辑。
- 阅读NETWORK六份入口文档，新增NETWORK/WENKU8.md记录本计划的源码证据和待验证表。
- 固定使用参考SHA；如主动采用新版本，先记录变更及重新核对许可证，不静默换参考版本。
- 取得有效的匿名／用户操作后的搜索、详情、目录、正文与插图样本。页面核验优先通过当前前台浏览器流程或可用浏览工具，低频只读，不借用内置凭据。
- 核对实际登录表单和登录后标志；账号输入与人工验证码交由用户完成，不读取本地凭据。
- 完成移植文件／片段清单及许可证方案。

退出条件：核心DOM／接口证据有记录；未取得的项仍标为待验证。403不能证明协议已验证，也不能成为采用参考账户Cookie的理由。

若站点仍无法获得有效页面，可继续不依赖站点的身份、导航、来源隔离、解析测试和共享能力接入；相关parser按参考证据标注，不宣称适配完成。必需协议缺证据时准确报告具体缺项，不填猜测值或假登录流程。

### 阶段B：URL、身份和导航

- 实现Wenku URL／来源／身份helper和当前模型的来源识别。
- 修改独立Wenku的DetailedNovel.id、书架novelId和历史／统计使用点。
- 修复ChapterPage／readerToken／readerRouteFromToken对带来源ID的编解码。
- 修复ReaderRoute.restore、ensureChapterOrder和ChapterPage内实际相关ESJ URL回退。
- 建立必要结果测试，包含ESJ作品持有Wenku外链的归属保留。

退出条件：同数字跨源身份不同；文库阅读路由能往返及恢复；ESJ路由语义不变。

### 阶段C：会话、传输和页面校验

- 扩展已有独立Wenku客户端及WebView的页面类型，处理合法同源业务重定向。
- 实现一致的Wenku解码策略、分类错误和成功页面校验。
- 通过已验证页面接入用户WebView登录／本地会话清理，补会话同步及缓存作用域。
- 独立处理页面缓存；保证后台不启动WebView。

退出条件：各类业务页能区分挑战／登录／限流／受限／解析失败，不混入缓存；两站Cookie和设置独立。

### 阶段D：搜索、详情和目录

- 实现书名／作者搜索分页及列表／单本／空／限流结果分支。
- 解析详情和分卷目录，映射现有模型。
- 统一getNovelDetail入口分流，覆盖第5.1节所有调用者及其URL构造。
- 正文继续走已有getChapterDetail，确认GB18030及插图处理。

退出条件：搜索→详情→分卷目录→正文完整，所有URL在网络层校验；单本命中和失败状态正确。

### 阶段E：本地书架、历史、下载及备份

- 接入独立文库scope的收藏、订阅、元数据、删除、分组。
- 给书架VM按源稳定key；ESJ同步不触及文库。
- 实现Worker来源分流及前台验证后的预取／续下载，图片和封面使用正确客户端。
- 完成离线详情重建、卷信息及既有导出链路。
- 修正实际受影响的分组备份scope，凭据仍不进入任何备份。

退出条件：未登录ESJ也能使用文库本地功能；ESJ换站／换号不改变文库数据；离线及重启恢复正确。

### 阶段F：UI、资源、归属文件及统一验证

- 接入主页入口、独立文库搜索／会话页、现有详情和书架来源切换。
- 补Navigation恢复和按来源功能可用性；核对暗色主题、返回、滚动和状态切换。
- 完成双语／已有语言资源及许可证归属展示。
- 更新NETWORK/WENKU8.md、受影响NETWORK入口及必要README／备份说明，准确记录支持范围。
- 执行第9节检查，按第10节验收；填写第13节状态表。

退出条件：规定检查通过，未完成的站点／设备验收有明确记录。源码合成测试通过不能替代真实登录／页面验证。

## 8. 待验证清单

| 项目 | 已有证据 | 下一步证据 | 未验证时的处理 |
| --- | --- | --- | --- |
| 登录入口／表单 | 源码访问login.php用于连通性；当前GET403 | 有效表单action、method、字段名称／类型、编码、验证码 | 不实现猜测的原生POST |
| 登录成功／过期 | 无完整协议 | 登录后可确认用户标志、失败及过期页面样本 | 不把Cookie或200视为成功，保留未知状态 |
| 退出和切换账号 | 无可信远端协议 | 本站退出入口／实际表单，以及本站Cookie作用域 | 仅明确实现本地会话清理，不调用猜测endpoint |
| 搜索 | 固定源码GET参数、类型、GB2312 | 书名／作者、分页、单本、零结果、限流及特殊字符样本 | 候选parser标待验证，不伪造空结果 |
| 详情selector | 位置XPath | 有效详情DOM，含版权受限作品差异 | 不发明多套兜底selector |
| 目录结构 | vcss／vid、章节链接及整数分组公式 | 有效分卷目录、不同bookGroup及合法相对链接 | 保持候选规则和失败分类 |
| 正文／GB18030 | 当前已有正文链路；上游新版本采用GB18030 | 中文、扩展字符、空行及插图正文 | 不宣称线上解码／排版已经确认 |
| 封面／插图主机 | 当前仅允许www.wenku8.net | 实际image URL、HTTPS、Referer、重定向 | 不凭猜测开放CDN域名或发送Cookie |
| 登录访问差异 | 参考请求依赖内置Cookie，匿名403 | 相同作品在匿名／用户会话下的有效响应 | 保守隔离会话缓存，不宣称支持匿名全站 |
| .cc／.com镜像 | 参考host列表 | 有效DOM、重定向、会话和内容一致性 | 首期不启用 |
| 文库云书架 | 无可信协议 | 收藏读取／写入接口和真实行为 | 首期仅本地书架，不伪造同步 |
| 设备WebView与Cookie持久化 | 源码设计及既有实现 | 实机或CI设备验证 | 与JVM结果分开报告 |

## 9. 必要测试和验证命令

### 9.1 有价值的测试范围

沿用现有测试风格，只针对本次行为及真实风险，不新增重复实现细节的测试：

- URL／身份：合法详情／目录／正文，非法主机或跨源重定向，bookGroup计算，ESJ与Wenku同数字ID不同身份。
- 导航：`wenku8:数字`身份token往返，完整HTTPS章节URL不被截断，进程重建时恢复Wenku详情URL，ESJ仍按原路恢复。
- 解码：GBK声明下的GB18030四字节样本、默认编码、明确UTF-8；浏览器与原始字节结果一致。
- 搜索：书名／作者参数编码一次、列表分页、单本、合法空、限流后的可取消有界重试，不无限拉取全部页。
- 详情／目录：核心字段、多个卷、跨行顺序、相对链接、版权受限与缺失容器；不依赖真实账户样本。
- 正文：共享parser保持必要段落及图片，挑战／登录／受限文本不缓存，已有Wenku章节测试不退化。
- 书架／历史：Wenku收藏／删除不调用ESJ远端；scope与分组隔离；同数字历史不覆盖；ESJ外链章节仍归ESJ作品。
- 下载：入队基址不改前台domain、后台不启动WebView、部分成功恢复、离线文库详情及卷顺序、删除收藏不删除文件。
- 会话：模拟换号／清理后的旧响应不污染新缓存；Cookie域隔离。Android实际持久化另做设备测试，不能称JVM已验证。

已有可参考的测试：`WenkuChapterTest.kt`、`ui/page/WenkuVerificationPolicyTest.kt`、`NovelDownloadContractTest.kt`、`StructuredChapterCacheTest.kt`、`ReaderChapterCatalogTest.kt`、`BookshelfSyncRulesTest.kt`、`PersistentCookieJarIsolationTest.kt`。

合成样本只能证明解析器在给定结构下的行为；所有真实页面样本必须先脱敏。

### 9.2 运行时机和命令

文档／小改动做静态检查。本任务正式实现涉及网络、书架、导航和下载核心流程，全部完成后统一执行：

```bash
python3 tools/qa/verify_static_contracts.py
git diff --check
./gradlew testDebugUnitTest lintDebug --build-cache
```

说明：

- 新增文件未跟踪时，`git diff --check`和现有脚本未必扫描它们；要显式检查新文档／代码、资源、链接和凭据，不把默认脚本的扫描边界误报为覆盖全部文件。
- 当前静态脚本会核对values、values-en和values-zh-rCN的资源名称，对新增可见文字同步维护。
- 修改期间不反复运行Gradle；失败后集中修复相关问题，再复跑受影响检查；通过且没有新变化不重复运行。
- 本地不要求assembleDebug／assembleRelease；Release、R8、资源压缩、签名和Baseline Profile由CI检查。
- 本地Termux没有模拟器；设备验证另行执行，不将编译或JVM测试表述为设备测试。
- 长任务优先由进程／CLI持续等待，遵守AGENTS与当前工具可用的等待上限，避免高频轮询。
- 若用户要求推送和监控，使用`gh run watch --exit-status --interval 30`等待最终结果；CI完成仍不等于实机验收。
- 不读取gradle.properties、local.properties、签名密钥库或其他敏感本地配置内容。现有构建可透明使用它们。

## 10. 核心验收场景

| 场景 | 必须达到的结果 |
| --- | --- |
| 从主页打开文库入口 | 不修改ESJ站点设置；保留四个底部Tab和原ESJ搜索 |
| 书名／作者搜索 | 结果和分页正确，单本命中可进入详情，空／限流／网络失败区分 |
| 文库详情及目录 | 正确作者／简介／封面／标签／更新与分卷顺序；没有ESJ论坛评论请求 |
| 阅读正文和插图 | 现有分页／滚动／前后章／目录／书签／划线可用，GB18030不乱码 |
| ESJ目录中的Wenku章节 | 保留ESJ作品身份和ESJ目录顺序，现有跨源翻章不改变 |
| 数字ID相同的两站作品 | 书架、历史、统计、下载和路由互不覆盖 |
| 切换ESJ镜像或账号 | 文库会话和本地书架不改变；任务继续使用入队时基址 |
| 文库登录／清理／换号 | 只影响文库，敏感值不进入日志／备份，新旧会话缓存不混合 |
| 未登录ESJ使用文库 | 不被ESJ认证流程阻止文库本地功能；不伪造文库登录状态 |
| 退出并恢复页面或进程重建 | 文库阅读器恢复到正确作品、章节、位置；不请求ESJ detail |
| 收藏、分组和批量删除 | 文库在本地完成，无ESJ云端同步；切源无旧选择／旧scope操作 |
| 选章下载、中断并重启 | 已完成文件保留，进度恢复，后台不启动WebView，通知回到正确作品 |
| 离线打开及导出 | 详情、卷顺序、正文、已下载图片及阅读位置正确，复用现有导出 |
| 清缓存／删收藏 | 不删除用户下载；凭据不会随普通备份导出 |
| ESJ回归 | 原搜索、详情、登录、评论、书架同步、下载、导航恢复仍正常 |

设备检查以核心链路为准，覆盖明暗主题、返回／页面恢复、阅读模式、会话及必要触摸操作，不为了理论边界无限扩展测试。

## 11. 许可证核查与移植归属

### 11.1 已核查范围

- 参考固定提交的根[LICENSE][up-license]是Apache-2.0。
- [README版权说明][up-readme]列出2024年的NightFish和yukonisen版权声明；实施时保留其必要原文和联系方式，不凭提交历史创造新的版权声明。
- 已查看第4节的Wenku核心文件、Jsoup helper、原内容Builder／Cache及相关构建依赖；所检查文件未见另行覆盖它们的许可证头。
- 固定提交的文件树未发现附属LICENSE或NOTICE文件；该核查结论不能推及未检查的外部依赖。
- 当前仓库根[LICENSE](../LICENSE)为GPL-3.0，保持不变。Apache-2.0与GPLv3可组合，仍需满足Apache分发、归属及修改标记要求。[官方兼容说明][apache-gpl]。

| 实际相关依赖 | 核对版本 | 许可证证据 | 本次选择 |
| --- | --- | --- | --- |
| Ktor | 参考3.6.0 | [该版本LICENSE][ktor-license]：Apache-2.0 | 不引入 |
| kotlin-result／coroutines | 参考2.3.1 | [该版本LICENSE][result-license]：ISC | 不引入，改用当前错误／协程方式 |
| OkHttp | 参考和当前5.4.0 | [该版本LICENSE][okhttp-license]：Apache-2.0 | 复用当前依赖和归属文件 |
| Jsoup | 参考1.22.2，当前1.23.2 | [参考LICENSE][jsoup-up-license]、[当前LICENSE][jsoup-license]：MIT | 使用当前版本及已有归属文件 |
| 原项目自有接口／Builder／Cache | 参考固定SHA | 已检查文件及根Apache-2.0 | 不移植这套架构 |

若实现时新增或直接复制此表之外的工具、依赖、资源，应补查那个实际文件及对应版本许可证；不因主项目Apache-2.0就默认外部内容同许可。

### 11.2 必须生成的归属材料

只要直接移植或改写了原项目代码片段，就做到：

1. 保留适用的版权／归属声明；文件中注明源项目、原路径、固定SHA及链接。
2. 醒目标记本项目修改，例如OkHttp替代Ktor、当前模型替代Builder、移除内置Cookie、来源独立、校验和解析适配。不要把实际衍生代码声明为完全原创。
3. 保留原代码适用的Apache许可说明；不要仅改成GPL头而丢弃原许可信息。
4. 增加`LICENSES/Apache-2.0.txt`保存完整许可，并增加`doc/WENKU8_THIRD_PARTY_SOURCES.md`列出实际移植的文件／片段和修改。未移植的上游模块不列为已移植。
5. 增加`app/src/main/assets/open_source_licenses/lightnovelreader_wenku8.txt`，包含标题、来源、适用版权、修改说明和Apache全文，使现有OpenSourceLicensesPage自动展示。
6. 若后来采用的代码发行物带NOTICE，保留与移植部分相关的NOTICE；当前固定提交没有发现NOTICE，不编造“上游NOTICE”。
7. 保留已有GPL和第三方归属文件；已有OkHttp／Jsoup归属不删除，不无关升级依赖。
8. 完成实际移植清单后重新核对引用及许可证链接。[Apache第4条要求][apache-license]。

## 12. 明确不纳入首期的内容

- 文库云端收藏／阅读历史同步，未确认的站点写接口。
- `.cc`／`.com`镜像轮换、跨镜像Cookie共享、代理池。
- 原项目插件架构、Ktor、Result框架、内容Builder、epub模块、完整探索／排行榜／标签筛选体系。
- 原生用户名／密码POST登录和自动CAPTCHA操作，除非后续有真实协议证据及明确任务需要。
- 全局数据源设置大改、四Tab重排、无关UI改版、阅读器交互／性能重构。
- 首期文库持久化搜索历史、为非核心额外字段进行Room大迁移。
- 重置旧ESJ数据、下载格式全面改写、凭据迁移或读取。

## 13. 交接状态与完成报告模板

新session推进时在本表记录真实状态和证据，不将“方案已写”勾成“功能完成”。

| 项目 | 当前状态 | 实施证据／后续 |
| --- | --- | --- |
| 基线复核 | HEAD 与计划 SHA 一致 | 原有工作区 doc 计划保留并更新状态 |
| 固定上游片段及许可证 | 已重新核对实际采用部分 | 解析器文件头、Apache 全文、归属资产和实际清单已生成；无新增依赖 |
| 站点页面 | 详情／目录／登录 GET 仍为 403 | 缺有效 DOM，不能报告线上可用 |
| NETWORK/WENKU8.md | 已创建并持续更新 | 记录候选协议、实现与验证边界 |
| URL／身份／导航 | 已接入 | 带来源 ID、token、恢复、书签、历史与统计回退 |
| 页面传输／编码／缓存 | 源码编译与 JVM／Lint 通过 | 来源和路径校验、GB18030、代际缓存、保留 GB2312 查询字节；真实传输待设备验收 |
| 用户会话页 | 已接入用户操作 WebView 与本地清理 | 不读取密码、不推断登录状态；持久化和完整 Cookie 同步待设备验证 |
| 搜索／详情／分卷目录 | 固定上游候选规则已接入 | 列表、单本、分页、五秒限流；零结果标志仍缺证据 |
| 正文／插图 | 复用旧链路，新增 JVM 用例通过 | 同作品辅助翻章、GB18030、版权通知拒绝；实际样本及插图仍待验证 |
| 本地书架／分组／历史 | 源码改造完成 | 固定 scope，无 ESJ 同步；分组清理与备份的设备测试源码已编译，Room／UI 未实际运行 |
| 下载／离线／导出 | 源码与 JVM 回归通过 | 部分离线目录／卷序、迟到卷写入、预取、恢复及封面分流；实际后台及导出仍待设备验收 |
| UI／资源／评论限制 | 已接入 | 主页和未登录入口、原书架复用、文库不创建 ESJ 评论模型；明暗主题／恢复仍待设备验证 |
| 静态检查 | 最终检查通过 | 静态契约、diff、XML、全部新增文件、资源、凭据模式及文档链接 |
| JVM／Lint | 全量 330 项通过，Lint 通过 | 最后单本结果修正后 6 项解析回归及 Lint 复查通过；关闭本次增量编译 |
| APK／CI／设备／真实站点 | APK／CI／设备未执行，实站仍缺有效 DOM | Instrumentation 仅源码编译；ADB 连接 0，不视为设备验收 |

完成报告至少写明：实际功能、变更范围、复用与移植内容、许可证处理、已运行检查、未验证协议／设备场景、残留阻碍。无法取得必需站点证据时，明确适配未完成的具体范围，不能因为编译通过而称完整适配已完成。

## 14. 参考链接

[up-api]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/app/src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/defaultplugin/wenku8/Wenku8Api.kt
[up-search]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/app/src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/defaultplugin/wenku8/Wenku8SearchProvider.kt
[up-dispatch]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/app/src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/defaultplugin/wenku8/book/BookRequestDispatcher.kt
[up-contract]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/app/src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/defaultplugin/wenku8/book/Wenku8BookDataSource.kt
[up-website]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/app/src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/defaultplugin/wenku8/book/Wenku8WebsiteDataSource.kt
[up-jsoup-helper]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/app/src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/utils/network/Jsoup.kt
[up-license]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/LICENSE
[up-readme]: https://github.com/dmzz-yyhyy/LightNovelReader/blob/8681711f020af991fe37ca89983cc4531fe94c53/README.md#license
[apache-gpl]: https://apache.org/licenses/GPL-compatibility.html
[apache-license]: https://www.apache.org/licenses/LICENSE-2.0
[ktor-license]: https://raw.githubusercontent.com/ktorio/ktor/3.6.0/LICENSE
[result-license]: https://raw.githubusercontent.com/michaelbull/kotlin-result/2.3.1/LICENSE
[okhttp-license]: https://raw.githubusercontent.com/square/okhttp/parent-5.4.0/LICENSE.txt
[jsoup-up-license]: https://raw.githubusercontent.com/jhy/jsoup/jsoup-1.22.2/LICENSE
[jsoup-license]: https://raw.githubusercontent.com/jhy/jsoup/jsoup-1.23.2/LICENSE

### 2026-10-09 实施与验证记录

已按第 7 节推进首轮代码接入，复用原模型、详情页、章节阅读器、书架与下载，没有引入上游网络和 DI 框架。实际移植清单见 [WENKU8_THIRD_PARTY_SOURCES.md](WENKU8_THIRD_PARTY_SOURCES.md)，证据边界见 [NETWORK/WENKU8.md](../NETWORK/WENKU8.md)。

首次 A/B 修改统一执行 `./gradlew testDebugUnitTest lintDebug --build-cache`，结果 `BUILD SUCCESSFUL in 11m 4s`；新增 `Wenku8IdentityTest` 四项、reader token 新增两项均通过。运行期间用户明确要求本次运行结束后不得再次运行测试，直到整个项目改造全部完成后统一运行。此后追加的 C–F 候选实现、GB18030、Cookie、分组备份、离线卷信息以及解析测试均没有重新编译或执行 Gradle；不得把首次结果当作最终树通过。

零结果搜索 DOM、真实登录／过期标志、远端登出和完整浏览器 Cookie 同步仍缺证据；当前候选接入不能报告完整适配完成。后续先补有效页面与必要协议分支，全部改造完成后再统一 JVM／Lint、CI 及设备验收。APK 构建仍交给获授权后的 CI，本次不提交或推送。

最终轻量检查：`python3 tools/qa/verify_static_contracts.py` 与 `git diff --check` 均通过；另外显式检查新增／修改源码的 package／import 顺序、空白、凭据模式、Wenku8 资源对称性与本地文档链接。上述静态检查不证明追加代码可编译或功能正确。此后没有再次启动测试、Lint 或 APK 构建。

### 2026-10-09 第二轮改进记录

- 搜索再次提交相同关键词／类型时直接刷新当前 ViewModel 并绕过页面缓存；失败重试同样刷新，从详情或会话页返回不重置已有页码。顶部类型和入口控件允许水平滚动。
- 本地会话清理等待已知 Cookie 删除回调和持久化后重新打开登录页；清理期间防止重复触发，退出页面后不再操作已销毁的 WebView。
- 浏览器 pair 按采集 URL 保存在进程内；根 URL 快照限同源，其他快照只适用于采集 URL，保留能匹配的原生 Cookie 属性。原生 Set-Cookie 不再被旧桥接值覆盖，手动导入根快照清除旧路径快照。
- 业务请求、重定向及缓存写入使用捕获的会话命名空间；导入／清理与缓存写入互斥，迟到响应不把 Cookie 或正文写入新会话。首次采集新路径不使详情／目录缓存失效，保留后台下载读取能力。
- 离线卷信息的写入和恢复仅用于独立文库作品，ESJ 保留原有恢复路径。新增四项 Cookie 作用域回归用例，暂不执行。

验证继续限于静态检查。用户规定的统一测试时机不变；浏览器未知 Cookie 的完整清理、真实登录／过期及零结果协议仍待验证，不能报告项目改造全部完成。

第二轮静态验证通过：静态契约脚本、`git diff --check`、13 个新增文件的空白／package 顺序检查、文库三套语言资源名一致性及本地文档链接检查。新增回归用例未运行，未执行 Gradle、Lint、APK 或设备检查。

### 2026-10-09 全部轮次集中收尾

本次连续完成剩余本地改造，不再逐轮交接：

- 补稳定来源 ViewModel key、进程恢复时的搜索页码及未登录 ESJ 的历史／下载／书签入口。
- 搜索、详情及下载使用实际业务 URL 打开站内验证；详情明确解析、版权、WebView 和存储失败，不伪造章节缓存。
- 业务页缓存保留 GB2312 查询字节，防止不同中文关键词经过 UTF-8 查询解码后合并；详情／目录最终 URL 必须属于目标作品，分页页码必须匹配。
- Cookie 桥接保留采集 URL；原生 Set-Cookie 按真实属性同步浏览器，手动导入强制切换代际。清理覆盖已知范围和已采集 URL 的可能路径／域，不清全局 Cookie。根快照缺失的旧会话不恢复。
- 收藏使用实际加载后的标题、作者及封面；文库元数据补充不启动 WebView。单本／批量移除同时删除文库分组成员，ESJ scope 保持独立。
- 下载前保存完整元数据／目录并预取详情和目录；部分文库下载离线恢复完整卷序，迟到章节写入保留新目录卷信息，后台可从已下载目录恢复。
- 正文拒绝版权限制通知；Android 云备份及设备迁移排除文库 Cookie 与 WebView 会话目录；补相关回归用例及 README 支持边界。

本地代码、资源、文档及回归用例已集中完成，准备统一 JVM／Lint。外部验收仍缺有效站点业务 DOM、合法零结果标志、真实登录／过期／退出证据及设备环境；不得因此实现猜测协议，也不得将源码改造完成称作实站适配完全验收。

### 最终本地验收记录

- 统一全量检查：`./gradlew testDebugUnitTest lintDebug --build-cache -Pkotlin.incremental=false`，`BUILD SUCCESSFUL in 6m 35s`，330 项 JVM 用例全部通过，0 失败／错误／跳过，Lint 无错误。
- 单本搜索复核修正：使用详情字段映射携带作者／封面／成人标记，防止单本命中绕过成人过滤；随后 `testDebugUnitTest --tests com.breakyuna.esjzone.Wenku8ParsersTest lintDebug compileDebugAndroidTestKotlin --build-cache -Pkotlin.incremental=false` 成功，6 项解析用例全部通过，耗时 6 分 26 秒。
- 设备用例结束后恢复 BookshelfRepository 原有依赖，避免同进程其他用例受影响；仅对这项测试清理修改执行 `./gradlew compileDebugAndroidTestKotlin lintDebug --build-cache -Pkotlin.incremental=false`，`BUILD SUCCESSFUL in 5m 35s`，不重复 JVM 测试。设备测试源码编译通过不代表设备用例已执行。
- 最终只剩外部验收：有效业务 DOM／合法零结果标志、真实登录／过期／远端退出证据，以及设备上的 Cookie 持久化、明暗主题、阅读、后台下载及导出。匿名请求仍为挑战型 403；未使用参考账户或本地凭据。ADB 可用设备为 0，Instrumentation 用例未执行，APK／CI 未执行。

全部可实现的本地源码改造已集中收尾，不再留待下一轮实施；候选站点规则仍不能称为已完成实站验收。改动保留在工作区，未提交或推送。
