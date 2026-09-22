package com.leo.erp.master.project.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的项目价格规定迁移回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 * <p>
 * 断言 V160 新增 {@code md_project_price_rule} 及销售订单价格快照列。迁移由 Flyway 管理，
 * 本用例只读 {@code information_schema}。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ProjectPriceRuleMigrationPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v160_projectPriceRuleTable_existsWithExpectedColumns() {
        assertThat(columnType("md_project_price_rule", "mode")).isEqualTo("character varying");
        assertThat(columnMaxLength("md_project_price_rule", "mode")).isEqualTo(8);
        assertThat(columnType("md_project_price_rule", "amount")).isEqualTo("numeric");
        assertThat(columnNullable("md_project_price_rule", "amount")).isFalse();
        assertThat(columnNullable("md_project_price_rule", "deleted_flag")).isFalse();
    }

    @Test
    void v160_salesOrder_hasPriceSnapshotColumns() {
        assertThat(columnNullable("so_sales_order", "price_rule_id")).isTrue();
        assertThat(columnNullable("so_sales_order", "price_rule_name")).isTrue();
        assertThat(columnNullable("so_sales_order", "price_float_mode")).isTrue();
        assertThat(columnNullable("so_sales_order", "price_float_value")).isTrue();
    }

    @Test
    void v161_steelQuoteAndProject_haveSourceColumns() {
        assertThat(columnType("mk_steel_quote", "source")).isEqualTo("character varying");
        assertThat(columnNullable("mk_steel_quote", "source")).isFalse();
        assertThat(columnType("mk_steel_article", "source")).isEqualTo("character varying");
        assertThat(columnNullable("md_project", "quote_source")).isTrue();
        assertThat(columnNullable("md_project", "quote_region")).isTrue();
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private boolean columnNullable(String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (is_nullable = 'YES') FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Boolean.class, table, column));
    }

    private Integer columnMaxLength(String table, String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
    }
}
