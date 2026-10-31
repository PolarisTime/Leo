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
 * 分区预建服务的「只给缺失月份建分区」行为测试。
 *
 * <p>锁定：已存在分区跳过、只对缺失月份执行 DDL、非法 lookahead 配置不动作。</p>
 */
@ExtendWith(MockitoExtension.class)
class OperationLogPartitionPrecreateServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private OperationLogPartitionProperties properties;

    @Mock
    private OperationLogPartitionMetrics metrics;

    private OperationLogPartitionPrecreateService service;

    @BeforeEach
    void setUp() {
        service = new OperationLogPartitionPrecreateService(jdbcTemplate, properties, metrics);
    }

    @Test
    void precreate_shouldOnlyCreateMissingPartitions() {
        when(jdbcTemplate.queryForObject(contains("to_char(CURRENT_DATE"), eq(String.class)))
                .thenReturn("2026-10");
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of(
                "sys_operation_log_y2026m10",
                "sys_operation_log_y2026m11",
                "sys_operation_log_default"));
        when(properties.getPartitionLookaheadMonths()).thenReturn(3);

        OperationLogPartitionPrecreateService.PrecreateSummary summary = service.precreateMissingPartitions();

        // 窗口 = 2026-10 / 2026-11 / 2026-12，只缺 2026-12
        assertThat(summary.expected()).isEqualTo(3);
        assertThat(summary.missing()).isEqualTo(1);
        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(jdbcTemplate).execute(contains("sys_operation_log_y2026m12"));
        verify(jdbcTemplate, never()).execute(contains("sys_operation_log_y2026m10"));
        verify(jdbcTemplate, never()).execute(contains("sys_operation_log_y2026m11"));
        verify(metrics).recordPartitionPrecreate(1);
    }

    @Test
    void precreate_shouldCreateNothingForInvalidLookahead() {
        when(jdbcTemplate.queryForObject(contains("to_char(CURRENT_DATE"), eq(String.class)))
                .thenReturn("2026-10");
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        when(properties.getPartitionLookaheadMonths()).thenReturn(0);

        OperationLogPartitionPrecreateService.PrecreateSummary summary = service.precreateMissingPartitions();

        assertThat(summary.expected()).isZero();
        assertThat(summary.missing()).isZero();
        assertThat(summary.created()).isZero();
        verify(jdbcTemplate, never()).execute(anyString());
        verify(metrics).recordPartitionPrecreate(0);
    }
}
