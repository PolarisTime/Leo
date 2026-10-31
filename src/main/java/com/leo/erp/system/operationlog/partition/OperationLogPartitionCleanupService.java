package com.leo.erp.system.operationlog.partition;

import com.leo.erp.system.operationlog.config.OperationLogPartitionProperties;
import com.leo.erp.system.operationlog.metrics.OperationLogPartitionMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * 操作日志月分区清理：删除「超出保留窗口且为空」的月分区。
 *
 * <p>背景：{@code sys_operation_log} 的月分区由基线迁移一次性预建到 2036 年，
 * {@code leo.observability.operation-log.retention-months} 此前只用于产出过期行数指标，
 * 没有任何常驻任务真正回收分区。V205 做过一次性清理（保留当前月 ±12 个月），
 * 本服务把同一口径固化为定时任务，避免分区再次只涨不删。</p>
 *
 * <p>安全边界：</p>
 * <ul>
 *   <li>窗口为 {@code [当前月 - retention-months, 当前月 + partition-lookahead-months]}，
 *       默认配置下与 V205 的「当前月 ±12 个月」等价；</li>
 *   <li>只删<b>窗口外且为空</b>的分区；非空分区一律保留（留给人审或后续归档），
 *       空判定用 {@code EXISTS ... LIMIT 1} 而非 {@code count(*)}，避免大分区全表扫描；</li>
 *   <li>只认 {@code sys_operation_log_yYYYYmMM} 形态且位于 public schema 的日历分区，
 *       默认分区（{@code sys_operation_log_default}）不参与；</li>
 *   <li>单个分区删除失败只记日志并继续，不阻断其它分区。</li>
 * </ul>
 *
 * <p>判定本身在 {@link OperationLogPartitionCleanupPlanner} 里，是纯函数；本类只负责
 * 查目录、查空、执行 DROP 与汇报指标。</p>
 */
@Slf4j
@Service
public class OperationLogPartitionCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final OperationLogPartitionProperties properties;
    private final OperationLogPartitionMetrics metrics;

    public OperationLogPartitionCleanupService(JdbcTemplate jdbcTemplate,
                                               OperationLogPartitionProperties properties,
                                               OperationLogPartitionMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 清理一轮：删掉超出保留窗口的空分区。
     *
     * @return 本轮统计（扫描/删除/各类跳过/失败）
     */
    public CleanupSummary cleanupExpiredEmptyPartitions() {
        YearMonth currentMonth = OperationLogPartitionCatalog.currentMonth(jdbcTemplate);
        List<String> partitions = OperationLogPartitionCatalog.listMonthPartitionNames(jdbcTemplate);

        int dropped = 0;
        int keptInWindow = 0;
        int keptNonEmpty = 0;
        int skippedInvalid = 0;
        int failed = 0;
        for (String partition : partitions) {
            OperationLogPartitionCleanupPlanner.PartitionPlan plan = planFor(partition, currentMonth);
            switch (plan.action()) {
                case DROP -> {
                    if (dropPartition(partition)) {
                        dropped++;
                    } else {
                        failed++;
                    }
                }
                case KEEP_IN_WINDOW -> keptInWindow++;
                case KEEP_NON_EMPTY -> keptNonEmpty++;
                case SKIP_INVALID_NAME -> {
                    log.debug("跳过无法识别的操作日志分区名: {}", partition);
                    skippedInvalid++;
                }
                default -> throw new IllegalStateException("未知的分区清理判定: " + plan.action());
            }
        }

        int skipped = keptInWindow + keptNonEmpty + skippedInvalid;
        metrics.recordPartitionCleanup(dropped, skipped);
        log.info("操作日志分区清理完成: 扫描 {} 个, 删除 {} 个, 跳过 {} 个"
                        + "（窗口内 {} / 非空 {} / 非法名 {}）, 删除失败 {} 个",
                partitions.size(), dropped, skipped, keptInWindow, keptNonEmpty, skippedInvalid, failed);
        return new CleanupSummary(partitions.size(), dropped, keptInWindow, keptNonEmpty, skippedInvalid, failed);
    }

    /**
     * 组装单个分区的判定输入。
     *
     * <p>只有「能解析且在窗口外」的分区才去查是否为空——窗口内分区无论空不空都会保留，
     * 多查一次 {@code EXISTS} 没有意义。</p>
     */
    private OperationLogPartitionCleanupPlanner.PartitionPlan planFor(String partition, YearMonth currentMonth) {
        int retentionMonths = properties.getRetentionMonths();
        int lookaheadMonths = properties.getPartitionLookaheadMonths();
        boolean hasRows = OperationLogPartitionCleanupPlanner.parsePartitionMonth(partition)
                .filter(month -> !OperationLogPartitionCleanupPlanner
                        .isWithinRetentionWindow(month, currentMonth, retentionMonths, lookaheadMonths))
                .map(month -> hasAnyRow(partition))
                .orElse(false);
        return OperationLogPartitionCleanupPlanner.plan(
                partition, currentMonth, retentionMonths, lookaheadMonths, hasRows);
    }

    private boolean hasAnyRow(String partitionName) {
        String qualifiedName = OperationLogPartitionCatalog.qualifiedPartitionName(partitionName);
        Boolean present = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + qualifiedName + " LIMIT 1)", Boolean.class);
        return Boolean.TRUE.equals(present);
    }

    /** 删除分区；失败返回 false（只记日志，不中断整轮清理）。 */
    private boolean dropPartition(String partitionName) {
        String qualifiedName = OperationLogPartitionCatalog.qualifiedPartitionName(partitionName);
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + qualifiedName);
            log.info("已删除超出保留期且为空的操作日志分区: {}", partitionName);
            return true;
        } catch (RuntimeException ex) {
            log.warn("删除操作日志分区失败，已跳过: {}", partitionName, ex);
            return false;
        }
    }

    /** 一轮清理的统计结果。 */
    public record CleanupSummary(int scanned, int dropped, int keptInWindow, int keptNonEmpty,
                                 int skippedInvalid, int failed) {
    }
}
