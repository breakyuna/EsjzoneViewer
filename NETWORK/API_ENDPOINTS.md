# ESJ Zone 页面与动态接口清单

> 本文只记录实际观察到的 URL、表单、内联脚本和 DOM 配置。没有通过网络面板确认的请求，明确标为 Inferred 或 UNKNOWN / NOT VERIFIED。

## 1. 访问与证据边界

站点主体是传统服务端渲染 HTML。首页、列表、搜索、详情、章节、会员页和论坛页均可从 DOM 直接读取内容；本次没有获得浏览器网络层的 XHR/fetch 列表，也没有读取静态 JS 文件正文。外部脚本 modifyDetail.js?v=204 在云浏览器中直接打开时被客户端拦截，因此收藏等逻辑不做未经证实的 API 猜测。

所有需要登录的请求都应复用浏览器登录会话，不能把会话 Cookie 或授权值写入配置文件。

## 2. 页面 GET 路由

| 路由模板 | 方法 | 参数 | 返回形态 | 证据 |
|---|---|---|---|---|
| / | GET | 无 | HTML | Observed |
| /update/ | GET | 无 | HTML，六个日期面板 | Observed |
| /list-{route}/ | GET | route 如 01、31 | HTML，小说卡片 | Observed |
| /list-{route}/{page}.html | GET | page | HTML，列表分页 | Observed |
| /tags/{keyword}/ | GET | path keyword | HTML，标签结果 | Observed |
| /tags-{sort}/{keyword}/ | GET | sort、keyword | HTML，排序结果 | Observed |
| /tags-{sort}/{keyword}/{page}.html | GET | page | HTML，结果分页 | Observed |
| /detail/{novelId}.html | GET | novelId | HTML，小说详情与 TOC | Observed |
| /forum/ | GET | 无 | HTML，论坛分类 | Observed |
| /forum/{categoryId}/ | GET | categoryId | HTML，论坛主题矩阵 | Observed |
| /forum/{categoryId}/{boardId}/ | GET | categoryId、boardId | HTML，Bootstrap Table 壳；主题数据可动态加载 | Observed |
| /forum/{novelId}/{postId}.html | GET | novelId、postId | HTML，正文与评论 | Observed |
| /guestbook/ | GET | 无 | HTML，留言板 | Observed |
| /faq/ | GET | 无 | HTML，FAQ | Observed |
| /my/login | GET | 无 | HTML，登录表单 | Observed |
| /my/profile | GET | 无 | HTML，当前用户资料 | Observed |
| /my/profile.html?uid={uid} | GET | uid | HTML，公开资料 | Observed |
| /my/book | GET | 无 | HTML，管理的小说 | Observed |
| /my/post | GET | 无 | HTML，我的贴文 | Observed |
| /my/favorite/{sort?}/{page?} | GET | sort、page | HTML，收藏表 | Observed |
| /my/reply | GET | 无 | HTML，回复分页容器 | Observed |
| /my/message | GET | 无 | HTML，私讯壳与联系人 | Observed |
| /my/view | GET | 无 | HTML，观看记录表 | Observed |
| /my/record | GET | 无 | HTML，Bootstrap Table 壳 | Observed |
| /my/fixed | GET | 无 | HTML，Bootstrap Table 壳 | Observed |
| /my/ticket | GET | 无 | HTML，问题建议 | Observed |
| /my/sys | GET | 无 | HTML，系统消息 | Observed |

## 3. 内联脚本直接暴露的动态接口

### 3.1 删除观看记录

| 项目 | 观察结果 |
|---|---|
| URL | /inc/mem_view_del.php |
| 方法 | POST |
| 请求体 | 表单字段 vid={viewRecordId} |
| 请求头 | authorization: <getAuthToken 回调返回值> |
| 响应 | dataType: 'json' |
| 成功条件 | result.status == 200 |
| 成功副作用 | 删除 DOM 节点 #view_{vid} |
| 证据 | /my/view 内联脚本 Observed |
| 是否执行 | 未执行，避免破坏用户记录 |

### 3.2 加载私讯

| 项目 | 观察结果 |
|---|---|
| URL | /inc/load_msg.php |
| 方法 | POST |
| 请求体 | 表单字段 uid={targetUserId} |
| 请求头 | authorization: <getAuthToken 回调返回值> |
| 响应 | dataType: 'json' |
| 成功条件 | result.status == 200 |
| 成功字段 | room、uid、html |
| 失败行为 | location.href = result.url |
| 证据 | /my/message 内联脚本 Observed |
| 是否执行 | 未点击联系人，实际请求未发出 |

### 3.3 经验值记录 Bootstrap Table

| 项目 | 观察结果 |
|---|---|
| URL | /inc/mem_record_data.php |
| 页面 | /my/record |
| DOM 配置 | data-toggle="table"、data-side-pagination="server"、data-pagination="true"、data-search="true" |
| page size | 30 |
| page list | [10, 20, 50, 100] |
| sort order | desc |
| 返回 JSON 结构 | UNKNOWN / NOT VERIFIED |
| 实际请求方法与参数 | UNKNOWN / NOT VERIFIED；推断由 Bootstrap Table 默认适配器发出 |
| 证据 | table#dataTable[data-url] Observed |

### 3.4 错漏字回报 Bootstrap Table

| 项目 | 观察结果 |
|---|---|
| URL | /inc/mem_fixed_data.php |
| 页面 | /my/fixed |
| DOM 配置 | 与经验值表相同，服务端分页、搜索、page size 30 |
| 返回 JSON 结构 | UNKNOWN / NOT VERIFIED |
| 实际请求方法与参数 | UNKNOWN / NOT VERIFIED |
| 证据 | table#dataTable[data-url] Observed |

