package com.leo.erp.system.operationlog.partition;

import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 操作日志月分区清理判定（纯函数，不访问数据库，便于单测）。
 *
 * <p>分区由基线迁移按月预建，命名固定为 {@code sys_operation_log_yYYYYmMM}。保留窗口
 * {@code [当前月 - retentionMonths, 当前月 + lookaheadMonths]} 与 V205 一次性清理口径一致
 * （两个配置都取默认 12 时就是「当前月 ±12 个月」）：过去按热数据保留期回收，
 * 未来按预建月数保护可写分区，避免把「热数据保留」和「预建写缓冲」混成同一个配置。
 * 窗口外的空分区可删；窗口外但仍有数据的分区一律保留，留给人审或后续归档；
 * 无法解析的分区名（例如默认分区 {@code sys_operation_log_default}）直接跳过。</p>
 *
 * <p>这里只回答「该不该删」，存在性检查与 {@code DROP TABLE} 由
 * {@link OperationLogPartitionCleanupService} 执行，避免把业务判定埋进 SQL 字符串里。</p>
 */
public final class OperationLogPartitionCleanupPlanner {

    /** 月分区名模式：sys_operation_log_yYYYYmMM。 */
    private static final Pattern PARTITION_NAME_PATTERN =
            Pattern.compile("^sys_operation_log_y(\\d{4})m(\\d{2})$");

    private static final int MIN_MONTH = 1;
    private static final int MAX_MONTH = 12;

    private OperationLogPartitionCleanupPlanner() {
    }

    /**
     * 判定单个分区该保留还是删除。
     *
     * @param partitionName    分区名
     * @param currentMonth     当前月（由调用方传入，便于测试固定时间）
     * @param retentionMonths  热数据保留月数，窗口下界为「当前月 - 该值」
     * @param lookaheadMonths  预建月数，窗口上界为「当前月 + 该值」，保护可写分区
     * @param hasRows          该分区是否已有数据行
     * @return 判定结果；{@code SKIP_INVALID_NAME} 时 {@code partitionMonth} 为空
     */
    public static PartitionPlan plan(String partitionName,
                                     YearMonth currentMonth,
                                     int retentionMonths,
                                     int lookaheadMonths,
                                     boolean hasRows) {
        Optional<YearMonth> partitionMonth = parsePartitionMonth(partitionName);
        if (partitionMonth.isEmpty()) {
            return new PartitionPlan(partitionName, null, Action.SKIP_INVALID_NAME);
        }
        if (isWithinRetentionWindow(partitionMonth.get(), currentMonth, retentionMonths, lookaheadMonths)) {
            return new PartitionPlan(partitionName, partitionMonth.get(), Action.KEEP_IN_WINDOW);
        }
        if (hasRows) {
            return new PartitionPlan(partitionName, partitionMonth.get(), Action.KEEP_NON_EMPTY);
        }
        return new PartitionPlan(partitionName, partitionMonth.get(), Action.DROP);
    }

    /**
     * 解析月分区名。
     *
     * <p>只接受 {@code sys_operation_log_yYYYYmMM} 这一种形态；月份越界（如 {@code y2026m13}）
     * 同样视为非法名，避免把脏名字当成可删分区。</p>
     */
    public static Optional<YearMonth> parsePartitionMonth(String partitionName) {
        if (partitionName == null) {
            return Optional.empty();
        }
        Matcher matcher = PARTITION_NAME_PATTERN.matcher(partitionName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        if (month < MIN_MONTH || month > MAX_MONTH) {
            return Optional.empty();
        }
        return Optional.of(YearMonth.of(year, month));
    }

    /**
     * 分区月是否落在保留窗口内（含两端）。
     *
     * <p>下界来自热数据保留期，上界来自预建月数：调大预建月数（例如为未来半年预留分区）
     * 不会因为保留期仍是 12 而被清理任务误删。</p>
     */
    public static boolean isWithinRetentionWindow(YearMonth partitionMonth,
                                                  YearMonth currentMonth,
                                                  int retentionMonths,
                                                  int lookaheadMonths) {
        YearMonth earliest = currentMonth.minusMonths(retentionMonths);
        YearMonth latest = currentMonth.plusMonths(lookaheadMonths);
        return !partitionMonth.isBefore(earliest) && !partitionMonth.isAfter(latest);
    }

    /** 判定结论。 */
    public enum Action {
        /** 窗口外且无数据：可删。 */
        DROP,
        /** 窗口内：保留。 */
        KEEP_IN_WINDOW,
        /** 窗口外但仍有数据：保留待人工归档。 */
        KEEP_NON_EMPTY,
        /** 分区名无法解析：跳过。 */
        SKIP_INVALID_NAME
    }

    /** 单个分区的判定结果；{@code partitionMonth} 仅在非法分区名时为 null。 */
    public record PartitionPlan(String partitionName, YearMonth partitionMonth, Action action) {
    }
}
