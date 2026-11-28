# EsjzoneViewer 过度防御代码全面审查与评估报告

## 1. 审查范围与方法

- **审查范围**：全仓 `app/src/main/java/com/breakyuna/esjzone/` 全部 341 个 Kotlin 源文件（约 71.5k LOC），覆盖阅读器核心与渲染、网络/缓存/解析、离线下载与备份、存储与日志、数据库与领域模型、导航与页面交互。
- **验证方式**：全部为静态源码与调用链审查，所有 P0 / P1 条目均已对照真实源文件行号与实际调用路径完成二次核实；不涉及编译、运行测试或读取敏感配置。
- **整改进度**：全部 **11 项 P0**、全部 **22 项 P1**（对应本地提交 `5651e0b`）以及 **P2 低影响/维护负担与死代码**均已完成整改并同步更新受影响的单元测试源码与 `NETWORK/`、`docs/` 文档，且通过静态验证（`verify_static_contracts.py`、`git diff --check`；JVM 单元测试、Android Lint、构建与设备测试未运行）；第 7 节 10 项合理防御经核实全部保留。
- **判定原则**：严格区分**合理的工程防御**（如 Zip-Slip 防护、非破坏性鉴权检查、分页代际令牌、未登录态隔离等）与**过度防御**（在受信任本地路径重复做重型完整性/格式校验、服务端 SSRF 风格防御误用于移动端客户端、以“一处异常即全盘作废/阻断”取代局部降级、为兜底极小概率竞态而放大常态网络/IO/CPU 开销）。

### 分级标准

| 等级 | 含义 | 典型表现 |
| --- | --- | --- |
| **P0（高影响）** | 直接拖慢高频主路径（冷启动、翻章、切页、发评、下载）或直接导致功能失效/卡死/数据或排版静默丢失 | 每次翻章全量 SHA-256 或双遍 DOM 遍历、8.5 秒触控锁死、删除有效插图、页面双倍 HTTP 请求、离开页面静默取消下载 |
| **P1（中影响）** | 持续消耗后台流量/CPU/磁盘/电量，或在较常见边界下损害可用性与交互体验 | 每次冷启动全表扫描与反复查 404 书籍、重复解压/双编缩略图、单条坏记录拖垮全章划线、跳转销毁中间栈、带参数链接失效 |
| **P2（低影响/维护负担）** | 仅影响极低频路径或已无生产调用方，主要带来代码膨胀与维护成本 | 一次性迁移逻辑重复执行、无调用方兼容层、冗余锁或可简化逻辑 |

---

## 2. P0 高影响问题速查总览（11 项，全部已完成）

| 编号 | 状态 | 模块与核心位置 | 过度防御行为 | 性能影响 | 体验影响 |
| --- | --- | --- | --- | --- | --- |
| P0-1 | ✅ 已修复 | `data/reader/ChapterBody.kt` | 每次读缓存/已下载章节均重排序、Gson 序列化并计算整章 SHA-256 | 高 | 中 |
| P0-2 | ✅ 已修复 | `data/reader/ChapterFormatting.kt` | 生产环境每次加载做两遍全章“内容守恒”比对，任意微差即清空全章排版 | 高 | 高 |
| P0-3 | ✅ 已修复 | `ui/reader/ReaderNameReplace.kt` | 每段落无缓存编译 RE2/J、单字符切片，超预算直接静默放弃所有替换规则 | 高 | 高 |
| P0-4 | ✅ 已修复 | `ui/reader/ReaderFontStore.kt` | 每次冷启动对 16–25 MB 私有字体全量 SHA-256，下载后连续哈希两遍 | 高 | 中 |
| P0-5 | ✅ 已修复 | `ui/page/ChapterPage.kt` 等 | 转场串联 6.5–8.5 秒超时链并吞掉全屏触控，失败后把已加载章节锁在弹窗后 | 中 | 高 |
| P0-6 | ✅ 已修复 | `network/external/*` | 客户端套用服务端 SSRF 私有 IP 拦截，且删除非 `www.wenku8.net` 的插图 | 低 | 高 |
| P0-7 | ✅ 已修复 | `network/CacheImageDiskCache.kt` 等 | 禁用 Coil 原生淘汰改自建全局锁索引；冷启逐个打开快照，差 1 字节清全库 | 高 | 高 |
| P0-8 | ✅ 已修复 | `ui/page/InAppBrowserActivity.kt` 等 | 逐个 DOM 文本节点（含空白）同步调 JS-Java 桥，耗尽 4096 次会话上限即失效 | 中 | 高 |
| P0-9 | ✅ 已修复 | `ui/page/CommentSection.kt` | 进页即预刷资料、发评前同步 RTT、每 6 秒轮询 3 次、超时冻结 12 秒+并锁死发送 | 高 | 高 |
| P0-10 | ✅ 已修复 | `ui/page/CommunityPages.kt` / `GetCommunity.kt` | 帖子冷开连续 2 次完整 HTTP+Jsoup；板块强制绕过缓存且每行解析 Jsoup 6 次 | 高 | 高 |
| P0-11 | ✅ 已修复 | `ui/page/NovelPage.kt` | 文库8点下载时在前台协程先抓取全书剩余章节，离开页面直接静默取消 | 中 | 高 |

---

## 3. 推荐高 ROI 优先改造顺序（Top 5，全部已完成）

1. **✅ 已完成 — 阅读器热路径瘦身（P0-1 + P0-2 + P0-3）**：移除本地已持久化章节的重复序列化/SHA-256 与生产态内容守恒降级；将 `ReaderNameReplace` 普通文本改为纯字符串匹配并缓存编译后的正则对象。收益最大且对所有阅读用户立竿见影。
2. **✅ 已完成 — 阅读器冷启与进场解耦（P0-4 + P0-5）**：字体仅在下载写入时校验 SHA-256，冷启改用轻量元数据核对；移除转场与正文渲染的串行死等，缩短定位兜底超时，超时直接展示回退位置而不锁死页面。
3. **✅ 已完成 — 社区与评论区去冗余请求（P0-9 + P0-10）**：`ForumPostPageModel` 改用纯缓存首屏加载；`getForumBoard` 恢复缓存与离线回退并合并单元格 Jsoup 解析；评论区移除进页预刷、发评前阻断式资料 RTT 与长时经验轮询，仅在真实待确认超时场景做轻量状态核对。
4. **✅ 已完成 — 图片磁盘缓存与文库8兼容修复（P0-6 + P0-7）**：恢复 Coil 原生 `DiskLruCache` 上限与异步初始化，去除冷启逐条校验和全清策略；允许文库8官方插图域名（如 `pic.wenku8.com`）并移除误杀代理/VPN 的客户端私网 IP 拦截。
5. **✅ 已完成 — 内置浏览器与文库8后台下载修复（P0-8 + P0-11）**：内置浏览器批量拼接文本节点转换并跳过纯空白节点、取消全局 4096 次硬上限；文库8下载仅探测首章或交由 `WorkManager` 后台执行，避免页面退出导致下载中断。

---

## 4. P0 高影响问题详细分析

### [✅ 已修复] P0-1：章节读取时重复排序、Gson 序列化并计算整章 SHA-256

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/data/reader/ChapterBody.kt:51-55, 254-257`（`ChapterBody.validate`、`ChapterBody.digest`）
  - 调用方：`data/reader/StructuredChapterCache.kt:39`、`offline/NovelDownloadStore.kt:655, 2154`
- **原防御意图**：校验已落盘的结构化章节 JSON 未损坏、且块列表与持久化摘要一致。
- **为何过度防御**：
  - `StructuredChapterCache` 与 `NovelDownloadStore` 的章节内容由本应用写入并受限于私有存储/压缩包完整性；但每次打开缓存章节、离线章节或批量恢复时，都会调用 `ChapterBody.validate()`。
  - `validate()` 先执行 `readerBlocks()` 完整构建块对象后再直接丢弃返回值，接着调用 `digest()` 对所有块按索引排序、用 Gson 重新序列化为完整 JSON 字符串并计算 SHA-256，与字段中的旧摘要比对。
- **性能与体验影响**：每次翻章、切章和批量校验均白白付出“反序列化 + 重序列化 + SHA-256 + 临时对象分配”的 CPU 与 GC 开销，长章节尤为明显。
- **最小改动建议**：信任应用自身落盘的结构化 JSON（底层已有 GZIP/JSON 解析校验）；`validate()` 仅做 O(1)/轻量结构字段校验，不再重建 `readerBlocks()`、不再重序列化与计算 SHA-256。
- **整改落地**：`ChapterBody.validate()` 改为轻量版本/指纹格式/块与样式结构校验，不再重建 `readerBlocks()`、不再重序列化与计算 SHA-256；仅在外部备份导入边界（`NovelDownloadStore.kt`）调用独立的 `validateImportedContent()` 核对正文指纹与长度；已同步更新 `StructuredChapterTest.kt` 与 `docs/maintenance-notes.md`。

---

### [✅ 已修复] P0-2：生产环境每次解析执行双遍全章“内容守恒”比对，微小差异即清空全章排版

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/data/reader/ChapterFormatting.kt:39-86`（`analyseChapterContent`、`extractDomContentSnapshot`、`extractBlocksContentSnapshot`）
  - 调用方：`network/features/GetChapterDetail.kt:224`、`offline/NovelDownloadStore.kt:2174`、`data/reader/ChapterBody.kt:62, 96`
