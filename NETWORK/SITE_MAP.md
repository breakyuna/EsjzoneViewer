# ESJ Zone 站点地图与导航逆向

> 采集日期：2026-08-28  
> 目标站点：esjzone.cc  
> 规范域名：https://www.esjzone.cc/  
> 证据范围：已登录浏览器中的低频、只读页面访问与 DOM/内联脚本检查。

## 1. 证据约定

- **Observed**：在页面 URL、DOM、内联脚本或表单属性中直接观察到。
- **Inferred**：根据页面结构或常见实现推断，尚未通过网络层确认。
- **UNKNOWN / NOT VERIFIED**：当前浏览器能力或安全边界下没有确认，客户端不得依赖。
- 本次未读取真实 Cookie、会话值、授权 token 或密码，也未执行发帖、留言、资料更新、收藏切换、删除观看记录、登出等写操作。

## 2. 规范化域名

打开 https://esjzone.cc 后观察到跳转到 https://www.esjzone.cc/，因此客户端应将 www.esjzone.cc 作为规范 origin。

部分列表卡片的最新章节链接直接出现 https://www.esjzone.one/forum/...。这是页面数据中观察到的跨主机链接，不应在解析时强行替换为 .cc；客户端可以保留原始 URL，同时将 .cc 作为默认站点 origin。

## 3. 顶层导航

| 页面 | URL 模板 | 认证 | 状态 | 说明 |
|---|---|---:|---|---|
| 首页 | / | 否/可选 | Observed | 首页推荐、最新翻译、最新原创、R18 区块与主导航 |
| 一週更新 | /update/ | 否/可选 | Observed | 六个日期 tab，页面一次性包含 2026-08-23 至 2026-08-28 的卡片 |
| 小说全部 | /list-01/ | 否/可选 | Observed | 40 卡片/页，当前页码总数 157 |
| 小说原创 | /list-21/ | 否/可选 | Observed | 分类值为原创 |
| 小说日轻 | /list-11/ | 否/可选 | Observed | 分类值为日轻 |
| 小说韩轻 | /list-31/ | 否/可选 | Observed | 40 卡片/页，内联脚本显示总页数 32 |
| 标签/搜索 | /tags/{keyword}/ | 否/可选 | Observed | 默认排序为最新更新 |
| 排序标签 | /tags-{sort}/{keyword}/ | 否/可选 | Observed | 例如 tags-04 为最多观看 |
| 论坛首页 | /forum/ | 否/可选 | Observed | 两组论坛分类表 |
| 论坛分类 | /forum/{categoryId}/ | 否/可选 | Observed | 静态四列主题卡表 |
| 论坛子板块 | /forum/{categoryId}/{boardId}/ | 否/可选 | Observed | ESJ 作品板与天空大公國讨论板共用两段 URL；主题表可能由 Bootstrap Table 动态填充 |
| 章节/帖子 | /forum/{novelId}/{postId}.html | 否/可选 | Observed | 服务器渲染正文、评论与前后导航 |
| 留言板 | /guestbook/ | 可选 | Observed | 服务器渲染留言与本地分页 |
| 问与答 | /faq/ | 可选 | Observed | FAQ 锚点目录与图片资源 |
| 站方公告 | /forum/1584622376/1585970655/ | 否/可选 | Observed | 主导航中的公告入口 |
| 登录 | /my/login | 否 | Observed | 登录页；当前已登录访问会跳转会员资料 |
| 会员资料 | /my/profile | 是 | Observed | 当前用户资料与编辑表单 |
| 收藏 | /my/favorite | 是 | Observed | 当前用户收藏列表 |
| 观看记录 | /my/view | 是 | Observed | 当前用户每本小说最后阅读章节 |
| 我的回覆 | /my/reply | 是 | Observed | 当前账号为空，分页脚本总数为 0 |
| 我的私讯 | /my/message | 是 | Partial | 联系人列表为空或未渲染；动态加载私讯 |
| 经验值记录 | /my/record | 是 | Partial | Bootstrap Table 服务端分页，当前无记录 |
| 错漏字回报 | /my/fixed | 是 | Partial | Bootstrap Table 服务端分页，当前无记录 |
| 管理的小说 | /my/book | 是 | Observed | 当前账号为空表 |
| 我的贴文 | /my/post | 是 | Observed | 当前账号为空表 |
| 问题与建议 | /my/ticket | 是 | Observed | 工单表为空，有新建/回复表单 |
| 系统消息 | /my/sys | 是 | Observed | 当前账号为空表 |

## 4. 页面关系

### 4.1 小说阅读主链路

首页/分类/搜索 → 小说详情 → 章节列表 → 章节正文 → 评论或下一章

具体模板：

- 列表卡片详情：/detail/{novelId}.html
- 详情页章节：/forum/{novelId}/{postId}.html
- 详情页返回：a.view-all 指向 /detail/{novelId}.html
- 章节前后：a.btn-prev 与 a.btn-next

章节页的前后链接是论坛帖子的时间顺序导航，不保证等同于小说章节号顺序。样本中第 1 章的上一篇是一个“挂人通知”帖子，因此客户端应以详情页 TOC 为正典章节顺序。

### 4.2 搜索链路

首页的搜索按钮为 div.search。点击后 .site-search 获得 search-visible，隐藏输入框 input[name="site_search"] 变为可用；输入后按 Enter 进入：

