package com.leo.erp.system.operationlog.partition;

import com.leo.erp.system.operationlog.config.OperationLogPartitionProperties;
import com.leo.erp.system.operationlog.metrics.OperationLogPartitionMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分区清理服务的「只删窗口外空分区」行为测试。
 *
 * <p>重点锁定安全边界：窗口内分区不查空也不删；窗口外非空分区保留；
 * 只有窗口外且为空的分区才执行 DROP。</p>
 */
@ExtendWith(MockitoExtension.class)
class OperationLogPartitionCleanupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private OperationLogPartitionProperties properties;

    @Mock
    private OperationLogPartitionMetrics metrics;

    private OperationLogPartitionCleanupService service;

    @BeforeEach
    void setUp() {
        service = new OperationLogPartitionCleanupService(jdbcTemplate, properties, metrics);
    }

    @Test
    void cleanup_shouldOnlyDropEmptyPartitionsOutsideRetentionWindow() {
        when(jdbcTemplate.queryForObject(contains("to_char(CURRENT_DATE"), eq(String.class)))
                .thenReturn("2026-10");
        when(properties.getRetentionMonths()).thenReturn(12);
        when(properties.getPartitionLookaheadMonths()).thenReturn(12);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of(
                "sys_operation_log_y2026m10",
                "sys_operation_log_y2025m09",
                "sys_operation_log_y2024m03",
                "sys_operation_log_default"));
        // 只有窗口外的两个分区会被查是否为空
        when(jdbcTemplate.queryForObject(contains("sys_operation_log_y2025m09"), eq(Boolean.class)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(contains("sys_operation_log_y2024m03"), eq(Boolean.class)))
                .thenReturn(true);

        OperationLogPartitionCleanupService.CleanupSummary summary =
                service.cleanupExpiredEmptyPartitions();

        assertThat(summary.scanned()).isEqualTo(4);
        assertThat(summary.dropped()).isEqualTo(1);
        assertThat(summary.keptInWindow()).isEqualTo(1);
        assertThat(summary.keptNonEmpty()).isEqualTo(1);
        assertThat(summary.skippedInvalid()).isEqualTo(1);
        assertThat(summary.failed()).isZero();

        // 只有「窗口外且为空」的那个分区会被 DROP
        verify(jdbcTemplate).execute(contains("sys_operation_log_y2025m09"));
        verify(jdbcTemplate, never()).execute(contains("sys_operation_log_y2024m03"));
        verify(jdbcTemplate, never()).execute(contains("sys_operation_log_y2026m10"));
        // 窗口内分区连空检查都不做：避免对活跃分区做多余的 EXISTS 查询
        verify(jdbcTemplate, never()).queryForObject(contains("sys_operation_log_y2026m10"), eq(Boolean.class));
        // 删除 1 个、跳过 3 个（窗口内 + 非空 + 非法名）
        verify(metrics).recordPartitionCleanup(1, 3);
    }
}