- **原防御意图**：防止富文本解析器漏字或错序，在解析后对比原始 DOM 文本快照与解析结果块快照，不一致时自动退化为纯文本解析。
- **为何过度防御**：
  - 将原本属于**离线单元测试**的回归断言无条件放入了生产热路径，每次打开、下载或恢复章节都会额外执行两遍整章遍历（一次遍历 DOM 构建 `StringBuilder`/事件列表，一次遍历已解析 blocks 构建对比快照），若触发降级还会执行第 3 遍纯文本遍历。
  - 两套独立遍历器对空白字符、注音（`<ruby>`）、行内图片或特殊嵌套标签只要存在 1 字符差异，严格相等 `expected == actual` 就会失败。
- **性能与体验影响**：阅读切章与批量离线下载时 CPU/GC 翻倍；任意无害的单字符差异都会导致 `degraded = true`，静默清空整章所有文字样式、对齐方式、`<hr>` 分隔线与 `<details>` 折叠块。
- **最小改动建议**：将“内容守恒”比对移至单元测试；生产路径直接采用单遍富文本解析，仅对解析抛出的真正异常或空结果做最小兜底。
- **整改落地**：`analyseChapterContent()` 移除生产路径双遍快照比对，仅在主解析抛异常或非空内容产出空块时回退无损解析；同时在 `Component.kt` 抽取 `collectRuby` 修复 `<ruby>` 内含 `<img>` 时误将 `<rt>` 拼入正文的根因，守恒断言保留在 `ChapterContentIntegrityTest.kt`。

---

### [✅ 已修复] P0-3：名称替换无缓存重复编译 RE2/J 正则、逐字符切片并因预算上限静默失效

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/ui/reader/ReaderNameReplace.kt:108-161`（`edits`、`scan`、`MAX_WORK`）
  - 调用方：`app/src/main/java/com/breakyuna/esjzone/ui/reader/ReaderScriptConverter.kt:85, 107`（`snapshot`）
- **原防御意图**：用 Google RE2/J 避免用户自定义正则引发 ReDoS 回溯卡死，并用字符步数预算 `MAX_WORK` 限制最坏耗时。
- **为何过度防御**：
  - `edits()` 对每个文本块、每条规则都实时重新编译 RE2/J `Pattern`（无任何缓存），甚至普通纯文本替换规则也会走 RE2/J 编译；而 `ReaderScriptConverter.snapshot()` 对同一文本块调用了两次 `edits()`（第 85 行与第 107 行）。
  - `scan()` 在循环中对每个字符调用 1 字符 `substring()`（第 151、158 行），制造大量短命 `String` 对象。
  - 一旦长段落触及 `MAX_WORK` 上限，逻辑直接放弃并返回空替换集，导致该段落所有替换规则静默失效。
- **性能与体验影响**：开启名称替换后，翻章/切换简繁出现明显卡顿与 GC 抖动；长段落（如大段独白或设定集）中人名替换会突然全部失效且无任何提示。
- **最小改动建议**：对规则列表预编译并按规则快照缓存；非正则规则直接使用 `String.indexOf` 扫描，不再编译 RE2/J；消除 `snapshot()` 对同一块的重复调用及 `scan()` 中的逐字符 `substring` 分配；预算保护改为单条规则超时/步数截断而非整段作废。
- **整改落地**：`ReaderNameReplace.kt` 增加正则与规则集 LRU 缓存、普通规则直接 `String.indexOf`、批量切片/数组拷贝消除逐字符 `substring()`、超匹配/输出上限按单规则隔离；`ReaderScriptConverter.snapshot()` 消除同块重复调用；已同步更新 `ReaderNameReplaceTest.kt` 与 `docs/maintenance-notes.md`。

---

### [✅ 已修复] P0-4：每次冷启动对 16–25 MB 私有字体全量 SHA-256，下载后连续哈希两遍

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/ui/reader/ReaderFontStore.kt:79-96, 112-118, 120-159, 161-172`（`request`、`publishIfValid`、`download`、`sha256`）
- **原防御意图**：在构造 `Typeface` 前验证已下载的中文字体文件未被篡改或写半截。
- **为何过度防御**：
  - 下载后的 CJK 字体体积达 16.5–25.6 MB，存放在应用私有目录 `filesDir/reader_fonts`。`publishIfValid()` 在**每次应用冷启动**进入阅读器时都会读取整个字体文件重算 SHA-256。
  - 在 `download()` 中，流式下载阶段（L134-152）已逐块计算并校验了 SHA-256，随后原子 `renameTo`（L154）落盘，但 L155 紧接着又调用 `publishIfValid()`，将刚写完的 25 MB 文件再完整读取并哈希**第二遍**。
- **性能与体验影响**：每次冷启动进入阅读器触发 100–400 ms+ 的磁盘读与 CPU 哈希阻塞；哈希期间 `family()` 回退为 `FontFamily.Default`，造成肉眼可见的“默认字体闪烁 -> 切换自定义字体 -> 全章重新排版与重分页”。
- **最小改动建议**：仅在下载阶段做一次流式 SHA-256 校验与原子落盘；冷启动仅核对文件存在、非空及字节长度（必要时结合 `Typeface.createFromFile` 异常兜底），避免重复全量读取哈希。
- **整改落地**：`ReaderFontStore.kt` 移除 `sha256(file)` 与冷启/落盘后二次全量哈希，仅保留流式下载阶段一次 SHA-256 校验与原子替换；`publishIfValid()` 改为文件存在性、字节大小与 `Typeface.Builder` 异常兜底（含按字体加锁防并发竞态）。

---

### [✅ 已修复] P0-5：阅读器进场多级遮罩 + 6.5–8.5 秒超时链与全屏触控锁死

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/ui/page/ChapterPage.kt:275-281, 1711-1748, 1836-1867, 2068-2080, 2156`
  - `app/src/main/java/com/breakyuna/esjzone/ui/reader/ReaderContentPosition.kt:11-25`（`restoreReaderPosition`）
  - `app/src/main/java/com/breakyuna/esjzone/ui/reader/ReaderEntryPresentation.kt:72-100`
- **原防御意图**：防止进入阅读器或切章时出现滚动跳动，确保转场动画、简繁转换、分页、精确锚点跳转与布局确认全部完成后再显示正文。
- **为何过度防御**：
  - L2156 将转场动画结束（`entryAnimationFinished` + `withFrameNanos`）设为正文开始组合的前置条件，导致转场期间完全浪费 CPU/布局准备窗口。
  - 随后在 `contentVisible = false` 且 `PointerEventPass.Initial` 吞掉全部触摸事件的状态下，串联 `restoreReaderPosition`（4000 ms 精确恢复 + 2500 ms 兜底恢复 = **6.5 秒**）与 2000 ms 布局确认（`ChapterPage.kt:1843`）及额外 `withFrameNanos` 帧等待，总阻塞链高达 **8.5 秒**。
  - 若定位确认超时（`ChapterPage.kt:1741-1745, 1858`），直接将已经成功加载的正文锁在阻断式“重试”弹窗后面，而不是显示近似回退位置。
- **性能与体验影响**：每次开章都必须硬等转场结束才开始排版；一旦遇到图片高度变化或分页竞态，界面可冻结无响应长达 6.5–8.5 秒，甚至把完全可读的章节锁死在错误弹窗后。
- **最小改动建议**：转场期间允许正文提前组合与排版（仅遮罩/淡入呈现）；缩短定位确认超时（如 300–600 ms）；超时或精确锚点不可用时立即揭开遮罩展示最佳回退位置并解除触控拦截，绝不锁死已加载章节。
- **整改落地**：已修改 `ChapterPage.kt`、`ChapterPositionRestore.kt`、`ReaderContentPosition.kt`、`ReaderEntryPresentation.kt`，解除转场动画对正文组合的前置阻塞、大幅缩短定位超时，超时后直接显示最佳回退位置并解除触控拦截；已同步更新三份 `strings.xml` 与 `ReaderContentPositionTest.kt`。

---

### [✅ 已修复] P0-6：文库8客户端套用服务端 SSRF 私有 IP 拦截，且强删非单一主域插图

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/network/external/ExternalChapterHtml.kt:58-64`
  - `app/src/main/java/com/breakyuna/esjzone/network/external/WenkuChapterClient.kt:36-41, 64-76, 181-187`（`isPublicAddress`、`readerImageClient`）
  - `app/src/main/java/com/breakyuna/esjzone/network/external/WenkuCookieJar.kt:14-60`
