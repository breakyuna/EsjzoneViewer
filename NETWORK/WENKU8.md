# Wenku8 集成证据与实现状态

### 隐藏浏览器最终地址修正（2026-10-10）

收到首页 `stage=browser-final-url, kind=HOME, actual=invalid-url` 报告：该错误位于
HTML 抓取后的最终地址校验，旧日志不能区分空地址、JavaScript null／undefined 或其他非法值，
不能据此推断 Cookie 失效或站点跳转目标。

导航加载现使用 WebView 自身的实际地址，并在抓取前后地址一致时返回 HTML 与地址；
页面内 fetch 则使用该次 Response.url，每次 fetch 开始时清空旧值。
两条路径分别携带结果，取消统一读取 `window.__esjWenkuFinalUrl||location.href`，
最终仍执行原有页面类型、主机和路径校验，不用请求地址代替缺失的实际地址。
诊断补充空地址、JavaScript null／undefined 和 about:blank 分类，其余非法值仍不输出原文。
此修正尚未经过设备 WebView 或实站验收，原始非法值的具体来源仍待设备日志确认。

### 请求诊断日志（2026-10-10）

文库日志补充页面类型、HTTP 状态、跳转序号、挑战识别结果，以及隐藏 WebView 的请求、导航拦截、fetch 回退和最终地址。URL 拒绝异常携带具体检查阶段与请求／实际地址，可在混淆堆栈之外定位拒绝原因。地址只保留主机、端口、路径和查询参数名，剔除用户信息、查询参数值和 fragment；不记录 Cookie、请求凭据或网页正文。此修改仅增强诊断，不改变 URL 允许规则。

更新日期：2026-10-09。实施计划：[WENKU8_INTEGRATION_PLAN.md](../doc/WENKU8_INTEGRATION_PLAN.md)。

## 证据与限制

实施基线为 `f1e3153a1b7b35063f3e3966921c93f71f23647c`，开始实施时 HEAD 与计划一致。参考项目固定为 LightNovelReader `8681711f020af991fe37ca89983cc4531fe94c53`，此次重新读取了筛选后的详情、目录、搜索及书卡解析片段，没有运行上游请求代码或使用内置账户。

2026-10-09 使用只读浏览工具重新访问以下页面，均返回 HTTP 403，没有取得业务 DOM：

- `https://www.wenku8.net/book/1.htm`
- `https://www.wenku8.net/novel/0/1/index.htm`
- `https://www.wenku8.net/login.php`

数字仅是探测路径，不证明作品存在。403 不足以确定挑战类型或登录状态。当前解析器是**参考源码确认／站点待验证**的候选实现，不能称为已验证的完整站点适配。零结果页标志、登录成功／过期、远端退出协议仍缺直接证据，不实现猜测分支。

## URL、身份与导航

- 仅接入准确的 `www.wenku8.net` HTTPS 主机、443 端口、无用户信息的地址；没有启用 `.cc`／`.com` 或外部 CDN。
- 独立文库作品本地身份为 `wenku8:{bookId}`，作品 URL 为 `/book/{bookId}.htm`，目录模板为 `/novel/{bookId / 1000}/{bookId}/index.htm`；网络 URL builder 使用纯数字 bookId。
- ESJ 数字身份保持现有规则。ESJ 目录内的 Wenku 外链仍采用显式传入的 ESJ 所属作品身份与完整目录。
- Reader token 只对作品身份百分号编码；拆分第一个分隔冒号后解码作品身份，章节完整 URL 不切分。页面 key 与 registry 共用生成函数；恢复按作品身份生成来源正确的详情 URL。
- 阅读器、书签、历史封面、下载封面的 URL 回退使用来源 helper。书签逻辑身份保留冒号，文件名独立转义；统计与书架身份提取区分同数字的跨源作品。

## 传输、编码、缓存与会话

复用 `network/external/` 的独立 Wenku CookieJar、OkHttp 与隐藏 WebView，没有再建 Cookie 或网络框架。

