# 可靠业务事件运维说明

## 定位

`event_publication` 是 Spring Modulith 的投递注册表，不是业务审计表。操作日志结果保存在
`sys_operation_log`，采购订单快照结果保存在 JaVers 表及 `jv_business_event`。

应用使用 `completion-mode: delete`：消费成功后删除投递注册记录，未完成记录继续保留，并在应用启动时自动重投。
切换前由 `update` 模式留下的已完成记录不属于当前积压，不应与未完成记录混合告警。

## 指标与告警

Prometheus 暴露两个 Micrometer Gauge：

- `leo_business_events_incomplete`：未完成投递数量。
- `leo_business_events_oldest_age_seconds`：最早未完成投递的等待秒数。

建议先以“未完成数量持续大于 0 达 5 分钟”为告警，以“最老等待时间超过 300 秒”为升级条件；上线后根据正常吞吐和重启耗时调整阈值。告警应同时关联应用错误日志、监听器 ID 和事件类型。

## 只读诊断

汇总当前积压：

```sql
SELECT count(*) AS incomplete_count,
       COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - min(publication_date))), 0)::bigint
           AS oldest_age_seconds
FROM event_publication
WHERE completion_date IS NULL;
```

查看最早的未完成投递：

```sql
SELECT id, listener_id, event_type, publication_date
FROM event_publication
WHERE completion_date IS NULL
ORDER BY publication_date, id
LIMIT 100;
```

以上查询不读取序列化事件正文，避免在诊断输出中暴露业务载荷。

## 重放流程

1. 先根据 `listener_id` 和 `event_type` 定位消费者错误，恢复数据库、依赖服务或数据前置条件。
2. 重试前确认消费者幂等：操作日志以 `event_id` 唯一，采购订单快照以 `jv_business_event.event_id` 认领。
3. 通过受控应用重启触发未完成事件自动重投；滚动处理时先观察单个实例，避免重复重启放大故障。
4. 观察两个指标回落，并在目标审计表中按事件 ID 核对消费结果。
5. 指标未回落时保留现场，结合应用日志继续定位，不得通过删除注册记录伪造成功。

禁止删除 `completion_date IS NULL` 的记录。任何历史已完成记录治理都必须经过独立评审和备份流程，不在故障处置中直接执行持久化操作。