- **原防御意图**：防止文库8外链章节被重定向到恶意站点或内网地址，并隔离文库8 Cookie。
- **为何过度防御**：
  - `ExternalChapterHtml.kt:58-64` 仅保留字面主机完全等于 `https://www.wenku8.net` 的 `<img>` 标签，将其余所有图片节点直接删除；而文库8实际插图托管在独立图床域（如 `pic.wenku8.com` 等），导致合法插图被一刀切清空。
  - `WenkuChapterClient.kt:181-187` 在移动端客户端里照搬服务端 SSRF 防御，用 `isPublicAddress` 拦截一切 RFC1918 / ULA 私有 IP（注：Clash 默认 Fake-IP `198.18.0.0/15` 虽可绕过 Java `isSiteLocalAddress`，但企业/校园/家庭内网 DNS 劫持代理、WireGuard/Tailscale/部分 TUN 代理返回的私网 IP 会被直接拦截），且 `readerImageClient` 也继承了单一主机 DNS 白名单。
  - `WenkuCookieJar` 为公开无登录态的 Cloudflare 过盾 Cookie（`cf_clearance` 等）启用了 `EncryptedSharedPreferences` 加解密开销。
- **性能与体验影响**：文库8轻小说插图全面丢失；使用特定代理/VPN 环境的读者无法打开文库8章节或图片；额外引入 KeyStore 加密读写开销。
- **最小改动建议**：`ExternalChapterHtml` 放宽插图白名单至文库8合法图床域（或交由独立无凭据图片客户端按常规 HTTP/HTTPS 策略加载）；移除客户端侧私网 IP DNS 拦截，保留重定向白名单与无凭据隔离即可；公开 `cf_clearance` Cookie 使用普通存储即可。
- **整改落地**：`ExternalChapterHtml.kt` 放宽白名单至 `*.wenku8.net` / `*.wenku8.com` 并覆盖全部 `IMAGE_URL_ATTRIBUTES` 懒加载属性，同时在 `WenkuChapterClient.createImageClient` 校验允许域名、移除客户端私网 IP 拦截，`ReaderDocument.kt` / `AppImage.kt` 同步兜底旧快照；`WenkuCookieJar.kt` 改用普通私有偏好 `wenku8_cookies` 且严格限定仅读写 Cloudflare 过盾 Cookie（`cf_clearance`、`__cf_bm`、`_cfuvid`）；已更新 `WenkuChapterTest.kt`、`NETWORK/AUTH.md`、`NETWORK/HTML_PARSERS.md` 与 `docs/maintenance-notes.md`。

---

### [✅ 已修复] P0-7：禁用 Coil 原生磁盘淘汰，自建全局锁图片缓存并在 1 字节偏差时清空全库

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/app/AppContainer.kt:105`（`maxSizeBytes(Long.MAX_VALUE)`）
  - `app/src/main/java/com/breakyuna/esjzone/network/CacheImageDiskCache.kt:26-54, 76-87, 116-120`（`load`、`release`）
  - `app/src/main/java/com/breakyuna/esjzone/network/CacheStorage.kt:28, 37-43, 83-96`
- **原防御意图**：接管 Coil 的磁盘缓存配额管理，确保本地索引与磁盘文件字节级严格一致。
- **为何过度防御**：
  - `AppContainer.kt:105` 主动把 Coil `DiskCache` 上限设为 `Long.MAX_VALUE` 来禁用其内置 `DiskLruCache` 淘汰，转而套一层自定义 `CacheImageDiskCache` 索引。
  - 冷启动时 `load()` 在全局 `lock` 下逐行编译内联正则并**顺序打开每一个已缓存快照**核对字节大小；只要磁盘上任意单个文件与索引记录相差 **1 字节**，就触发灾难性的全库清空（`clear()`）。
  - 每次图片快照 `release()` / `close()`（L119）都会调用 `CacheStorage.requestTrim()`（虽有 `CONFLATED` + 100 ms 防抖略有缓解，仍高频触发后台磁盘清点）。
- **性能与体验影响**：图片缓存越多，冷启动首次加载封面/插图前的持锁阻塞越长；一次非正常杀进程或 1 字节元数据误差就会瞬间清空全部图片缓存，造成封面和插图大规模重新下载。
- **最小改动建议**：直接将实际字节配额交给 Coil 原生 `DiskCache.Builder().maxSizeBytes(...)` 管理，移除 `CacheImageDiskCache` 的全量冷启扫描、全局锁与全库清空逻辑；单条坏项仅删除该条。
- **整改落地**：`AppContainer.kt` 恢复 Coil 原生有限上限（Release 128 MiB / Debug 2048 MiB）；`CacheImageDiskCache.kt` 移除冷启逐快照扫描与差 1 字节清全库，改为单条损坏局部清理，仅在延迟淘汰时才于 `release()` 补调 `requestTrim()`；`CacheStorage.trim()` 优先按共享 LRU 淘汰，仅在仍因未索引孤儿图片超限时后置调用 `reclaimUnindexedBytes()` 兜底；已更新 `CacheImageDiskCacheTest.kt`、`CacheStorageTest.kt` 与 `docs/cache-analysis.md`。

---

### [✅ 已修复] P0-8：内置浏览器对每个 DOM 文本节点（含空白）同步调用 JS-Java 桥，且 4096 次上限静默破坏简繁转换

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/ui/page/InAppBrowserActivity.kt:193-235`
  - `app/src/main/java/com/breakyuna/esjzone/ui/reader/WebsiteScriptConverter.kt:4-22`
- **原防御意图**：在内置 WebView 中实现网页正文简繁转换，并用 4096 次调用上限防止恶意页面无限刷爆 JS 接口。
- **为何过度防御**：
  - 注入脚本遍历 DOM 中每一个 `TextNode`（**包括纯空白/换行缩进节点**），逐节点**同步**调用 `EsjScriptBridge.convert()` 跨进程/跨引擎通信。
  - `WebsiteScriptConverter` 按整个浏览器会话累计硬上限 4096 次（L8, L13-16），普通目录页/帖子页包含上千个文本与空白节点，浏览 2–4 个页面即耗尽配额，之后所有转换静默返回原文。
- **性能与体验影响**：每次页面加载或 DOM 变动触发数百至上千次同步 WebView 桥接调用，拖慢页面滚动与渲染；浏览几页后简繁转换突然完全失效且无任何提示。
- **最小改动建议**：JS 侧跳过纯空白文本节点，并将同一批次文本节点按分隔符打包为少量批量调用（如每批 100–200 个节点）；将调用限制改为按单次文本长度/单页频率限制，而非跨页累计的 4096 次硬熔断。
- **整改落地**：`InAppBrowserActivity.kt` 跳过纯空白节点并批量打包调用桥接接口，`WebsiteScriptConverter.kt` 新增 `convertBatch()` 与 `resetPage()`，改为翻页重置的单页配额（8 Mi 字符 / 4,096 次，每批最多 256 项 / 256 Ki 字符）；已更新 `SecurityResourceLimitsTest.kt` 与 `docs/maintenance-notes.md`。

---