- 业务页按搜索／详情／目录／正文分别允许路径；原生重定向最多四次，逐跳验证同源业务 URL，搜索可跳详情。浏览器 fetch 不自动跟随重定向，导航同样受来源限制。
- 非章节页复用已有 PageCache，以 Wenku 会话代际、页面类别和保留原始查询编码的 URL 区分缓存；不经过通用 UTF-8 查询解码，避免 GB2312 中文关键词合并；详情／目录按候选结构验证，搜索只有可识别书卡或单本命中才缓存。限流、挑战、缺失 DOM、版权受限页不写业务缓存。
- 统一详情入口在 ESJ 缓存及请求之前分流，不传递 ESJ Authorization。后台 Worker 不恢复文库所不需要的 ESJ 会话，也不启动 WebView；可读取前台有效页面缓存后继续既有章节下载。
- 原始字节的 GBK／GB2312 声明与缺省编码使用 GB18030；明确 UTF-8 保持优先。浏览器 fetch 使用相同兼容策略；浏览器 DOM 字符串不再次转码。该策略来自固定上游，当前站点四字节样本仍待验证。
- 正文继续复用 `analyseComponents`；正文页的辅助前后链接只允许同一文库作品，正典翻章仍以传入目录为准。
- 正常 Set-Cookie 保留其属性并使用加密存储，同时按捕获的会话代际同步至 CookieManager；旧桥接值不覆盖新的原生 Cookie。浏览器 getCookie 缺失 domain／path／expiry 属性，导入 pair 保留已观察的 URL 作用范围：根 URL 快照限本站同源，非根快照只用于采集时的完整 URL，不伪造统一有效期。快照同步提交至现有 `wenku8_cookies` 加密存储，重建 CookieJar 后原生请求直接恢复；原生 Cookie 的刷新、删除和已知到期同时更新快照，清理会话同步删除快照。
- 会话页每次同源页面加载完成和退出时调用 flush 并保存快照；打开会话页和隐藏传输 WebView 前，等待已保存 Cookie 恢复至 CookieManager，不覆盖浏览器中已有的同名值。恢复仍受会话代际约束。会话 Cookie 可从加密快照跨进程恢复，服务端失效／撤销登录仍需用户重新登录；未观察到真实到期属性的桥接值不承诺永久有效。
- 手动会话导入／清理切换缓存代际；业务请求携带捕获的代际，迟到 Set-Cookie 不回写新会话。缓存写入使用请求或浏览器桥接时捕获的命名空间，并与手动导入／清理互斥。首次采集新路径不会单独切换代际，以保留详情／目录缓存供后台下载读取。清理覆盖已知 Cookie 作用域、根路径，以及已采集 URL 可能匹配的路径前缀和本站域；未采集路径仍不可枚举。未调用全局 removeAllCookies。
- 受限站点会话 WebView 由用户操作，原生层不读取表单输入、不提交密码、不自动处理 CAPTCHA、不推断已登录。清理按钮等待已知 Cookie 的删除回调与 flush 后重新打开登录页；清理期间禁用重复操作，页面退出后不再触碰已销毁的 WebView。它只表示本地操作，不调用未确认的远端登出。
- 搜索／详情验证取得 `cf_clearance` 后由既有策略返回，不要求重定向后的页面仍匹配原始路径或章节 DOM；章节验证继续优先采用可读正文，只有 clearance 时等待正文 20 秒再回退。
- 会话页 WebView 由 Navigation 3 entry 的 ViewModel 持有，Activity 因旋转重建时复用当前页面与表单，重新绑定当前 Context；退出 entry 时同步会话并销毁 WebView。表单不提取或写入 SavedState，进程退出后不恢复未提交输入。清理中的状态及回调随 ViewModel 保留。
- 浏览器 Cookie 清理与持久化、HttpOnly／非根路径 Cookie 的完整同步及换号仍需设备验证；不能将候选代码视为真实会话验收。

## 搜索、详情、目录与 UI

候选 GET 搜索参数来自固定上游：`/modules/article/search.php`，`searchtype=articlename|author`，`searchkey` 按 GB2312 表单百分号编码一次，`page` 为当前页。只加载指定页；确认的上游五秒限流文本最多延迟重试一次，可以取消。没有零结果页证据时，缺书卡或分页结构报解析失败；不把异常页面当作零结果。分页当前页不匹配请求也拒绝缓存。

单本命中复用详情字段映射，携带作者、封面及成人标记，不绕过列表成人过滤。详情使用上游位置 XPath，保留原标题括号，映射作者、分类／连载状态、字数、更新、简介、标签与同源封面；目录以 `vcss` 卷头和 `td > a` 按行顺序映射现有 ChapterListItem／ChapterItem，拒绝跨作品链接。

主页与 ESJ 登录页提供文库入口。文库搜索／会话／书架页面纳入 Navigation 3 恢复；搜索结果复用 NovelPage。相同关键词／类型再次提交和失败重试强制刷新；从详情或会话返回时保留现有搜索页码，顶部控件可水平滚动以适应窄屏。文库详情不创建 ESJ 评论 ViewModel，也不显示论坛或评论。搜索、详情和下载均可打开原业务 URL 的验证页；详情区分解析、版权限制、WebView 和会话存储失败。本地历史、下载与书签通过文库入口菜单访问，不依赖 ESJ 登录。控件资源同步 values、values-en、values-zh-rCN；保留四个主 Tab 和原 ESJ 搜索。

