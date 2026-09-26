package com.leo.erp.system.printtemplate.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的 V166 项目打印模板偏好表迁移回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 * <p>迁移由 Flyway 管理，本用例只读 {@code information_schema}。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ProjectPrintPreferenceMigrationPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v166_projectPrintPreferenceTable_existsWithExpectedColumns() {
        assertThat(columnType("project_id")).isEqualTo("bigint");
        assertThat(columnNullable("project_id")).isFalse();
        assertThat(columnType("bill_type")).isEqualTo("character varying");
        assertThat(columnMaxLength("bill_type")).isEqualTo(64);
        assertThat(columnType("template_id")).isEqualTo("bigint");
        assertThat(columnNullable("template_id")).isFalse();
        assertThat(columnType("template_name")).isEqualTo("character varying");
        assertThat(columnMaxLength("template_name")).isEqualTo(128);
        assertThat(columnNullable("deleted_flag")).isFalse();
    }

    private String columnType(String column) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'sys_project_print_preference'
                  AND column_name = ?
                """, String.class, column);
    }

    private boolean columnNullable(String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (is_nullable = 'YES') FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'sys_project_print_preference'
                  AND column_name = ?
                """, Boolean.class, column));
    }

    private Integer columnMaxLength(String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'sys_project_print_preference'
                  AND column_name = ?
                """, Integer.class, column);
    }
}