### [✅ 已修复] P0-9：评论区进页预刷资料、发评前同步 RTT 阻断、长时轮询及发送按钮锁死

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/ui/page/CommentSection.kt:578-584, 859-860, 896-922, 1216-1222, 1355-1363, 1384-1586`（`CommentComposer`、`CommentPageModel.load`/`submit`/`silentVerifySubmission`/`freshExperienceBaseline`/`checkExperienceIncrease`）
- **原防御意图**：在评论 POST 超时或服务端响应不明确时，通过“用户经验值是否增加 + 延迟重拉评论列表”判断评论是否实际发布成功，并防止用户误触重复发送。
- **为何过度防御**：
  - **进页预刷**：`load()`（L1216-1222）在用户根本还没打算发评时，每次打开评论页就主动触发一次 `getUserProfile(forceRefresh = true)`。
  - **发评前阻塞 RTT**：`submit()`（L1554-1561）强制等待 `freshExperienceBaseline`，且预刷缓存 10 秒即过期，导致多数发评前必须先同步阻塞请求一次个人资料页。
  - **伴随轮询**：即使正常发评，也会启动 `experienceJob` 发起最多 3 次间隔 6 秒、且 `coalesceRequests = false` 的个人资料请求。
  - **超时冻结 12–20 秒**：一旦 POST 超时或未确认，保持 `isSubmitting = true` 死等 `experienceJob` 完成（12 秒+），再等 3 秒拉全量评论（`silentVerifySubmission`）并再补第 4 次资料请求。
  - **发送键锁死**：`CommentComposer`（L859-860）与 `submit`（L1355-1363）在遇到**任意非 `EMPTY` 错误**（包括断网/DNS 失败的 `FAILED` 或服务端明确业务拒绝）后直接禁用发送按钮，并 10 秒内静默丢弃相同文本重试，逼迫用户修改草稿或整页刷新评论区。
- **性能与体验影响**：每次看评论多出无效 HTTP 请求；每次发评增加明显等待延迟；弱网超时导致输入框假死 12–20 秒并发出 5–6 个冗余请求；普通发评失败后无法直接点重试。
- **最小改动建议**：取消进页预刷、发评前同步资料 RTT 与常态经验轮询；仅在真实发生“网络超时且结果未知”时做一次轻量评论列表刷新核对；仅对“结果未知待确认”状态限制重复提交，对明确断网/服务端拒绝立即允许直接重试。
- **整改落地**：`CommentSection.kt` 移除进页预刷、发评前资料同步 RTT 与 6 秒经验轮询；`GetCommunity.kt` 新增 `commentPostFailure()` 精准区分业务拒绝/`4xx`（立即允许重发）与网络超时/`5xx`/协议解析异常（做单次轻量评论列表核对）；已更新 `CommunityParserTest.kt` 与 `NETWORK/API_ENDPOINTS.md`。

---

### [✅ 已修复] P0-10：论坛帖子冷开双倍 HTTP+Jsoup，板块硬编码绕过缓存且每行解析 Jsoup 6 次

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/ui/page/CommunityPages.kt:1057-1097`（`ForumPostPageModel.load`）
  - `app/src/main/java/com/breakyuna/esjzone/network/features/GetCommunity.kt:605-672, 717-736, 844-890`（`getForumBoard`、`getForumPost`、`parseForumTopicRow`、`htmlCellMainText`、`htmlCellDescription`）
- **原防御意图**：帖子页“先展示缓存再后台刷新”；板块页保证拿到最新表格 token 与实时主题列表并安全提取单元格 HTML。
- **为何过度防御**：
  - `ForumPostPageModel.load` L1066 调用 `getForumPost(..., forceRefresh = false)`，但该方法底层是 `getPage(..., forceRefresh = false)`——**未命中缓存时会直接发起完整网络请求并执行 Jsoup 解析**（并非像评论页那样只读本地快照）；随后 L1087 又无条件调用 `getForumPost(..., forceRefresh = true)`。结果所有冷开/未缓存帖子必跑 **2 次连续完整 HTTP GET + 2 次完整 Jsoup 解析**。
  - `getForumBoard`（L619-620）硬编码 `forceRefresh = true, allowStaleOnError = false`（甚至不暴露 `forceRefresh` 参数），每次进入板块都绕过 `PageCacheTtl.COMMUNITY` 与离线回退，串行执行 3–5 次网络 RTT 且断网即失败。
  - `parseForumTopicRow`（L861-864）对同一行的 `authorCell` 和 `statsCell` 分别调用 `htmlCellMainText()` 与 `htmlCellDescription()`，导致单个主题行最多运行 **6 次** `Jsoup.parseBodyFragment()`（每页 20 行即约 120 次 HTML 解析）。
- **性能与体验影响**：帖子页首开流量、延迟与解析 CPU 翻倍；板块页打开慢、完全不能离线查看缓存；列表解析制造大量无谓 GC。
- **最小改动建议**：`ForumPostPageModel` 首屏仅读纯本地缓存快照（命中即渲染再按需刷新，未命中只发 1 次请求）；`getForumBoard` 支持标准 `forceRefresh` 参数与 `allowStaleOnError = true` 离线回退；每单元格仅 `Jsoup.parseBodyFragment()` 一次后同时提取主文本与描述。
- **整改落地**：`CommunityPages.kt` 改用 `getCachedForumPost()` 读纯缓存快照并补充并发 `generation` 防回写；`GetCommunity.kt` 为 `getForumBoard` / `loadForumBoardTopicsPayload` 恢复缓存、离线回退与 `PageCache.beginWrite()` 写入令牌防旧响应回写，并合并 `parseForumTopicRow` 每单元格为单次 `Jsoup.parseBodyFragment()`；已更新 `CommunityParserTest.kt` 与 `NETWORK/API_ENDPOINTS.md`。

---

### [✅ 已修复] P0-11：文库8下载在前台页面协程中预抓全书剩余章节，离开页面直接静默取消

- **代码位置**：
  - `app/src/main/java/com/breakyuna/esjzone/ui/page/NovelPage.kt:1520, 1587-1667`（`NovelDownloadActions.enqueueDownload`）
- **原防御意图**：在交给 `WorkManager` 后台下载前，先在页面上下文中捕获 `CloudflareChallengeRequiredException`，以便直接弹窗让用户过盾。
- **为何过度防御**：
  - 代码并非仅探测 1 章验证 Cloudflare 状态，而是在 Composable 生命周期绑定的 `downloadScope` 内以 `Semaphore(2)` + `awaitAll()` **把全书所有未下载的文库8章节全部先抓完**（L1616-1640），等全部完成后才调用 `NovelDownloadManager.enqueue()`（L1645）或统一抛出首个失败异常（L1641）。
- **性能与体验影响**：用户点击“后台下载”后一旦退出小说详情页，`downloadScope` 被取消，整个下载任务在尚未进入 `WorkManager` 队列前就静默中止；若触发 Cloudflare 验证，程序还会把剩余全部章节全部撞墙请求一遍后才弹出验证框，浪费大量流量并加剧风控。
- **最小改动建议**：点击下载前最多仅对首个未缓存章节做一次快速 CF 探测（或直接入队 `WorkManager`，在 Worker 遇到 CF 异常时通知前端过盾），绝不在页面作用域内全量预抓整本书。
- **整改落地**：`NovelPage.kt` 移除页面协程内全书预抓，点击下载直接入队 `WorkManager`；`CloudflareChallenge.kt` 与 `NovelDownloadWorker.kt` 持久化待验证章节 URL 与原选章范围（`SELECTION_TAG_PREFIX`）；当前页发起遇盾时自动弹窗，离开后返回详情页通过“打开验证页”按钮与下载面板一键恢复验证并按原选章范围续传（不重复打断普通阅读）；已更新 `NovelDownloadContractTest.kt` 与 `NETWORK/HTML_PARSERS.md`。

---

## 5. P1 中影响问题详细分析（22 项，全部已完成）

### 5.1 核心、存储、备份与日志

1. **[✅ 已修复] 本地 JSON 双遍解析与下载预检全书重解压** — **性能：中 | 体验：中**
   - `util/BoundedJson.kt:16-69`、`offline/NovelDownloadStore.kt:192, 270, 1170, 1229-1248, 1419-1443, 2792-2794`
   - **问题**：`BoundedJson.readText()` 先拷贝整段字符串并用 `JsonReader` 逐 token 预扫一遍，再交给 Gson 解析第二遍；`reclaimSavedChapter`（L1239）每回收一章都重读并校验整章；下载预检（L1419-1443）重解压并校验整本书已下章节；`saveChapter`（L192, 270, 1170）对仅 ~15 KB 的阅读器自动保存硬性要求 100 MiB 剩余空间（注：`prepareJson:2792` 仅在迁移/`legacyPositions` 时重读）。
   - **建议**：对可信本地 JSON 移除预扫描；下载预检改读轻量索引元数据；降低小文件自动保存的可用空间阈值（如 4–8 MiB）。
   - **整改落地**：`BoundedJson.readText()` 增加 `validateTokens` 开关（外部备份导入保持 `true`，受信任本地读取传 `false` 并保留 `MAX_CHARS` 上限）；`saveChapter()` 剩余空间阈值下调至 `4 MiB`；`reclaimSavedChapter()` 复用已验证内存快照；`download()` 普通增量预检信任 manifest + 非空文件，仅在显式选章或全书目标已全部标记完成时复验磁盘正文与插图；`prepareJson()` 改用写前 `BoundedCountingOutputStream`/`BoundedCountingWriter` 按解压口径流式校验 `MAX_BYTES` 与 `MAX_CHARS`，不再落盘重读。

