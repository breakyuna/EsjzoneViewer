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

### WeeklyPopularNovel

用于首页上周热门前十名轮播。`rank` 和 `weekly_views` 来自首页侧栏；封面、简介、作者、类型及成人标记由缓存优先的详情页解析补全。`weekly_views` 与详情页累计 `view_count` 是不同指标。

| 字段 | 类型 | 说明 |
|---|---|---|
| rank | integer | 首页榜单 DOM 顺序，从 1 开始 |
| weekly_views | integer | 上周热度数值 |
| description_preview | string | 详情简介的短文本预览 |
| type | string | 详情页作品类型 |
| cover_url/title/detail_url | string | 详情展示和导航字段 |
| author | string/null | 详情作者 |
| is_r18 | boolean | 详情标签判定 |

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

### 应用内部正文快照（非网站接口）

`DownloadedChapterContent.schemaVersion = 2` 的 `body` 为显式 `ChapterBody` DTO：`schemaVersion`、`parserVersion`、有序 `blocks`、SHA-256 `fingerprint` 与 UTF-16 `textLength`。块类型为 paragraph/text/image/break；文本片段保存样式标签、数值参数及 ruby 读音。图片原 URL 属于正文身份，下载相对路径及媒体类型仍位于 `components` 资源记录中，不参与指纹。`contentHtml` 仅用于富结构导出。

下载 manifest 版本 2 另存 `catalogComplete`；章节记录包含 `bodyAvailable`、`textLength` 和仅本地保留的 `localOnly`。正文可读与图片齐全分别判断，部分选择不会被标为全本完整。增量提交仅补新章、缺章和缺图，不覆盖有效的已有正文。

页面 HTML、结构化页面快照和离线章节 JSON 的新写入使用 gzip，文件名与 JSON 字段保持不变；读取按 gzip 文件头识别格式，继续支持旧 UTF-8 文本。下载 manifest 保持明文 JSON，`contentHtml` 继续保留，TXT、EPUB 导出及新旧混合备份无需预先迁移。缓存容量与下载占用按实际文件字节统计。

下载章节索引首次使用时扫描下载目录；后续清单提交和删除只更新对应小说的索引，批量导入及更换存储目录时重建。清单读取和合并在同一存储锁下复用已提交缓存；原子写入成功后发布新清单，单章自动保存复用已写入的正文信息，避免再次解析该章节文件。

备份按小说复制独立快照并在小说之间释放存储锁。manifest 独立写入并移除通用密码，随后在存储锁外流式追加到原有 ZIP 路径，完成一本即删除该临时快照，避免同时保留整库副本。[AOSP SELinux 策略](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/app_neverallows.te)禁止普通应用创建硬链接，不能依赖硬链接减少复制。

本地阅读历史新增可空的字符锚点和全书进度。锚点包含章节键、正文指纹、块序号、UTF-16 偏移或图片内比例；仅匹配同一正文快照，正文不同则采用旧比例恢复，不进行正文版本间重定位。

正文累计字符长度按不可变章节快照计算一次，位置查询不再拼接或扫描整章。原文模式直接使用显示字符偏移；繁简转换在后台同时准备文字与段落字符映射，阅读窗口持有映射，不依赖全局转换缓存持续命中。

滚动位置以可见行的源字符锚点更新，同一行内的像素变化不更新阅读历史位置；图片仍保留图内比例。章节窗口更新时，繁简转换复用同一文字模式下的已有段落映射，只保留新窗口需要的记录。滚动段落的排版结果随组合释放，不累计保留已退出组合的段落。

全书进度显示与滑块选章共用完整目录的字符权重；任一章节字数未知时，两者均回退到章节数权重。滑块仍定位到目标章节开头。历史、书架与阅读器复用按小说缓存的已发布清单，写入、删除、导入和更换存储目录时同步更新缓存。

阅读器随目录或下载清单变化在后台建立全书累计字数索引；滚动进度通过索引查询，滑块按累计字数二分选章，不在重组中遍历目录或解析全部章节 URL。

