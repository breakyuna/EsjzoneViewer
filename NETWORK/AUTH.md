# ESJ Zone 认证与会话逆向

## 1. 已验证登录状态

本次使用云浏览器完成登录后，访问 /my/login 会跳转到 /my/profile，并显示会员侧栏、当前昵称、经验值、等级与注册日期。该结果证明当前浏览器上下文中的登录会话可跨新页面继续使用。

为保护账户，本文不记录密码、Cookie、token 值或任何可复用会话材料。

## 2. 登录页面结构

登录页观察到：

| 控件 | 选择器/属性 | 说明 |
|---|---|---|
| 邮箱 | form.login-box input[name="email"] | type=email，placeholder 为 Email |
| 密码 | form.login-box input[name="pwd"] | type=password，placeholder 为 密碼 |
| 记住我 | 登录框中的 checkbox | 样本中默认勾选 |
| 登录按钮 | form.login-box .btn.btn-primary.margin-bottom-none.btn-send | 文案为 登入 |

登录表单页面位于 /my/login。由于网络层不可用，表单提交的 action/method 没有做独立 HTTP 抓包；结合登录成功行为，POST /my/login 是高可信 Inferred，客户端仍应以真实页面表单属性或浏览器自动化结果为准。

未观察到明确的 CSRF hidden input，是否存在服务端其他校验为 UNKNOWN / NOT VERIFIED。

## 3. 会话模型

### 3.1 浏览器客户端

推荐让浏览器负责：

1. 打开规范域名。
2. 由用户或受支持的认证能力填写登录信息。
3. 保留同一浏览器上下文中的 Cookie。
4. 后续 GET 页面直接复用上下文。
5. 仅在页面内联脚本要求时调用站点自己的 getAuthToken() 机制。

不要通过脚本读取或回显 Cookie 值，不要把登录信息写到本地配置或 Markdown。

### 3.2 独立 HTTP 客户端

独立客户端可以实现公开页面 GET，但不能假设能独立产生已登录会话。若没有官方登录 API、Cookie 注入能力或受支持的浏览器认证桥接，应将会员写操作标记为不可用，而不是绕过认证。

## 4. 运行时授权 token

多个页面有如下内联定义：

~~~javascript
function getAuthToken(){
  return new JinJing().Callback('getAuthToken', getAuthToken.arguments);
}
~~~

/my/view 的删除观看记录和 /my/message 的加载私讯都观察到：

~~~javascript
getAuthToken({
  "onFinish": function(result) {
    $.ajax({
      headers: {"authorization": result},
      method: "POST",
      dataType: "json"
    });
  }
});
~~~

结论：

- authorization 是运行时产生的短期或会话相关值，具体格式、寿命和刷新规则 UNKNOWN / NOT VERIFIED。
- 客户端不能把它当作长期 API key。
- 如果浏览器运行时不能提供 JinJing().Callback，应返回“需要浏览器会话”而不是自行伪造 token。

## 5. 登录态可见页面

已登录页面共同显示会员侧栏：

/my/profile、/my/book、/my/post、/my/favorite、/my/reply、/my/message、/my/view、/my/record、/my/fixed、/my/ticket、/my/sys、/my/logout

当前会话可看到：

- 收藏列表
- 观看记录
- 资料编辑控件
- 经验值记录空表
- 错漏字回报空表
- 私讯与工单入口

“看到表单”只代表页面渲染了控件，不代表当前用户对对应写操作已有可验证权限。

## 6. 认证状态判定

| 状态 | 页面证据 | 客户端判断 |
|---|---|---|
| 未登录 | /my/login 显示登录框 | anonymous |
| 已登录 | /my/login 跳转 /my/profile，会员侧栏出现 | authenticated |
| 会话失效 | UNKNOWN；未执行登出或强制过期 | 不应猜测具体状态码 |
| 需要二次验证/CAPTCHA | 本次未遇到 | UNKNOWN / NOT VERIFIED |
| 无权操作 | 真实写请求未执行 | UNKNOWN / NOT VERIFIED |

## 7. 安全实现建议

- 认证上下文只在内存中保存，不写日志。
- 日志只记录 authenticated=true/false、最终 URL 和脱敏状态码。
- 每个写操作单独要求用户在动作前确认。
- 收藏当前已是“已收藏”，不自动切换。
- 不在测试中执行删除观看记录、发评论、发留言、举报、资料更新、工单提交或登出。
- 当出现 CAPTCHA、登录跳转或授权错误时停止自动化并交给用户。

## 8. 未验证项

- Cookie 名称、Domain、Path、Secure、SameSite、过期时间。
- 登录响应状态码和 Set-Cookie。
- “记住我”对会话寿命的确切影响。
- CSRF token、Origin/Referer 校验。
- token 的具体结构、刷新接口和失效状态。

## 9. Android 客户端会话实现

客户端不会记录任何真实会话值到日志或文档，但会在应用私有存储中持久化服务器返回的 Cookie 属性，以便重启后复用同一会话。

- Cookie 按域名、路径和过期时间匹配请求，`www.esjzone.cc` 与 `www.esjzone.one` 的会话相互隔离。
- 登录流程先在临时 Cookie 容器中完成，只有登录响应成功且包含必要会话 Cookie 时才提交持久存储。
- 网络异常、超时、`429`、`5xx`、空响应和无法解析的页面不会删除本地会话。
- 明确跳转登录页或 `401` 会视为未授权；疑似 WAF/代理的 `403` 可重试，但重复的拦截响应仍保持 `UNKNOWN`，不会清除本地会话。
- 旧版本 Room 中的 `ews_key`、`ews_token` 仅在首次恢复时迁移到持久 Cookie 容器，迁移后不再用旧值覆盖服务器刷新后的 Cookie。

## 10. Android 启动与后台校验

启动时 `LoadingScreen` 只读取本地设置、会话 Cookie 和旧版 Room 会话，不等待网络请求；有本地凭据时立即进入 `MainScreen`，因此本地书架、阅读历史和已缓存内容可以在离线状态下打开。

`MainScreen` 显示后通过生命周期绑定的后台协程调用 `checkAuthorization`。校验使用不持久化响应 Cookie 的独立 OkHttp 客户端，并以整个校验流程的有界超时限制实际 HTTP 请求；域名从本次启动恢复的会话快照解析，不读取可能已切换的全局镜像站点。网络异常、空页面、拦截页和无法确认的响应均为 `UNKNOWN`，不会清除本地会话。

只有明确的登录页跳转或确认的未授权响应才显示重新登录提示。提示提供“重新登录”和“继续离线”操作；在用户主动重新登录前，不删除本地凭据，也不阻止本地书架和阅读历史。