2. **[✅ 已修复] PageCache 每次读写全量 HTML SHA-256、过期双解压与高频加解密计算作用域** — **性能：中**
   - `network/PageCache.kt:182, 307, 345`、`network/EsjzoneClient.kt:228-259, 702-719`、`network/features/GetChapterDetail.kt:75-76`、`network/features/GetNovelDetail.kt:65-67`
   - **问题**：每次读写都对整份 HTML 算 SHA-256 并存入无界 `bodyFingerprints`；`writeDerived()`（L345）再从磁盘重读一次 HTML；过期缓存 `getPage()`（L228, 250）解压两次同一文件；`accountScope()`（L706-710）每次构造缓存键都读 `EncryptedSharedPreferences` 并对每个域名做 SHA-256。
   - **建议**：在内存缓存 `accountScope` 哈希（随登录/域名变化刷新）；移除 `bodyFingerprints` 冗余全量哈希；`writeDerived()` 直接复用内存中的 HTML；过期回退复用首次解压结果。
   - **整改落地**：`PageCache` 移除 `bodyFingerprints` 与每次读写全量 HTML SHA-256，新增单次解压的 `readWithFreshness()`、`writeAndReference()` 与基于元数据的 `writeDerived()`；`EsjzoneClient` 新增 `getPageWithSource()`（且仅目标 `cacheKey` 或迁移成功结果透出 `sourceReference`，杜绝旧键引用覆盖新派生缓存）及按 `host`/凭据/`sessionEpoch`/`PersistentCookieJar.accountScopeRevision()` 失效的有界 `accountScopeCache`。

3. **[✅ 已修复] PageResponsePolicy 每次命中缓存拷贝全量小写 HTML 并触发误判 Jsoup 解析** — **性能：中**
   - `network/PageResponsePolicy.kt:82, 88, 164, 169-189`
   - **问题**：每次缓存读取均执行全量 `lowercase()` 字符串拷贝并内联编译 `Regex`；小说正文只要出现 `"captcha"` / `"access denied"` 等普通词即触发整份 `Jsoup.parse(lowerBody)`；文库8章节无条件 Jsoup 解析两次（L82 + `ExternalChapterHtml:39`）。
   - **建议**：已验证落盘的缓存无需重复走拦截页检测；正则提取为 `private val` 常量；缩小检测窗口到 `<head>`/小体积响应；消除文库8双解析。
   - **整改落地**：`PageResponsePolicy` 移除全量 `lowercase()` 拷贝与内联正则，存在 ESJ 结构标记且无活跃挑战 DOM 标记时跳过 `Jsoup.parse`；`PageCache` 为通过策略校验写入的条目首行添加 `|v4` 标记并透出 `Entry.validated`，`EsjzoneClient`、`GetNovelDetail` 与 `GetCommunity` 对已标记缓存走快路径、对未标记旧缓存复用 `PageResponsePolicy.validate` 并在无效时清理；`WenkuChapterClient` 消除文库8双次 Jsoup 解析。

4. **[✅ 已修复] 图片传输拦截器首字节前双调 `usableSpace` 且 64 MiB 底线误阻纯内存图片** — **体验：中 | 性能：低**
   - `network/ImageTransferInterceptor.kt:18-27`
   - **问题**：`nextSpaceCheck = 0L` 导致第 1 字节前即连续调用两次 `usableSpace` 系统调用；64 MiB 剩余空间硬底线会直接拦截小体积、甚至仅加载到内存的 Coil 图片请求。
   - **建议**：首次检查后正确推进 `nextSpaceCheck`；仅在写入磁盘缓存或下载大文件时限制空间，允许低存储下正常解码小图到内存。
   - **整改落地**：`ImageTransferInterceptor` 移除读取前重复 `checkSpace()`，将 `nextSpaceCheck` 初值改为 `SPACE_CHECK_INTERVAL`（1 MiB），避免首字节前双调 `usableSpace` 并允许低存储下正常加载小图。

5. **[✅ 已修复] 本地备份跳过 `downloads/` 仍先解压并计入剩余空间限额** — **体验：中**
   - `backup/LocalBackup.kt:191-238, 373-379`
   - **问题**：用户仅恢复书签/设置（跳过 `downloads/`）时，仍会解压跳过的条目并将其字节数计入 `usableSpace - 64 MiB` 配额，低存储下导致轻量元数据恢复失败；每个 ZIP 条目均调 `canonicalFile`（L376）且 `seen` 集合最多容纳 20 万条。
   - **建议**：先判断路径是否跳过，跳过项直接 `continue` 不解压、不计入空间配额。
   - **整改落地**：`LocalBackup.restore()` 拆分流处理上限（`16 GiB`）与落盘上限（`min(16 GiB, usableSpace - 64 MiB)`），跳过项与目录项不调用 `safeEntry`、不计入磁盘空间配额，同时统一按 64 KiB 分块排空并受 `checkActive()` 与流上限约束；保留提取项的 `safeEntry` Zip-Slip 防护，并同步更新 `docs/maintenance-notes.md`。

6. **[✅ 已修复] 日志系统每条跑 6 次正则 + 拷贝 500 项列表 + 每次落盘 3 次 SAF IPC** — **性能：中**
   - `util/AppLogger.kt:42, 118-137, 202, 282-328`、`util/PublicLogWriter.kt:62-82`、`storage/StorageFile.kt:159-173, 397-424`
   - **问题**：单条 `AppLogger.log()` 运行 6 个正则脱敏、新建 `SimpleDateFormat` 并复制最多 500 条日志列表；开启公共目录日志时，`PublicLogWriter.flush()` + `StorageFile.appendText()` 每行日志触发 3+ 次 SAF Binder IPC。另 `DocumentTree.metadata()`（`StorageFile.kt:400, 419`）在子文件不存在时额外补一次根 `metadata("")` Binder IPC。
   - **建议**：脱敏正则与时间格式器复用（`ThreadLocal` 或 `DateTimeFormatter`）；环形缓冲替代整表拷贝；公共目录日志批量攒批写入并缓存文档 URI，去除多余根 `metadata("")` 探测。
   - **整改落地**：`AppLogger` 改用共享 `DateTimeFormatter`、`ArrayDeque`、脱敏快筛预检与按订阅者按需拷贝 `_logsFlow`（联动 `LogsPage.refreshLogs()`），合并延迟刷盘；`PublicLogWriter` 缓存目录/活跃文件句柄并攒批写入；`StorageFile.appendText()` 复用已解析文档 URI 并按异常重建，`DocumentTree.metadata()` 移除多余根 `metadata("")` 探测。

7. **[✅ 已修复] 冷启动永久跑 8 次 Room `DELETE` 与 Release 保留 60 秒看门狗线程** — **性能：中（冷启动）**
   - `data/settings/SettingsDataStore.kt:186-196`、`EsjzoneApplication.kt:32-36`
   - **问题**：即使旧 Room 设置已迁移完毕，每次冷启动仍执行 8 条 `DELETE` 语句；Release 包每次启动都会拉起常驻 60 秒的 `StartupWatchdog` 后台线程。
   - **建议**：Room 旧键清理受迁移完成标记保护，仅迁移时执行一次；`StartupWatchdog` 仅在 `BuildConfig.DEBUG` 启用。
   - **整改落地**：`SettingsDataStore` 新增 `legacy_room_cleanup_complete` 持久化标记门控 8 条 Room `deleteByKey`；`EsjzoneApplication` 仅在 `BuildConfig.DEBUG` 下启动 `StartupWatchdog`。

### 5.2 网络特性、URL 路由与 HTML 解析

8. **[✅ 已修复] URL 校验拒绝 Query/Fragment（及 `http://`），阻断分享链接与原生路由** — **体验：中**
   - `network/EsjzoneUrls.kt:24-36`（`novelDetailUrl`）、`ui/navigation/ForumLinkRoute.kt:30-38`（`nativeSitePath`）
   - **问题**：`novelDetailUrl()` 拒绝 `http://`、任意 query 与 fragment；`nativeSitePath()` 虽允许 `http://`（80 端口），但同样拒绝任何 query 或 fragment。带追踪参数或锚点（如 `#comment-123`）的合法分享链接会被拒收或降级到外部浏览器。
   - **建议**：校验协议与主机后按规范剥离无关 query/fragment（或允许已知安全锚点），统一归一化为目标详情/论坛路径。
   - **整改落地**：`EsjzoneUrls.novelDetailUrl()` 对可信镜像默认端口详情路径自动升级 `http://` 为 `https://` 并剥离 `query`/`fragment`（仍拒绝 `userinfo` 与非数字 ID）；`ForumLinkRoute.nativeSitePath()` 放行常见无害追踪参数（`utm_*`、`fbclid`、`gclid`、`spm` 等），同时保留非空 `fragment`（如 `#myQA`）及非追踪参数（如 `?uid=`）回退 WebView；已同步更新 `EsjzoneUrlsTest.kt` 与 `docs/maintenance-notes.md`。

