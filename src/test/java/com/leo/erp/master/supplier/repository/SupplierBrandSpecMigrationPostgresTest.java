package com.leo.erp.master.supplier.repository;

import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import com.leo.erp.master.supplier.domain.entity.SupplierBrandSpec;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的「供应商品牌规格配置」迁移与写入回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>断言 V212 的库侧契约: 列与类型/长度/空值/默认值、未删除行的部分唯一索引、按品牌的普通索引、
 * 指向 {@code md_supplier_brand} 的外键、{@code spec > 0} 的 CHECK、以及关键列注释。</p>
 *
 * <p>另用一条写入用例锁定整体替换最容易坏的地方: 旧行软删({@code flush} 落库)之后再插入<b>完全相同</b>
 * 的组合不得撞部分唯一索引 —— Hibernate flush 顺序是 INSERT 先于 UPDATE, 若实现里少了中间那次 flush,
 * 这条用例会红。</p>
 *
 * <p>用例开在 {@code @DataJpaTest} 的事务里, 结束时整体回滚, 不写共享开发库的既有数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SupplierBrandSpecMigrationPostgresTest {

    private static final String TABLE = "md_supplier_brand_spec";
    private static final long SUPPLIER_ID = 942100000000000001L;
    private static final long BRAND_ID = 942100000000000011L;
    private static final long FIRST_SPEC_ID = 942100000000000101L;
    private static final long SECOND_SPEC_ID = 942100000000000102L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SupplierBrandSpecRepository supplierBrandSpecRepository;

    @Autowired
    private SupplierBrandRepository supplierBrandRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void v212_表结构含配置列与时间列() {
        assertThat(columnType("id")).isEqualTo("bigint");
        assertThat(columnNullable("id")).isFalse();

        assertThat(columnType("supplier_brand_id")).isEqualTo("bigint");
        assertThat(columnNullable("supplier_brand_id")).isFalse();

        assertThat(columnType("category")).isEqualTo("character varying");
        assertThat(columnMaxLength("category")).isEqualTo(32);
        assertThat(columnNullable("category")).isFalse();

        assertThat(columnType("material")).isEqualTo("character varying");
        assertThat(columnMaxLength("material")).isEqualTo(32);
        assertThat(columnNullable("material")).isFalse();

        assertThat(columnType("spec")).isEqualTo("integer");
        assertThat(columnNullable("spec")).isFalse();

        assertThat(columnType("length")).isEqualTo("character varying");
        assertThat(columnMaxLength("length")).isEqualTo(16);
        assertThat(columnNullable("length")).isFalse();

        assertThat(columnType("deleted_flag")).isEqualTo("boolean");
        assertThat(columnNullable("deleted_flag")).isFalse();
        assertThat(columnDefault("deleted_flag")).isEqualTo("false");

        // 任务约定该表的时间列带时区(timestamptz), 由数据库默认值填充, 实体只读映射
        assertThat(columnType("created_at")).isEqualTo("timestamp with time zone");
        assertThat(columnNullable("created_at")).isFalse();
        assertThat(columnDefault("created_at")).isEqualTo("now()");
    }

    @Test
    void v212_未删除行内组合唯一() {
        String definition = indexDefinition("uk_supplier_brand_spec_active");

        assertThat(definition)
                .contains("UNIQUE")
                .contains("(supplier_brand_id, category, material, spec, length)")
                .contains("WHERE (deleted_flag = false)");
    }

    @Test
    void v212_按品牌建普通索引() {
        assertThat(indexDefinition("idx_md_supplier_brand_spec_brand"))
                .contains("(supplier_brand_id)")
                .doesNotContain("UNIQUE");
    }

    @Test
    void v212_外键指向供应商品牌() {
        assertThat(constraintDefinition("fk_supplier_brand_spec_brand"))
                .contains("FOREIGN KEY (supplier_brand_id)")
                .contains("REFERENCES md_supplier_brand(id)");
    }

    @Test
    void v212_CHECK约束要求规格为正整数() {
        assertThat(constraintDefinition("ck_supplier_brand_spec_spec")).contains("spec > 0");
    }

    @Test
    void v212_关键列注释写清语义() {
        assertThat(columnComment("supplier_brand_id")).contains("md_supplier_brand");
        assertThat(columnComment("spec")).contains("直径").contains("大于 0");
        assertThat(columnComment("deleted_flag")).contains("整体替换").contains("唯一性");
    }

    /** 仓储派生查询能在真实库上执行(不存在的品牌 → 空集合而不是异常)。 */
    @Test
    void v212_仓储查询在真实库上可执行() {
        assertThat(supplierBrandSpecRepository.findBySupplierBrandIdInAndDeletedFlagFalse(List.of(-1L)))
                .isEmpty();
    }

    @Test
    void v212_软删旧行后可以再次写入完全相同的组合() {
        saveSupplierAndBrand();
        SupplierBrandSpec first = row(FIRST_SPEC_ID);
        supplierBrandSpecRepository.save(first);
        supplierBrandSpecRepository.flush();

        // 整体替换: 旧行软删并落库
        first.setDeletedFlag(true);
        supplierBrandSpecRepository.save(first);
        supplierBrandSpecRepository.flush();

        // 同一组合再次插入: 部分唯一索引只看未删除行, 这里不得抛唯一键冲突
        supplierBrandSpecRepository.save(row(SECOND_SPEC_ID));
        supplierBrandSpecRepository.flush();

        List<SupplierBrandSpec> active = supplierBrandSpecRepository
                .findBySupplierBrandIdInAndDeletedFlagFalse(List.of(BRAND_ID));
        assertThat(active).extracting(SupplierBrandSpec::getId).containsExactly(SECOND_SPEC_ID);
        assertThat(totalRows()).isEqualTo(2);

        // timestamptz → OffsetDateTime 的只读映射能读回(实体不插入该列, 由库默认值填充)
        entityManager.clear();
        OffsetDateTime createdAt = supplierBrandSpecRepository.findById(SECOND_SPEC_ID)
                .orElseThrow()
                .getCreatedAt();
        assertThat(createdAt).isNotNull();
    }

    private void saveSupplierAndBrand() {
        Supplier supplier = new Supplier();
        supplier.setId(SUPPLIER_ID);
        supplier.setSupplierCode("PG-BS-001");
        supplier.setSupplierName("PG品牌规格供应商");
        supplier.setStatus("正常");
        supplierRepository.save(supplier);

        SupplierBrand brand = new SupplierBrand();
        brand.setId(BRAND_ID);
        brand.setSupplierId(SUPPLIER_ID);
        brand.setBrandName("永钢");
        supplierBrandRepository.save(brand);
        supplierBrandRepository.flush();
    }

    private SupplierBrandSpec row(long id) {
        SupplierBrandSpec row = new SupplierBrandSpec();
        row.setId(id);
        row.setSupplierBrandId(BRAND_ID);
        row.setCategory("HRB400E");
        row.setMaterial("螺纹钢");
        row.setSpec(12);
        row.setLength("9米");
        return row;
    }

    private int totalRows() {
        Integer count = jdbc.queryForObject(
                "select count(*) from " + TABLE + " where supplier_brand_id = ?",
                Integer.class, BRAND_ID);
        return count == null ? 0 : count;
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