## 本地数据、离线与备份

- 文库书架固定 scope 为 `wenku8:local`，收藏和删除仅写本地，不调度 ESJ 同步；详情只补充已有本地收藏的元数据，不因浏览作品自动收藏。
- 分组、排序、编辑和批量删除复用书架页面及 DAO；按稳定来源 scope 使用独立 ViewModel key。单本和批量删除在事务内同时清理文库分组成员，不触碰 ESJ 行。文库缺失元数据通过统一文库详情入口补充，失败保留本地数据且不启动 WebView。ESJ 账号和站点变化不改文库 scope。
- 仅文库作品的下载清单增量保存卷名与章节 URL；旧清单和 ESJ 作品继续按原有扁平目录读取，文库新清单离线恢复卷和章节顺序。入队前先落盘完整元数据／目录，部分下载也可离线打开并保留完整目录；迟到章节写入不覆盖刷新后的卷信息。后台遇详情验证时可采用已下载清单目录继续恢复，正文仍禁止后台 WebView。既有选章预取、正文、图片和导出链路继续复用。
- 封面与插图按实际图片 URL 选择独立 Wenku 客户端／Coil loader，不向其他主机发送 Wenku Cookie。
- 分组备份增加 `wenku8Groups`，以固定本地 scope 导入；原 ESJ 分组继续使用传入 scope。下载／历史仍走原白名单，新增代码不导出会话或凭据。Android 云备份和设备迁移均排除 `wenku8_cookies.xml` 与 `app_webview`；应用 ZIP 备份仍采用原有白名单。

## 许可证

详情、目录、搜索和书卡规则实际改写自固定上游，文件保留来源、版权和修改说明。完整 Apache-2.0、应用归属资产及实际清单见 [WENKU8_THIRD_PARTY_SOURCES.md](../doc/WENKU8_THIRD_PARTY_SOURCES.md)。没有引入 Ktor、Result 或其他依赖，原 GPL 和已有归属不变。

## 验证记录与剩余项

历史上，首次 A/B 的 JVM／Lint 检查耗时 11 分 4 秒；随后按用户要求暂停测试，直到本地全部改造集中完成。最终统一检查先补齐一处扩展函数 import，再遇到仓库已有的 Kotlin 增量编译内部错误（未修改的 SearchTab.kt）；使用本次命令参数 `-Pkotlin.incremental=false` 恢复，未修改依赖或构建配置。

最终全量 `testDebugUnitTest lintDebug --build-cache -Pkotlin.incremental=false` 成功，耗时 6 分 35 秒：330 项 JVM 用例，0 失败／错误／跳过；Lint 无错误。复核后补齐单本搜索的作者、封面和成人标记，6 项受影响解析用例与 Lint 复查通过，并成功编译 `compileDebugAndroidTestKotlin`，耗时 6 分 26 秒。设备测试的单例状态清理另做编译／Lint 复查，结果见计划最终记录。

这些结果证明源码编译和给定合成数据下的行为，不代表实站业务或设备交互验收。ADB 可用设备数为 0，没有执行 Instrumentation 用例；没有构建 APK、推送或触发 CI。合法零结果 DOM、真实登录／过期／退出和完整浏览器 Cookie 行为仍缺实站／设备证据。

### 第二轮改进（2026-10-09）

源码修复了重复搜索不刷新、返回页面重置页码、窄屏控件拥挤、会话清理后停留空白页、浏览器 Cookie 路径范围丢失与迟到缓存写入使用新命名空间的问题；原生 Set-Cookie 覆盖同名旧浏览器桥接值。新增 `WenkuBrowserCookieScopeTest` 四项合成数据回归用例，尚未执行。已知 Cookie 清理回调不代表未知 domain／path Cookie 已全部删除，完整会话同步仍需真实浏览器验证。

本轮继续只做静态检查，不启动 JVM 测试、Lint 或构建；真实站点协议证据及最终统一验收仍未完成。

### 集中收尾（2026-10-09）

用户要求一次完成剩余轮次。本地可实现的身份／导航、传输／候选解析、会话／本地书架、离线／下载、入口／资源及归属改造已集中补齐，已完成统一 JVM／Lint 和设备测试源码编译。再次匿名只读访问 2552 的详情、目录及 login.php，均为 403；本地传输检查识别到挑战文档标记，没有业务容器。没有保存响应正文、Cookie 或表单内容。不能将这个结果当作有效 DOM、登录成功或完整协议证据。

已补充中文搜索缓存身份、版权通知拒绝、部分离线分卷、迟到卷信息写入和 Cookie 范围的 JVM 用例；另保留本地书架／分组删除及分组备份隔离的 Instrumentation 用例，需设备单独执行。统一 JVM／Lint 结果已记入计划；APK、真实账号、零结果 DOM 和设备交互仍单列为未验证。