9. **[✅ 已修复] 登录每次新建完整 `OkHttpClient` 线程池，且 `InvalidResponse` 掩盖网络错误** — **性能：中 | 体验：中**
   - `network/features/Login.kt:50-56, 72, 101, 136-139`、`network/features/ParallelMirrorLogin.kt:65-66`
   - **问题**：`loginWithOutcome` 每次调用都 `OkHttpClient.Builder()` 新建独立 `ConnectionPool` 与 `Dispatcher` 线程池（并行多镜像 × 最多 2 次重试），而非基于 `sharedHttpClient.newBuilder()` 派生。另重定向主机不符或泛化 `catch (Exception)` 返回 `InvalidResponse`，被 `ParallelMirrorLogin` 视作 `receivedServerResult = true`，掩盖真实的 `IoFailure`。
   - **建议**：基于 `sharedHttpClient.newBuilder()` 复用底层连接池与线程调度器；区分网络/重定向异常与真实服务端业务响应。
   - **整改落地**：`EsjzoneClient` 新增 `loginClient(cookieJar)` 基于 `sharedHttpClient.newBuilder()` 复用连接池与调度器；`Login.kt` 将非 2xx HTTP 状态与异常重定向主机归类为 `IoFailure`；`ParallelMirrorLogin.kt` 仅对明确业务拒绝 `NonSuccessStatus` 置 `receivedServerResult = true`，使 `InvalidResponse` 不再掩盖另一镜像的 `IoFailure`；已同步更新 `ParallelMirrorLoginTest.kt` 与 `NETWORK/AUTH.md`。

10. **[✅ 已修复] 首页数据重复读快照、吞掉 `CancellationException` 且卡片解析做无效 DOM 扫描** — **性能：中 | 体验：中**
    - `network/features/GetHomeData.kt:39, 62-112, 132, 248-258`、`network/features/NovelCardParser.kt:78-118, 120-209`
    - **问题**：`getHomeData` 两次读取并反序列化 `HomeDataCache.readSnapshot`（L39, L132）；5 个分区的 `catch (e: Exception)` 会吞掉 `CancellationException` 并在单卡异常时放弃该分区剩余条目；`enrichWeeklyPopular` L257 内联编译 `Regex` 且存在重叠选择器。`NovelCardParser` 在 `NovelCardLayout.HOME` 布局下仍执行 `AUTHOR_LINK` 与 3 次 `statCount` 多选择器 DOM 扫描后再用 `.takeIf { isList }` 丢弃，每次首页/周更加载浪费 50–100+ 张卡片的 DOM 遍历。
    - **建议**：单次读取快照；重新抛出 `CancellationException` 并按卡片隔离错误；`NovelCardParser` 先判断 `isList` 再执行列表专属选择器，合并冗余 `.icon-X` 选择器。
    - **整改落地**：`GetHomeData` 单次读取 `HomeDataCache.readSnapshot`，通过 `parseHomeSectionCards` 按卡片隔离异常并重抛 `CancellationException`，提升 `TYPE_LABEL_PREFIX_REGEX` 常量并去重标签选择器；`NovelCardParser` 在 `HOME` 布局跳过 `LIST` 专属 DOM 查询并简化 `.icon-X` 选择器。

11. **[✅ 已修复] 章节块提取平方级 DOM 遍历与评论解析循环内重复编译 `Regex`** — **性能：中**
    - `novellibrary/component/Component.kt:50-188, 351-357`、`network/features/GetCommunity.kt:392, 574, 943-1107, 1139-1147`
    - **问题**：`extractParagraphElements` / `analyseParagraph` 反复调用 `hasBlockDescendants()`、`containsBlockElement()` 与 `selectFirst("img")` 造成 O(D×N) 嵌套遍历与节点克隆，`parseStandaloneColor` 每个 `<font color>` 重编译正则；`GetCommunity` 在逐条评论/逐属性循环中反复新建 `Regex`（L574, 1066-1067, 1105, 1140, 1147），缺 `id` 时用整段 `outerHtml()` 做哈希去重（L952）。
    - **建议**：所有 `Regex` 提升为文件级/伴生对象常量；`Component.kt` 改为自底向上单遍判定块级节点，避免重复子树扫描。
    - **整改落地**：`Component.kt` 用迭代自底向上 `computeBlockDescendants` 与 `imgAncestors`（`IdentityHashMap`）替代平方级子树扫描并提升颜色正则；`GetCommunity.kt` 提升循环内 7 处正则为常量，并用 `System.identityHashCode` 替代缺失 `id` 时的 `outerHtml().hashCode()`。

### 5.3 数据库、领域层与持久化

12. **[✅ 已修复] 结构化章节重复检查排版索引并解析旧排版** — **性能：中**
    - `data/reader/ChapterFormatting.kt:266-312`
    - **问题**：即便 `parserVersion >= 2` 仍每次缓存读取都重检全部对齐/分隔/折叠索引；且 L304 在无有效对齐/分隔或不匹配时直接 `return this` 而不递增 `parserVersion`，导致包含相关标记的旧快照每次打开都重跑 `Jsoup.parseBodyFragment` 与整章 `analyseChapterContent`。
    - **建议**：已达当前 `parserVersion` 的快照跳过全量索引复验，复用结构化缓存中旧排版的恢复结果。
    - **整改落地**：`ChapterFormatting.withFormatting` 对已达 `CURRENT_PARSER_VERSION` 且布局字段非空的快照直接返回。旧 HTML 含排版标记且与正文匹配时才恢复有效排版。结构化页面缓存按正文和 HTML 复用恢复结果。

13. **[✅ 已修复] 书架缩略图双编码（WebP + JPEG）并经历 3–4 个临时文件** — **性能：中**
    - `database/BookshelfCoverThumbnail.kt:14-53`
    - **问题**：对每张不透明静态封面创建 3 个临时文件，分别编码为 WebP-82 与 JPEG-85 仅为了挑体积较小的那份，再把胜出者拷贝到第 4 个临时文件 `pending` 后才原子移动到目标路径，使书架封面冷加载 CPU 与磁盘 I/O 翻倍。
    - **建议**：直接使用 WebP-82（API 29+ 全平台原生支持且体积稳定优于 JPEG）并单次写入临时文件后直接 `renameTo` 目标路径。
    - **整改落地**：`BookshelfCoverThumbnail` 改为单临时文件 `pending` + 单次 WebP-82 编码（API 30+ 使用 `WEBP_LOSSY`，API 29 使用 `WEBP`）并原子重命名，保留动图回退逻辑。

14. **[✅ 已修复] 书架后台补全对无作者/无封面书每 30 分钟无限重试，同步存在 N 次非事务写入** — **性能/流量：中**
    - `database/BookshelfRepository.kt:80-85, 148-153, 357-501, 522-672`
    - **问题**：站点本身缺少封面或作者的小说持续满足 `coverUrl.isBlank() || author.isBlank()`（L425-433），导致每 30 分钟无限重拉详情页 HTML；`sync` 在 `toggleFavorite` 前后多查两次 `dao.find`（SQL `WHERE operation_version = :version` 已原子保护），且 L626-651 在事务外循环执行 N 次 `findAnyWithCover` + `insertIfAbsent`（N 次独立 SQLite 提交）；`setCoverState` 每次分配 4 个集合，`novelIdFor` 循环内重编译正则。
    - **建议**：记录补全失败/空结果的冷却时间或已检查版本；将 `sync` 批量插入移入同一数据库事务并去掉冗余 `dao.find`；正则提取为常量。
    - **整改落地**：`BookshelfRepository` 提升 `DETAIL_ID_REGEX` 常量、`setCoverState` 改用有界 `LinkedHashMap(256)`、新增 `MetadataAttempt` 完成态与失败退避、移除 4 处冗余 `dao.find`，并将 `sync()` 远端对账与批量导入包裹进单次 `withTransaction`。

15. **[✅ 已修复] 标题自愈在每次会话恢复执行 4 次无索引全表扫描，且对 404 小说无负缓存无限请求** — **性能/流量：中**
    - `database/NovelTitleRepository.kt:30-56, 101-145`、`ui/navigation/AppNavigation.kt:692`
    - **问题**：每次会话/存储恢复都运行 `repairAll()`，对 4 张表执行前导通配符 `LIKE '%/detail/%'` 全表扫描 + 扫描下载目录；对已删/404 书籍，`resolve()` 反复查询全部 5 个本地存储并串行发起 `getNovelDetail` HTTP 请求，**没有任何负缓存或重试上限**。
    - **建议**：增加负缓存（如失败记录退避 7 天或按次数封顶），避免每次启动都做全表通配扫描与重复拉取已失效详情页。
    - **整改落地**：`NovelTitleRepository` 新增持久化负缓存与指数退避（404/410/空标题退避 7 天）及 24 小时自动扫描冷却；`AppNavigation` 仅在显式修复请求（`request > 0L`）时传 `force = true`。

