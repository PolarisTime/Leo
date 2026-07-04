# 数据库备份与导入运维说明

## 职责边界

PR7 起，生产数据库备份职责默认外部化，由数据库平台、云厂商快照、对象存储归档或独立运维任务负责。

应用内仍保留手动导出、受控导入和非生产环境定时备份能力，但生产环境默认关闭以下能力：

- 备份文件导入：`leo.database.import.enabled=false`
- 应用内定时数据库备份：`leo.maintenance.database-backup.enabled=false`

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `leo.database.import.enabled` | 非生产 `true`，生产 `false` | 控制 `DatabaseBackupService.importBackup(...)` 是否允许执行导入。关闭时会在任何文件校验或外部进程启动前失败。 |
| `leo.database.backup.auto-backup-before-import` | `true` | 导入前是否自动执行一次 `pg_dump`。仅在导入开关开启后生效。 |
| `leo.database.backup.storage-path` | `/tmp/leo/database-backups` | 后台导出和应用内定时备份文件目录。 |
| `leo.database.backup.pg-dump-command` | `/usr/pgsql-18/bin/pg_dump` | `pg_dump` 可执行文件路径。 |
| `leo.database.backup.psql-command` | `/usr/pgsql-18/bin/psql` | `psql` 可执行文件路径。 |
| `leo.maintenance.database-backup.enabled` | 非生产 `true`，生产 `false` | 控制应用内定时数据库备份任务。生产如需临时启用，必须显式设置环境变量。 |

## 生产导入流程

生产环境恢复或回灌数据前，应优先使用数据库平台提供的恢复能力。确需通过应用导入备份文件时，按变更流程显式开启：

```bash
LEO_DATABASE_IMPORT_ENABLED=true
```

导入完成后应移除该环境变量或恢复为 `false`，避免导入入口长期暴露。

当 `leo.database.import.enabled=false` 时，`DatabaseBackupService.importBackup(...)` 会立即抛出业务异常，不会读取导入文件，也不会启动 `pg_dump` 或 `psql` 进程。

## 生产定时备份

生产默认不由应用内调度创建数据库备份：

```bash
LEO_MAINTENANCE_DATABASE_BACKUP_ENABLED=false
```

生产备份应在应用外配置独立保留策略，并定期验证恢复链路。若需要临时启用应用内备份，应确认备份目录容量、保留天数和 `pg_dump` 路径后再设置：

```bash
LEO_MAINTENANCE_DATABASE_BACKUP_ENABLED=true
LEO_DATABASE_BACKUP_STORAGE_PATH=/var/backups/leo/database
LEO_MAINTENANCE_DATABASE_BACKUP_RETENTION_DAYS=30
```
