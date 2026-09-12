# 个人资料、书架崩溃与安全检查记录

基线：`beta` / `5477a5e5b4a94301f5e4bd420e4244b9471f4cc2`。
本次仅修改本地工作树，未提交、未推送，未运行 Gradle、编译、JVM 测试或模拟器。

## 页面调整

- 个人资料：柔和渐变资料卡、统一图标底座、四个带方向提示的入口；离线且没有缓存时结束骨架加载并提供重试。
- 书签：信息概览与圆角条目，突出作品名和章节层级；数据库写入失败时不再提前隐藏书签。
- 下载管理：下载数量/空间概览、统一浅色条目和选中态；确认删除后立即关闭确认框，并阻止重复启动删除。
- 设置：统一分组图标、卡片背景及控件间距。
- 关于：应用标识概览、版本信息层级和统一开源库卡片；找不到浏览器或受系统限制时给出提示，不让异常逃出点击回调。
- 这五个页面使用现有主题色、设计 token 和宽屏内容上限；深浅色均由当前主题提供颜色。

## 崩溃定位与修复边界

日志异常：`Place was called on a node which was placed already`。
下载 GitHub Actions 运行 `34675611737` 的已签名 APK，读取 DEX 与版本元数据，**没有运行或重编译 APK**。
APK 的 `r8-map-id-3576d9c4b2a19f18e8d7444a9bfca86a85fb3ecdeef375251920357a99b54cc3`
与用户日志完全相同。该包实际含 Compose Foundation/UI `1.12.0` 和 Navigation 3 `1.1.7`。

对 `sz1` 构造参数、尺寸计算与放置函数，以及 `qz1.n` 的两组条目放置循环进行字节码核对，
对应的是 `LazyGridMeasuredItem` / lazy-grid 的放置路径。日志不是内存不足或网络异常。
因为这次构建没有留存 mapping.txt，不能声称完成了完整 Retrace，亦不能确定触发时点击的具体条目。

书架原先将近期阅读区、动态高度标题、全列列表条目和普通宫格条目放在同一个 LazyVerticalGrid，
并在编辑/展示模式切换时改变 span。修复改为 **LazyColumn + 自适应 Row**：仍是原来的 112dp 最小宫格宽度和间距，
仍保持顶部最近阅读四本书的 2-1-3-4 顺序；列表模式用单列，空缺位置用 Spacer，不复制任何书籍条目。
条目使用稳定的行标识和不同布局 contentType；进入书籍或切换模式前释放焦点，避免保留焦点固定的旧条目。
这移除了书架对上述 LazyGrid 放置路径的依赖。该触发组合是基于代码的判断，修复效果仍须同设备回归确认。

另外为四个常驻 Tab 隔离 NavigationEventDispatcher，并把隐藏 Tab 的生命周期限制在 CREATED、清除其无障碍语义，
避免仅 alpha=0 的隐藏页面仍处理返回事件或被读屏访问，同时保留状态持有者。
构建工作流新增独立的 release-symbols artifact，后续可直接通过 mapping.txt 还原崩溃。

## 额外发现与处置

| 问题 | 影响 | 本次处置 |
| --- | --- | --- |
| 退出/重新登录后的迟到 Set-Cookie 仍可进入持久容器 | 已发出请求可能恢复旧会话或覆盖切换后的 Cookie | 客户端捕获会话代次；容器在同一把锁内校验代次并读写，显式清除和登录切换时递增 |
| 非持久化 CookieJar 只校验域名，没有校验协议 | 即使 Cookie 带 secure 属性，手工返回列表仍不应依赖调用方过滤 | HTTP 请求一律返回空 Cookie 列表 |
| HTML/JSON 使用无限制 body.string() | 异常大或分块/压缩响应可造成内存耗尽 | 所有现有网络文本响应读取统一限制解压后 8 MiB；超限抛 IOException，由现有错误路径处理 |
| EPUB 整书正文同时驻留 | 大小说导出期间内存随全书长度增长 | 第一遍只保留目录及图片引用，第二遍逐章写出 |
| EPUB 只删除部分标签及 on* 属性 | javascript: 链接、远程 CSS、SVG 活动内容仍可能被带入外部阅读器 | Jsoup Cleaner + Safelist 保留基本排版/ruby/表格，清理活动内容，跳过未净化 SVG 资源 |
| 自动检查更新的结束状态只在手动检查时更新 | 自动请求完成或失败后，关于页可能永久显示检查中 | 所有结果分支更新状态，失败仍不自动弹窗 |
| 下载删除确认框未关闭、可重复启动任务 | 重复删除及状态混乱 | 关闭确认框、清理待删除目标并增加执行中保护 |
| 没有浏览器时直接打开开源库链接 | 点击后主线程异常 | 捕获不可处理/权限限制错误并提示 |

会话保护限制：阻止的是已经创建的旧客户端在会话切换后读写 Cookie，不代表服务器上已发出的请求可以撤销。
EPUB 限制：不保留任意 CSS，SVG 以图片占位文字代替；这是导出内容安全处理的可见变化。
正文超过 8 MiB 的单页会进入加载失败/缓存回退路径，不再无限分配内存。

## 其他已检查的边界

检查了 Manifest 导出组件、备份排除规则、TLS/明文配置、会话存储、URL 规范化、下载路径与图片大小限制、日志脱敏、
更新链接来源、核心懒加载条目标识及自定义布局。现有代码没有信任全部证书的 TrustManager/hostnameVerifier，
没有开启 WebView JavaScript 桥；会话采用 Keystore 加密，备份排除了会话、旧数据库与本地内容；下载读取已有 canonical path 边界检查。
没有据此得出“项目不存在任何漏洞”的结论；未进行在线渗透测试或完整第三方依赖漏洞库扫描。

## 静态验证

- 修改/新增的 24 个 Kotlin/KTS 文件经 Tree-sitter Kotlin 语法解析通过。
- XML、JSON、版本 TOML、GitHub workflow YAML 解析通过；新增字符串的默认/英文/简体资源对齐，引用有效。
- 仓库 `tools/qa/verify_static_contracts.py` 通过，`git diff --check` 通过。
- 新增 7 个安全回归测试案例：响应字节上限、分块/虚报长度、BOM，HTTP 会话隔离，EPUB 活动 HTML 与 SVG。
  这些 JVM 测试仅完成代码静态审查，尚未执行。
- 修改范围不包含签名密钥、包名、数据库结构、用户凭据或原始崩溃日志。

设备回归重点：书架快速开书/返回、宫格与列表切换、阅读历史更新后返回书架、四个 Tab 的返回手势、深浅色与大字体、
离线个人资料、大书导出，以及旧请求仍在途时退出再登录。

## 参考

- [AndroidX LazyGridMeasuredItem 源码](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/grid/LazyGridMeasuredItem.kt)
- [Lifecycle 2.10 的 rememberLifecycleOwner](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.10.0)
- [NavigationEventDispatcherOwner 的 enabled 隔离](https://developer.android.com/reference/kotlin/androidx/navigationevent/compose/rememberNavigationEventDispatcherOwner.composable)
- [Jsoup 官方 HTML Safelist 清理说明](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer)
