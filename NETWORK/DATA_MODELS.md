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

下载 manifest 版本 2 另存 `catalogComplete`；章节记录包含 `bodyAvailable`、`textLength` 和仅本地保留的 `localOnly`。正文可读与图片齐全分别判断，部分选择不会被标为全本完整。增量提交仅补新章、缺章和缺图，不覆盖有效的已有正文。正文成功保存后，图片超时、TLS 握手失败等图片获取错误不再使下载任务失败；保留图片原 URL、已有正文与已下载图片，`bodyAvailable` 为 true，缺图章节的 `downloaded` 及全书 `complete` 仍为 false，后续继续下载只补缺图。批量密码解锁以正文保存成功为准，缺图不计为解锁失败。

通用密码批量解锁复用 `NovelDownloadWorker`，与同一本书的普通下载串行执行。私有下载目录将通用密码保存在本地 manifest；公共下载目录将密码保存在私有 `download_secrets`，manifest 的 `commonPassword` 为 null，读取时在内存中补充。迁移时先保存私有密码，再生成不含密码的公共 manifest。任务参数仅保存站点和待解锁章节范围，不携带密码。Worker 按该范围读取本地清单并逐章解锁、保存，不重新请求小说详情；页面及通知使用 WorkManager 进度，暂停取消该书任务。重试跳过已完成章节，保留尚未下载完整的图片；密码不匹配保留为待解锁，网络或保存失败执行有界重试。

页面 HTML、结构化页面快照和离线章节 JSON 的新写入使用 gzip，文件名与 JSON 字段保持不变；读取按 gzip 文件头识别格式，继续支持旧 UTF-8 文本。下载 manifest 保持明文 JSON，`contentHtml` 继续保留，TXT、EPUB 导出及新旧混合备份无需预先迁移。缓存容量与下载占用按实际文件字节统计。

下载文件可位于旧私有目录或用户通过 SAF 授权目录的 `downloads/`。首次登录成功或启动时恢复已有会话后，若尚未选定公共目录，先显示全屏存储引导页：首次安装使用欢迎文案，恢复已登录会话且仍使用私有目录的升级用户使用迁移文案。引导类型写入存储设置，登录后重启也保持原文案。选择并验证目录后，先持久化待迁移目标并交给唯一 WorkManager 后台任务；此时即可点击底部“下一步”进入主界面。现有下载和自动备份迁往目标的 `downloads/`、`autobackup/`，复制期间继续从旧目录读取，暂停下载写入、内容删除及备份导入；文件复制不持有下载存储锁。取消系统选择器仍停留在引导页，失败可重试或重新选择目录且不删除原文件；已选公共目录或已登记后台迁移目标的用户不重复引导。公共目录暂时断开时仍允许启动并进入设置修复，不静默切回私有目录。公共下载目录被删除后，后续写入重建目录并补建 `.nomedia`。

后台迁移恢复后，已有目录或已记录引导类型的启动路径不等待迁移锁。新目录提交后，旧文件清理日志清空前继续暂停下载写入、内容删除和备份导入，并暂停自动备份的手动删除与轮换；等待阅读页释放旧图片或清理失败时仍保持保护，阅读及新自动备份可继续。清理全部完成并持久化移除日志后才恢复这些操作；重启同样从待迁移目标或清理日志恢复保护。

应用日志自动另存到已选择公共目录的 `logs/`：每次应用进程启动使用本地时间创建 `app_yyyy-MM-dd_HH-mm-ss-SSS.txt`，同次启动继续追加；单份达到 2 MiB 上限时使用 `-part2` 等后缀另建分段，同毫秒名称冲突也使用分段后缀避免覆盖。历史日志不自动删除，既有异常命名的 `app_logs.log.txt` 和带数字后缀的文件保留。`last_crash.log` 用于最近的未捕获异常报告，崩溃报告同样有大小限制。日志目录在 Application 创建时独立读取已持久化的选择；已登记迁移目标时使用该目标，不等待数据库、缓存或存储迁移初始化。选择恢复前最多暂存 500 条、合计 2 MiB 的脱敏记录并随后补写；后续切换目录时，新记录跟随选择，旧目录的日志不搬迁。SAF 写入由独立线程逐条追加并关闭，不占用主线程和私有日志线程；目录不可访问时保留有界待写记录，后续记录触发重试。未选择公共目录或选择私有存储时只保留原有私有日志。应用内清空日志会清理当前公共目录中上述新旧命名的日志及崩溃报告，不删除其他文件；异常退出时最多等待公共崩溃报告写入 2 秒，系统直接杀进程或文件提供方持续不可用时不能保证最后记录写入。该功能记录应用日志及启动阶段，不抓取其他应用的系统日志。

