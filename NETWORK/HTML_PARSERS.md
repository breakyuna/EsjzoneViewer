# ESJ Zone HTML 解析器设计

## 1. 解析原则

- 以语义选择器和链接 URL 为主，不依赖第几个 div。
- 保留原始 href，另存规范化 URL；不要丢弃章节链接中的 # 片段。
- 处理传统服务端 HTML、空表、Loading 占位和本地 tab。
- 只把 DOM 中可观察字段标记为已知，缺失字段使用 null 或 UNKNOWN。
- 小说正文使用 HTML 级别保存，不能只取纯文本，否则会丢失段落、换行和插图。

## 2. 通用页面骨架

常见公共选择器：

| 目标 | 选择器 | 备注 |
|---|---|---|
| 页面标题 | document.title | 中文页面，lang 属性观察为 en |
| 主标题 | h1, h2, h3 | 不同模板层级略有差异 |
| 主导航 | header a[href] 或公共导航区域 | 以 href 为准 |
| 创建小说模态 | form#createBook | 多数页面都会带，不能当作当前页面主表单 |
| 会员侧栏 | /my/ 开头的链接 | 登录态页面出现 |
| 分页 | #page-selection a、.pagination a | 服务端分页与本地分页并存 |

## 3. 小说列表卡片

适用页面：/、/update/、/list-*、/tags*。

主选择器：

~~~css
.card.mb-30
.card.mb-30 h5.card-title a
.card.mb-30 .card-ep a
.card.mb-30 .card-author a
.card.mb-30 .card-img-tiles
.card.mb-30 .lazyload
.card.mb-30 .card-other
~~~

字段映射：

| 字段 | 选择器/来源 | 归一化 |
|---|---|---|
| 小说标题 | .card.mb-30 的 title 属性，或 h5.card-title a 文本 | 保留原文 |
| 详情 URL | h5.card-title a[href] 或 .card-img-tiles[href] | 匹配 /detail/{novelId}.html |
| 小说 ID | 详情 URL | 十进制字符串，模型中可保留 string |
| 最新章节标题 | .card-ep a 文本 | 纯文本 trim |
| 最新章节 URL | .card-ep a[href] | 允许 .cc 与 .one 主机 |
| 作者 | .card-author a 文本 | 保留原文 |
| 作者/标签 URL | .card-author a[href] | 通常 /tags/{author}/ |
| 封面 | .lazyload[data-src] | 优先 data-src，不依赖最终 img |
| 字数 | .icon-file-text 所在 .card-other | 去逗号，失败为 null |
| 观看数 | .icon-eye 所在 .card-other | 同上 |
| 收藏数 | .icon-heart 所在 .card-other | 同上 |
| 章节/文章数 | .icon-feather 所在 .card-other | 同上 |
| 讨论数 | .icon-message-square 所在 .card-other | 同上 |
| R18 标记 | .product-badge.top | 只记录是否存在和文案 |

列表页每页观察到 40 张卡片。数字字段的图标与数字位于相邻文本中，建议使用字段节点的 innerText 后正则提取，而不是依赖空格数量。

### 3.1 首页上周热门

首页桌面侧栏的上周热门榜直接嵌在首页 HTML 中，并非独立接口。客户端只取前十名用于首页横向轮播。

| 字段 | 选择器/来源 | 备注 |
|---|---|---|
| 榜单项 | `.widget-categories-hot li` | DOM 顺序即排名 |
| 小说标题 | `li > a` | 纯文本 trim |
| 详情 URL | `li > a[href*='/detail/']` | 相对详情链接 |
| 上周热度 | `li > span` | 不是详情页累计观看数 |

榜单本身不提供封面、简介、作者、类型或 R18 标记。客户端通过详情页六小时 HTML 缓存顺序补全前十名，不因首页下拉刷新强制刷新详情页；单项补全失败时跳过该项，不能让榜单拖垮首页其他分区。

## 4. 搜索与标签结果

客户端将关键词作为单一路径段编码后再构造首屏与分页 URL；因此 `#`、`?`、`/` 和空格不会改变路由层级。分页仍使用 `/tags-{sort}/{keyword}/{page}.html`。

页面选择器：

| 目标 | 选择器 |
|---|---|
| 结果标题 | h1, h2, h3 中包含 搜尋結果： |
| 类型 | select#category |
| 排序 | select#sorting |
| 结果卡片 | .card.mb-30 |
| 分页 | #page-selection a |

下拉值：

- category：0=全部、2=原創、1=日輕、3=韓輕
- sorting：1=最新更新、2=最新上架、3=最高評分、4=最多觀看、5=最多文章、6=最多討論、7=最多收藏、8=最多字數