16. **[✅ 已修复] 单条损坏划线抛异常拖垮全章划线与备份恢复，且热路径重复算 SHA-256** — **体验：中 | 性能：中**
    - `domain/reader/ReaderUnderline.kt:43-46, 86-155`、`ui/reader/ReaderUnderlinesModel.kt:27`、`backup/LocalBackup.kt:259`
    - **问题**：`decode()` 对列表内任意一条格式异常/越界划线直接 `require` 抛异常，导致 `ReaderUnderlinesModel` 隐藏整章所有正常划线，或导致 `LocalBackup` 整次恢复中止；`signature()` 未缓存 `MessageDigest`，使用 `$block` 拼接完整 span 字符串并逐字节 `"%02x".format`，在加载和迁移时多次执行。
    - **建议**：`decode()` 改为逐条校验、跳过损坏单条并保留有效划线；用轻量文本哈希或共享十六进制编码替代 `"%02x".format` 循环。
    - **整改落地**：`ReaderUnderline.kt` 的 `signature()` 改用 `ThreadLocal<MessageDigest>` 流式字节摘要与 `CharArray(64)` 编码并按块缓存 `sourceSignature`；`decode()` 改为 `mapNotNull` 逐条校验跳过损坏项（非法根 JSON 仍保持抛出 `JsonParseException`/`IllegalArgumentException`）；已同步更新 `ReaderUnderlinesTest.kt`。

### 5.4 UI、导航、全局文本与阅读器外围

17. **[✅ 已修复] `AppNavigator.pushIfNotCurrent` 搜索全栈并销毁中间页面；会话修订号变化卸载整个导航树** — **体验：中 | 性能：中**
    - `ui/navigation/AppNavigation.kt:327-343, 633-654, 757-764`
    - **问题**：
      - `pushIfNotCurrent`（全仓约 50 处导航调用）未仅检查栈顶 `backStack.lastOrNull()`，而是 `indexOfLast` 搜索整条返回栈并把目标页之上的**全部中间页面弹出销毁**，导致从帖子/搜索跳到曾看过的页面时丢失浏览路径。
      - `remember(currentDomain, loginSessionRevision)` 重置 `sessionRestored = false` 与 `authorization = null`，L757 直接卸载整棵 `NavDisplay` 树并全屏闪烁 `SessionLoadingContent()`，尽管 `MainScreen` 与 `FavoritePage` 已在原位响应 `loginSessionRevision`。
    - **建议**：`pushIfNotCurrent` 仅防抖/去重栈顶项（需回退到已有项时另设显式方法）；会话修订号更新仅后台静默刷新 `authorization` 状态，不重置 `sessionRestored`、不卸载 `NavDisplay`。
    - **整改落地**：`pushIfNotCurrent` 改为仅去重栈顶路由并为每次入栈的 `AppNavKey.Legacy` 分配唯一可序列化 `instanceId`，同时在 `AppNavigation.kt` 与 `AdaptiveShell.kt` 按实例键注册/查找/清理页面对象，彻底隔离同路由多实例状态；根导航不再因会话变更重置 `sessionRestored` 闪白屏，改以 `LocalAuthorizationSessionGeneration` 驱动各 Tab `NavDisplay` 与根 `Legacy` 条目在保留返回栈的前提下按代际重建持有 `authorization` 的 ViewModel；已同步更新 `NavigationRouteContractTest.kt`。

18. **[✅ 已修复] `GlobalText.convertAnnotatedText` 对每个 Span 边界重复前缀转换，O(M×N) 且污染全局缓存** — **性能：中**
    - `ui/designsystem/GlobalText.kt:111-118`
    - **问题**：开启简繁转换渲染 `AnnotatedString` 时，对每个唯一 Span 边界调用 `substring(0, index)` 重新转换整段前缀，形成 O(M×N) 复杂度，并把大量仅使用一次的截断前缀写入全局 4096 项 `ReaderScriptConverter.convertCache`，挤出真正可复用的正文段落缓存。
    - **建议**：基于单次整段转换构建字符偏移映射表（或按 Span 分段增量转换），不再写入截断前缀到共享 LRU 缓存。
    - **整改落地**：`GlobalText.convertAnnotatedText` 改用单次 `ReaderScriptConverter.mapping` 配合 `sourceStarts`/`sourceEnds` 二分查找映射 Span 边界，消除 O(M×N) 前缀转换与缓存污染。

19. **[✅ 已修复] 章节含 `<a>` 链接时整章重跑第二次 Jsoup，且单块失配即清空全章链接** — **性能：中 | 体验：中**
    - `ui/reader/ReaderLinks.kt:17-62`、`ui/reader/ReaderDocument.kt:43-57`
    - **问题**：只要章节含任意 `<a>` 标签，就会重新完整执行一次 `Jsoup.parseBodyFragment` + `parseRichChapterContent`；对齐逻辑（L28-39）采用全有或全无模式，任意 1 个块的 `sourceText()` 或数量不一致就返回 `emptyMap()`，静默丢弃整章所有可点击链接。
    - **建议**：在主解析阶段一次性保留链接元数据（或放宽为按块模糊/逐块匹配，不再因局部偏差清空整章链接）。
    - **整改落地**：`ReaderDocument` 将 `DetailedChapter.content` 传入 `ReaderLinks.readerChapterLinks` 直接复用已解析 `LinkTextStyle` 避免第二次 Jsoup；`alignComponentLinks` 改为逐块锁步 + 有界对角重同步恢复，不再因单块失配清空整章链接（且不改动 `ChapterBody.digest`）。

20. **[✅ 已修复] 首页热门轮播每补全 1 张卡即全量写盘 JSON 并卡住轮播** — **性能：中 | 体验：中**
    - `ui/tab/HomeTabModel.kt:282-304`、`ui/tab/HomeSections.kt:128-139, 165`
    - **问题**：循环内每补全 1 张热门卡（每次停顿 2 张，最多覆盖整个轮播）都调用 `HomeDataCache.writeSnapshot`（`ioLock` 下全量 Gson 序列化 + 临时文件写盘 + 原子重命名）并触发整页状态重组；同时 `enriching` 为真期间跳过自动轮播计时。
    - **建议**：批量完成本轮补全后合并写入一次状态与磁盘快照；后台补全详情不阻断轮播翻页。
    - **整改落地**：`HomeSections` 移除阻断轮播自动翻页的 `enriching` 门控；`HomeTabModel.loadVisiblePopular` 每张卡成功后即时更新内存状态避免翻页取消丢弃已取成果，并在 `finally` 中异步合并单次写盘，同时在 `HomeDataCache` 锁内校验 `snapshotVersion` 防止旧轮播写盘覆盖更新后的周更/主页快照。

21. **[✅ 已修复] 小说详情转场包装器双倍收集 `StateFlow`，把已缓存详情拖在骨架屏上** — **性能：中 | 体验：中**
    - `ui/page/NovelDetailPresentation.kt:18-47`、`ui/page/NovelPage.kt:239, 386`
    - **问题**：L39 调用 `source.collectAsStateWithLifecycle()` 仅为了读 `initial`，同时 L42 又在 `combine(source, ...)` 里收集第二次；转场期间前者仍触发无意义重组，而后者把瞬时可用的本地/缓存详情强行按在静态 `LoadingSkeleton` 上等待 ~300–400 ms 动画结束后才突然弹出。
    - **建议**：直接用 `source.value` 作初值去除重复收集；已命中缓存/已下载的小说详情直接渲染，无需强等转场结束。
    - **整改落地**：`NovelDetailPresentation` 去除重复的 `source.collectAsStateWithLifecycle()`，并对已命中的 `State.Result` 立即渲染，不再拖在骨架屏上等待转场结束。

22. **[✅ 已修复] 历史页每次变更全量扫描下载进度、封面与成人校验抢 2 并发槽并致行闪烁** — **性能：中 | 体验：低-中**
    - `ui/page/HistoryPage.kt:679-683, 709-717, 818-823, 1065, 1117-1128, 1232-1263`、`ui/page/LocalHistoryLoading.kt:37-47`
    - **问题**：每次 Room/下载状态变更都在 `collectLatest` 中对全部本地历史行逐一执行 `downloads.refreshProgress(activity)`；封面恢复与成人状态校验共用 `coverPermits = Semaphore(2)`（L1065），大量缺封面时饿死成人校验；云端历史每可见行启一个 `Dispatchers.Default` 协程仅做线性 URL 查找（L818-823）；`key(cover.image)`（L709）在封面加载完成时销毁重建整个 `AppNovelCover`。
    - **建议**：按变更的小说 ID 增量刷新进度；拆分封面与成人校验信号量；云端章节序号预建 `Map` O(1) 查找；移除 `key(cover.image)` 避免组件重建闪烁。
    - **整改落地**：`NovelDownloadStore` 新增按书追踪的 `progressVersionFor()`，`LocalHistoryLoading` 据此增量刷新进度；`HistoryPage` 拆分 `coverPermits`（2）与 `adultStatusPermits`（3）、为云端历史预建 O(1) `chapterIndexMap`，并移除导致封面闪烁的 `key(cover.image)`。

