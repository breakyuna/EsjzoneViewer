# M7 设置持久化与备份

审计文件：`data/settings/SettingsDataStore.kt`、`data/settings/ReaderSettingsDataStore.kt`、`backup/LocalBackup.kt`、`backup/AutoBackup.kt`、`ui/page/LocalBackupPage.kt`、`ui/page/SettingsFormatting.kt`、`util/StoredTextFile.kt`

## 调用链概要

- 设置：双 DataStore（app settings / reader settings），`ReplaceFileCorruptionHandler` 容错，读值全部 coerce/clamp；旧 Room/SharedPreferences 一次性迁移后删源。
- 本地备份：显式 allowlist 导出（不含会话/账号），ZIP 带 zip-slip 防护与体积上限；导入为事务内 merge（新者优先），下载目录单独校验后导入。
- 自动备份：24h 周期 Worker（电量/存储约束），`.part` 临时文件 + 原子重命名，保留 3 份。

## 发现

### M7-1（P3）旧版 `theme` 键被删除但从未迁移
- 证据：`data/settings/SettingsDataStore.kt:143-153`：清理列表含 `"theme"`，但 `migrateFromLegacy`（`:118-137`）未读取该键写入 `THEME_MODE`。
- 触发路径：从保留旧 Room `theme` 设置的版本升级。
- 影响：用户旧版选择的主题静默重置为 SYSTEM（一次性问题，新设置立即可改）。
- 最小修复：迁移时读取 `theme` 并按旧值映射 `AppThemeMode`，或明确删除该键为有意行为并在发布说明注明。

### M7-2（P3）恢复备份时数据库与下载目录非原子
- 证据：`backup/LocalBackup.kt:145-177`（Room 事务先提交）→ `:178-179`（随后 `NovelDownloadStore.importBackup`，其内部 `require` 失败会抛出）。
- 触发路径：包含下载的备份文件中下载部分损坏/版本不符。
- 影响：DB 数据已合并但下载导入失败，UI 报错；数据不损坏但处于"部分恢复"状态，重试会重复合并（merge 语义幂等，可接受）。
- 最小修复：先校验下载归档（试读 manifest）再提交 Room 事务；或在 UI 明确区分"已恢复部分"。

## 结论

设置与备份的校验、allowlist、合并策略完整，未发现凭据泄漏进备份或导出。M7-1/M7-2 为低优先级完善项。
