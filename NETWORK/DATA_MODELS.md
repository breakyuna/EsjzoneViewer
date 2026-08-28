# ESJ Zone 数据模型

## 1. 建模原则

- ID 默认保存为字符串，避免超长数字或跨语言精度问题。
- 原始 URL 与规范化 URL 同时保存。
- 页面缺失字段使用 null；无法确认的协议字段使用 UNKNOWN。
- ForumPost 同时覆盖小说章节和普通论坛帖子，但用 kind 区分。
- HTML 正文与纯文本摘要分开保存。

## 2. Novel

| 字段 | 类型 | 来源 |
|---|---|---|
| novel_id | string | /detail/{novelId}.html |
| title | string | 详情 h2 或卡片标题 |
| type | enum/string | 详情信息，如韩轻、日轻、原创 |
| author_name | string | 详情作者链接 |
| author_url | string | /tags/{author}/ |
| other_title | string/null | 详情其他标题 |
| source_url | string/null | Web 生肉链接 |
| cover_url | string/null | 封面 img 或 lazy data |
| description_html | string/null | 详情说明区域 |
| view_count | integer/null | #vtimes |
| favorite_count | integer/null | #favorite |
| word_count | integer/null | #txt，去逗号 |
| forum_url | string/null | a.btn-forum |
| updated_at | date/null | 详情信息 |
| is_r18 | boolean/unknown | 列表徽标或详情字段 |
| source_page | string | 抓取页面 |

## 3. NovelCard

用于首页、更新、列表和标签搜索结果。

| 字段 | 类型 | 说明 |
|---|---|---|
| novel_id | string | 从 detail URL 提取 |
| title | string | 卡片标题 |
| detail_url | string | 原始详情 URL |
| latest_post_id | string/null | 最新章节 URL 提取 |
| latest_title | string/null | .card-ep |
| latest_url | string/null | 原始 host 保留 |
| author_name | string/null | 卡片作者 |
| author_url | string/null | 标签页 URL |
| cover_url | string/null | data-src |
| word_count | integer/null | 卡片统计 |
| view_count | integer/null | 卡片统计 |
| favorite_count | integer/null | 卡片统计 |
| article_count | integer/null | feather 统计 |
| discussion_count | integer/null | message-square 统计 |
| is_r18 | boolean/unknown | badge |

## 4. NovelSection 与 ChapterRef

### NovelSection

| 字段 | 类型 | 说明 |
|---|---|---|
| novel_id | string | 所属小说 |
| section_index | integer | DOM 顺序 |
| title | string | details > summary |
| is_empty | boolean | 本次 Q&A 卷为 true |
| chapter_refs | list[ChapterRef] | 目录链接 |

### ChapterRef

| 字段 | 类型 | 说明 |
|---|---|---|
| novel_id | string | URL segment |
| post_id | string | URL segment |
| title | string | data-title 优先 |
| url | string | 原始 href，可能含 fragment |
| section_index | integer | 所属卷 |
| section_title | string | 所属卷名 |
| ordinal | integer/null | 仅对可解析的数字章节保存 |
| is_extra | boolean | 漫画、杂项、Q&A 等非章节 |

目录样本：782 个链接、780 个数字章节、2 个漫画链接。不能假设章节编号连续或每一卷都非空。

## 5. ForumPost

| 字段 | 类型 | 来源 |
|---|---|---|
| post_id | string | /forum/{novelId}/{postId}.html |
| novel_id | string/null | 小说章节 URL；普通帖子可能 null |
| category_id | string/null | 面包屑或论坛 URL |
| title | string | 页面标题/主标题 |
| kind | enum | chapter、forum_thread、announcement、unknown |
| author_name | string/null | single-post meta |
| author_url | string/null | 用户链接 |
| published_at | datetime/null | meta 文本 |
| content_html | string/null | .forum-content.mt-3 |
| content_text | string/null | HTML 转纯文本摘要 |
| image_urls | list[string] | 正文 img |
| view_count | integer/null | file-text meta |
| discussion_count | integer/null | .btn-likes |
| prev_url | string/null | a.btn-prev |
| next_url | string/null | a.btn-next |
| novel_detail_url | string/null | a.view-all |
| source_url | string | 当前 URL |

