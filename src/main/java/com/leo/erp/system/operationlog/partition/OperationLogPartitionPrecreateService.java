package com.leo.erp.system.operationlog.partition;

import com.leo.erp.system.operationlog.config.OperationLogPartitionProperties;
import com.leo.erp.system.operationlog.metrics.OperationLogPartitionMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作日志月分区预建：补齐「当前月起连续 lookahead 个月」中缺失的分区。
 *
 * <p>背景：V205 把预建到 2036 年的空分区清理到「当前月 ±12」之后，应用内不再有任何分区创建者；
 * 时间推进到窗口末尾时写入会落到 {@code sys_operation_log_default}，随行数增长退化。
 * 本服务按 {@code leo.observability.operation-log.partition-lookahead-months}
 * 计算应存在的月份并补建，保证月分区始终连续可用。</p>
 *
 * <p>安全边界：</p>
 * <ul>
 *   <li>只补建缺失分区，已存在的直接跳过（判定见 {@link OperationLogPartitionCreationPlanner}）；</li>
 *   <li>DDL 使用 {@code CREATE TABLE IF NOT EXISTS ... PARTITION OF}，多实例并发下幂等
 *       （极小概率的并发 DDL 冲突会记失败并在下一轮重试）；</li>
 *   <li>单个分区创建失败记 ERROR（含月份/分区名/default 分区提示）并继续，不影响其它月份；
 *       失败数写入 {@code leo.operation.log.partition.precreate.failed}，持续大于 0 需人工介入；</li>
 *   <li>配置非法（lookahead 小于 1 或过大）时判定返回空清单，本任务不做任何动作。</li>
 * </ul>
 */
@Slf4j
@Service
public class OperationLogPartitionPrecreateService {

    private final JdbcTemplate jdbcTemplate;
    private final OperationLogPartitionProperties properties;
    private final OperationLogPartitionMetrics metrics;

    public OperationLogPartitionPrecreateService(JdbcTemplate jdbcTemplate,
                                                 OperationLogPartitionProperties properties,
                                                 OperationLogPartitionMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 预建缺失分区。
     *
     * @return 本轮统计（应存在/缺失/创建成功/失败）
     */
    public PrecreateSummary precreateMissingPartitions() {
        YearMonth currentMonth = OperationLogPartitionCatalog.currentMonth(jdbcTemplate);
        Set<YearMonth> existingMonths = OperationLogPartitionCatalog.listMonthPartitionNames(jdbcTemplate).stream()
                .map(OperationLogPartitionCleanupPlanner::parsePartitionMonth)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        int lookaheadMonths = properties.getPartitionLookaheadMonths();
        List<YearMonth> expectedMonths =
                OperationLogPartitionCreationPlanner.expectedMonths(currentMonth, lookaheadMonths);
        List<YearMonth> missingMonths =
                OperationLogPartitionCreationPlanner.monthsToCreate(currentMonth, lookaheadMonths, existingMonths);

        int created = 0;
        int failed = 0;
        for (YearMonth month : missingMonths) {
            if (createPartition(month)) {
                created++;
            } else {
                failed++;
            }
        }

        metrics.recordPartitionPrecreate(created, failed, expectedMonths.size());
        log.info("操作日志分区预建完成: 当前月 {}, 窗口内应存在 {} 个, 缺失 {} 个, 创建成功 {} 个, 失败 {} 个",
                currentMonth, expectedMonths.size(), missingMonths.size(), created, failed);
        return new PrecreateSummary(expectedMonths.size(), missingMonths.size(), created, failed);
    }

    /** 创建单个月分区；失败返回 false（记 ERROR 并计入失败指标，不中断整轮）。 */
    private boolean createPartition(YearMonth month) {
        String partitionName = OperationLogPartitionCreationPlanner.partitionName(month);
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        String sql = "CREATE TABLE IF NOT EXISTS " + OperationLogPartitionCatalog.qualifiedPartitionName(partitionName)
                + " PARTITION OF " + OperationLogPartitionCatalog.qualifiedParentTable()
                + " FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
        try {
            jdbcTemplate.execute(sql);
            log.info("已预建操作日志月分区: {}（{} ~ {}）", partitionName, from, to);
            return true;
        } catch (RuntimeException ex) {
            // 失败要能被运维看见：单月失败会逐轮重试，若原因是 default 分区已有该月数据，
            // 则 CREATE ... PARTITION OF 会一直失败，必须人工先把该月行迁出 default。
            log.error("预建操作日志月分区失败，已跳过: {}（{} ~ {}）。"
                            + "若 sys_operation_log_default 已存在该月数据，CREATE ... PARTITION OF 会因"
                            + "default 分区约束校验失败而持续不成功：需先把该月行从 default 迁出（或 detach/attach），"
                            + "再由下一轮任务自动补建；指标 leo.operation.log.partition.precreate.failed 持续 > 0 需人工介入。",
                    partitionName, from, to, ex);
            return false;
        }
    }

    /** 一轮预建的统计结果。 */
    public record PrecreateSummary(int expected, int missing, int created, int failed) {
    }
}