Application 启动后的首分钟另由独立线程检查主线程心跳。主线程接受心跳任务后约 5 秒仍未执行时，以 `StartupWatchdog` 标记向现有应用日志写入一次进程内线程堆栈：主线程优先，最多 64 个线程、每线程最多 64 帧，不读取局部变量、请求正文或账户凭据。该记录表示主线程长时间未响应，不等同于系统已经判定 ANR。根导航切换、主界面组合提交和首次绘制完成分别记录阶段标记，不能将“初始化完成”视为主界面已经显示。

迁移和小说索引扫描复用目录列表返回的类型信息，避免逐个查询子目录类型；迁移先读取小说清单，没有有效清单的目录不再递归扫描。迁移重试逐字节校验时，整轮复用两个 64 KiB 缓冲区；重启后的清理校验按小说复用，保留文件长度、完整内容及取消检查。同一阶段、同一本书的文件计数向界面最多每 250 毫秒发布一次，阶段、作品切换及完成立即发布，通知继续使用原有节流。

小说目录使用书名，替换路径分隔符、控制字符等不适合作为文件名的字符，限制 UTF-8 长度；重名或已有同名文件时添加短 URL 摘要后缀，不覆盖其他作品。作品身份仍为 manifest 中的规范化 URL，目录名称不参与身份判断；首次访问下载目录时建立 URL 到实际目录的索引，并尝试将旧哈希目录重命名为书名，重命名失败时继续读取原目录。同一路径重新扫描不重命名正在使用的目录；外部改名后可通过重新扫描恢复定位。迁移与备份导入按清单 URL 查找已有作品，兼容旧哈希目录和书名目录；书名变化不会创建重复下载或移动正在写入的目录。章节和图片文件名、URL 标识和文件格式保持不变；公共图片按内容 URI 加载，EPUB 导出直接读取内容流。下载章节索引首次使用时扫描下载目录；后续清单提交和删除只更新对应小说的索引，批量导入及更换存储目录时重建。清单读取和合并在同一存储锁下复用已提交缓存；原子写入成功后发布新清单，单章自动保存复用已写入的正文信息，避免再次解析该章节文件。

离线图片按小说懒加载 URL 摘要到文件路径的内存索引，首次访问扫描该书 `images/`，后续命中和缺失查询不再遍历目录。图片原子提交后在同一存储锁内更新索引；删除小说、导入备份和切换存储目录时失效。图片文件格式、URL 摘要及备份目录结构保持不变。

备份按小说在锁内固定开始时的 manifest 及文件列表，按目录批量获取 SAF 元数据，再逐文件持锁复制，文件之间及 ZIP 打包阶段释放存储锁；目录切换或删除导致存储令牌失效时停止快照发布。manifest 独立写入并移除通用密码，随后在存储锁外通过缓冲流追加到原有 ZIP 路径，完成一本即删除该临时快照，避免同时保留整库副本。gzip 章节和图片使用无压缩 DEFLATE 块，旧明文 JSON 仍正常压缩，不改变 ZIP 格式及恢复方式。自动备份直接写入目标目录的临时 ZIP，关闭成功后再重命名发布，不在私有缓存保留完整 ZIP 或再次复制整包。备份复制、ZIP 压缩及解压在文件之间和数据块之间检查取消；恢复中断时删除尚未提交的小说临时目录，并刷新已提交小说的内存索引。[AOSP SELinux 策略](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/app_neverallows.te)禁止普通应用创建硬链接，不能依赖硬链接减少复制。

本地阅读历史新增可空的字符锚点和全书进度。锚点包含章节键、正文指纹、块序号、UTF-16 偏移或图片内比例；仅匹配同一正文快照，正文不同则采用旧比例恢复，不进行正文版本间重定位。

正文累计字符长度按不可变章节快照计算一次，位置查询不再拼接或扫描整章。原文模式直接使用显示字符偏移；繁简转换在后台同时准备文字与段落字符映射，阅读窗口持有映射，不依赖全局转换缓存持续命中。

滚动位置以可见行的源字符锚点更新，同一行内的像素变化不更新阅读历史位置；图片仍保留图内比例。章节窗口更新时，繁简转换复用同一文字模式下的已有段落映射，只保留新窗口需要的记录。滚动段落的排版结果随组合释放，不累计保留已退出组合的段落。

全书进度显示与滑块选章共用完整目录的字符权重；任一章节字数未知时，两者均回退到章节数权重。滑块仍定位到目标章节开头。历史、书架与阅读器复用按小说缓存的已发布清单，写入、删除、导入和更换存储目录时同步更新缓存。历史与书架另按小说复用目录索引、累计字数索引及上一条阅读位置的投影；只有对应小说清单变化才使该索引失效，其他小说的下载进度通知不再触发全目录重算。

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


### 公共目录后台迁移与中断恢复

待迁移目标与实际生效目录分别发布状态；本地备份页监听实际生效目录，在根目录提交切换时刷新归档，即使仍在等待阅读页退出清理也使用新目录。小说详情的清除通用密码及单章解锁须确认写入成功后才更新界面并提示成功；写入暂停时保留原显示状态并提示稍后重试。

