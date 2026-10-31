# 操作日志分区保留策略

## 策略

- `sys_operation_log` 热数据保留 12 个月；
- **空分区由应用定时任务自动回收**：`leo.maintenance.operation-log-partition-cleanup`
  （默认每月 1 日 03:20，`enabled`/`cron` 可配）会删除「窗口外且为空」的月分区。
  窗口为 `[当前月 - leo.observability.operation-log.retention-months,
  当前月 + leo.observability.operation-log.partition-lookahead-months]`，
  默认配置（12/12）下即 V205 一次性清理采用的「当前月 ±12 个月」；
- **非空分区不自动删除**：只要分区里还有一行数据，清理任务就跳过，留给人审或后续归档；
  含数据的过期分区仍必须按下方「到期处理」走人工确认 + Flyway 前滚；
- 应用不执行备份与 detach；分区结构变更（预建新分区、detach 归档）仍必须通过新增 Flyway
  前滚脚本实施，禁止直接对环境执行 DDL/DML。

## 日常检查

执行只读诊断：

```bash
bash scripts/maintenance.sh operation-log-partitions
```

以及以下指标：

| 指标 | 含义 | 需要处理的条件 |
| --- | --- | --- |
| `leo.operation.log.expired.rows` | 超过热数据保留期仍在活动分区中的行数 | 大于 0（说明还有数据等归档，清理任务不会碰） |
| `leo.operation.log.default.partition.rows` | 落入默认分区的行数 | 大于 0（说明对应月份的分区不存在，需预建） |
| `leo.operation.log.partition.months.available` | 从当前月起连续可用月分区数 | 小于 6 |
| `leo.operation.log.partition.cleanup.dropped` | **最近一轮**清理删除的空分区数 | 仅用于确认任务真的跑过 |
| `leo.operation.log.partition.cleanup.skipped` | **最近一轮**清理跳过的分区数（窗口内/非空/非法名） | 非空占比长期偏高时人工排查归档 |

清理任务本身失败只记 `log.error`（`OperationLogPartitionCleanupService` / `MaintenanceScheduledTasks`），
不会删除任何非空分区；单轮删除失败的分区会在下一轮重试。

## 到期处理（非空分区 / 归档）

1. 确认数据库平台备份、WAL 归档和恢复演练状态正常；
2. 只选择已确认的月分区（按分区名逐个确认，不使用动态匹配批量 detach），记录分区名、边界、行数和校验结果；
3. 新增递增版本 Flyway，仅 detach 已确认的明确分区名；
4. 在回滚窗口内保留独立分区表，验证业务查询、身份表数量和审计检索；
5. 脱机归档并校验完成后，再使用新的更高版本 Flyway 清理 `sys_operation_log_identity` 对应记录并 drop 独立分区表；
6. `sys_operation_log_unpartitioned` 是 V81 迁移核对副本，不纳入自动保留策略，删除前必须单独确认。

任何阶段失败时停止后续步骤，不通过全表 `DELETE` 补救。

## 预建分区

V205 清理后，未来侧只保留到「当前月 + `partition-lookahead-months`」。分区不存在时写入会落到
`sys_operation_log_default`（`default_partition_rows` 指标会变大），因此：

- `months.available` 下降或 `default_partition_rows` 大于 0 时，需要新增 Flyway 预建后续月份分区；
- 清理任务的未来侧窗口跟随 `partition-lookahead-months`，调大该值即可保护更多预建分区，不会与保留期冲突。
