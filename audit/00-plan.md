# EsjzoneViewer 静态代码审计计划

审计时间：2026-10-14
范围：app/src/main 全部 Kotlin 源码（333 个文件，约 4.9 万行），不含构建与测试代码。
方式：纯静态审计，不修改源码，不运行构建。按模块检查实际调用链，只记录有代码证据与触发路径的问题。

## 模块划分

| 模块 | 目录/文件 | 关注点 |
| M1 启动与生命周期 | MainActivity, StartupCoordinator, EsjzoneApplication, app/AppContainer | 初始化竞态、Activity 重建、启动阻塞 |
| M2 网络与认证 | network/ 全部 | Cookie/Token 持久化、登录态恢复、超时重试、取消异常 |
| M3 数据库与缓存 | database/、network/*Cache | Room 线程模型、迁移、缓存一致性 |
| M4 离线下载 | offline/ | Worker 生命周期、任务取消、文件一致性 |
| M5 阅读器 | ui/page/ChapterPage*, domain/reader/, ui/reader/ | 性能、内存泄漏、协程作用域 |
| M6 UI 与导航 | ui/navigation/、各 PageModel、screen/ | Compose 生命周期、状态提升、重复加载 |
| M7 设置与持久化/备份 | data/settings/、backup/ | DataStore 迁移、导入导出一致性 |
| M8 其他 | update/、util/、InAppBrowserActivity | 更新检查、日志敏感信息 |

## 产出

- audit/M1..M8-*.md：每模块发现
- audit/REPORT.md：总报告，按严重程度排序

## 严重程度定义

- P0：崩溃/数据丢失/安全泄漏，触发路径明确
- P1：明确功能缺陷或明显影响体验（ANR、状态错乱、任务泄漏）
- P2：潜在问题，触发条件较窄
- P3：观察项/建议
