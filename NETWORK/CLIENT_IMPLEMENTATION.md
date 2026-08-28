# ESJ Zone 客户端落地设计

## 1. 目标

实现一个低频、可恢复、以 HTML 解析为主的 ESJ Zone 客户端，覆盖：

1. 首页、分类、搜索、标签结果。
2. 小说详情、目录和章节正文。
3. 论坛分类、留言板和评论读取。
4. 已登录用户的收藏与观看记录读取。
5. 已明确证实的动态会员接口适配。

写操作默认关闭，认证材料只由浏览器会话管理。

## 2. 推荐分层

~~~text
Route Builder
    ↓
Transport / Browser Session
    ↓
HTML Snapshot + Response Metadata
    ↓
Page Parsers
    ↓
Domain Models
    ↓
Cache / CLI / UI
~~~

### 2.1 Route Builder

集中生成以下 URL：

- home()
- update()
- list(category, sorting, page)
- tags(keyword, sorting, page)
- novel(novel_id)
- chapter(novel_id, post_id)
- forum_index()
- forum_category(category_id)
- book_forum(category_id, novel_id)
- favorite(sort, page)
- view_history()
- member(path)

Route Builder 必须使用 URL 编码处理 keyword，并保留实际域名。对 .one 最新章节链接只做外部 URL 解析，不重写 host。

### 2.2 Transport

建议提供两个适配器：

- BrowserTransport：复用已登录浏览器上下文，适合会员页和 getAuthToken 依赖。
- HttpTransport：只处理公开 HTML GET，Cookie 由调用者显式提供且不得记录。

公共参数：

- 连接超时：短超时后重试一次即可，避免重复高频访问。
- 每个 host 串行请求。
- 解析失败时保存 URL、标题、HTML hash 和脱敏错误。
- 不自动跟随未知外部 host 的写请求。

本次网络层没有确认状态码、重定向、ETag 或实际请求头，因此 Transport 不得写死这些协议细节。

### 2.3 Snapshot

保存轻量快照：

~~~json
{
  "url": "https://www.esjzone.cc/detail/1716174812.html",
  "canonical_url": "https://www.esjzone.cc/detail/1716174812.html",
  "fetched_at": "2026-08-28T00:00:00Z",
  "title": "…",
  "auth_state": "authenticated",
  "parser_version": "esjzone-v1",
  "warnings": []
}
~~~

不保存 Cookie、authorization、密码和未授权私讯内容。

## 3. 解析流水线

### 3.1 列表页

1. 选取 .card.mb-30。
2. 从详情 href 提取 novel ID。
3. 从 .card-ep a 解析最新章节。
4. 从图标对应 .card-other 解析统计数字。
5. 优先读取 .lazyload[data-src] 封面。
6. 读取 #page-selection 和内联 bootpag 的 total。

解析数字时统一：

- 去掉千位逗号和空白。
- 纯数字转整数。
- 空字符串、非数字或广告文本转 null。

### 3.2 详情页

1. 从 URL 取得 novel ID。
2. 读取 #vtimes、#favorite、#txt。
3. 解析 .book-detail 的标签和值。
4. 解析 #integration details，按 DOM 顺序生成 sections。
5. 用 a[data-title] 生成 ChapterRef。
6. 保存按钮当前文案，但不把“已收藏”当作接口成功证明。

目录正典优先级：

TOC DOM 顺序 > 章节数字推断 > btn-prev/btn-next

### 3.3 章节页

1. 解析 .forum-content.mt-3 的 innerHTML。
2. 仅从该容器收集正文图片。
3. 解析 single-post meta。
4. 解析 a.view-all 关联详情。
5. 将 .comments-section 与 .comment 分离保存。
6. 记录 .comments-page-N 为本地 page group，不发起分页请求。

正文清洗要保留：

- p
- br
- img src alt class
- 原始相对/绝对链接

不得把评论、页脚、广告或举报表单混入正文。

## 4. 认证适配

### 4.1 浏览器优先

登录由支持的浏览器认证能力或用户手动完成。客户端只检查：

- 最终 URL 是否从 /my/login 跳转到会员页。
- 会员侧栏是否存在。
- 页面是否出现当前用户数据。

不读取密码和会话 Cookie。

### 4.2 动态 token

对于 /inc/mem_view_del.php 与 /inc/load_msg.php：

1. 通过页面运行时取得 getAuthToken 回调结果。
2. 仅在当前用户明确确认对应动作时发送。
3. 将 token 放在内存请求头 authorization。
4. 请求完成后立即丢弃。

