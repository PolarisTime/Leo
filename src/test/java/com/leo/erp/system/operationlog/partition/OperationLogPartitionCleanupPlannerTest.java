package com.leo.erp.system.operationlog.partition;

import com.leo.erp.system.operationlog.partition.OperationLogPartitionCleanupPlanner.Action;
import com.leo.erp.system.operationlog.partition.OperationLogPartitionCleanupPlanner.PartitionPlan;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作日志月分区清理判定的纯函数测试：窗口判断、非空保留、非法分区名跳过。
 *
 * <p>窗口口径与 V205 一次性清理一致：过去保留 {@code retentionMonths} 个月、未来保护
 * {@code lookaheadMonths} 个预建分区，两端都算窗口内；默认 12/12 时即「当前月 ±12 个月」。</p>
 */
class OperationLogPartitionCleanupPlannerTest {

    private static final YearMonth CURRENT_MONTH = YearMonth.of(2026, 10);
    private static final int RETENTION_MONTHS = 12;
    private static final int LOOKAHEAD_MONTHS = 12;

    @Test
    void plan_shouldKeepPartitionsInsideRetentionWindow() {
        // 当前月与前后边界（-12 / +12 个月）即使为空也不删
        assertThat(planByMonth(CURRENT_MONTH, false).action()).isEqualTo(Action.KEEP_IN_WINDOW);
        assertThat(planByMonth(YearMonth.of(2025, 10), false).action()).isEqualTo(Action.KEEP_IN_WINDOW);
        assertThat(planByMonth(YearMonth.of(2027, 10), false).action()).isEqualTo(Action.KEEP_IN_WINDOW);
    }

    @Test
    void plan_shouldKeepPartitionOutsideWindowWhenItStillHasRows() {
        PartitionPlan plan = planByMonth(YearMonth.of(2025, 9), true);

        assertThat(plan.action()).isEqualTo(Action.KEEP_NON_EMPTY);
        assertThat(plan.partitionMonth()).isEqualTo(YearMonth.of(2025, 9));
    }

    @Test
    void plan_shouldDropEmptyPartitionOutsideWindow() {
        // 过去 13 个月与未来 13 个月的空分区都可删
        assertThat(planByMonth(YearMonth.of(2025, 9), false).action()).isEqualTo(Action.DROP);
        assertThat(planByMonth(YearMonth.of(2027, 11), false).action()).isEqualTo(Action.DROP);
    }

    @Test
    void plan_shouldProtectFuturePartitionsWithinLookaheadWindow() {
        // 预建月数调大到 24 时，未来 24 个月内的空分区必须保留，不能因为保留期是 12 就被删
        YearMonth futureMonth = YearMonth.of(2028, 3);
        PartitionPlan plan = OperationLogPartitionCleanupPlanner.plan(
                partitionName(futureMonth), CURRENT_MONTH, RETENTION_MONTHS, 24, false);

        assertThat(plan.action()).isEqualTo(Action.KEEP_IN_WINDOW);
    }

    @Test
    void plan_shouldSkipInvalidPartitionName() {
        // 默认分区、越界月份、格式不符、null 都不是可清理的月分区
        assertThat(planByName("sys_operation_log_default", false).action()).isEqualTo(Action.SKIP_INVALID_NAME);
        assertThat(planByName("sys_operation_log_y2026m13", false).action()).isEqualTo(Action.SKIP_INVALID_NAME);
        assertThat(planByName("sys_operation_log_y2026m1", false).action()).isEqualTo(Action.SKIP_INVALID_NAME);
        assertThat(planByName("other_table", false).action()).isEqualTo(Action.SKIP_INVALID_NAME);
        assertThat(planByName(null, false).action()).isEqualTo(Action.SKIP_INVALID_NAME);
    }

    @Test
    void parsePartitionMonth_shouldAcceptOnlyCalendarMonthNames() {
        assertThat(OperationLogPartitionCleanupPlanner.parsePartitionMonth("sys_operation_log_y2036m12"))
                .contains(YearMonth.of(2036, 12));
        assertThat(OperationLogPartitionCleanupPlanner.parsePartitionMonth("sys_operation_log_y2026m00"))
                .isEmpty();
        assertThat(OperationLogPartitionCleanupPlanner.parsePartitionMonth("sys_operation_log_y2026m12_backup"))
                .isEmpty();
        assertThat(OperationLogPartitionCleanupPlanner.parsePartitionMonth(null)).isEmpty();
    }

    @Test
    void isWithinRetentionWindow_shouldIncludeBothBoundaries() {
        assertThat(OperationLogPartitionCleanupPlanner.isWithinRetentionWindow(
                YearMonth.of(2025, 10), CURRENT_MONTH, RETENTION_MONTHS, LOOKAHEAD_MONTHS)).isTrue();
        assertThat(OperationLogPartitionCleanupPlanner.isWithinRetentionWindow(
                YearMonth.of(2027, 10), CURRENT_MONTH, RETENTION_MONTHS, LOOKAHEAD_MONTHS)).isTrue();
        assertThat(OperationLogPartitionCleanupPlanner.isWithinRetentionWindow(
                YearMonth.of(2025, 9), CURRENT_MONTH, RETENTION_MONTHS, LOOKAHEAD_MONTHS)).isFalse();
        assertThat(OperationLogPartitionCleanupPlanner.isWithinRetentionWindow(
                YearMonth.of(2027, 11), CURRENT_MONTH, RETENTION_MONTHS, LOOKAHEAD_MONTHS)).isFalse();
    }

    private static PartitionPlan planByName(String partitionName, boolean hasRows) {
        return OperationLogPartitionCleanupPlanner.plan(
                partitionName, CURRENT_MONTH, RETENTION_MONTHS, LOOKAHEAD_MONTHS, hasRows);
    }

    private static PartitionPlan planByMonth(YearMonth partitionMonth, boolean hasRows) {
        return planByName(partitionName(partitionMonth), hasRows);
    }

    private static String partitionName(YearMonth month) {
        return "sys_operation_log_y" + month.getYear() + "m" + String.format("%02d", month.getMonthValue());
    }
}