---

## 6. P2 低影响 / 维护负担与死代码汇总（全部已完成）

- **一次性迁移与低频路径**：
  - ✅ `ui/reader/ReaderUnderlineMapping.kt` + `ui/page/ChapterPage.kt`：旧划线迁移闭包对未变更的 `rows` 直接复用已计算的 `migrated` 快照，消除重复执行 2 遍 8 种简繁×替换组合。
  - ✅ `network/features/RemoveHistory.kt`：经复核，站点仅确认删除成功状态 `status == 200`，未证实存在可安全跳过的单条拒绝业务码，因此保留原“遇到未确认响应立即停止批次”的写安全策略（归属合理防御）。
  - ✅ `network/features/GetFavorites.kt`：保留书架完整对账所需的 `complete = true, allowFallback = false`，单页 `networkAttempts` 从 `3` 下调至 `2`。
  - ✅ `network/features/GetMemberRecords.kt`：`getExperienceRecords` 改为单次 JSON 解析复用已解析的 `JsonObject` 检查 `301` 与构造分页结果，消除二次反序列化（`extras` 保留未知字段向前兼容契约以维持既有 `MemberCenterRecordsTest` 与 UI 行为）。
  - ✅ `novellibrary/novel/Chapter.kt` + `NovelChapterList.kt` + `novellibrary/component/Item.kt`：`Chapter.source` 以 `@Transient @Volatile` 惰性缓存避免重复 `HttpUrl` 解析；`NovelChapterList.init` 改为单遍遍历 `orderedChapters`；`ChapterItem` 改用 `!chapter.isExternal` 统一判定可阅读章节。
- **轻量性能/体验毛刺**：
  - ✅ `ui/page/FavoritePage.kt` + `FavoritePageModel.kt`：`loadingShelf` 不再被 `readingSnapshot == null` 阻塞首次渲染；`collectLocalHistory` 接入 `progressVersionFor` 增量刷新下载进度。
  - ✅ `ui/page/SettingsPageModel.kt` + `network/features/Logout.kt`：抽出不提前清本地会话的 `requestRemoteLogout`，先并发完成全部镜像的远端登出请求，再统一调用 `clearSession()` 与清理旧凭据；整段已确认登出流程置于 `NonCancellable` 内，防止退出设置页中断本地会话清理。
  - ✅ `ui/page/UnderlinesPage.kt`：移除 `check(!unreadable)`，单条损坏划线记录仅记警告并跳过，不再将整页永久锁死在错误态。
  - ✅ `ui/page/ReaderBackgroundPageModel.kt` + `ReaderBackgroundPage.kt`：模糊滑块预览增加 120 ms 防抖、`canApply` 不再因预览重算闪烁禁用、`apply()` 取消进行中的预览任务，并解除保存时 `BackHandler` 与返回键锁退；`initialLoad` 加 `savedPhotoMutex.withLock` 与后台保存串行，防止退出立即重进读到即将被删除的旧文件。
  - ✅ `ui/page/DownloadPage.kt` + `offline/NovelDownloadStore.kt`：移除 `deleting` 导致的删除后双刷新；`listDownloadedNovels` 仅在 `inventorySnapshot == null` 的缓存缺失分支锁外构建 `metadataByKey` 复用 `metadataSnapshot`（避免缓存命中时持锁遍历全量元数据），并在 `onMetadata` 回调中保留已计算的文件大小。
  - ✅ `ui/page/ChapterPage.kt` + `database/BookmarkCoverStore.kt` + `database/dao/BookmarkDao.kt`：新增按去除了 `#fragment`/`?query` 的基路径预筛选的 `forChapterKey` 与轻量投影 `coverRefs()`，以 `BookmarkCoverStore.hasBookmarksForNovel` 复用 `cleanNovelId` 规则并在确认无书签后直接 `deleteCover` 避免重复查询，同时提升 `BookmarkCoverStore` 内联正则为常量。
  - ✅ `ui/reader/ReaderSidePanels.kt`：已拒绝手势直接早退，惰性分配 `VelocityTracker`，竖向滚动/多指/消费拦截即刻跳出循环停止双 pass 监听。
  - ✅ `database/BookshelfSort.kt`：`dateCache` 加上限（512），`sort()` 按排序方式分支委托 `sortWithReadProvider` 跳过无关映射与日期解析。
  - ✅ `domain/reader/ReaderPosition.kt`：提升 `FINGERPRINT_PATTERN` 与 `VALID_KINDS` 常量，`decode` 改用条件校验替代异常流；同步补充 `ReaderPersistenceContractTest.kt`。
  - ✅ `util/CommentRequestTiming.kt`：仅在 `BuildConfig.DEBUG` 下采集与输出计时日志，Release 零日志与计时开销。
  - ✅ `network/features/HomePageParser.kt`：保持语义标记优先于 DOM 位置以保障 R18 分区隔离（仅简化 `.card` 选择器），并补充语义标记与 DOM 位置冲突布局的回归测试（`NovelCardParserTest.kt`）。
  - ✅ `network/features/IsAuthorized.kt`：移除 403 二次同步重试，对重定向登录/401/403/非 2xx/空响应短路返回跳过无意义的 `Jsoup.parse`；同步更新 `NETWORK/AUTH.md`。
  - ✅ `network/features/ProfilePageParser.kt`：`profileCardText` 改为 `asSequence()` 惰性取文本，并提升空白正则为常量。
  - ✅ `storage/StorageFile.kt`：`sameContent` 改用原始 `for` 循环比较字节，消除 `(0 until count).all` 迭代装箱。
- **无调用方兼容层 / 死代码**：
  - ✅ 已删除全仓零引用的 `network/features/CommunitySyncManager.kt`、`domain/reader/ReaderSessionController.kt`、`data/repository/ReaderDomainAdapters.kt`、`domain/reader/ReaderRepositories.kt`（及 `ReaderModels.kt` 中仅被其引用的 `ReadingProgress`/`ReaderBookmark`/`ReaderSettingsState`），以及 `ChangeFavorite.kt` 中的 `changeFavorites`、`GetCommunity.kt` 中的 `getGuestbookComments`、`IsAuthorized.kt` 中的 `isAuthorized`。

---

## 7. 经核实属于合理防御（建议保留，切勿误删；✅ 整改后核实全部保留）

| 模块 / 位置 | 防御机制 | 保留理由 |
| --- | --- | --- |
| `backup/LocalBackup.kt`（`safeEntry`） | 路径穿越 / Zip-Slip 校验 | 解压外部备份包的标准安全基线 |
| `ui/navigation/AdaptiveShell.kt:410-471` | 非活跃 Tab `maxLifecycle = CREATED` 与触控/返回键隔离 | 保持多 Tab 状态同时防止后台页截获手势与返回键 |
| `ui/screen/MainScreen.kt:87-122` | 非破坏性 `checkAuthorization` + `sessionEpoch` | 符合项目离线不变量，防止切镜像时过期回调误弹窗 |
| `ui/page/SearchPage.kt` / `NovelListPage.kt` | 分页 `generation` 代际令牌 | 切换搜索词或筛选条件时防止旧请求回写覆盖新列表 |
| `network/features/GetFavorites.kt:34-57` | `complete = true, allowFallback = false`（不含 3 次重试） | 保障书架远端对账完整性，防止只拿到前几页就误删后几页收藏 |
| `network/features/ParallelMirrorLogin.kt:27-71` | `LoginSessionBatch` 代际守卫与多镜像竞速 | 避免慢镜像登录回调覆盖已完成的登录会话 |
| `network/features/GetMemberRecords.kt:120-161` | 禁重定向 + 301 重试与取消联动 | 适配站点特定接口返回 301/跳转行为 |
| `ui/page/HistoryPage.kt:613-625, 789-814` | `adult == false` 时隐藏未知成人状态项 | 符合项目强制内容分级策略 |
| `database/LocalReadingHistoryRecorder.kt` | `writeMutex` 串行化写入 | 防止快速退出阅读器时的并发进度覆盖竞态 |
| `ui/reader/ReaderShell.kt` / `ReaderUnderlineText.kt` | 选区焦点清理、`drawWithCache` 几何缓存与链接/划线点击消歧 | 直接服务于正文选择复制与交互准确性的必要逻辑 |
