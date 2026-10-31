package com.leo.erp.system.operationlog.partition;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作日志月分区预建判定纯函数测试：已存在跳过、缺 N 个补齐、非法配置不动作。
 *
 * <p>口径：应存在分区 = 当前月起连续 {@code lookaheadMonths} 个月（offset 0 ~ N-1），
 * 与 {@code leo.operation.log.partition.months.available} 指标定义一致。</p>
 */
class OperationLogPartitionCreationPlannerTest {

    private static final YearMonth CURRENT_MONTH = YearMonth.of(2026, 10);
    private static final int LOOKAHEAD_MONTHS = 12;

    @Test
    void expectedMonths_shouldCoverCurrentMonthAndFollowingLookahead() {
        List<YearMonth> months = OperationLogPartitionCreationPlanner
                .expectedMonths(CURRENT_MONTH, LOOKAHEAD_MONTHS);

        assertThat(months).hasSize(LOOKAHEAD_MONTHS);
        assertThat(months.get(0)).isEqualTo(CURRENT_MONTH);
        assertThat(months.get(LOOKAHEAD_MONTHS - 1)).isEqualTo(YearMonth.of(2027, 9));
    }

    @Test
    void monthsToCreate_shouldReturnNothingWhenAllExpectedPartitionsExist() {
        Set<YearMonth> existing = new HashSet<>(
                OperationLogPartitionCreationPlanner.expectedMonths(CURRENT_MONTH, LOOKAHEAD_MONTHS));

        assertThat(OperationLogPartitionCreationPlanner
                .monthsToCreate(CURRENT_MONTH, LOOKAHEAD_MONTHS, existing)).isEmpty();
    }

    @Test
    void monthsToCreate_shouldFillAllExpectedPartitionsWhenNoneExist() {
        List<YearMonth> toCreate = OperationLogPartitionCreationPlanner
                .monthsToCreate(CURRENT_MONTH, LOOKAHEAD_MONTHS, Set.of());

        assertThat(toCreate).hasSize(LOOKAHEAD_MONTHS);
        assertThat(toCreate).first().isEqualTo(CURRENT_MONTH);
        assertThat(toCreate.get(toCreate.size() - 1)).isEqualTo(YearMonth.of(2027, 9));
    }

    @Test
    void monthsToCreate_shouldFillOnlyMissingMonths() {
        Set<YearMonth> existing = Set.of(
                YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12),
                YearMonth.of(2027, 1), YearMonth.of(2027, 2));

        List<YearMonth> toCreate = OperationLogPartitionCreationPlanner
                .monthsToCreate(CURRENT_MONTH, LOOKAHEAD_MONTHS, existing);

        assertThat(toCreate).hasSize(LOOKAHEAD_MONTHS - existing.size());
        assertThat(toCreate).first().isEqualTo(YearMonth.of(2027, 3));
        assertThat(toCreate.get(toCreate.size() - 1)).isEqualTo(YearMonth.of(2027, 9));
    }

    @Test
    void monthsToCreate_shouldDoNothingForInvalidLookahead() {
        // 0 / 负数 / 超过上限（误配置一次建上千分区）都视为非法配置，不动作
        assertThat(OperationLogPartitionCreationPlanner.monthsToCreate(CURRENT_MONTH, 0, Set.of())).isEmpty();
        assertThat(OperationLogPartitionCreationPlanner.monthsToCreate(CURRENT_MONTH, -1, Set.of())).isEmpty();
        assertThat(OperationLogPartitionCreationPlanner.monthsToCreate(CURRENT_MONTH, 121, Set.of())).isEmpty();
        assertThat(OperationLogPartitionCreationPlanner.expectedMonths(null, LOOKAHEAD_MONTHS)).isEmpty();
    }

    @Test
    void partitionName_shouldMatchDatabaseConvention() {
        assertThat(OperationLogPartitionCreationPlanner.partitionName(YearMonth.of(2026, 10)))
                .isEqualTo("sys_operation_log_y2026m10");
        assertThat(OperationLogPartitionCreationPlanner.partitionName(YearMonth.of(2027, 1)))
                .isEqualTo("sys_operation_log_y2027m01");
    }
}