/tags/{keyword}/

搜索词由浏览器进行 URL 编码，站点使用 path segment 而非 query string。排序变更会把路由前缀改为：

/tags-{sort}/{keyword}/

分页形如：

/tags-{sort}/{keyword}/{page}.html

### 4.3 收藏与观看记录

- 详情页 button.btn-favorite 展示当前状态，样本账号状态为“已收藏”。
- 收藏排序先进入 /my/favorite/new/ 或 /my/favorite/udate/；最新收藏的后续分页使用裸 /my/favorite/{page}，最近更新的后续分页使用 /my/favorite/udate/{page}。
- 观看记录每行包含小说详情链接与最后观看章节，删除按钮使用 /inc/mem_view_del.php，见 API_ENDPOINTS.md。

## 5. 小说分类与排序路由

分类首位数字：

| 路由前缀 | 页面分类 | DOM 选项值 | 证据 |
|---|---|---:|---|
| list-01 | 全部 | 0 | Observed |
| list-21 | 原创 | 2 | 导航与列表样本 Observed |
| list-11 | 日轻 | 1 | 导航 Observed |
| list-31 | 韩轻 | 3 | 直接访问 Observed |

排序后缀：

| 后缀 | 选择值 | 排序 |
|---:|---:|---|
| 01 | 1 | 最新更新 |
| 02 | 2 | 最新上架 |
| 03 | 3 | 最高评分 |
| 04 | 4 | 最多观看 |
| 05 | 5 | 最多文章 |
| 06 | 6 | 最多讨论 |
| 07 | 7 | 最多收藏 |
| 08 | 8 | 最多字数 |

list-04 在实际选择排序值 4 后被观察到，分类下拉框在该交互中未改变 URL 或内容，因此分类与排序组合路由不得仅靠猜测生成，见 UNKNOWN / NOT VERIFIED。

## 6. 论坛层级

论坛首页观察到：

- ESJ-曉朔國度：1584680829、1584678947、1584622251、1584622325、1584679807
- 天空大公國：1584622376、1584622613、1584622628

界面层级与 URL 层级不是同一件事：

- ESJ-曉朔國度 → 论坛分类 → 作品论坛板块 → 主题/帖子。分类页例如 `/forum/1584622325/`，作品板块例如 `/forum/1584622325/1788015863/`，其主题链接使用 `/forum/1788015863/{postId}.html`，并可从页面上的 `/detail/1788015863.html` 识别为作品板块。
- 天空大公國 → 论坛分类 → 讨论板块 → 主题/帖子。分类页例如 `/forum/1584622376/`，讨论板块例如 `/forum/1584622376/1585405336/`，其主题链接使用 `/forum/1585405336/{postId}.html`，页面没有作品详情链接。

因此，用户界面中的 ESJ 路径有两级子项，天空大公國路径有三级子项；两者进入最终主题页前都要完整保留分类 ID 与板块 ID，不能把第二段 ID 统一当作小说 ID。

论坛分类 `/forum/{categoryId}/` 使用静态 `.table.forum-board-detail`，每个单元格含：

- 主题标题链接 /forum/1584680829/{threadId}/
- 主題：{n}　回覆：{m}
- 最後發表：{date}

子板块卡片排列在四列矩阵中，解析器必须遍历所有 `td`，不能只读取每个 `tr` 的第一个链接。样本分类页面表格有 382 个 tr，内容直接在 HTML 中，不显示 Bootstrap Table 分页配置。

子板块 `/forum/{categoryId}/{boardId}/` 使用 `#dataTable[data-url]` 的 Bootstrap Table 壳。页面初始 HTML 可能只有 `no-records-found` 占位行，即使 `data-url` 的 `totalRows` 大于 0；等待站点脚本后可看到主题行。`totalRows=0` 才表示已确认的合法空板块。

## 7. 分页与本地 tab

### 7.1 服务端 HTML 分页

- 分类列表：/{listPrefix}/{page}.html，内联 bootpag 给出总页数。
- 标签/搜索：/tags-{sort}/{keyword}/{page}.html。
- 收藏：先访问 /my/favorite/new/ 或 /my/favorite/udate/，随后分别跟随站点暴露的裸数字或 /udate/ 数字分页链接。

### 7.2 客户端本地切换

- 更新页六个日期面板均已在同一 HTML 中，ID 为 tab0 至 tab5，仅切换 .show.active。
- 章节评论和留言板都由客户端统一按评论顺序每页 15 条，站点 .comments-page-N 分组不作为分页依据。
- 评论分页链接为 javascript:void(0);，客户端提供首页、上一页、下一页和末页按钮。

## 8. 未验证项

- HTTP 状态码、响应头、实际 XHR/fetch 请求、重定向链和网络瀑布：UNKNOWN / NOT VERIFIED。
- /detail/0.html 与 /forum/1716174812/0.html 的错误页检查因云浏览器超时未完成，不对 404、空页或重定向行为作断言。
- list-{category}{sort} 的所有组合是否支持：UNKNOWN / NOT VERIFIED。
- 子板块的 Bootstrap Table 数据接口及其初始占位行为：已在云浏览器观察到，具体参数见 API_ENDPOINTS.md。
- 登出后的匿名页面与权限差异：未执行登出，UNKNOWN / NOT VERIFIED。