若无法调用 JinJing().Callback，返回 requires_browser_auth。

## 5. 读取接口适配器

### 5.1 ViewHistory

解析 /my/view：

- record_id = .view-del[data-id]
- novel_id = /detail/{id}.html
- last_post_id = /forum/{novelId}/{postId}.html

删除能力默认注册为 disabled。脚本证实的请求形状见 API_ENDPOINTS.md，但不应在只读同步中调用。

### 5.2 Messages

私讯列表需要先从 .chat-list-box[data-id] 取得目标用户 ID，再通过浏览器 token 调用 /inc/load_msg.php。响应中的 html 应使用独立 MessageHtmlParser，禁止直接拼入信任边界外的页面。

当前未点击联系人，响应 JSON 字段虽由内联脚本使用得到线索，但真实实例未验证，解析器必须容忍缺字段。

### 5.3 Bootstrap Table

/my/record 与 /my/fixed 的 data-url 是页面配置证据，不是已抓到的网络响应。适配器应：

- 先读取 data-url、page-size、pagination、search。
- 使用可配置的参数编码器。
- 校验响应是否为 JSON。
- 对字段结构只做版本化映射。
- 若返回 HTML 登录页，报告 auth_required。

不要凭空假设 Bootstrap Table 的 limit、offset、search 参数名已被站点接受。

## 6. 写操作开关

定义：

~~~text
READ_ONLY = default
WRITE_ENABLED = explicit per-action confirmation
~~~

以下动作不应自动执行：

- 添加/取消收藏。
- 删除观看记录。
- 发表章节评论或留言。
- 举报。
- 更新昵称、密码、年龄、私介和黑名单。
- 创建/回复工单。
- 创建小说。
- 登出。

当前详情页已经显示“已收藏”，所以同步收藏状态时只读按钮文案和样式。

## 7. 缓存与节流

- 公开详情、目录和章节可按 URL 缓存。
- 收藏和观看记录按用户会话隔离缓存。
- cache key 至少包含规范 URL、认证状态、解析器版本。
- 不用 query 参数伪造 cache bust。
- 列表分页按页缓存，失败页不覆盖成功快照。
- 低频串行请求，遇到超时停止扩张，不对同一页面循环重试。

## 8. 错误分类

建议错误类型：

| 错误 | 含义 |
|---|---|
| auth_required | 跳回登录或会员侧栏消失 |
| captcha_required | 出现 CAPTCHA，需要用户处理 |
| page_not_found | 只有在实际可确认的页面提示/状态后使用 |
| parse_error | HTML 存在但选择器契约不匹配 |
| dynamic_unknown | data-url/API 存在但响应结构未确认 |
| external_host | 资源或章节链接来自 .one 等其他 host |
| write_confirmation_required | 需要用户动作确认 |
| browser_unavailable | 云浏览器或认证运行时不可用 |

本次不存在资源检查因浏览器超时未完成，因此不能把 /detail/0.html 或 /forum/1716174812/0.html 标成 page_not_found。

## 9. 回归测试清单

### 公开页面

- 首页可见搜索按钮点击后输入框出现。
- 关键词进入 /tags/{keyword}/。
- 搜索排序 4 进入 /tags-04/{keyword}/。
- /list-31/ 有 40 张卡片，分页脚本 total 为 32。
- /update/ 有 tab0 至 tab5，仅一个面板初始可见。
- 详情页 #integration 能得到卷和章节 href。
- 章节页正文与评论区不混淆。
- 论坛分类能解析 .forum-desc。

### 已登录只读页面

- /my/login 在已登录状态跳到 /my/profile。
- /my/favorite 能解析详情、最新章节、最后观看。
- /my/view 能解析 .view-del[data-id]，但测试不点击。
- /my/record 与 /my/fixed 能发现 data-url。
- /my/message 能发现 load_msg.php 脚本线索。

### 安全回归

- 测试不会提交表单。
- 测试不会点击收藏、删除、登出。
- 日志不含 Cookie、token、密码、完整私讯。
- 外部 .one 链接不自动转换为写请求。

## 10. 最小交付顺序

1. Route Builder + BrowserTransport + NovelCardParser
2. NovelDetailParser + ChapterParser
3. Search/List/Update pagination
4. UserFavoriteParser + ViewHistoryParser
5. Forum/Guestbook/CommentParser
6. 动态会员接口只读适配
7. 写操作逐项、逐次确认后再评估

