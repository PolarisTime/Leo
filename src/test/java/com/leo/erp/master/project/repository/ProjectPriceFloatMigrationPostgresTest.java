package com.leo.erp.master.project.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的项目网价浮动迁移回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 * <p>
 * 断言 V159 新增 {@code md_project.price_float_mode}/{@code price_float_value}：类型、可空与默认值口径。
 * 迁移由 Flyway 管理，本用例只读 {@code information_schema}。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ProjectPriceFloatMigrationPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v159_project_hasNullablePriceFloatColumns() {
        assertThat(columnType("price_float_mode")).isEqualTo("character varying");
        assertThat(columnNullable("price_float_mode")).isTrue();
        assertThat(columnMaxLength("price_float_mode")).isEqualTo(8);

        assertThat(columnType("price_float_value")).isEqualTo("numeric");
        assertThat(columnNullable("price_float_value")).isTrue();
    }

    private String columnType(String column) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'md_project' AND column_name = ?
                """, String.class, column);
    }

    private boolean columnNullable(String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (is_nullable = 'YES') FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'md_project' AND column_name = ?
                """, Boolean.class, column));
    }

    private Integer columnMaxLength(String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'md_project' AND column_name = ?
                """, Integer.class, column);
    }
}
