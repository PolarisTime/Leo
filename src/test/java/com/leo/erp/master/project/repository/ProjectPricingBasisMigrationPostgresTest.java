package com.leo.erp.master.project.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 PostgreSQL 的「按日期自动取价」迁移回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>断言 V179 的两组变更：{@code md_project} 新增 5 个取价基准配置列（基准/偏移/休市结算开关/
 * 时段策略/固定时段），默认值必须是 {@code DELIVERY / 0 / false / AUTO / NULL}；{@code so_sales_order}
 * 新增 6 个取价快照列且全部可空。CHECK 约束既要声明允许取值，也要在数据库层真的拦住脏值 ——
 * 应用层的归一化与校验都不能替代这一层兜底（历史数据、手工 SQL、其他写入方都会绕过应用层）。</p>
 *
 * <p>迁移由 Flyway 管理，本用例只读 {@code information_schema}/{@code pg_constraint}，
 * 写入用例的测试事务由 {@code @DataJpaTest} 回滚。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ProjectPricingBasisMigrationPostgresTest {

    private static final long PROJECT_ID = 942000000000000001L;
    private static final long CUSTOMER_ID = 942000000000000011L;
    private static final int MAX_OFFSET_DAYS = 30;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v179_项目表取价基准列的类型可空与默认值口径正确() {
        assertThat(columnType("md_project", "price_date_basis")).isEqualTo("character varying");
        assertThat(columnMaxLength("md_project", "price_date_basis")).isEqualTo(16);
        assertThat(columnNullable("md_project", "price_date_basis")).isFalse();
        assertThat(columnDefault("md_project", "price_date_basis")).contains("DELIVERY");

        assertThat(columnType("md_project", "price_date_offset_days")).isEqualTo("integer");
        assertThat(columnNullable("md_project", "price_date_offset_days")).isFalse();
        assertThat(columnDefault("md_project", "price_date_offset_days")).isEqualTo("0");

        assertThat(columnType("md_project", "price_closed_day_prev_settle")).isEqualTo("boolean");
        assertThat(columnNullable("md_project", "price_closed_day_prev_settle")).isFalse();
        assertThat(columnDefault("md_project", "price_closed_day_prev_settle")).isEqualTo("false");

        assertThat(columnType("md_project", "price_period_policy")).isEqualTo("character varying");
        assertThat(columnMaxLength("md_project", "price_period_policy")).isEqualTo(16);
        assertThat(columnNullable("md_project", "price_period_policy")).isFalse();
        assertThat(columnDefault("md_project", "price_period_policy")).contains("AUTO");

        assertThat(columnType("md_project", "price_period_fixed")).isEqualTo("character varying");
        assertThat(columnMaxLength("md_project", "price_period_fixed")).isEqualTo(8);
        assertThat(columnNullable("md_project", "price_period_fixed")).isTrue();
        assertThat(columnDefault("md_project", "price_period_fixed")).isNull();
    }

    @Test
    void v179_销售订单取价快照六列可空存在() {
        assertThat(columnType("so_sales_order", "price_target_date")).isEqualTo("date");
        assertThat(columnType("so_sales_order", "price_quote_date")).isEqualTo("date");
        assertThat(columnType("so_sales_order", "price_quote_period")).isEqualTo("character varying");
        assertThat(columnMaxLength("so_sales_order", "price_quote_period")).isEqualTo(8);
        assertThat(columnType("so_sales_order", "price_date_basis")).isEqualTo("character varying");
        assertThat(columnMaxLength("so_sales_order", "price_date_basis")).isEqualTo(16);
        assertThat(columnType("so_sales_order", "price_date_offset_days")).isEqualTo("integer");
        assertThat(columnType("so_sales_order", "price_closed_settle_prev")).isEqualTo("boolean");

        for (String column : new String[]{"price_target_date", "price_quote_date", "price_quote_period",
                "price_date_basis", "price_date_offset_days", "price_closed_settle_prev"}) {
            assertThat(columnNullable("so_sales_order", column)).as(column).isTrue();
            assertThat(columnDefault("so_sales_order", column)).as(column).isNull();
        }
    }

    @Test
    void v179_项目表CHECK约束声明了允许取值() {
        assertThat(constraintDefinition("chk_project_price_date_basis"))
                .contains("DELIVERY").contains("ORDER").contains("MANUAL");
        assertThat(constraintDefinition("chk_project_price_date_offset_days"))
                .contains(">= 0").contains("<= 30");
        assertThat(constraintDefinition("chk_project_price_period_policy"))
                .contains("AUTO").contains("FIXED").contains("MANUAL");
        assertThat(constraintDefinition("chk_project_price_period_fixed"))
                .contains("IS NULL").contains("上午").contains("中午").contains("下午");
    }

    @Test
    void v179_销售订单CHECK约束声明了允许取值() {
        assertThat(constraintDefinition("chk_sales_order_price_date_basis"))
                .contains("IS NULL").contains("DELIVERY").contains("ORDER").contains("MANUAL");
        assertThat(constraintDefinition("chk_sales_order_price_quote_period"))
                .contains("IS NULL").contains("上午").contains("中午").contains("下午");
        assertThat(constraintDefinition("chk_sales_order_price_date_offset_days"))
                .contains("IS NULL").contains(">= 0").contains("<= 30");
    }

    @Test
    void v179_非法取价基准被数据库拒绝() {
        assertThatThrownBy(() -> insertProject("WEEKLY", 0, "AUTO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v179_非法时段策略被数据库拒绝() {
        assertThatThrownBy(() -> insertProject("DELIVERY", 0, "SOMETIMES", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v179_非法固定时段被数据库拒绝() {
        assertThatThrownBy(() -> insertProject("MANUAL", 0, "FIXED", "傍晚"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v179_偏移超出上限被数据库拒绝() {
        assertThatThrownBy(() -> insertProject("ORDER", MAX_OFFSET_DAYS + 1, "AUTO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v179_偏移为负被数据库拒绝() {
        assertThatThrownBy(() -> insertProject("ORDER", -1, "AUTO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v179_合法取值可以落库并按默认值补齐未指定列() {
        insertProject("ORDER", MAX_OFFSET_DAYS, "FIXED", "下午");

        assertThat(jdbc.queryForObject("""
                SELECT price_date_basis FROM public.md_project WHERE id = ?
                """, String.class, PROJECT_ID)).isEqualTo("ORDER");
        assertThat(jdbc.queryForObject("""
                SELECT price_date_offset_days FROM public.md_project WHERE id = ?
                """, Integer.class, PROJECT_ID)).isEqualTo(MAX_OFFSET_DAYS);
        assertThat(jdbc.queryForObject("""
                SELECT price_period_fixed FROM public.md_project WHERE id = ?
                """, String.class, PROJECT_ID)).isEqualTo("下午");
        assertThat(jdbc.queryForObject("""
                SELECT price_closed_day_prev_settle FROM public.md_project WHERE id = ?
                """, Boolean.class, PROJECT_ID)).isFalse();
    }

    private void insertProject(String basis, int offsetDays, String periodPolicy, String fixedPeriod) {
        // md_project.customer_id 是 NOT NULL 且有指向 md_customer 的外键（V38/V40/V42），
        // 取价基准的写入用例必须自带一条隔离的客户主数据。
        jdbc.update("""
                INSERT INTO public.md_customer
                    (id, customer_code, customer_name, project_name, status, created_by, created_name, created_at,
                     deleted_flag, version)
                VALUES (?, ?, 'V179取价基准测试客户', 'V179取价基准测试项目', '正常', 0, 'system', now(),
                        false, 0)
                ON CONFLICT (id) DO NOTHING
                """, CUSTOMER_ID, "V179-C-" + CUSTOMER_ID);
        jdbc.update("""
                INSERT INTO public.md_project
                    (id, project_code, project_name, customer_code, customer_id, status, created_by, created_name,
                     created_at, deleted_flag, price_date_basis, price_date_offset_days, price_period_policy,
                     price_period_fixed)
                VALUES (?, ?, 'V179取价基准测试项目', ?, ?, '正常', 0, 'system', now(),
                        false, ?, ?, ?, ?)
                """, PROJECT_ID, "V179-P-" + PROJECT_ID, "V179-C-" + CUSTOMER_ID, CUSTOMER_ID,
                basis, offsetDays, periodPolicy, fixedPeriod);
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
}
