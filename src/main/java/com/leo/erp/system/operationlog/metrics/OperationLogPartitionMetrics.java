package com.leo.erp.system.operationlog.metrics;

import com.leo.erp.system.operationlog.config.OperationLogPartitionProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class OperationLogPartitionMetrics {

    private final JdbcTemplate jdbcTemplate;
    private final OperationLogPartitionProperties properties;
    private final AtomicLong defaultPartitionRows = new AtomicLong();
    private final AtomicLong availablePartitionMonths = new AtomicLong();
    private final AtomicLong expiredRows = new AtomicLong();

    public OperationLogPartitionMetrics(JdbcTemplate jdbcTemplate,
                                        MeterRegistry meterRegistry,
                                        OperationLogPartitionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        Gauge.builder("leo.operation.log.default.partition.rows", defaultPartitionRows, AtomicLong::get)
                .description("落入操作日志默认分区的行数")
                .register(meterRegistry);
        Gauge.builder("leo.operation.log.partition.months.available", availablePartitionMonths, AtomicLong::get)
                .baseUnit("months")
                .description("从当前月开始连续可用的操作日志月分区数量")
                .register(meterRegistry);
        Gauge.builder("leo.operation.log.expired.rows", expiredRows, AtomicLong::get)
                .description("超过操作日志热数据保留期但仍在活动分区中的行数")
                .register(meterRegistry);
        Gauge.builder("leo.operation.log.retention.months", properties,
                        OperationLogPartitionProperties::getRetentionMonths)
                .baseUnit("months")
                .description("操作日志热数据保留月数")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${leo.observability.partition-metrics-refresh-interval:5m}")
    public void refresh() {
        PartitionMetrics metrics = jdbcTemplate.queryForObject(
                """
                WITH expected_partitions AS (
                    SELECT month_offset,
                           format(
                               'public.sys_operation_log_y%sm%s',
                               to_char(date_trunc('month', CURRENT_DATE) + make_interval(months => month_offset), 'YYYY'),
                               to_char(date_trunc('month', CURRENT_DATE) + make_interval(months => month_offset), 'MM')
                           ) AS relation_name
                    FROM generate_series(0, ?) AS offsets(month_offset)
                )
                SELECT (SELECT count(*) FROM public.sys_operation_log_default) AS default_partition_rows,
                       COALESCE(
                           min(month_offset) FILTER (WHERE to_regclass(relation_name) IS NULL),
                           ?
                       ) AS available_partition_months,
                       (
                           SELECT count(*)
                           FROM public.sys_operation_log
                           WHERE operation_time < date_trunc('month', CURRENT_DATE)
                                   - make_interval(months => ?)
                       ) AS expired_rows
                FROM expected_partitions
                """,
                (resultSet, rowNum) -> new PartitionMetrics(
                        resultSet.getLong("default_partition_rows"),
                        resultSet.getLong("available_partition_months"),
                        resultSet.getLong("expired_rows")
                ),
                properties.getPartitionLookaheadMonths() - 1,
                properties.getPartitionLookaheadMonths(),
                properties.getRetentionMonths()
        );
        if (metrics == null) {
            return;
        }
        defaultPartitionRows.set(metrics.defaultPartitionRows());
        availablePartitionMonths.set(metrics.availablePartitionMonths());
        expiredRows.set(metrics.expiredRows());
    }

    private record PartitionMetrics(long defaultPartitionRows, long availablePartitionMonths, long expiredRows) {
    }
}