### WebView 会话跨进程保存（2026-10-09）

补齐浏览器快照的加密同步保存、CookieJar 重建恢复、WebView 导航前恢复，以及原生轮换／删除／到期与清理同步。新增两项 JVM 用例检验快照序列化后的 URL 范围和来源隔离；新增设备用例检验加密存储重建、清理和原生 Cookie 更新不恢复旧快照，使用测试 APK 私有存储和合成 Cookie。

静态检查与 `git diff --check` 通过。统一 `testDebugUnitTest lintDebug compileDebugAndroidTestKotlin --build-cache -Pkotlin.incremental=false` 成功，耗时 10 分 40 秒：334 项 JVM 用例全部通过，其中 Cookie 范围／快照用例 8 项；Android Lint 与设备测试源码编译通过。ADB 无连接设备，未执行设备测试及真实登录后杀进程重启验收；未构建 APK 或触发 CI。旧版只存在内存中的桥接 Cookie 无法凭空恢复，首次安装此修改后需要先打开仍持有会话的 WebView 或重新登录一次。

### 实站登录与发现入口（2026-10-09，beta 基线 92be9b1）

通过云浏览器用户安全登录流程访问 `https://www.wenku8.net/`，匿名访问重定向到
`/login.php?jumpurl=...`，提交后进入 `/index.php`，页面显示用户欢迎栏和退出登录入口。
登录页的旧“本站正式关闭”文案不代表当前业务关闭；登录后的首页正常展示更新内容。
没有读取、导出或存储该浏览器的密码或 Cookie，云浏览器会话不会自动进入 Android 应用。
应用继续通过独立的文库会话 WebView 由用户登录。

本轮直接观察首页、全部列表与总排行榜的公开内容 DOM：

| 页面 | 路径 / 参数 | DOM |
| --- | --- | --- |
| 首页 | `/index.php` | `#centers > .block` 与 `#right > .block`；标题 `.blocktitle` / `.txt`，作品 `.blockcontent a[href]` |
| 全部 / 完结 | `/modules/article/articlelist.php`，完结 `fullflag=1` | `#content > table > tbody > tr > td > div` 书卡 |
| 排行 / 更新 / 新书 / 动画 | `/modules/article/toplist.php?sort=...` | 同一书卡结构；总榜页面已直接打开验证 |
| 分页 | 上述路径加 `page=N` | `#pagelink em` 为 `当前页/总页数` |

导航实际暴露 `allvisit`、`dayvisit`、`monthvisit`、`lastupdate`、`postdate`、`anime`；
本轮加入以上入口及全部、完结筛选，仅总榜与全部列表取得了直接业务 DOM，其余入口参数由站点导航确认，未逐项打开验收。
站点还暴露分类 `class=1..14`、Tags、更多推荐榜和年度专题；这些不在本轮实现范围。

首页按动态标题分组（新番原作、新书风云榜、会员推荐、最近更新及右侧榜单），
仅保留合法同源作品链接，在分组内合并封面与标题重复链接；排行使用 `tiptitle` 保留未截断的完整书名。
列表图片链接也是 `tiptitle`，正文区第一个作者段落为 `作者:.../分类:...`，Tags 段落独立查找；
修正原先只读取 `title` 和固定段落序号导致的解析失败／作者错位／成人标记丢失。
站点封面实际使用 `http://img.wenku8.com/image/...`，仅该精确图片域名升级为 HTTPS，
使用现有普通图片加载器，不向其发送文库会话。CDN HTTPS 加载仍需 Android 设备验收。
首页缺少 Tags，无法仅凭首页确定未标记作品的成人属性，不为此请求每本详情；列表继续按已解析的 R18 标记过滤。

扩展传输允许 HOME/BROWSE 页类，仍严格限制主机、端口、路径及参数；
首页缓存 15 分钟、列表 30 分钟，沿用会话隔离与强制刷新；请求跳到同源 `/login.php`
时抛出明确登录需求，不缓存登录页、不自动输入密码。首页会按动态分区复用书卡，
其他入口分页加载，保留分类与页码状态，支持刷新、书名／作者搜索与既有详情、阅读和书架。

新增 `Wenku8DiscoveryTest` 覆盖实站书卡形状、首页去重、完整标题、CDN 地址、
R18 和分页、登录页拒绝、浏览 URL 允许范围。按用户要求不运行 Gradle、JVM 测试或 Lint，
只执行静态契约检查、XML 资源校验和 `git diff --check`；这些检查不证明 Android 编译或设备行为。
未提交或推送 GitHub。
