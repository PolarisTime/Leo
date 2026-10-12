package com.leo.erp.master.project.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的「允许品牌/材质处理方式」迁移回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 * <p>
 * 断言 V177 新增 {@code md_project.allowed_brand_severity}/{@code allowed_material_severity} 的
 * 类型、可空与 CHECK 约束口径：可空是必须的（清单为空时不落力度），CHECK 是脏数据兜底
 * （读路径会把未知值退化成 WARN，所以数据库这层不能再放行非法值）。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ProjectAllowedSpecEnforcementMigrationPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v177_projectHasNullableSeverityColumns() {
        for (String column : new String[]{"allowed_brand_severity", "allowed_material_severity"}) {
            assertThat(columnType(column)).as(column).isEqualTo("character varying");
            assertThat(columnMaxLength(column)).as(column).isEqualTo(16);
            assertThat(columnNullable(column)).as(column).isTrue();
        }
    }

    @Test
    void v177_severityColumnsHaveCheckConstraint() {
        assertThat(checkConstraintDefinition("chk_project_allowed_brand_severity"))
                .contains("OFF").contains("WARN").contains("BLOCK");
        assertThat(checkConstraintDefinition("chk_project_allowed_material_severity"))
                .contains("OFF").contains("WARN").contains("BLOCK");
    }

    private String columnType(String column) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'md_project' AND column_name = ?
                """, String.class, column);
    }

    private Integer columnMaxLength(String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'md_project' AND column_name = ?
                """, Integer.class, column);
    }

    private boolean columnNullable(String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (is_nullable = 'YES') FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'md_project' AND column_name = ?
                """, Boolean.class, column));
    }

    private String checkConstraintDefinition(String constraintName) {
        return jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?
                """, String.class, constraintName);
    }
}