搜索输入框不是初始可见控件。先点击可见 div.search，再填写 input[name="site_search"]，按 Enter 后进入 /tags/{keyword}/。

## 5. 小说详情页

URL：/detail/{novelId}.html

### 5.1 基础字段

| 字段 | 选择器/来源 |
|---|---|
| 详情容器 | .book-detail |
| 标题 | .book-detail h2 |
| 作者 | .book-detail a[href^="/tags/"] |
| 访问数 | #vtimes |
| 收藏数 | #favorite |
| 字数 | #txt |
| 封面 | .product-gallery img，实际图片常在 src 或懒加载属性 |
| Web 生肉 | .book-detail a[rel*="nofollow"] 或外部 URL |
| 收藏按钮 | button.btn-favorite |
| 书籍论坛 | a.btn-forum |
| 章节 tab | #integration |
| 章节排序按钮 | #integration button 中的 正序/倒序 文案 |
| 内容标签 | .widget-tags a, .widget-tags a.tag, a.tag[href*="/tags/"] |

详情页以 `.book-detail h2` 的实际标题为书名；仅在标题缺失时沿用列表入口的书名，避免导航恢复后把数字 ID 写入阅读历史。

详情样本的 button.btn-favorite 文案为 已收藏，类名含 btn-danger；这是当前用户状态，不是通用默认值。
内容标签容器为 .widget.widget-tags（页面分别在移动端 .widget-tags.hidden-lg-up 与桌面侧栏呈现相同标签），每个标签链接为 a.tag[href^="/tags/"]；解析器去重后得到完整标签列表。包含 R18 标签时作为小说成人内容标识。

### 5.2 章节目录

兼容选择器：

~~~css
#integration #chapterList a[data-title]
#integration #chapterList a[data-title] p
#integration details
#integration details > summary
#integration details a[data-title]
#integration details a[data-title] p
~~~

字段：

- `#chapterList` 可以混合组外章节、说明段落与 `details`，不能用后代链接选择器将整个容器平铺。
- 从 `#integration` 按 DOM 顺序递归遍历包装节点，遇到 `details` 保留分组，标题取直接子节点 `summary`，支持嵌套分组与空分组；章节链接只解析一次。
- 折叠初始状态来自 `details[open]`；客户端保存当前页面的展开状态。折叠只影响显示，完整章节顺序仍用于阅读导航、历史和下载。
- 2026-09-01 浏览器验证 `1635692176`：`#chapterList` 内五组分别有 18、99、102、100、62 个链接，均默认收起，组外另有三个章节链接。
- 章节标题：a[data-title] 的 data-title，若缺失再取其中 p 文本。
- 章节 URL：a[href]。
- 小说 ID/帖子 ID：从 /forum/{novelId}/{postId}.html 解析。
- 外部顺序：DOM 顺序；正序/倒序按钮可能改变显示顺序。

已验证的当前扁平模板包括 1772649515（129 个章节）与 1784452084（52 个章节）；旧样本还包含 10 个 details，前 9 卷有内容，最后一卷 Q&A 为空。目录链接可能包含非数字标题，客户端不能用“数字章节连续”替代实际 DOM 目录。

## 6. 章节/帖子页

URL：/forum/{novelId}/{postId}.html

### 6.1 正文

~~~css
.forum-content.mt-3
.forum-content.mt-3 p
.forum-content.mt-3 img
~~~

保存策略：

1. 保留正文容器的 innerHTML。
2. 允许 p、br、img 以及 section/div/article/blockquote 等嵌套容器排版节点；解析时递归提取独立段落块以避免换行坍缩。
3. 图片 URL 用 img[src] 提取，并保留 alt/class。
4. 评论区不并入正文，只取 .forum-content.mt-3。

离线任务读取章节及提交章节密码时，URL 均以任务入队时保存的站点基址解析；前台切换镜像不改变该任务的请求主机。

### 6.2 元数据和导航

| 字段 | 选择器 |
|---|---|
| 标题 | 主内容区标题，或 document.title 分隔出的章节标题 |
| 作者 | .single-post-meta.m-t-20 中的用户链接 |
| 时间 | 同一 meta 区中的日期文本 |
| 观看数 | .single-post-meta.m-t-20.file-text |
| 评论/讨论数 | .btn-likes 的文本 |
| 上一篇 | a.btn-prev[href] |
| 下一篇 | a.btn-next[href] |
| 返回详情 | a.view-all[href] |
| 举报表单 | form#forumReport |
| 评论表单 | form.commentEditor |

