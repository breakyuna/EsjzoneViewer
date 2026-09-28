# 隐藏 WebView 评论会话

## 改造方案与当前实现

保留 Compose 输入框和评论列表，发送改由 `CommentWebSession` 创建不可见 WebView，加载真实目标页面。评论列表的普通刷新仍使用现有 HTML 读取接口。没有自动重发，也没有提交后的 OkHttp 写入兜底。

1. 检查当前站点登录、账号及 sessionEpoch；强制获取发送前经验值，保留已有经验值辅助判断。
2. 串行创建本次操作独有的 `esj_comments_{UUID}` WebView Profile，只导入当前目标 HTTPS 主机可用的原生 Cookie，等待 setCookie 回调完成。
3. 在文档开始时安装限同源、顶层 frame 的消息桥与脚本；等待真实页面编辑器就绪，最长 30 秒。
4. 记录发送前评论 ID。发起 JS 调用之前，将操作 UUID、内容、回复目标、旧 ID 写入 noBackupFilesDir；按账号身份、站点与页面的摘要隔离。同页不同内容的待核验操作分别保存，旧版单记录文件在读取时迁入。记录不包含登录凭据。
5. 通过页面 Froala 编辑器设置转义后的文本，点击原 `.btn-send`；动态 token、表单路由、提交由网站处理。脚本只允许一次评论 POST。
6. 观察 XHR 业务响应及 MutationObserver 的评论更新；网页自身重新加载后重新安装观察器，不重新发送。观察窗口最长 90 秒，期间没有客户端轮询 HTTP 请求。XHR 失败立即提示不确定；已发出请求但 12 秒无结果也先显示不确定，之后最多观察约 10 秒。已受理后可停止等待，操作记录仍保留。
7. JSON status=200 表示受理，立即显示“已受理，等待显示”，持久化受理状态并结束按钮转圈。出现新评论后恢复列表、清除对应草稿与提示；用户在此期间编辑的新草稿保留。网页快照只用于确认新评论，不覆盖已加载的原生列表。
8. 未确认受理且未见评论则显示结果未知，保留操作与草稿，比较发送前后的新经验值。用户明确刷新后可以主动重试。已受理的同内容同回复目标操作不允许直接重发。
9. Activity 重建复用 ViewModel；进程/页面重建读取操作记录，只进行列表核验，不自动发送。离开页面导致协程取消时，销毁 WebView，保留待核验记录。

## 网站证据（2026-09-28，只读公开资源）

公开详情页加载 `/assets/js/customizer.min.js?v=311`。解包后的脚本显示：

- body 委托 `.btn-send` 点击，从 `data-form` 找到 `editor[form].html.get()`，写入 hidden content。
- `getAuthToken({onFinish: ...})` 后使用 jquery.ajaxform 的 ajaxSubmit，目标 `/inc/{data-send}.php`。
- 评论目标 `forum_reply`，留言回复目标 `gb_reply`。
- `.forum_reply` 点击会根据 `data-comment` 创建 `replyEditor` 与 hidden reply，并初始化 Froala。
- `showResponse` 解析 JSON，提示框 `showSweetAlert` 的 onClose 按响应 reload/anchor 刷新定位，也可直接 prepend 返回的 comments。

回复目标若不在当前网页 DOM，编辑器在页面完成加载后再等待 2 秒，随后提示目标未载入。现有样本的 `comments-page-N` 分组均在同一 HTML；没有已验证的跨页动态加载接口，因此不猜测网站分页请求。

因此提示框计时结束前的等待不代表响应丢失。仅观察 `onPageFinished` 或 HTTP 200 不足以证明业务成功。

来源为当前公开脚本，不是登录后的实发抓包。未使用本地账号，也未向真实网站发布测试评论。详情、章节、论坛和留言模板仍需设备验收。

## 登录及安全边界

- 使用 AndroidX WebKit 1.15.0 的 MULTI_PROFILE、DOCUMENT_START_SCRIPT、WEB_MESSAGE_LISTENER；缺少任一能力时在发送前提示更新 WebView，不使用默认 Profile，避免影响 Wenku 和内置浏览器。
- 原生 CookieJar 仍是登录来源。导入 Cookie 保留其当前模型支持的 Domain/Path/expiry/Secure/HttpOnly；原生模型未保存 SameSite，不能声称完整复刻浏览器原始会话属性。
- 浏览器 Cookie 不经 JS、消息桥或日志传输。暂不反向写回原生 CookieJar，避免不完整或过期的浏览器 Cookie 覆盖原生会话。若服务器仅在浏览器中轮换会话，下次原生校验可能要求重新登录，这是本版限制。
- 桥只传评论 DOM、评论作者显示名及受限响应字段，不传整页 HTML、请求头或 token。DOM 快照移除 form/input/script/iframe。
- 顶层导航限制为原 HTTPS 主机及原路径；禁止文件、内容 URI、混合内容、第三方 Cookie；其他 HTTPS 子资源供站点依赖脚本使用。
- 操作结束/取消时清理 Profile Cookie 与 DOM storage 并尝试删除 Profile。部分内核不能立即删除已加载的 Profile，下一次进程启动后的首次评论操作会清理遗留 Profile。每次使用新 Profile，避免 Cookie 之外的缓存或 Service Worker 跨操作复用；默认 WebView 存储不受影响。Android 备份排除 app_webview；操作记录位于 noBackupFilesDir。
- 账号/代次改变后忽略回调并阻断后续请求；已在服务器处理的请求不能撤回。

## 判定与限制

优先用受理响应 anchor 的新 ID 确认 DOM。无 anchor 时，浏览器要求新 ID、全文规范化后相等及网页当前 nickname 与评论作者一致；用户名并非唯一身份，此路径仍是辅助核验。手动刷新使用同一判定；一旦取得服务器 ID，不再退回内容匹配。经验值上涨只改变“可能成功”的提示，不升级为受理。

隐藏 WebView 不是系统后台常驻服务，不能保证锁屏、系统回收或厂商节流后的页面计时精度。网站验证码或要求可见交互时不会自动绕过，保留草稿并提示检查登录/网络。网站编辑器、XHR 或接口结构变化会导致准备失败或结果未知。

超过 1.5 MB 的网页评论快照会显示专门提示。登录状态在发送过程中改变时显示结果未确认，并保留适用账号的待核验记录。用户可停止观察，但停止动作不撤销已经到达服务器的请求。

## 验证与验收

- JS 契约测试：`node --test tools/qa/tests/comment_session.test.cjs`，覆盖一次发送、未授权写入拦截、文本转义、业务失败、HTTP 错误、回复目标缺失、快照超限和受理 anchor；Build 与 Release CI 均运行。
- JVM `CommentBrowserEvidenceTest` 覆盖服务器 ID 优先、旧评论/其他作者排除、全文匹配、短快照保留原列表以及多操作防重规则。
- 仓库静态检查、JVM 单元测试、Android Lint 按 AGENTS.md 统一执行。
- 设备验收：两个域名分别登录、详情/章节/论坛/留言回复、延迟刷新、每日限额、断网、旋转、离开页面、进程终止、切换账号、旧 WebView、编辑新草稿。脚本模拟测试不能替代这些检查。

AndroidX Profile 生命周期依据：[ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore)。删除仍在使用或通过 ProfileStore 加载的 Profile 可能抛出 IllegalStateException；清理失败不得复用该 Profile。