规范化目录及相邻章节索引一并替换，追加、前插和状态发布直接查询索引；正文快照构建与目录规范化不占用章节窗口的状态锁。

## 6. Comment

| 字段 | 类型 | 说明 |
|---|---|---|
| comment_id | string | .comment id 去掉 comment- |
| parent_post_id | string | 所属章节/帖子 |
| author_id | string/null | 用户链接 query uid |
| author_name | string/null | header 文本 |
| author_url | string/null | 支持 .html 与无扩展名 |
| author_avatar_url | string/null | .comment-author-ava .lazyload-author-ava 的 data-src 或 background-image，也兼容 img |
| floor | string/null | comment-floor |
| created_at | datetime/null | comment-meta |
| content_html | string | comment-text HTML |
| content_text | string | 纯文本 |
| quoted_content_text | string/null | 回复正文前 blockquote 的引用文本 |
| page_group | integer/null | 客户端固定每 15 条计算的页码 |
| is_visible_initially | boolean | 首屏显隐状态 |
| reply_token | string/null | `.forum_reply[data-comment]` 原样读取；格式为 `{commentId}-{authorUserId}`，回复提交时直接作为 `reply` 字段 |

评论分页是客户端固定每页 15 条的本地分页，不代表服务器 page API；评论区 UI 统一提供首页、上一页、下一页和末页操作。

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
| record_id | string | 优先 .view-del[data-id]，其次 tr 的 view_ 后缀；对应 HistoryNovel.vid，不是小说或章节 ID |
| novel_id | string | 详情链接 |
| novel_title | string | 详情链接文本 |
| last_post_id | string/null | 最后章节链接 |
| last_post_title | string/null | 章节文本 |
| delete_endpoint | string | /inc/mem_view_del.php |

## 10. ForumCategory、ForumThread 与 ForumBoard

### ForumCategory

| 字段 | 类型 |
|---|---|
| category_id | string |
| group_name | string |
| name | string |
| description | string/null |
| post_count | integer/null |
| url | string |

### ForumThread

| 字段 | 类型 |
|---|---|
| category_id | string |
| thread_id | string |
| title | string |
| topic_count | integer/null |
| reply_count | integer/null |
| last_post_date | date/null |
| url | string |

### ForumTopic

| 字段 | 类型 |
|---|---|
| board_id | string |
| post_id | string |
| title | string |
| author | string/null |
| created_at | date/null |
| reply_count | integer/null |
| view_count | integer/null |
| last_reply_at | date/null |
| url | string |

### ForumBoardResult

| 类型 | 字段 | 说明 |
|---|---|---|
| Topics | items: list[ForumTopic] | 天空大公國等普通讨论板；列表来自 Bootstrap Table 动态 JSON |
| Topics | total_count: integer/null | `data-url` 或 JSON 的总主题数 |
| Novel | detail_url: string | ESJ 作品板对应的 `/detail/{novelId}.html`，由页面正向证据识别 |
| Novel | items: list[ForumTopic] | 非 ESJ 页面兼容字段；五个 ESJ 小说分类不再解析旧主题表 |
| Novel | total_count: integer/null | 非 ESJ 页面兼容字段；ESJ 小说旧入口为空 |

`ForumTopic.board_id` 必须从最终主题 URL `/forum/{boardId}/{postId}.html` 读取。天空大公國讨论板中它等于当前讨论板 ID；五个 ESJ 小说分类不再从旧入口构造 `ForumTopic`。

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
- Novel 可通过 forum_url 连接到 ForumCategory 与论坛子板块页面；ESJ 作品板由 detail 链接识别，天空大公國讨论板按动态主题表识别

## Wenku8 本地身份扩展

独立文库作品使用 `wenku8:{bookId}` 本地身份，ESJ 数字身份保持不变。详情 URL、章节身份和导航恢复规则见 [WENKU8.md](WENKU8.md)。ESJ 作品中的文库外链仍采用显式传入的 ESJ 所属作品身份；详情／目录已按固定上游候选规则接入，本地书架使用 `wenku8:local` scope；真实站点及后续修改统一验证仍待完成。
