# 操作日志分区保留策略

## 策略

- `sys_operation_log` 热数据保留 12 个月；
- 月分区只有在整个分区都早于 `date_trunc('month', CURRENT_DATE) - interval '12 months'` 时才允许退出活动表，因此实际窗口介于 12 个月和不足 13 个月；
- 应用仅发布保留期、过期行数、默认分区行数和未来分区可用月数指标，不在应用进程中执行备份、detach 或 drop；
- 分区结构和持久化数据变更必须通过新增 Flyway 前滚脚本实施，禁止直接对环境执行 DDL/DML。

## 日常检查

执行只读诊断：

```bash
bash scripts/maintenance.sh operation-log-partitions
```

以下任一条件需要处理：

- `expired_active_rows` 大于 0；
- `default_partition_rows` 大于 0；
- `leo_operation_log_partition_months_available` 小于 6；
- `leo_operation_log_expired_rows` 大于 0。

## 到期处理

1. 确认数据库平台备份、WAL 归档和恢复演练状态正常；
2. 只选择上界不晚于完整分区截止日的月分区，记录分区名、边界、行数和校验结果；
3. 新增递增版本 Flyway，仅 detach 已确认的明确分区名，不使用动态匹配批量处理；
4. 在回滚窗口内保留独立分区表，验证业务查询、身份表数量和审计检索；
5. 脱机归档并校验完成后，再使用新的更高版本 Flyway 清理 `sys_operation_log_identity` 对应记录并 drop 独立分区表；
6. `sys_operation_log_unpartitioned` 是 V81 迁移核对副本，不纳入自动保留策略，删除前必须单独确认。

任何阶段失败时停止后续步骤，不通过全表 `DELETE` 补救。
