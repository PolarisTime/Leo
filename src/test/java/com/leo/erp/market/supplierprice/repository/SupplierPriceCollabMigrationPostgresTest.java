package com.leo.erp.market.supplierprice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V218 协作表结构回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>锁的不是「SQL 能跑」，而是**边车能跑**：边车以 {@code schema-check=strict} 自检
 * {@code leo/collab-server/src/store/postgres-store.ts} 的 {@code REQUIRED_COLUMNS}，
 * 列名/类型/默认值任何一处偏离都会让边车启动失败或物化 SQL 报错。</p>
 *
 * <p>执行方式：库中尚无这三张表时，真实执行 V218 脚本再断言（{@code @DataJpaTest} 事务回滚，
 * 不留残留）；已在某个环境执行过时，直接对现网表结构做同一套断言。两种路径都不 DROP 任何表，
 * 不会干扰正在运行的边车。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SupplierPriceCollabMigrationPostgresTest {

    private static final String MIGRATION =
            "db/migration/V218__create_supplier_price_collab_tables.sql";
    private static final String DOC_TABLE = "mk_supplier_price_collab_doc";
    private static final String UPDATE_TABLE = "mk_supplier_price_collab_update";
    private static final String CELL_OP_TABLE = "mk_supplier_price_cell_op";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migration_shouldProvideExactlyTheColumnsTheSidecarRequires() throws Exception {
        if (!tablesExist()) {
            executeScript(new ClassPathResource(MIGRATION)
                    .getContentAsString(StandardCharsets.UTF_8));
        }

        // 列集合与边车 REQUIRED_COLUMNS 完全一致：多一列不可怕，少一列边车就起不来。
        assertThat(columnsOf(DOC_TABLE))
                .containsExactlyInAnyOrder("room", "state", "updated_at");
        assertThat(columnsOf(UPDATE_TABLE))
                .containsExactlyInAnyOrder("id", "room", "update_blob", "created_at");
        assertThat(columnsOf(CELL_OP_TABLE))
                .containsExactlyInAnyOrder("id", "list_id", "cell_key", "price", "price_status",
                        "remark", "actor_id", "list_revision", "created_at");

        // 绝不能出现旧契约的 version / seq / update_bytes / actor_name 列（边车不读，容易误配）。
        assertThat(columnsOf(DOC_TABLE)).doesNotContain("version");
        assertThat(columnsOf(UPDATE_TABLE)).doesNotContain("seq", "update_bytes", "actor_name");
        assertThat(columnsOf(CELL_OP_TABLE)).doesNotContain("seq", "update_bytes", "actor_name");
    }

    @Test
    void migration_shouldUseSidecarCompatibleTypesAndDefaults() throws Exception {
        if (!tablesExist()) {
            executeScript(new ClassPathResource(MIGRATION)
                    .getContentAsString(StandardCharsets.UTF_8));
        }

        // 主键：room 文本主键；update 自增；cell_op 无默认值（雪花由边车生成）。
        assertThat(dataType(DOC_TABLE, "room")).isEqualTo("text");
        assertThat(dataType(DOC_TABLE, "state")).isEqualTo("bytea");
        assertThat(columnDefault(UPDATE_TABLE, "id")).contains("nextval");
        assertThat(columnDefault(CELL_OP_TABLE, "id")).isNull();

        // 价格精度必须是 numeric(12,2)：边车按字符串读、Java 按 BigDecimal 读，精度漂移会算错钱。
        assertThat(dataType(CELL_OP_TABLE, "price")).isEqualTo("numeric");
        assertThat(numericPrecision(CELL_OP_TABLE, "price")).isEqualTo(12);
        assertThat(numericScale(CELL_OP_TABLE, "price")).isEqualTo(2);

        assertThat(dataType(CELL_OP_TABLE, "cell_key")).isEqualTo("text");
        assertThat(dataType(CELL_OP_TABLE, "price_status")).isEqualTo("character varying");
        assertThat(columnMaxLength(CELL_OP_TABLE, "price_status")).isEqualTo(16);
        assertThat(columnMaxLength(CELL_OP_TABLE, "remark")).isEqualTo(255);

        // NOT NULL 与默认时间：边车 INSERT 只给业务列，时间列必须由数据库兜底。
        assertThat(isNullable(CELL_OP_TABLE, "list_id")).isFalse();
        assertThat(isNullable(CELL_OP_TABLE, "list_revision")).isFalse();
        assertThat(isNullable(UPDATE_TABLE, "update_blob")).isFalse();
        assertThat(columnDefault(UPDATE_TABLE, "created_at")).contains("now()");
        assertThat(columnDefault(CELL_OP_TABLE, "created_at")).contains("now()");
    }

    @Test
    void migration_shouldCreateTheIndexesTheSidecarAndAuditQueriesRelyOn() throws Exception {
        if (!tablesExist()) {
            executeScript(new ClassPathResource(MIGRATION)
                    .getContentAsString(StandardCharsets.UTF_8));
        }

        assertThat(indexesOf(UPDATE_TABLE))
                .contains("idx_supplier_price_collab_update_room_id");
        assertThat(indexesOf(CELL_OP_TABLE))
                .contains("idx_supplier_price_cell_op_list_created",
                        "idx_supplier_price_cell_op_cell_key");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private boolean tablesExist() {
        Integer count = jdbc.queryForObject("""
                select count(*)
                  from information_schema.tables
                 where table_schema = 'public'
                   and table_name in (?, ?, ?)
                """, Integer.class, DOC_TABLE, UPDATE_TABLE, CELL_OP_TABLE);
        return count != null && count == 3;
    }

    /** 逐条执行迁移脚本：去掉整行 {@code --} 注释后按分号切分（脚本内不包含函数体/存储过程）。 */
    private void executeScript(String script) {
        String withoutComments = script.lines()
                .map(line -> line.stripLeading().startsWith("--") ? "" : line)
                .collect(Collectors.joining("\n"));
        for (String statement : withoutComments.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList("""
                select column_name
                  from information_schema.columns
                 where table_schema = 'public' and table_name = ?
                 order by ordinal_position
                """, String.class, table);
    }

    private String dataType(String table, String column) {
        return jdbc.queryForObject("""
                select data_type from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);
    }

    private String columnDefault(String table, String column) {
        return jdbc.queryForObject("""
                select column_default from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);
    }

    private Integer numericPrecision(String table, String column) {
        return jdbc.queryForObject("""
                select numeric_precision from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """, Integer.class, table, column);
    }

    private Integer numericScale(String table, String column) {
        return jdbc.queryForObject("""
                select numeric_scale from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """, Integer.class, table, column);
    }

    private Integer columnMaxLength(String table, String column) {
        return jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """, Integer.class, table, column);
    }

    private boolean isNullable(String table, String column) {
        String nullable = jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);
        return "YES".equalsIgnoreCase(nullable);
    }

    private List<String> indexesOf(String table) {
        return jdbc.queryForList("""
                select indexname from pg_indexes
                 where schemaname = 'public' and tablename = ?
                """, String.class, table);
    }
}
