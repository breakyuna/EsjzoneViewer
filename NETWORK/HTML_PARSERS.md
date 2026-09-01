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

详情样本的 button.btn-favorite 文案为 已收藏，类名含 btn-danger；这是当前用户状态，不是通用默认值。

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
2. 允许 p、br、img 等原始排版节点。
3. 图片 URL 用 img[src] 提取，并保留 alt/class。
4. 评论区不并入正文，只取 .forum-content.mt-3。

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

### 9.1 论坛分类

页面 `/forum/{categoryId}/` 使用 `.table.forum-board-detail` 的四列卡片矩阵。每个 `td` 内第一个匹配 `/forum/{categoryId}/{boardId}/` 的 `a[href]` 是一个子板块入口，`.forum-desc` 包含主题数、回复数和最后发表日期；解析器必须遍历全部 `td`，不能按 `tr` 只取一个入口。

子板块页 `/forum/{categoryId}/{boardId}/` 有两种语义：

- 作品论坛板：页面存在匹配 `/detail/{novelId}.html` 的作品详情链接，例如 `/forum/1584622325/1788015863/`；该板块仍可能同时包含自己的主题列表。
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
