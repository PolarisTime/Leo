package com.leo.erp.market.supplierprice.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的「供应商品牌基价加价规则」迁移回归（默认跳过，设置
 * {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>断言 V198 的库侧契约：表结构与审计列、未删除行的两个部分唯一索引
 * （规则键唯一 + 每个 (supplier, brand) 至多一条基价）、spec/delta 与基价 delta 的 CHECK、
 * 指向 {@code md_supplier} 的外键、以及关键列注释。</p>
 *
 * <p>迁移由 Flyway 管理，本用例只读 {@code information_schema}/{@code pg_indexes}/
 * {@code pg_constraint}/{@code pg_description}，不写任何数据（共享开发库上禁止依赖或清理他人数据）。
 * 注入仓储并实际调用一次查询，同时验证 JPQL/派生查询能被正确解析。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SupplierBasePriceRuleMigrationPostgresTest {

    private static final String TABLE = "mk_supplier_base_price_rule";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SupplierBasePriceRuleRepository repository;

    @Test
    void v198_表结构含规则列与审计列() {
        assertThat(columnType("supplier_id")).isEqualTo("bigint");
        assertThat(columnNullable("supplier_id")).isFalse();
        assertThat(columnType("brand_name")).isEqualTo("character varying");
        assertThat(columnMaxLength("brand_name")).isEqualTo(64);
        assertThat(columnType("material")).isEqualTo("character varying");
        assertThat(columnMaxLength("material")).isEqualTo(16);
        assertThat(columnType("spec")).isEqualTo("integer");
        assertThat(columnType("length")).isEqualTo("character varying");
        assertThat(columnMaxLength("length")).isEqualTo(16);
        assertThat(columnType("delta")).isEqualTo("numeric");
        assertThat(columnNumericPrecision("delta")).isEqualTo(12);
        assertThat(columnNumericScale("delta")).isEqualTo(2);
        assertThat(columnNullable("delta")).isFalse();
        assertThat(columnDefault("delta")).isEqualTo("0");
        assertThat(columnType("is_base")).isEqualTo("boolean");
        assertThat(columnDefault("is_base")).isEqualTo("false");
        assertThat(columnType("version")).isEqualTo("bigint");
        assertThat(columnDefault("version")).isEqualTo("0");
        assertThat(columnNullable("deleted_flag")).isFalse();
        assertThat(columnDefault("deleted_flag")).isEqualTo("false");

        assertThat(columnType("created_by")).isEqualTo("bigint");
        assertThat(columnType("created_name")).isEqualTo("character varying");
        assertThat(columnType("created_at")).isEqualTo("timestamp without time zone");
        assertThat(columnNullable("updated_by")).isTrue();
        assertThat(columnNullable("updated_name")).isTrue();
        assertThat(columnNullable("updated_at")).isTrue();
    }

    @Test
    void v198_未删除行内规则键唯一() {
        String definition = indexDefinition("uk_supplier_base_price_rule_key");

        assertThat(definition).contains("UNIQUE")
                .contains("(supplier_id, brand_name, material, spec, length)")
                .contains("deleted_flag = false");
    }

    @Test
    void v198_每个供应商品牌至多一条基价行() {
        String definition = indexDefinition("uk_supplier_base_price_rule_base");

        assertThat(definition).contains("UNIQUE")
                .contains("(supplier_id, brand_name)")
                .contains("deleted_flag = false")
                .contains("is_base = true");
    }

    @Test
    void v198_CHECK约束覆盖规格加价上限与基价delta() {
        assertThat(constraintDefinition("ck_supplier_base_price_rule_spec")).contains("spec > 0");
        assertThat(constraintDefinition("ck_supplier_base_price_rule_delta"))
                .contains("-100000").contains("100000");
        assertThat(constraintDefinition("ck_supplier_base_price_rule_base_delta"))
                .contains("is_base = false").contains("delta = (0)::numeric");
    }

    @Test
    void v198_外键指向供应商主数据() {
        assertThat(constraintDefinition("fk_supplier_base_price_rule_supplier"))
                .contains("REFERENCES md_supplier(id)");
    }

    @Test
    void v198_关键列注释写清语义() {
        assertThat(columnComment("delta")).contains("相对基价").contains("可为负");
        assertThat(columnComment("is_base")).contains("基价");
        assertThat(columnComment("version")).contains("整体替换");
        assertThat(columnComment("deleted_flag")).contains("唯一性");
    }

    /** 仓储查询可执行: 派生查询与 {@code findLatestVersion} 的 JPQL 都能被正确解析。 */
    @Test
    void v198_仓储查询在真实库上可执行() {
        assertThat(repository
                .findBySupplierIdAndBrandNameAndDeletedFlagFalseOrderByMaterialAscSpecAscLengthAsc(
                        -1L, "不存在的品牌"))
                .isEmpty();
        assertThat(repository.findLatestVersion(-1L, "不存在的品牌")).isNull();
    }

    private String columnType(String column) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, TABLE, column);
    }

    private Integer columnMaxLength(String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Integer.class, TABLE, column);
    }

    private Integer columnNumericPrecision(String column) {
        return jdbc.queryForObject("""
                SELECT numeric_precision FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Integer.class, TABLE, column);
    }

    private Integer columnNumericScale(String column) {
        return jdbc.queryForObject("""
                SELECT numeric_scale FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Integer.class, TABLE, column);
    }

    private boolean columnNullable(String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (is_nullable = 'YES') FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Boolean.class, TABLE, column));
    }

    private String columnDefault(String column) {
        return jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, TABLE, column);
    }

    private String indexDefinition(String indexName) {
        return jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = ? AND indexname = ?
                """, String.class, TABLE, indexName);
    }

    private String constraintDefinition(String constraintName) {
        return jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?
                """, String.class, constraintName);
    }

    private String columnComment(String column) {
        return jdbc.queryForObject("""
                SELECT col_description(format('public.%I', ?)::regclass, a.attnum)
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname = ? AND a.attname = ?
                """, String.class, TABLE, TABLE, column);
    }
}
