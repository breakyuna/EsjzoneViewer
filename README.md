<p align="center">
  <img src="docs/images/logo.png" width="160" alt="EsjzoneViewer 标志">
</p>

<h1 align="center">EsjzoneViewer</h1>

<p align="center">面向 Android 的 ESJ Zone 第三方小说阅读客户端</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 10 及以上">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="GPL-3.0"></a>
  <a href="https://github.com/breakyuna/EsjzoneViewer"><img src="https://img.shields.io/badge/Maintainer-breakyuna-red" alt="维护者 breakyuna"></a>
</p>

<p align="center">
  <a href="https://github.com/breakyuna/EsjzoneViewer/releases">下载</a> ·
  <a href="#功能">功能</a> ·
  <a href="#-后续计划-todo">后续计划</a> ·
  <a href="https://github.com/breakyuna/EsjzoneViewer/issues">反馈问题</a> ·
  <a href="#致谢">致谢</a>
</p>

## 📜 项目来源与许可声明

**EsjzoneViewer 是 ESJ Zone 的第三方 Android 应用，并非 ESJ Zone 官方客户端。**

本项目基于 [DeeChael/Esjzone](https://github.com/DeeChael/Esjzone) 继续开发，在原有基础上改进阅读体验、本地书架、下载与社区功能，感谢原作者的贡献。

- **原始项目**：[DeeChael/Esjzone](https://github.com/DeeChael/Esjzone)
- **原作者**：[DeeChael](https://github.com/DeeChael)
- **当前维护者**：[breakyuna](https://github.com/breakyuna)
- **开源许可证**：[GNU General Public License v3.0](LICENSE)

---

## 📋 使用与数据说明

### 🌐 数据来源

小说、封面、章节、评论等内容主要来自当前选择的 ESJ Zone 站点；目录中的部分文库8章节链接会访问文库8并获取正文。应用提供适合 Android 设备的浏览与阅读界面，不代表原站发布内容或作出承诺。

### 🔐 账户与本地数据

- 登录信息会发送到当前选择的站点，应用在本机保存登录会话以恢复登录状态。
- 设置、搜索历史、书架、阅读历史、书签、阅读划线和阅读统计保存在本地；本地阅读历史不上传，云端历史单独展示。
- 云端收藏仅用于补充本地书架，不会因云端缺失而删除本地书籍；主动从书架移除书籍时，应用会尝试取消对应的云端收藏。
- 应用会缓存内容，并保存用户下载的小说章节；请妥善保护设备与应用数据。
- 检查与下载更新会访问 GitHub 及其附件下载服务，不携带 ESJ Zone 登录会话。应用内自动备份保存在本机，卸载或清除应用数据前请按需导出。

### ©️ 内容与使用边界

- 小说、插画、封面及其他站点内容的相关权利归各自权利人所有，本项目不声称拥有这些内容。
- 使用、下载或导出内容时，请遵守原站规则及适用要求，尊重作者与译者的权益。
- 成人内容显示可在设置中调整，请根据年龄与所在地要求使用相关功能。
- 站点结构、登录状态或网络环境变化可能影响在线功能，应用不保证所有页面始终可用。

## 📮 权利声明

若 ESJ Zone（`www.esjzone.cc` / `www.esjzone.one`）运营方或相关权利人认为本应用存在不当行为，可通过 [GitHub Issues](https://github.com/breakyuna/EsjzoneViewer/issues) 联系维护者，说明相关页面与具体问题，以便核实处理。

---

## 📥 下载与使用

**下载渠道：[GitHub Releases](https://github.com/breakyuna/EsjzoneViewer/releases)**

1. 使用 **Android 10（API 29）或更高版本**的设备，下载 Release 中的 APK 并安装。
2. 在登录页或设置页选择可用站点：`www.esjzone.cc` 或 `www.esjzone.one`。
3. 使用 ESJ Zone 账户登录后，浏览小说、管理书架或继续阅读。

> 💡 下载完成的章节可以直接在应用内阅读，无需先导出 TXT 或 EPUB；未下载的内容、远程插图和在线社区功能仍可能需要网络。

## 功能

- 🔍 **浏览与搜索**：热门推荐、最新更新、分类浏览、关键词搜索与搜索历史。
- 📚 **本地优先书架**：云端收藏补充、宫格与列表、本地分组、排序及批量管理。
- 📖 **阅读体验**：连续滚动与分页阅读，支持目录、全书进度定位、点击和音量键翻页。
- 🎨 **阅读自定义**：字体、排版、主题、简繁转换、亮度、保持亮屏及快捷工具栏。
- 🕘 **历史与标记**：恢复阅读位置，管理本地书签与正文划线，本地和云端历史分开展示。
- 📊 **阅读统计**：阅读时长、趋势、书籍排行与标签分布，支持暂停统计的无痕模式。
- 📥 **下载与导出**：后台下载、补下载及离线阅读，按已下载章节导出 TXT 或 EPUB。
- 💾 **备份与恢复**：按类别导入、导出本地数据，支持自动备份；下载内容可选包含。
- 💬 **社区互动**：论坛、留言板、评论与回复。
- 🌐 **站点与站外链接**：切换两个 ESJ Zone 站点，支持 ESJ 目录中的部分文库8章节阅读与下载；遇到站点验证时可能需要在应用内完成验证，其他站外链接通过浏览器打开。
- ⚙️ **设置与维护**：主题、中英文界面、内容显示、存储与缓存管理，以及应用更新检查、后台下载和系统安装。

## 📷 界面预览

以下为历史版本界面，实际布局与功能以当前版本为准。

<p align="center">
  <img src="docs/images/preview.jpg" width="1200" alt="EsjzoneViewer 首页、历史记录、书架与个人资料界面展示">
</p>

## 🛠️ 技术栈

- **Kotlin**：应用开发语言
- **Jetpack Compose / Material 3**：界面与交互
- **AndroidX Navigation 3**：类型化页面与 Tab 导航
- **Room**：本地书架、历史、书签和阅读统计存储
- **DataStore**：应用与阅读设置持久化
- **WorkManager**：后台下载、字体下载与自动备份
- **OkHttp**：网络请求与会话处理
- **Jsoup**：HTML 与 CSS 选择器解析
- **Coil**：封面、头像和插图加载
- **Gson**：JSON 数据处理

## 🌐 网络接口与页面分析

ESJ Zone 的网络请求、登录认证、页面结构与 HTML 解析相关说明整理在 [`NETWORK/`](NETWORK/) 目录中。

这些文档最初由 GPT Work 使用云浏览器分析页面 DOM、表单、内联脚本及可观察的请求行为，后续结合维护者抓包确认和客户端实现持续维护，也包含解析约束与本地存储说明。它们并非 ESJ Zone 官方 API 文档；推断或尚未验证的内容已标注，使用时请注意其验证状态。

## 📌 后续计划 (TODO)

- [ ] 扩展站外小说与章节的阅读适配，如成为小说家吧（syosetu）等来源。

## 🐛 反馈与贡献

欢迎通过 [Issues](https://github.com/breakyuna/EsjzoneViewer/issues) 报告问题或提出建议，也欢迎提交 Pull Request。

为了方便定位问题，请尽量提供：

- 应用版本、设备型号和 Android 版本
- 复现步骤、预期表现与实际表现
- 相关页面地址、截图或错误日志

> 🔒 提交截图和日志前，请删除密码、Cookie、会话凭据及其他不希望公开的个人信息。涉及账户或凭据的安全问题请遵循 [安全说明](SECURITY.md)，不要公开提交敏感细节。

开发前请阅读 [项目协作指南](AGENTS.md)，按其中的权限与验证要求操作。相关使用说明：

- [本地数据备份与书架分组](LOCAL_BACKUP.md)
- [阅读手势](docs/reader-gestures.md)

## 致谢

感谢原作者 [DeeChael](https://github.com/DeeChael) 提供项目基础，感谢所有参与开发、测试和反馈的贡献者。

同时感谢 Jetpack Compose、Material 3、Room、AndroidX Navigation 3、[OkHttp](https://github.com/square/okhttp)、[Coil](https://github.com/coil-kt/coil)、[Gson](https://github.com/google/gson) 和 [Jsoup](https://github.com/jhy/jsoup) 等开源项目。

## 📄 许可证

本项目采用 **GNU General Public License v3.0**，完整文本见 [LICENSE](LICENSE)。保留原项目的版权与许可证信息，第三方依赖遵循各自的许可证。

应用内可从设置页版本号下方的“开源许可”进入次级页面，离线查看本项目、第三方依赖和阅读字体的来源与完整许可文本。项目依赖许可位于 `app/src/main/assets/open_source_licenses/`，字体许可保留在 `app/src/main/assets/font_licenses/`；新增依赖时同步补充对应归属与许可。
