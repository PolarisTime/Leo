package com.leo.erp.master.project.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的「基准日休市位移（方向 + X 个交易日）」迁移回归（默认跳过，设置
 * {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>断言 V195 的两组变更：</p>
 * <ul>
 *   <li>{@code md_project} 新增 {@code price_closed_settle_direction}（varchar(8) NOT NULL，
 *       默认 {@code PREV}）与 {@code price_closed_settle_days}（integer NOT NULL，默认 1），
 *       并带「枚举取值 + 0..30 范围」两个 CHECK；旧列 {@code price_closed_day_prev_settle} 保留；</li>
 *   <li>{@code so_sales_order} 新增同口径的两个可空快照列与两个 CHECK；旧列
 *       {@code price_closed_settle_prev} 保留；</li>
 *   <li>两个旧列都被注释为 DEPRECATED（保留只为历史只读与回滚留痕，待清理发布删除）。</li>
 * </ul>
 *
 * <p>迁移由 Flyway 管理，本用例只读 {@code information_schema}/{@code pg_constraint}/
 * {@code pg_description}，不写任何数据（共享开发库上禁止依赖或清理他人数据）。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ProjectClosedSettleDirectionMigrationPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v195_项目表新增方向与天数两列且默认值正确() {
        assertThat(columnType("md_project", "price_closed_settle_direction"))
                .isEqualTo("character varying");
        assertThat(columnMaxLength("md_project", "price_closed_settle_direction")).isEqualTo(8);
        assertThat(columnNullable("md_project", "price_closed_settle_direction")).isFalse();
        assertThat(columnDefault("md_project", "price_closed_settle_direction")).contains("PREV");

        assertThat(columnType("md_project", "price_closed_settle_days")).isEqualTo("integer");
        assertThat(columnNullable("md_project", "price_closed_settle_days")).isFalse();
        assertThat(columnDefault("md_project", "price_closed_settle_days")).isEqualTo("1");
    }

    @Test
    void v195_项目表CHECK约束声明了方向枚举与天数范围() {
        assertThat(constraintDefinition("chk_project_price_closed_settle_direction"))
                .contains("PREV").contains("NEXT");
        assertThat(constraintDefinition("chk_project_price_closed_settle_days"))
                .contains(">= 0").contains("<= 30");
    }

    @Test
    void v195_销售订单新增两列可空且带CHECK() {
        assertThat(columnType("so_sales_order", "price_closed_settle_direction"))
                .isEqualTo("character varying");
        assertThat(columnMaxLength("so_sales_order", "price_closed_settle_direction")).isEqualTo(8);
        assertThat(columnNullable("so_sales_order", "price_closed_settle_direction")).isTrue();
        assertThat(columnDefault("so_sales_order", "price_closed_settle_direction")).isNull();

        assertThat(columnType("so_sales_order", "price_closed_settle_days")).isEqualTo("integer");
        assertThat(columnNullable("so_sales_order", "price_closed_settle_days")).isTrue();
        assertThat(columnDefault("so_sales_order", "price_closed_settle_days")).isNull();

        assertThat(constraintDefinition("chk_sales_order_price_closed_settle_direction"))
                .contains("IS NULL").contains("PREV").contains("NEXT");
        assertThat(constraintDefinition("chk_sales_order_price_closed_settle_days"))
                .contains("IS NULL").contains(">= 0").contains("<= 30");
    }

    /** 旧列必须保留（V195 只把它们标成 deprecated，删除留给后续清理发布）。 */
    @Test
    void v195_旧列保留并标注DEPRECATED() {
        assertThat(columnType("md_project", "price_closed_day_prev_settle")).isEqualTo("boolean");
        assertThat(columnType("so_sales_order", "price_closed_settle_prev")).isEqualTo("boolean");

        assertThat(columnComment("md_project", "price_closed_day_prev_settle")).contains("DEPRECATED");
        assertThat(columnComment("so_sales_order", "price_closed_settle_prev")).contains("DEPRECATED");
        assertThat(columnComment("md_project", "price_closed_settle_direction"))
                .contains("PREV").contains("NEXT");
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private Integer columnMaxLength(String table, String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
    }

    private boolean columnNullable(String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (is_nullable = 'YES') FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Boolean.class, table, column));
    }

    private String columnDefault(String table, String column) {
        return jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private String constraintDefinition(String constraintName) {
        return jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?
                """, String.class, constraintName);
    }

    private String columnComment(String table, String column) {
        return jdbc.queryForObject("""
                SELECT col_description(format('public.%I', ?)::regclass, a.attnum)
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname = ? AND a.attname = ?
                """, String.class, table, table, column);
    }
}
