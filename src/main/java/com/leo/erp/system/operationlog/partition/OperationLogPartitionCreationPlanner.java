package com.leo.erp.system.operationlog.partition;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 操作日志月分区预建判定（纯函数，不访问数据库，便于单测）。
 *
 * <p>背景：V205 一次性清理后，未来侧只保留到「当前月 + partition-lookahead-months」，
 * 而应用此前没有任何分区创建者 —— 时间推进到窗口末尾后，操作日志写入会落到
 * {@code sys_operation_log_default} 单分区，随行数增长退化。本判定按
 * {@code partition-lookahead-months} 计算「当前月起连续 N 个月」应存在的分区，
 * 并过滤掉已存在的，供定时任务按缺失清单补建。</p>
 *
 * <p>「N 个月」与现有指标 {@code leo.operation.log.partition.months.available} 的口径一致：
 * 从当前月（offset 0）到当前月 + N - 1；与清理任务的上界（当前月 + lookahead）兼容，
 * 预建产物不会被清理任务立即删除。</p>
 */
public final class OperationLogPartitionCreationPlanner {

    /** 预建月数上限：防止误配置（如 1000）一次创建上千个分区。 */
    static final int MAX_LOOKAHEAD_MONTHS = 120;

    private OperationLogPartitionCreationPlanner() {
    }

    /**
     * 应存在的分区月份：当前月起连续 {@code lookaheadMonths} 个月。
     *
     * <p>配置非法（小于 1 或超过 {@link #MAX_LOOKAHEAD_MONTHS}）时返回空列表，
     * 定时任务据此不做任何动作，避免误配置造成批量 DDL。</p>
     */
    public static List<YearMonth> expectedMonths(YearMonth currentMonth, int lookaheadMonths) {
        if (currentMonth == null || lookaheadMonths < 1 || lookaheadMonths > MAX_LOOKAHEAD_MONTHS) {
            return List.of();
        }
        List<YearMonth> months = new ArrayList<>(lookaheadMonths);
        for (int offset = 0; offset < lookaheadMonths; offset++) {
            months.add(currentMonth.plusMonths(offset));
        }
        return List.copyOf(months);
    }

    /**
     * 需要补建的分区月份：应存在但当前不存在的月份，按时间升序。
     *
     * @param existingMonths 已存在的分区月份（可为 null，视为全部缺失）
     */
    public static List<YearMonth> monthsToCreate(YearMonth currentMonth,
                                                 int lookaheadMonths,
                                                 Set<YearMonth> existingMonths) {
        Set<YearMonth> existing = existingMonths == null ? Set.of() : existingMonths;
        return expectedMonths(currentMonth, lookaheadMonths).stream()
                .filter(month -> !existing.contains(month))
                .toList();
    }

    /** 月分区名，与 V81 / 清理任务的正则 {@code sys_operation_log_yYYYYmMM} 一致。 */
    public static String partitionName(YearMonth month) {
        return String.format("sys_operation_log_y%04dm%02d", month.getYear(), month.getMonthValue());
    }
}