前后导航表示论坛帖子顺序，不等于目录章节序号。

### 6.3 wenku8 站外章节

ESJ 目录中形如 `https://www.wenku8.net/novel/{bookGroup}/{bookId}/{chapterId}.htm` 的 HTTPS 链接由原生阅读器处理。来源识别检查解析后的完整主机和路径；其余站外链接默认在应用内 WebView 打开，右上角可交给系统浏览器。wenku8 响应从有大小限制的原始字节解码，按 HTTP charset、HTML meta charset、HTML http-equiv charset、GBK 的顺序选择编码。

正文只读取 `#content`，标题优先读取 `#title`。清除脚本、样式、广告、导航及书签控件后，正文仍经共享的 `analyseComponents` 解析；图片相对地址以章节 URL 解析为 HTTPS wenku8 地址。缺少正文容器或正文过短时拒绝缓存。详情页的目录顺序优先于站外页面自己的上一页、下一页链接，因此跨来源翻章仍由 ESJ 目录决定。

Cloudflare 挑战在正文解析和缓存之前识别。冷会话先尝试 OkHttp；遇盾时前台使用受限的隐藏 WebView/Chromium 直接取得章节 HTML，不依赖搬运 clearance 后由 OkHttp 重试。热浏览器会话优先在同源页面内使用带 Cookie 的 fetch，失败时退回章节顶层导航；浏览器已确认需要人工验证时不再重复请求。成功后尽力将浏览器 Cookie 同步到独立的 wenku8 CookieJar，供 OkHttp 图片请求与下次冷会话尝试使用；Cloudflare 仍可能因传输指纹不同拒绝 OkHttp。浏览器会话退出阅读器后销毁，空闲 60 秒也会回收。只有后台浏览器无法取得有效正文时才要求用户打开可见验证页。下载入队前，详情页以最多 2 个并发请求读取并缓存选中的 wenku8 正文；需要人工验证时，验证页可将当前正文写入缓存，再继续预取。浏览器会话仍串行处理验证与页面抓取。后台下载不启动 WebView，遇盾保留已下载文件并通知用户返回小说详情。

挑战判定优先使用 `cf-mitigated: challenge` 响应头及实际挑战页标题、控件和安全提示标题。Cloudflare 的 `/cdn-cgi/challenge-platform/` 脚本也可能出现在含有效 `#content` 的正常章节页中，不能仅凭该脚本把章节判成挑战页。可见 WebView 在检测到新 `cf_clearance` 后继续等待章节正文最多 20 秒；检查 60 秒仍无结果时会提示用户重试，若刚检测到 clearance 则允许等满正文等待时间。WebView 加载到同一路径的有效章节正文时，只捕获标题、正文和章节导航，分 32 KiB 小块传回应用，并先经过原有解析与清理再写入本地页面缓存；UTF-8 正文超过 4 MiB 时记录不含页面数据的诊断并回退网络请求。

## 7. 评论解析

主选择器：

~~~css
.comments-section
.comments-section.comments-page-N
.comment
.comment-header
.comment-title
.comment-author-ava .lazyload-author-ava
.comment-header img / .comment-title img / [data-avatar]
.comment-floor
.comment-meta
.comment-body > blockquote
.comment-text
.comment-footer
.forum_report
.forum_reply
~~~

字段：

- 评论 ID：.comment 的 id，样本形如 comment-{commentId}。
- 用户名：.comment-header 内用户链接文本。
- 用户 URL：通常 /my/profile?uid={uid} 或 /my/profile.html?uid={uid}。
- 用户头像：优先读取 .comment-author-ava .lazyload-author-ava 的 data-src、data-original 等懒加载属性；懒加载完成后再读取 style 中的 background-image，兼容旧模板的 img/[data-avatar]。
- 楼层：.comment-floor。
- 日期：排除 .comment-floor 后的 .comment-meta，或 time[datetime]/[data-time]；不能把 #楼层当作日期。
- 内容：.comment-text 的 HTML 或纯文本。
- 回复引用：评论正文前的 `.comment-body > blockquote`；引用与当前回复正文分开保存和显示。
- 操作：.forum_report、.forum_reply。

评论区统一由客户端按解析后的评论顺序每页 15 条分页，忽略站点不一致的 .comments-page-N DOM 分组；分页链接通常是 javascript:void(0);，不要把它当作服务端 URL。

## 8. 会员页面解析

### 8.1 会员侧栏

用 /my/ 链接建立功能索引，避免依赖侧栏位置。当前观察到的链接包括 profile、book、post、favorite、reply、message、view、record、fixed、ticket、sys、logout。

