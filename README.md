# Esjzone

Esjzone 是一个面向 Android 的小说浏览与阅读客户端，使用 Jetpack Compose 构建。应用通过网站接口获取小说、章节和账户数据，并提供适合移动设备的浏览、搜索与阅读体验。

![界面预览](https://pic.imgdb.cn/item/68a6e6e0f7f3e515a3c7ee7c.png)

> 本项目是网站内容的客户端展示工具，不托管或重新分发小说内容。使用前请遵守目标网站的服务条款及所在地法律法规。

## 主要功能

- 账户登录、登录状态检查、退出登录。
- 主页浏览：译作、原创、可选的成人内容分区以及推荐内容。
- 分类浏览：查看站点分类，并按类型和站点提供的排序方式浏览小说。
- 关键词搜索，以及本地保存和管理搜索历史。
- 小说详情：封面、作者、简介、分类、章节列表和收藏状态。
- 阅读功能：开始阅读或继续阅读，支持上一章、下一章和阅读位置调整。
- 章节内容解析：尽量保留正文段落、样式、注音和插图等网页内容结构。
- 个人中心：收藏列表、阅读历史、历史记录删除和账户信息。
- 设置：切换可用站点、显示或隐藏成人内容，以及选择 Catppuccin 主题配色。
- 关于页面：查看构建信息和项目使用的开源库。

## 支持的站点

应用内置以下可切换站点：

- `https://www.esjzone.cc`
- `https://www.esjzone.one`

站点页面结构、登录状态和网络可用性可能发生变化。若某个站点无法访问，可以在登录页或设置页切换到其他可用站点。

## 技术栈

- Kotlin
- Jetpack Compose、Material 3
- Voyager：页面与 Tab 导航
- OkHttp：网络请求
- Jsoup、Xsoup：HTML 与 XPath 数据解析
- Coil：封面、头像和章节插图加载及缓存
- Room：本地缓存、会话信息和搜索历史存储
- Gson：JSON 数据处理

项目当前使用 Android SDK 34，最低支持 Android 10（API 29），Java/Kotlin 编译目标为 17。

## 项目结构

```text
.
├── app/
│   └── src/main/java/      # Kotlin 源码
│       ├── database/       # Room 数据库、DAO 与实体
│       ├── network/        # HTTP 客户端、认证、站点 URL 与网络功能
│       ├── novellibrary/   # 小说、章节及网页内容模型与解析器
│       ├── ui/             # Compose 页面、Tab、导航和主题
│       └── util/           # 通用工具
├── NETWORK.md             # 网络接口与网页解析说明
├── metadata.json          # 项目元数据
└── gradle/                # Gradle 版本目录与 Wrapper 文件
```

应用启动后，`MainActivity` 初始化图片加载器和 Room 数据库；`LoadingScreen` 恢复本地会话并检查登录状态；登录成功后进入包含主页、分类、搜索和个人中心的主界面。网络层统一通过 `GlobalSettings` 与 `EsjzoneUrls` 读取当前站点，网页内容由 `Jsoup` 和 `Xsoup` 解析为领域模型，再交给 Compose 页面展示。

## 本地运行

开发环境需要：

1. Android Studio，以及可用的 Android SDK 34。
2. JDK 17。
3. 一台 Android 10 或更高版本的设备，或对应的模拟器。

将项目克隆到本地后，用 Android Studio 打开仓库根目录，等待项目同步完成，选择 `app` 模块运行即可。项目不需要额外的 API Key；登录功能使用目标站点的账户凭据。

## 数据与隐私说明

- 登录时输入的账户信息会发送到当前选中的站点。
- 应用会在本地数据库保存站点返回的会话凭据，用于恢复登录状态；请妥善保护设备和应用数据。
- 当前站点、主题、成人内容显示选项和搜索历史也会保存在本地。
- 应用没有独立的内容服务器，封面、头像、简介和章节正文等数据来自当前选中的站点。

## 网络解析文档

网络请求流程、登录方式、网页字段和 XPath 解析说明见 [NETWORK.md](./NETWORK.md)。如果站点页面结构发生变化，应优先检查 `network/EsjzoneXPaths.kt`、`network/features/` 以及相关领域模型。

## 许可证

本项目使用 [GNU General Public License v3.0](./LICENSE) 发布。项目依赖的第三方开源库及其许可证信息可在应用的“关于”页面查看。