## 6. Comment

| 字段 | 类型 | 说明 |
|---|---|---|
| comment_id | string | .comment id 去掉 comment- |
| parent_post_id | string | 所属章节/帖子 |
| author_id | string/null | 用户链接 query uid |
| author_name | string/null | header 文本 |
| author_url | string/null | 支持 .html 与无扩展名 |
| floor | string/null | comment-floor |
| created_at | datetime/null | comment-meta |
| content_html | string | comment-text HTML |
| content_text | string | 纯文本 |
| page_group | integer/null | .comments-page-N |
| is_visible_initially | boolean | 首屏显隐状态 |

评论分页是本地 DOM 分组，不代表服务器 page API。留言板观察到 8 组，章节页样本为 3 组。

## 7. UserProfile

| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | string/null | uid query |
| nickname | string | 页面昵称 |
| profile_url | string | /my/profile?uid={uid} 或 .html |
| exp | integer/null | 经验值文本 |
| level | string/null | 如 F级 Lv2、SSS級 Max |
| registered_at | date/null | 注册日期 |
| age_visibility | enum/unknown | 预设/已满18/未满18 |
| bio_html | string/null | 个人简介 |
| open_message | boolean/unknown | 仅自己的编辑页有控件 |
| display_post | boolean/unknown | 仅自己的编辑页有控件 |

公开资料页还提供：

- /my/profile?uid={uid}
- /my/book?uid={uid}
- /my/post?uid={uid}
- /my/favorite?uid={uid}

这些页面是否允许访问全部字段需按用户权限单独验证。

## 8. FavoriteRecord

| 字段 | 类型 | 说明 |
|---|---|---|
| novel_id | string | 详情 URL |
| novel_title | string | 行内详情链接 |
| latest_post_id | string/null | 最新章节链接 |
| latest_title | string/null | 最新： 后文字 |
| last_viewed_post_id | string/null | 最後觀看： 链接 |
| last_viewed_title | string/null | 观看记录文本 |
| updated_at | date/null | 更新日期 |
| list_sort | enum | new 或 udate |

## 9. ViewRecord

| 字段 | 类型 | 说明 |
|---|---|---|
| record_id | string | .view-del[data-id] |
| novel_id | string | 详情链接 |
| novel_title | string | 详情链接文本 |
| last_post_id | string/null | 最后章节链接 |
| last_post_title | string/null | 章节文本 |
| delete_endpoint | string | /inc/mem_view_del.php |

## 10. ForumCategory 与 ForumThreadCard

### ForumCategory

| 字段 | 类型 |
|---|---|
| category_id | string |
| group_name | string |
| name | string |
| description | string/null |
| post_count | integer/null |
| url | string |

### ForumThreadCard

| 字段 | 类型 |
|---|---|
| category_id | string |
| thread_id | string |
| title | string |
| topic_count | integer/null |
| reply_count | integer/null |
| last_post_date | date/null |
| url | string |

## 11. Pagination 与 PageSnapshot

### Pagination

| 字段 | 类型 | 说明 |
|---|---|---|
| current_page | integer/null | 内联脚本或 active item |
| total_pages | integer/null | bootpag total 或最后页链接 |
| page_size | integer/null | 页面观察值 |
| next_url | string/null | 服务器分页 |
| is_local | boolean | javascript:void(0) 本地分页 |

### PageSnapshot

建议每次抓取保存：

- url
- canonical_url
- fetched_at
- title
- html_hash
- parser_version
- auth_state
- warnings

不保存原始 Cookie、authorization、密码或私讯敏感内容。

## 12. 关系

- Novel 1 -> N NovelSection
- NovelSection 1 -> N ChapterRef
- ChapterRef 1 -> 1 ForumPost
- ForumPost 1 -> N Comment
- UserProfile 1 -> N FavoriteRecord
- UserProfile 1 -> N ViewRecord
- ForumCategory 1 -> N ForumThreadCard
- Novel 可通过 forum_url 连接到 ForumCategory 与单本论坛页面

