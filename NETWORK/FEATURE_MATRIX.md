# ESJ Zone 功能矩阵

状态说明：

- **已验证**：页面或 DOM 结构直接观察到。
- **部分验证**：入口或脚本配置已观察到，但动态响应或完整交互未验证。
- **未验证**：当前没有足够证据，不应实现为确定协议。

| 功能 | 路由/入口 | 登录 | 当前证据 | 写风险 | 客户端优先级 | 备注 |
|---|---|---:|---|---|---|---|
| 首页浏览 | / | 否 | 已验证 | 无 | P0 | 卡片和导航 |
| 搜索 | 首页搜索框 → /tags/{keyword}/ | 否 | 已验证 | 无 | P0 | path segment，不是 query |
| 搜索排序 | /tags-{sort}/{keyword}/ | 否 | 已验证 | 无 | P0 | tags-04 已观察 |
| 搜索分类 | #category | 否 | 部分验证 | 无 | P1 | 交互中未改变 URL，组合路由未知 |
| 小说分类 | /list-01/、/list-11/、/list-21/、/list-31/ | 否 | 已验证 | 无 | P0 | 韩轻 32 页 |
| 列表分页 | /{listPrefix}/{page}.html | 否 | 已验证 | 无 | P0 | 服务器 HTML |
| 一週更新 | /update/ | 否 | 已验证 | 无 | P0 | 6 个日期 tab 一次性加载 |
| 标签结果 | /tags/{tag}/ | 否 | 已验证 | 无 | P0 | 与搜索共用模板 |
| 论坛首页 | /forum/ | 否 | 已验证 | 无 | P1 | 分类表 |
| 论坛分类 | /forum/{categoryId}/ | 否 | 已验证 | 无 | P1 | 静态主题矩阵 |
| 单本论坛 | /forum/{categoryId}/{novelId}/ | 否 | 部分验证 | 无 | P2 | 样本显示 Loading/暂无资料 |
| 小说详情 | /detail/{novelId}.html | 否 | 已验证 | 无 | P0 | 元数据和 TOC |
| 章节目录 | #integration details | 否 | 已验证 | 无 | P0 | 782 链接样本 |
| 正序/倒序 | 详情 TOC 按钮 | 否 | 已验证 | 无 | P1 | data-sort 从 1 切到 2，并反转目录 DOM |
| 章节正文 | /forum/{novelId}/{postId}.html | 否 | 已验证 | 无 | P0 | HTML 正文 |
| 正文图片 | .forum-content img | 否 | 已验证 | 无 | P1 | 直接 img URL |
| 章节前后导航 | .btn-prev/.btn-next | 否 | 已验证 | 无 | P1 | 论坛时序，不保证章节序 |
| 章节评论读取 | .comment | 可选 | 已验证 | 无 | P1 | 静态分组 |
| 评论本地分页 | .comment | 可选 | 已实现 | 无 | P1 | 客户端固定每页 15 条，并提供首页/末页 |
| 留言板读取 | /guestbook/ | 可选 | 已验证 | 无 | P1 | 站点有 8 个 DOM 组，客户端统一按 15 条分页 |
| FAQ | /faq/ | 可选 | 已验证 | 无 | P2 | 4 个 FAQ 入口 |
| 登录态保持 | /my/login → /my/profile | 是 | 已验证 | 无 | P0 | 当前会话已验证 |
| 会员资料读取 | /my/profile | 是 | 已验证 | 无 | P1 | 表单字段可解析 |
| 公开资料读取 | /my/profile.html?uid={uid} | 否/可选 | 已验证 | 无 | P1 | 有个人页、贴文、收藏入口 |
| 收藏列表读取 | /my/favorite | 是 | 已验证 | 无 | P0 | 20 条/页样本 |
| 收藏排序 | /my/favorite/udate/ | 是 | 已验证 | 无 | P1 | udate 路由 |
| 收藏状态读取 | button.btn-favorite | 是 | 已验证 | 无 | P0 | 样本为已收藏 |
| 添加/取消收藏 | 详情页按钮 | 是 | 未验证 | 高 | P1 | 外部脚本被拦截，当前未切换 |
| 观看记录读取 | /my/view | 是 | 已验证 | 无 | P0 | 16 条样本 |
| 删除观看记录 | /inc/mem_view_del.php | 是 | 部分验证 | 高 | P2 | 脚本和请求形状已知，未执行 |
| 我的贴文 | /my/post | 是 | 已验证 | 无 | P2 | 当前为空表 |
| 我的回复 | /my/reply | 是 | 部分验证 | 无 | P2 | bootpag total 0 |
| 私讯联系人 | /my/message | 是 | 部分验证 | 无 | P2 | 联系人/列表未渲染 |
| 加载私讯 | /inc/load_msg.php | 是 | 部分验证 | 中 | P2 | 脚本和请求形状已知，未点击 |
| 经验值记录 | /my/record | 是 | 部分验证 | 无 | P2 | data-url 已知，JSON 未抓 |
| 错漏字回报 | /my/fixed | 是 | 部分验证 | 无 | P2 | data-url 已知，JSON 未抓 |
| 提交错漏字回报 | 章节举报表单 | 是 | 未验证 | 高 | P3 | 未提交 |
| 问题与建议 | /my/ticket | 是 | 已验证 | 中 | P2 | 表单可见，未提交 |
| 系统消息 | /my/sys | 是 | 已验证 | 无 | P2 | 当前为空表 |
| 创建小说 | #createBook | 是 | 部分验证 | 高 | P3 | 公共模态出现，字段未完整展开 |
| 评论提交 | form.commentEditor | 是 | 已实现/未验证 | 高 | P1 | 详情页补 data=books、forum_id=0；章节页补 data=forum、章节 post id，未执行真实提交 |
| 留言提交 | form.gbEditor | 是 | 已实现/未验证 | 高 | P1 | 复用统一评论编辑器，未执行真实提交 |
| 举报 | form#forumReport | 是 | 未验证 | 高 | P3 | 未提交 |
| 资料更新 | .form-edit | 是 | 未验证 | 高 | P3 | 未提交 |
| 登出 | /my/logout | 是 | 未验证 | 高 | P3 | 未执行，避免破坏会话 |

## 1. 已知站点问题线索

- 详情页控制台出现 Syntax error, unrecognized expression: .comments-page-1 #https://www.esjzone.cc/detail/1716174812.html，疑似 hash 与 CSS selector 拼接错误。
- 留言板内联脚本按每 15 条评论计算 hash 页，但 DOM 分组实际约 50 条，分页模型存在不一致。
- 章节页前一篇可跳到非章节通知帖，不能将 prev/next 当作目录顺序。
- 部分最新章节卡片链接使用 www.esjzone.one，需保留原始主机。

## 2. 客户端建议

- P0 只实现公开阅读主链路与已登录收藏/观看记录读取。
- P1 实现评论读取、留言读取、论坛分类和完整字段解析。
- P2 接入动态会员表与私讯读取，但把网络响应结构做成可演进适配器。
- P3 其余高风险写入默认不实现；评论和留言提交已实现请求链路，但仍需在真实环境完成验证。
