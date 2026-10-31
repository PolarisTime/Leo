package com.leo.erp.system.operationlog.partition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 操作日志月分区 DDL 的真实 PostgreSQL 回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>锁三件事：</p>
 * <ol>
 *   <li>预建任务使用的 {@code CREATE TABLE IF NOT EXISTS ... PARTITION OF ... FOR VALUES FROM ... TO ...}
 *       在真库可执行、重复执行幂等、写入按分区键正确路由；</li>
 *   <li>{@code sys_operation_log_default} 已存在目标月份数据时，再建同月分区会失败
 *       （PostgreSQL 会校验 default 分区约束）——把这条语义固化成测试，而不是只写在注释里；</li>
 *   <li>把该月行从 default 迁出后，同一条 DDL 可再次成功（对应 ops 文档里的人工处置口径）。</li>
 * </ol>
 *
 * <p>临时父表 {@code zz_partition_probe} 建在 {@code @DataJpaTest} 的测试事务内，测试结束回滚；
 * 另在前后各显式 {@code DROP ... CASCADE} 做双保险，确保任何情况下都不在开发库留下探针表。</p>
 *
 * <p>注意：失败分支用 {@code SAVEPOINT} + {@code ROLLBACK TO SAVEPOINT} 恢复事务，
 * 因为 PostgreSQL 里一条语句失败会让整个事务进入 aborted 状态，必须回滚到保存点才能继续断言
 * 「迁出 default 行后重试成功」。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class OperationLogPartitionDdlPostgresTest {

    private static final String PROBE_PARENT = "zz_partition_probe";
    private static final String PROBE_DEFAULT = "zz_partition_probe_default";
    private static final String PROBE_JAN = "zz_partition_probe_y2026m01";
    private static final String PROBE_MAR = "zz_partition_probe_y2026m03";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createProbeSchema() {
        dropProbe();
        jdbc.execute("CREATE TABLE " + PROBE_PARENT + " (id bigint, col date NOT NULL) PARTITION BY RANGE (col)");
        jdbc.execute("CREATE TABLE " + PROBE_DEFAULT + " PARTITION OF " + PROBE_PARENT + " DEFAULT");
    }

    @AfterEach
    void dropProbeSchema() {
        dropProbe();
    }

    @Test
    void createPartition_shouldBeExecutableIdempotentAndRouteRows() {
        String sql = createMonthPartition(PROBE_JAN, "2026-01-01", "2026-02-01");

        jdbc.execute(sql);
        // 幂等：生产任务每天都会重跑同一条 DDL，已存在分区必须静默跳过而不是报错
        jdbc.execute(sql);

        jdbc.update("INSERT INTO " + PROBE_PARENT + " (col) VALUES ('2026-01-15')");

        assertThat(countOf(PROBE_JAN)).isEqualTo(1);
        assertThat(countOf(PROBE_DEFAULT)).isZero();
    }

    @Test
    void createPartition_failsWhenDefaultHoldsThatMonth_thenSucceedsAfterMovingRows() {
        // 先让该月数据落进 default（模拟「分区缺失期间仍写入」）
        jdbc.update("INSERT INTO " + PROBE_PARENT + " (col) VALUES ('2026-03-15')");
        assertThat(countOf(PROBE_DEFAULT)).isEqualTo(1);

        String sql = createMonthPartition(PROBE_MAR, "2026-03-01", "2026-04-01");
        jdbc.execute("SAVEPOINT before_create_partition");
        assertThatThrownBy(() -> jdbc.execute(sql))
                .as("default 分区已含该月数据时，建同月分区必须失败（PostgreSQL 语义）")
                .isInstanceOf(DataAccessException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT before_create_partition");
        jdbc.execute("RELEASE SAVEPOINT before_create_partition");

        // 与 ops 文档同口径：先把该月行从 default 迁出，再重试同一条 DDL
        jdbc.update("DELETE FROM " + PROBE_PARENT + " WHERE col >= '2026-03-01' AND col < '2026-04-01'");
        jdbc.execute(sql);

        jdbc.update("INSERT INTO " + PROBE_PARENT + " (col) VALUES ('2026-03-15')");
        assertThat(countOf(PROBE_MAR)).isEqualTo(1);
        assertThat(countOf(PROBE_DEFAULT)).isZero();
    }

    private static String createMonthPartition(String partitionName, String from, String to) {
        return "CREATE TABLE IF NOT EXISTS " + partitionName
                + " PARTITION OF " + PROBE_PARENT
                + " FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
    }

    private long countOf(String table) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }

    private void dropProbe() {
        jdbc.execute("DROP TABLE IF EXISTS " + PROBE_PARENT + " CASCADE");
    }
}
