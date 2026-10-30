package com.leo.erp.master.material.service;

import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.MaterialQuery;
import com.leo.erp.master.material.domain.MaterialTypes;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 商品启停回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>锁定 V201 的库结构与口径：status 列默认「正常」且非空、状态索引存在；
 * 「选商品」查询（{@code findActiveProducts}）与列表状态筛选排除禁用商品，
 * 而 identity 查重查询仍覆盖禁用商品（否则同一 identity 会重复建档）。</p>
 *
 * <p>用例自建 {@code PG-MAT-STATUS-} 前缀数据并按该前缀收窄断言，不依赖、不改动开发库既有数据；
 * {@link DataJpaTest} 事务回滚保证不留残留。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class MaterialStatusPostgresTest {

    private static final String PREFIX = "PG-MAT-STATUS-";
    private static final long ENABLED_ID = 943000000000000001L;
    private static final long DISABLED_ID = 943000000000000002L;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migration_definesStatusColumnWithNormalDefaultAndIndex() {
        String columnDefault = jdbc.queryForObject(
                """
                select column_default
                from information_schema.columns
                where table_schema = 'public' and table_name = 'md_material' and column_name = 'status'
                """,
                String.class);
        assertThat(columnDefault).contains(StatusConstants.NORMAL);

        String nullable = jdbc.queryForObject(
                """
                select is_nullable
                from information_schema.columns
                where table_schema = 'public' and table_name = 'md_material' and column_name = 'status'
                """,
                String.class);
        assertThat(nullable).isEqualTo("NO");

        Integer maxLength = jdbc.queryForObject(
                """
                select character_maximum_length
                from information_schema.columns
                where table_schema = 'public' and table_name = 'md_material' and column_name = 'status'
                """,
                Integer.class);
        assertThat(maxLength).isEqualTo(16);

        Integer indexCount = jdbc.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = 'public' and tablename = 'md_material' and indexname = 'idx_md_material_status'
                """,
                Integer.class);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void selectionQueriesExcludeDisabledWhileIdentityQueriesStillCoverIt() {
        materialRepository.saveAndFlush(material(ENABLED_ID, PREFIX + "M001", "9米", StatusConstants.NORMAL));
        materialRepository.saveAndFlush(material(DISABLED_ID, PREFIX + "M002", "12米", StatusConstants.DISABLED));

        // 1) 「选商品」目录只返回启用商品
        assertThat(new MaterialQueryService(materialRepository).findActiveProducts())
                .extracting(MaterialQuery.MaterialSnapshot::materialCode)
                .contains(PREFIX + "M001")
                .doesNotContain(PREFIX + "M002");

        // 2) identity 查重仍覆盖禁用商品：导入命中已禁用商品时不会重复建档
        assertThat(materialRepository.findActiveIdentityCandidates(
                List.of(PREFIX + "品牌"), List.of("HRB400E"), List.of("16")))
                .extracting(Material::getId)
                .contains(ENABLED_ID, DISABLED_ID);

        // 3) 列表状态筛选：按自建前缀收窄后，禁用/正常各自只命中本状态行
        assertThat(pageIds(StatusConstants.DISABLED)).containsExactly(DISABLED_ID);
        assertThat(pageIds(StatusConstants.NORMAL)).containsExactly(ENABLED_ID);
    }

    private List<Long> pageIds(String status) {
        return materialRepository.findAll(
                        MaterialSearchPolicy.page(PREFIX, null, null, null, status),
                        PageRequest.of(0, 200, MaterialSearchPolicy.DEFAULT_SORT))
                .stream()
                .map(Material::getId)
                .toList();
    }

    private Material material(long id, String code, String length, String status) {
        Material material = new Material();
        material.setId(id);
        material.setMaterialCode(code);
        material.setBrand(PREFIX + "品牌");
        material.setMaterial("HRB400E");
        material.setCategory("直条");
        material.setSpec("16");
        material.setLength(length);
        material.setUnit("吨");
        material.setQuantityUnit("件");
        material.setPieceWeightTon(new BigDecimal("1.99800000"));
        material.setPiecesPerBundle(100);
        material.setUnitPrice(new BigDecimal("4000.00"));
        material.setMaterialType(MaterialTypes.PHYSICAL);
        material.setStatus(status);
        return material;
    }
}