### 3.5 论坛子板块主题 Bootstrap Table

| 项目 | 观察结果 |
|---|---|
| 页面 | `/forum/{categoryId}/{boardId}/` |
| DOM 壳 | `#dataTable[data-url]`、`data-side-pagination="server"`、`data-page-size="20"`、`data-sort-name="last_reply"`、`data-sort-order="desc"` |
| data-url 样例 | `/inc/forum_list_data.php?totalRows=3`、`/inc/forum_list_data.php?totalRows=142` |
| 客户端请求 | 先向当前板块页 POST `plxf=getAuthToken`，再使用 data-url 作为端点并补充 `limit=20&offset=0&sort=last_reply&order=desc`；GET 请求携带响应令牌到 `Authorization` 请求头；业务状态 `301` 时重新获取令牌并最多重试一次 |
| 返回形态 | JSON；观察到 Bootstrap Table 的 `total` 与 `rows` 字段，主题链接位于 `rows[].subject` HTML 中 |
| 初始占位 | HTML 首次解析可见 `no-records-found`，但脚本加载后非空板块会填充主题行 |
| 空板块判定 | 仅当 `totalRows=0` 或动态 JSON 确认总数为 0 时返回空列表 |
| 证据 | ESJ 作品板与天空大公國讨论板均在云浏览器中观察到；直接把接口 URL 当作顶层页面打开可能被浏览器客户端拦截，不能据此判定正常板页脚本请求失败 |

## 4. 表单端点

表单的 action 与 method 可以从 DOM 读取，但未提交，因此“页面表单目标”不等于“已确认网络请求”。

| 页面/表单 | action | method | 字段 | 状态 |
|---|---|---:|---|---|
| 登录框 .login-box | /my/login | POST 为高可信推断 | email、pwd、记住我复选框 | 页面控件 Observed；实际提交方法 Inferred |
| 资料编辑 .form-edit | 当前 /my/profile | POST | nickname、old_nick、pwd、pwd2、age、open_msg、display_post、blacklist、rmlist、content | action/method Observed，未提交 |
| 头像 .user-info | 当前 /my/profile | GET | upfile | 表单属性 Observed；实际上传流程 UNKNOWN |
| 章节举报 #forumReport | 当前章节 URL | GET | category、rid、data、memo | 表单属性 Observed，未提交 |
| 留言举报 #forumReport | /guestbook/ | GET | category、rid、memo | 表单属性 Observed，未提交 |
| 章节评论 .commentEditor | 当前章节 URL | POST | content、data=forum、forum_id=章节 post id | 表单与页面脚本 Observed；客户端实现，未提交 |
| 详情评论 .commentEditor | 当前详情 URL | POST | content、data=books、forum_id=0 | 表单与页面脚本 Observed；客户端实现，未提交 |
| 留言板 .gbEditor | /guestbook/ | POST | content | 表单属性 Observed；客户端实现，未提交 |
| 新建小说 #createBook | 当前页面 | POST | 仅看到按钮；其余控件可能由模态内容提供 | 表单属性 Observed，未提交 |
| 新建工单 #newReport | 当前 /my/ticket | GET | subject、category、content、modal | 表单属性 Observed，未提交 |
| 回复工单 .form-reply-ticket | 当前 /my/ticket | POST | code、id、modal | 表单属性 Observed，未提交 |
| 私讯模态 #newMessage | 当前 /my/message 或公开资料页 | GET | room、uid、content | 页面表单属性 Observed；发送动作 UNKNOWN |

## 5. 通用请求头、Cookie 与 CSRF

| 项目 | 结论 |
|---|---|
| Cookie | 必须保留浏览器会话 Cookie；具体名称和值未读取，UNKNOWN / NOT VERIFIED |
| authorization | 仅在已观察的两个内联 AJAX 中出现，由 getAuthToken() 运行时回调提供 |
| CSRF hidden field | 在已检查的登录、资料、评论、留言表单中没有观察到明确 CSRF 字段；是否由 Cookie/脚本承担，UNKNOWN / NOT VERIFIED |
| Referer、Origin、User-Agent | 浏览器会自动管理，但本次未从网络层确认是否由服务端校验 |
| Content-Type | 表单提交大概率为 URL-encoded；动态 JSON 结果由 dataType:'json' 指定，实际请求头未确认 |

## 6. 未观察到或不应猜测的接口

- 收藏新增/取消接口：当前按钮已是“已收藏”，未点击；逻辑位于被云浏览器拦截的 modifyDetail.js?v=204，UNKNOWN / NOT VERIFIED。
- 评论提交、留言提交、举报提交、资料更新、工单创建、工单回复：只有表单目标被观察，真实服务端响应未验证。
- 其他会员页面 Bootstrap Table 的实际请求参数和返回字段仍未逐项通过网络面板确认，不应与论坛子板块端点混用。
- 章节正文、评论、TOC：当前均直接出现在 HTML 中，没有证据表明需要额外 JSON API。

## 7. 客户端实现约束

1. 只把第 3 节中有内联脚本或 data-url 证据的路径实现为动态接口。
2. 对所有写操作设置显式确认开关，默认禁用。
3. API 错误解析不能假定固定 HTTP 状态码或 JSON 字段，遇到未验证响应应保留原始响应摘要并返回 UNKNOWN。
4. 不在日志中输出 Cookie、授权值、密码或完整私讯 HTML。
