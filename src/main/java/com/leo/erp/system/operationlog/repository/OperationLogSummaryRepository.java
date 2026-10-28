package com.leo.erp.system.operationlog.repository;

import com.leo.erp.system.operationlog.support.OperationLogConstants;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志概览聚合查询。
 *
 * <p>用 SQL 聚合(group by to_char(operation_time,'YYYY-MM-DD'))而不是把 40 万行拉回内存,
 * 走 operation_time 分区裁剪与既有时间索引。动态筛选只在参数存在时拼条件, 不绑定 null,
 * 避免 JDBC 参数类型推断问题(与 {@code CashLedgerQueryRepository} 同风格)。</p>
 *
 * <p>时间边界为左闭右开 {@code [startTime, endTimeExclusive)}, 由服务层换算。</p>
 */
@Repository
public class OperationLogSummaryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OperationLogSummaryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 汇总总数/成功数/失败数/平均耗时。 */
    public SummaryCounts count(SummaryCriteria criteria) {
        String sql = """
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE result_status = :successStatus) AS success_count,
                       count(*) FILTER (WHERE result_status = :failureStatus) AS failed_count,
                       COALESCE(round(avg(duration_ms)), 0) AS avg_duration_ms
                FROM public.sys_operation_log
                WHERE operation_time >= :startTime
                  AND operation_time < :endTime
                """ + filterSql(criteria);
        return jdbcTemplate.queryForObject(sql, parameters(criteria), (rs, rowNum) -> new SummaryCounts(
                rs.getLong("total"),
                rs.getLong("success_count"),
                rs.getLong("failed_count"),
                rs.getLong("avg_duration_ms")
        ));
    }

    /** 按天聚合(只返回有数据的日期)。 */
    public List<DailyCount> countByDay(SummaryCriteria criteria) {
        String sql = """
                SELECT to_char(operation_time, 'YYYY-MM-DD') AS day,
                       count(*) AS total,
                       count(*) FILTER (WHERE result_status = :failureStatus) AS failed
                FROM public.sys_operation_log
                WHERE operation_time >= :startTime
                  AND operation_time < :endTime
                """ + filterSql(criteria) + """
                GROUP BY 1
                ORDER BY 1
                """;
        return jdbcTemplate.query(sql, parameters(criteria), (rs, rowNum) -> new DailyCount(
                LocalDate.parse(rs.getString("day")),
                rs.getLong("total"),
                rs.getLong("failed")
        ));
    }

    /** 失败次数最多的模块(前 limit 个, 次数相同按模块名升序保证稳定)。 */
    public List<FailedModuleCount> topFailedModules(SummaryCriteria criteria, int limit) {
        String sql = """
                SELECT module_name AS module_name,
                       count(*) AS failed_count
                FROM public.sys_operation_log
                WHERE operation_time >= :startTime
                  AND operation_time < :endTime
                  AND result_status = :failureStatus
                """ + filterSql(criteria) + """
                GROUP BY module_name
                ORDER BY failed_count DESC, module_name ASC
                LIMIT :limit
                """;
        MapSqlParameterSource parameters = parameters(criteria).addValue("limit", limit);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> new FailedModuleCount(
                rs.getString("module_name"),
                rs.getLong("failed_count")
        ));
    }

    /** 只在参数存在时追加条件: 不绑定 null, 也不影响无参查询的可读性。 */
    private String filterSql(SummaryCriteria criteria) {
        StringBuilder sql = new StringBuilder();
        if (criteria.moduleName() != null) {
            sql.append(" AND module_name = :moduleName");
        }
        if (criteria.actionType() != null) {
            sql.append(" AND action_type = :actionType");
        }
        if (criteria.resultStatus() != null) {
            sql.append(" AND result_status = :resultStatus");
        }
        if (criteria.category() != null) {
            sql.append(" AND operation_category = :category");
        }
        if (criteria.operatorName() != null) {
            // 与分页「操作人」筛选同语义: 同时匹配操作人姓名与登录名
            sql.append(" AND (operator_name ILIKE :operatorPattern ESCAPE '\\'"
                    + " OR login_name ILIKE :operatorPattern ESCAPE '\\')");
        }
        return sql.toString();
    }

    private MapSqlParameterSource parameters(SummaryCriteria criteria) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("startTime", criteria.startTime())
                .addValue("endTime", criteria.endTime())
                .addValue("successStatus", OperationLogConstants.RESULT_SUCCESS)
                .addValue("failureStatus", OperationLogConstants.RESULT_FAILURE);
        if (criteria.moduleName() != null) {
            parameters.addValue("moduleName", criteria.moduleName());
        }
        if (criteria.actionType() != null) {
            parameters.addValue("actionType", criteria.actionType());
        }
        if (criteria.resultStatus() != null) {
            parameters.addValue("resultStatus", criteria.resultStatus());
        }
        if (criteria.category() != null) {
            parameters.addValue("category", criteria.category());
        }
        if (criteria.operatorName() != null) {
            parameters.addValue("operatorPattern", toContainsPattern(criteria.operatorName()));
        }
        return parameters;
    }

    /** 构造大小写不敏感包含匹配模式, 并转义 LIKE 通配符(与 {@code Specs} 的转义规则一致)。 */
    private String toContainsPattern(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** 聚合查询条件(服务层已归一化, 可选字段为 null 表示不过滤)。 */
    public record SummaryCriteria(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String moduleName,
            String actionType,
            String resultStatus,
            String category,
            String operatorName
    ) {
    }

    /** 总量聚合结果。 */
    public record SummaryCounts(long total, long successCount, long failedCount, long avgDurationMs) {
    }

    /** 单日聚合结果。 */
    public record DailyCount(LocalDate date, long total, long failed) {
    }

    /** 失败模块聚合结果。 */
    public record FailedModuleCount(String moduleName, long count) {
    }
}
