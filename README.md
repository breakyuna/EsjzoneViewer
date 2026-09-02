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
  <a href="https://github.com/breakyuna/EsjzoneViewer/issues">反馈问题</a> ·
  <a href="#致谢">致谢</a>
</p>

> 本项目是 **ESJ Zone 的第三方 Android 应用**，不代表 ESJ Zone 官方。基于原作者 [DeeChael](https://github.com/DeeChael) 的 [Esjzone](https://github.com/DeeChael/Esjzone) 项目继续开发，当前由 [breakyuna](https://github.com/breakyuna) 维护。

## 界面预览

<p align="center">
  <img src="docs/images/preview.jpg" width="1200" alt="EsjzoneViewer 首页、历史记录、书架与个人资料界面展示">
</p>

## 功能

- **发现与搜索**：浏览推荐、最新更新和分类，按关键词搜索小说，保留本地搜索历史。
- **本地优先书架**：打开即读取本地书架，后台补充云端收藏；网络失败或云端缺失不会清空本地书籍。支持封面宫格、最近阅读排序和批量移除。
- **连续阅读**：滚动衔接章节，通过目录或全书进度条跳转，使用进度预览与返回进度功能。
- **个性化阅读**：调整字体大小、行距、段距、页边距和背景，切换简繁体与 Catppuccin 主题配色。
- **历史与书签**：保存本机阅读位置和章节书签，本地历史与云端历史分开展示，本地历史不上传。
- **下载与导出**：后台下载章节，在应用内读取已下载内容，也可导出 TXT 或 EPUB；下载失败时保留已完成章节。
- **社区互动**：浏览论坛分区、主题和留言板，查看评论、发表评论及回复。
- **账户与站点**：恢复本地登录会话，在设置中切换站点与内容显示偏好。

## 下载与使用

需要 **Android 10（API 29）或更高版本**。

前往 [Releases](https://github.com/breakyuna/EsjzoneViewer/releases) 下载已发布的 APK 并安装。

应用支持 `www.esjzone.cc` 与 `www.esjzone.one`，可在登录页或设置页切换。登录使用 ESJ Zone 账户，无需额外的 API Key。

下载小说后，无需先导出文件即可在应用内阅读已下载章节；尚未下载的内容、远程插图以及在线社区功能仍可能需要网络。

## 开发

项目采用 **Kotlin + Jetpack Compose + Material 3**，使用 Room 保存本地数据、Voyager 管理导航、OkHttp 请求网络、Jsoup / Xsoup 解析页面、Coil 加载图片。

开发环境：JDK 17、Android SDK 34，以及支持项目 Gradle 配置的 Android Studio。克隆仓库后，用 Android Studio 打开根目录并同步项目。

```bash
git clone https://github.com/breakyuna/EsjzoneViewer.git
cd EsjzoneViewer
```

## 反馈与贡献

欢迎通过 [Issues](https://github.com/breakyuna/EsjzoneViewer/issues) 提交问题或建议，也欢迎 Pull Request。

报告问题时，请提供应用版本、Android 版本、复现步骤，以及相关截图或错误日志；页面加载问题请附对应页面地址。请勿在公开反馈中附上密码、Cookie 或其他登录凭据。

## 数据说明

登录信息会发送到当前选择的 ESJ Zone 站点，应用在本地保存会话、设置、书架、阅读历史、书签及下载内容。云端收藏用于补充本地书架；在书架中主动移除书籍时，应用会尝试取消对应的云端收藏。

本项目是第三方客户端，不代表 ESJ Zone 官方。小说、封面和其他站点内容来自所选站点，相关权利归各自权利人所有。

## 致谢

本项目基于 [DeeChael/Esjzone](https://github.com/DeeChael/Esjzone) 继续开发，当前由 [breakyuna](https://github.com/breakyuna) 维护。感谢原作者 **DeeChael** 和所有贡献者。

感谢 Jetpack Compose、Material 3、Room、[Voyager](https://github.com/adrielcafe/voyager)、[OkHttp](https://github.com/square/okhttp)、[Coil](https://github.com/coil-kt/coil)、[Gson](https://github.com/google/gson)、[Jsoup](https://github.com/jhy/jsoup)、[Xsoup](https://github.com/code4craft/xsoup) 和 [Catppuccin](https://github.com/catppuccin/catppuccin) 等开源项目。

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE)。第三方依赖遵循各自的许可证。
