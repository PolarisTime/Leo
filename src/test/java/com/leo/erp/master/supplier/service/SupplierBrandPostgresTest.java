package com.leo.erp.master.supplier.service;

import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import com.leo.erp.master.supplier.mapper.SupplierMapper;
import com.leo.erp.master.supplier.repository.SupplierBrandRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 真实 PostgreSQL 供应商品牌回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>覆盖: V148 表结构/唯一键/索引与实体映射一致; 同一供应商连续两次保存相同品牌不触发唯一键冲突,
 * 既有品牌行 ID 稳定(同名复用, 不删重建); 保存不同集合时按差异增删且最终集合正确。</p>
 * <p>用例自带隔离供应商(测试事务回滚), 不依赖开发库既有业务数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SupplierBrandPostgresTest {

    private static final long SUPPLIER_ID = 942000000000000001L;
    private static final long YONGGANG_BRAND_ID = 942000000000000011L;
    private static final long SHAGANG_BRAND_ID = 942000000000000012L;
    private static final long ZHONGTIAN_BRAND_ID = 942000000000000013L;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SupplierBrandRepository supplierBrandRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final SnowflakeIdGenerator idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
    private final MasterDataCodeIssuanceService codeIssuanceService = Mockito.mock(MasterDataCodeIssuanceService.class);
    private final SupplierMapper supplierMapper = Mockito.mock(SupplierMapper.class);

    @BeforeEach
    void setUp() {
        when(codeIssuanceService.resolve(eq("supplier"), any(), anyString())).thenReturn("PG-GYS-001");
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(null);
    }

    @Test
    void migration_definesExpectedColumnsUniqueKeyForeignKeyAndIndex() {
        Map<String, String> columns = jdbc.query(
                """
                select column_name, data_type
                from information_schema.columns
                where table_schema = 'public' and table_name = 'md_supplier_brand'
                """,
                rs -> {
                    Map<String, String> result = new java.util.LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("column_name"), rs.getString("data_type"));
                    }
                    return result;
                });
        assertThat(columns)
                .containsEntry("id", "bigint")
                .containsEntry("supplier_id", "bigint")
                .containsEntry("brand_name", "character varying")
                .containsEntry("created_at", "timestamp without time zone");

        List<String> uniqueColumns = jdbc.queryForList(
                """
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
                where tc.table_schema = 'public' and tc.table_name = 'md_supplier_brand'
                  and tc.constraint_type = 'UNIQUE' and tc.constraint_name = 'uk_supplier_brand'
                order by kcu.ordinal_position
                """, String.class);
        assertThat(uniqueColumns).containsExactly("supplier_id", "brand_name");

        Integer foreignKeys = jdbc.queryForObject(
                """
                select count(*) from information_schema.table_constraints
                where table_schema = 'public' and table_name = 'md_supplier_brand'
                  and constraint_type = 'FOREIGN KEY' and constraint_name = 'fk_supplier_brand_supplier'
                """, Integer.class);
        assertThat(foreignKeys).isEqualTo(1);

        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'public' and tablename = 'md_supplier_brand'",
                String.class);
        assertThat(indexes).contains("idx_md_supplier_brand_brand_name");
    }

    @Test
    void consecutiveSavesWithSameBrands_doNotConflictAndKeepBrandIdsStable() {
        when(idGenerator.nextId()).thenReturn(SUPPLIER_ID, YONGGANG_BRAND_ID, SHAGANG_BRAND_ID);
        SupplierService service = service();

        service.create(request(List.of("沙钢", "永钢")));
        supplierRepository.flush();

        List<SupplierBrand> first = supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(SUPPLIER_ID);
        assertThat(first).extracting(SupplierBrand::getBrandName).containsExactly("永钢", "沙钢");
        Map<String, Long> firstIds = first.stream()
                .collect(Collectors.toMap(SupplierBrand::getBrandName, SupplierBrand::getId));

        service.update(SUPPLIER_ID, request(List.of("永钢", "沙钢")));
        supplierRepository.flush();

        List<SupplierBrand> second = supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(SUPPLIER_ID);
        assertThat(second).extracting(SupplierBrand::getBrandName).containsExactly("永钢", "沙钢");
        assertThat(second).extracting(SupplierBrand::getId)
                .containsExactly(firstIds.get("永钢"), firstIds.get("沙钢"));
        assertThat(supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(SUPPLIER_ID)).hasSize(2);
    }

    @Test
    void savingChangedBrandSet_reconcilesByDifference() {
        when(idGenerator.nextId()).thenReturn(SUPPLIER_ID, YONGGANG_BRAND_ID, SHAGANG_BRAND_ID);
        SupplierService service = service();
        service.create(request(List.of("永钢", "沙钢")));
        supplierRepository.flush();

        when(idGenerator.nextId()).thenReturn(ZHONGTIAN_BRAND_ID);
        service.update(SUPPLIER_ID, request(List.of("永钢", "中天")));
        supplierRepository.flush();

        List<String> brands = supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(SUPPLIER_ID).stream()
                .map(SupplierBrand::getBrandName)
                .toList();
        assertThat(brands).containsExactly("中天", "永钢");
        assertThat(supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(SUPPLIER_ID))
                .extracting(SupplierBrand::getId)
                .containsExactly(ZHONGTIAN_BRAND_ID, YONGGANG_BRAND_ID);
    }

    private SupplierService service() {
        return new SupplierService(supplierRepository, idGenerator, supplierMapper, null,
                codeIssuanceService, null, supplierBrandRepository);
    }

    private SupplierRequest request(List<String> brands) {
        return new SupplierRequest("PG-GYS-001", "PG供应商", null, "联系人", "13800000000",
                "上海", "正常", "PG备注", brands);
    }
}
