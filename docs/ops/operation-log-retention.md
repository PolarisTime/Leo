# 操作日志分区保留策略

## 策略

- `sys_operation_log` 热数据保留 12 个月；
- **空分区由应用定时任务自动回收**：`leo.maintenance.operation-log-partition-cleanup`
  （默认每月 1 日 03:20，`enabled`/`cron` 可配）会删除「窗口外且为空」的月分区。
  窗口为 `[当前月 - leo.observability.operation-log.retention-months,
  当前月 + leo.observability.operation-log.partition-lookahead-months]`，
  默认配置（12/12）下即 V205 一次性清理采用的「当前月 ±12 个月」；
- **月分区由应用定时任务自动预建**：`leo.maintenance.operation-log-partition-precreate`
  （默认每天 03:10）按 `partition-lookahead-months` 补齐「当前月起连续 N 个月」中缺失的分区，
  已存在的分区跳过；DDL 为 `CREATE TABLE IF NOT EXISTS ... PARTITION OF`，多实例并发也幂等；
- **非空分区不自动删除**：只要分区里还有一行数据，清理任务就跳过，留给人审或后续归档；
  含数据的过期分区仍必须按下方「到期处理」走人工确认 + Flyway 前滚；
- 应用不执行备份与 detach；**归档/detach 等分区结构变更仍必须通过新增 Flyway 前滚脚本实施**，
  禁止直接对环境执行 DDL/DML（预建/清理空分区这两个已内建的维护动作除外）。

## 日常检查

执行只读诊断：

```bash
bash scripts/maintenance.sh operation-log-partitions
```

以及以下指标：

| 指标 | 含义 | 需要处理的条件 |
| --- | --- | --- |
| `leo.operation.log.expired.rows` | 超过热数据保留期仍在活动分区中的行数 | 大于 0（说明还有数据等归档，清理任务不会碰） |
| `leo.operation.log.default.partition.rows` | 落入默认分区的行数 | 大于 0（说明对应月份分区不存在：检查预建任务是否在跑/是否失败） |
| `leo.operation.log.partition.months.available` | 从当前月起连续可用月分区数 | 小于 6 |
| `leo.operation.log.partition.cleanup.dropped` | **最近一轮**清理删除的空分区数 | 仅用于确认任务真的跑过 |
| `leo.operation.log.partition.cleanup.skipped` | **最近一轮**清理跳过的分区数（窗口内/非空/非法名） | 非空占比长期偏高时人工排查归档 |
| `leo.operation.log.partition.precreated` | **最近一轮**预建成功创建的分区数 | 长期为 0 且 `months.available` 下降时检查预建任务 |

两个分区任务本身失败只记 `log.error`（`OperationLogPartitionCleanupService` /
`OperationLogPartitionPrecreateService` / `MaintenanceScheduledTasks`）：
清理不会删除非空分区，单轮失败的分区下一轮重试；预建单个月份失败不影响其它月份。

## 到期处理（非空分区 / 归档）

1. 确认数据库平台备份、WAL 归档和恢复演练状态正常；
2. 只选择已确认的月分区（按分区名逐个确认，不使用动态匹配批量 detach），记录分区名、边界、行数和校验结果；
3. 新增递增版本 Flyway，仅 detach 已确认的明确分区名；
4. 在回滚窗口内保留独立分区表，验证业务查询、身份表数量和审计检索；
5. 脱机归档并校验完成后，再使用新的更高版本 Flyway 清理 `sys_operation_log_identity` 对应记录并 drop 独立分区表；
6. `sys_operation_log_unpartitioned` 是 V81 迁移核对副本，不纳入自动保留策略，删除前必须单独确认。

任何阶段失败时停止后续步骤，不通过全表 `DELETE` 补救。

## 预建分区

预建任务默认每天 03:10 执行，覆盖「当前月起连续 `partition-lookahead-months` 个月」；
清理任务的未来侧窗口跟随同一配置，因此预建产物不会被清理任务立即删除。

只有在任务长期失败或需要一次补齐大量历史缺口时，才手动处理：

- `default_partition_rows` 大于 0 或 `months.available` 下降时，先查预建任务日志；
- 确需人工补齐时，新增 Flyway 预建脚本（显式分区名 + `CREATE TABLE IF NOT EXISTS ... PARTITION OF`）；
- 预建月数上限为 120 个月（`OperationLogPartitionCreationPlanner.MAX_LOOKAHEAD_MONTHS`），
  配置超过上限时预建任务不动作，避免误配置一次创建上千个分区。
