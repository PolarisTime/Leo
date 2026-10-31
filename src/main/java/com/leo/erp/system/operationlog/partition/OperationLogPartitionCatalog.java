package com.leo.erp.system.operationlog.partition;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * 操作日志月分区目录查询（分区命名与「当前月」的唯一实现）。
 *
 * <p>清理任务与预建任务共用本类，避免两处各写一份目录 SQL 与限定名拼接。只认
 * public schema 下 {@code sys_operation_log} 的 {@code sys_operation_log_yYYYYmMM} 日历分区，
 * 默认分区 {@code sys_operation_log_default} 不参与。</p>
 */
final class OperationLogPartitionCatalog {

    /** 列出 public schema 下 sys_operation_log 的月分区名（目录查询，不扫表数据）。 */
    private static final String LIST_MONTH_PARTITION_NAMES_SQL = """
            SELECT child.relname
              FROM pg_inherits inh
              JOIN pg_class parent ON parent.oid = inh.inhparent
              JOIN pg_class child ON child.oid = inh.inhrelid
             WHERE parent.relname = 'sys_operation_log'
               AND parent.relnamespace = 'public'::regnamespace
               AND child.relnamespace = 'public'::regnamespace
               AND child.relname ~ '^sys_operation_log_y[0-9]{4}m[0-9]{2}$'
             ORDER BY child.relname
            """;

    /** 以数据库时钟取当前月，避免 JVM 与数据库时区不一致导致窗口错一个月。 */
    private static final String CURRENT_MONTH_SQL = "SELECT to_char(CURRENT_DATE, 'YYYY-MM')";

    private static final String PARTITION_SCHEMA = "\"public\"";
    private static final String PARENT_TABLE = "\"public\".\"sys_operation_log\"";

    private OperationLogPartitionCatalog() {
    }

    /** 当前已存在的月分区名（未排序语义由 SQL 的 ORDER BY 保证）。 */
    static List<String> listMonthPartitionNames(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(LIST_MONTH_PARTITION_NAMES_SQL, String.class);
    }

    /** 以数据库时钟计算当前月。 */
    static YearMonth currentMonth(JdbcTemplate jdbcTemplate) {
        return YearMonth.parse(Objects.requireNonNull(
                jdbcTemplate.queryForObject(CURRENT_MONTH_SQL, String.class),
                "无法从数据库读取当前月"));
    }

    /** 父表（分区表）的限定名。 */
    static String qualifiedParentTable() {
        return PARENT_TABLE;
    }

    /**
     * 分区表的 public schema 限定形式。
     *
     * <p>分区名在进入这里之前已被严格正则限定为 {@code sys_operation_log_y[0-9]{4}m[0-9]{2}}，
     * 但 {@code DROP TABLE} / {@code CREATE TABLE} 无法用参数占位，仍按标识符加双引号做防御性包裹。</p>
     */
    static String qualifiedPartitionName(String partitionName) {
        return PARTITION_SCHEMA + ".\"" + partitionName + "\"";
    }
}