### 8.2 收藏列表

页面：/my/favorite。

选择器：

~~~css
#fav_sorting
table.table
table.table tr
table.table a[href^="/detail/"]
table.table a[href*="/forum/"]
~~~

每行一个 td，通常包含详情链接、最新章节链接、最後觀看 和 更新日期。默认页每页 20 行；排序选择会先进入 /my/favorite/new/ 或 /my/favorite/udate/，后续分页分别跟随页面生成的裸数字或 /udate/ 数字链接。由于裸数字页依赖首个路由建立站点会话排序，客户端进入排序时会强制刷新首个路由，同时保留旧缓存作为网络失败回退。

### 8.3 观看记录

页面：/my/view。

客户端直接按照 `/my/view` 页面的 `table.table` 行顺序解析并展示，不进行反转。该顺序契约由 `history.html` parser fixture 固定测试。

选择器：

~~~css
table.table
.view-log
.view-del
.book-ep
~~~

删除按钮的 data-id 是观看记录 ID，不一定是小说 ID；从 data-id 读取并发送给 /inc/mem_view_del.php。

### 8.4 资料表单

页面：/my/profile。

主要字段：

- input[name=nickname]
- input[name=pwd]
- input[name=pwd2]
- select[name=age]
- select[name=open_msg]
- select[name=display_post]
- input[name=blacklist]
- input[name=rmlist]
- input[name=content]

资料简介由 Froala 在 contenteditable=true 元素中编辑，提交前同步到 hidden content。编辑表单 .form-edit 的 method 为 POST，未提交验证。

## 9. 论坛与 FAQ

### 9.1 论坛分类与首页分组

论坛首页 `/forum/` 包含两组论坛版块，在解析与界面中划分为两个部分（数量比为 5:3）：
- **ESJ-曉朔國度**（5 个版块）：对应 ID `1584680829`、`1584678947`、`1584622251`、`1584622325`、`1584679807`。
- **天空大公國**（3 个版块）：对应 ID `1584622376`、`1584622613`、`1584622628`。

分类链接通过 `table.forum-category` 或 `table` 中的 `/forum/{categoryId}/` 匹配，所属分组优先根据表头文本（如 `thead`, `caption`, 标题元素）或已知 ID 集合映射；未分配时按 5:3 比例降级回退。

页面 `/forum/{categoryId}/` 使用 `.table.forum-board-detail` 的四列卡片矩阵。每个 `td` 内第一个匹配 `/forum/{categoryId}/{boardId}/` 的 `a[href]` 是一个子板块入口，`.forum-desc` 包含主题数、回复数和最后发表日期；解析器必须遍历全部 `td`，不能按 `tr` 只取一个入口。

子板块页 `/forum/{categoryId}/{boardId}/` 有两种语义：

- ESJ 小说旧入口：五个 ESJ 分类中的两段式页面存在匹配 `/detail/{novelId}.html` 的小说标题链接，例如 `/forum/1584622325/1788015863/`。客户端只解析该详情链接并直接进入小说详情，不解析或展示旧页面的主题表。
- 普通讨论板：页面没有作品详情链接，例如 `/forum/1584622376/1585405336/`；主题链接中的第一个 ID 是实际板块 ID，而不是父分类 ID。

主题表使用 `#dataTable[data-url]`。初始 HTML 的 `no-records-found` 行可能只是 Bootstrap Table 尚未完成动态加载的占位符；当 `data-url` 的 `totalRows` 大于 0 时必须请求其 JSON 数据并解析 `rows`，不能把初始占位符当作空列表。`totalRows=0` 才可以直接返回空列表。无法识别 JSON、行结构或主题链接时应返回加载错误，不能静默转换为空页。

### 9.2 FAQ

页面 /faq/ 的问答目录链接为 a[href^="#q"]，说明图片链接为 /assets/img/faq/...。当前 DOM 文本能看到四个问题标题，但折叠内容显示不完整，具体答案 HTML 不作为模型字段依赖。

## 10. 资源和清洗

- .lazyload[data-src]：优先保存 data-src，不要把空 src 当作没有封面。
- 正文图片是普通 img，可直接收集 src。
- 外部脚本、广告、统计和 Cloudflare beacon 不属于业务正文。
- 公共页有多处 createBook 模态表单，解析当前页面功能时应按表单 ID/业务容器过滤。
- 详情页控制台观察到一条 .comments-page-1 #https://www.esjzone.cc/detail/1716174812.html 选择器语法错误，属于站点前端已知问题线索，解析器不应依赖其滚动逻辑。