迁移按本书源文件清单复制到独占暂存目录，直接写入最终章节和图片文件名；每个目录一次查询同时取得文件类型和大小，写后批量核对，最后写入去除密码的 manifest，再重命名发布整本目录。暂存目录名称在创建前记录到私有存储设置，进程中断后的重试只清理该任务记录的未完成目录，不根据名称批量删除其他目录。已经发布的目标书先核对清单与逐文件内容，一致则跳过再次复制，冲突保留双方并报告失败。普通下载和备份仍保留原有逐文件替换保护。

SAF 的 URI 缓存按父目录记录直接子项；刷新枚举只核对当前目录，重命名和删除只失效对应子树，避免每本书反复遍历全部已缓存文件。该索引仅用于定位 URI，不长期缓存文件大小或类型。一次下载目录迁移顺序复用同一个 64 KiB 复制缓冲区，减少大量章节、图片逐文件复制时的数组分配；仍逐块检查取消并保留写后大小校验。

迁移校验逐目录枚举目标文件，复用源文件路径映射逐项核对并移除已验证项，不再同时保存完整目标文件列表和映射。新暂存目录必须没有缺失、多余或大小不同的文件；已有目标副本保留无关文件，但每个源文件仍须存在且内容完全一致。重启清理时直接从目标枚举建立必要的查询索引，源文件边遍历边比较，不额外保留完整源文件列表；目录进入与条目遍历均检查取消。

已有目标副本按规范化小说 URL 匹配。相同副本复用；同名但 URL 不同的小说另用带摘要后缀的目录。匹配到同一小说后，清单按完整字段比较（公共目录去除密码），下载时间、作品信息或章节状态不同也会阻止迁移；已存在的正文和图片还须逐字节一致。因此前次测试留下的旧下载副本可能造成持续冲突，单纯重试不会合并或覆盖。冲突保留双方，迁移阶段可选择空目录，或由用户备份后处理指定冲突副本。

后台失败状态保存原因类别和相关小说/文件的相对路径，并在设置、存储引导和下载页显示；重启保留诊断。源清单解析失败、版本不支持或必需字段缺失会停止迁移，不能当作无下载数据静默跳过；没有清单的无关目录仍忽略。迁移读取清单绕过旧缓存，避免旧有效缓存掩盖损坏或冲突。失败时先发布内存状态，再尝试持久化；即使 DataStore 写入失败也保留失败和重试入口，但这次诊断可能无法跨重启保留。日志记录阶段、原因、相对路径及异常类型，不输出原始清单、密码或提供方异常文本。普通异常等待手动重试；等待阅读器释放走 WorkManager 重试。清理阶段的副本冲突提示恢复当前副本，不建议改换新目录，以免丢失清理目标。

全部复制成功后，在同一次 DataStore 更新中提交新目录及精确的源书籍、备份清理清单，随后才删除原文件。清理每完成一项即移除对应记录，中断后先重新验证目标章节及仍存在的源文件副本，再继续剩余清理；目标不可访问或不一致时保留剩余原文件。持有旧图片路径的阅读页退出前保留原文件。WorkManager 因系统停止而中断时保留任务记录，下次运行或打开应用后继续；系统设置中的强行停止可能阻止任务运行，必须再次启动应用。权限丢失、空间不足、目标内容冲突等失败不会自动删除原文件；设置和下载页显示阶段、文件进度与重试入口。存储提供方故障和设备故障仍可能造成失败，不承诺任意故障下绝对无损。

### 存储往返迁移与批量统计

Release 点击设置中的存储目录直接打开系统文件夹选择页面；Debug 保留“选择文件夹 / 迁回应用私有目录”的选项弹窗。系统目录选择完成后仍确认是否迁移已有文件。

仅 Debug 版本的存储位置设置提供实验性的“迁回应用私有目录”，可将公共目录内的下载和自动备份迁回原私有路径 `files/downloaded_novels`、`files/auto_backups`，之后仍可选择公共目录迁出。Release 通过 `BuildConfig.DEBUG` 隐藏入口及确认弹窗，同时禁止直接调用及尚未提交的私有目标后台迁移；已提交目录切换的任务仍允许完成旧文件清理，避免永久锁住下载写入。显式私有选择以内部标记 `private` 持久化，与尚未完成引导的空值区分；该标记不作为 URI 解析。两个方向共用 WorkManager、暂存日志、提交后清理与阅读页保护流程；仅在迁移完成后删除对应源文件，不请求站点或重新下载。私有文件会随卸载应用删除，迁回前需确认。公共清单不写通用密码，迁回私有目录时从私有密码存储恢复至私有清单。

下载列表与存储分析按目录批量读取文件类型和大小，递归时直接使用枚举元数据。下载列表先发布书名和已保存章节数，大小尚未完成时显示统计状态，随后发布准确大小并缓存；设置统计只读取书籍元数据，下载占用复用完整存储分类结果，不再先递归计算每本书大小。进程重启后的首次访问及主动刷新仍需实际访问存储提供方，不承诺固定加载时间。
