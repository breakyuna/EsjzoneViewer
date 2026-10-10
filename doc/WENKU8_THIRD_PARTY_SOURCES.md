# Wenku8 实际移植归属

固定来源：[LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader/tree/8681711f020af991fe37ca89983cc4531fe94c53)，Apache-2.0。

保留原 README 的声明：

```text
Copyright (C) 2024 by NightFish <hk198580666@outlook.com>
Copyright (C) 2024 by yukonisen <yukonisen@curiousers.org>
```

| 本项目文件 | 上游文件及实际采用片段 | 修改 |
| --- | --- | --- |
| `network/wenku8/Wenku8Parsers.kt` | `app/src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/defaultplugin/wenku8/book/Wenku8WebsiteDataSource.kt` 中详情位置 XPath、分卷遍历、单本搜索判断；同目录上一级 `Wenku8Api.kt` 中书卡 selector | 使用现有模型与 Jsoup，纯解析，完整 URL 校验与来源隔离，保留原始标题，缺少结构报错；依据用户详情 MHT 调整文章状态、标签／简介定位及目录入口判断；排除内置账户及原请求／Builder／缓存体系 |

上游实际片段经筛选后读取，未使用内置凭据。未移植 Ktor、Result、其他站点、图片资源或依赖。首页／列表已取得实站 DOM，详情依据 2026-10-11 用户提供的 MHT 结构，目录页正文仍为候选；具体证据见 [WENKU8.md](../NETWORK/WENKU8.md)。合成测试和离线存档不代表 Android 设备验收。

完整许可证：[Apache-2.0.txt](../LICENSES/Apache-2.0.txt)。应用归属资产：`app/src/main/assets/open_source_licenses/lightnovelreader_wenku8.txt`。原项目 GPL-3.0 与其他库声明保留。固定提交核查未发现 NOTICE，不编造上游 NOTICE。
