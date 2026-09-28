package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import com.leo.erp.market.pricelist.repository.SupplierPriceAdjustmentItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceAdjustmentRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceListRepository;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentRequest;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentResponse;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListRequest;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListResponse;
import com.leo.erp.master.api.SupplierQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 真实 PostgreSQL 供应商价格表回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖迁移最容易出错的部分:</p>
 * <ol>
 *   <li>同一 (supplier_id, brand_name) 连续创建两个版本 → 第二个成功、第一个 ARCHIVED,
 *       且 {@code uk_supplier_price_list_active} 不冲突(验证 Hibernate INSERT-before-UPDATE 的显式 flush 生效);</li>
 *   <li>不同 brand / 不同 supplier 的同名版本互不影响;</li>
 *   <li>软删 ACTIVE 版本后可为同一 (supplier, brand) 重建 ACTIVE;</li>
 *   <li>{@code uk_supplier_price_item_key} 冲突在应用层被拦为业务异常(422)而不是 500;</li>
 *   <li>{@code price IS NULL}(不报价)条目在整表加减中被跳过, {@code price = 0} 正常参与。</li>
 * </ol>
 *
 * <p>用例自带隔离数据(测试事务回滚), 不依赖开发库既有业务数据, 也不手工执行 DDL。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SupplierPriceListPostgresTest {

    private static final long SUPPLIER_ID = 942000000000000101L;
    private static final long OTHER_SUPPLIER_ID = 942000000000000102L;
    private static final String BRAND = "PG富鑫";
    private static final String OTHER_BRAND = "PG汉钢";
    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 9, 28, 8, 0);

    @Autowired
    private SupplierPriceListRepository listRepository;

    @Autowired
    private SupplierPriceItemRepository itemRepository;

    @Autowired
    private SupplierPriceAdjustmentRepository adjustmentRepository;

    @Autowired
    private SupplierPriceAdjustmentItemRepository adjustmentItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final SnowflakeIdGenerator idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
    private final SupplierQuery supplierQuery = Mockito.mock(SupplierQuery.class);
    private final MaterialSpecCatalogQuery specCatalogQuery = Mockito.mock(MaterialSpecCatalogQuery.class);

    @BeforeEach
    void setUp() {
        when(supplierQuery.findActiveNormalById(any(Long.class)))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(SUPPLIER_ID, "PG-GYS", "PG供应商")));
        when(specCatalogQuery.findAll()).thenReturn(List.of(
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot("螺纹钢", "抗震钢E", 12, "9米", 0),
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot("螺纹钢", "抗震钢E", 12, "12米", 1)));
    }

    private SupplierPriceListStore store() {
        return new SupplierPriceListStore(listRepository, itemRepository, adjustmentRepository,
                adjustmentItemRepository, idGenerator, supplierQuery, specCatalogQuery, entityManager);
    }

    private static SupplierPriceListRequest request(long supplierId, String brandName, String length,
                                                    String price, LocalDateTime releasedAt) {
        return new SupplierPriceListRequest(supplierId, brandName, releasedAt, null, null, "PG库", null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, length,
                        price == null ? null : new BigDecimal(price), "NORMAL", null, 0)));
    }

    /** 迁移结构断言: 部分唯一索引/CHECK/COMMENT/新列都在。 */
    @Test
    void migration_definesExpectedTablesIndexesConstraints() {
        Map<String, String> listColumns = jdbc.query("""
                select column_name, data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'mk_supplier_price_list'
                """, rs -> {
            Map<String, String> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("column_name"), rs.getString("data_type"));
            }
            return result;
        });
        assertThat(listColumns)
                .containsEntry("supplier_id", "bigint")
                .containsEntry("brand_name", "character varying")
                .containsEntry("released_at", "timestamp without time zone")
                .containsEntry("status", "character varying")
                .containsEntry("deleted_flag", "boolean");

        List<String> indexes = jdbc.queryForList("""
                select indexdef from pg_indexes
                where schemaname = 'public' and tablename = 'mk_supplier_price_list'
                """, String.class);
        assertThat(indexes)
                .anySatisfy(def -> assertThat(def)
                        .containsIgnoringCase("uk_supplier_price_list_active")
                        .containsIgnoringCase("deleted_flag = false")
                        .containsIgnoringCase("status"))
                .anySatisfy(def -> assertThat(def).containsIgnoringCase("idx_supplier_price_list_lookup"));

        Map<String, String> itemColumns = jdbc.query("""
                select column_name, data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'mk_supplier_price_item'
                """, rs -> {
            Map<String, String> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("column_name"), rs.getString("data_type"));
            }
            return result;
        });
        assertThat(itemColumns)
                .containsEntry("list_id", "bigint")
                .containsEntry("price", "numeric")
                .containsEntry("price_status", "character varying")
                .containsEntry("spec", "integer");

        // mk_quote_item_price 扩展且历史行默认 MANUAL
        Map<String, String> priceColumns = jdbc.query("""
                select column_name, column_default from information_schema.columns
                where table_schema = 'public' and table_name = 'mk_quote_item_price'
                """, rs -> {
            Map<String, String> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("column_name"), String.valueOf(rs.getString("column_default")));
            }
            return result;
        });
        assertThat(priceColumns).containsKey("price_source").containsKey("price_list_id")
                .containsKey("price_list_released_at");
        assertThat(priceColumns.get("price_source")).contains("MANUAL");
    }

    /** 1) 连续两版: 第二版成功、第一版归档, 部分唯一索引不冲突。 */
    @Test
    void createSecondVersion_archivesFirstVersionWithoutUniqueIndexConflict() {
        when(idGenerator.nextId()).thenReturn(942000000000000201L, 942000000000000202L,
                942000000000000203L, 942000000000000204L);
        SupplierPriceListStore store = store();

        SupplierPriceListResponse first = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        assertThat(first.status()).isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        assertThat(first.archivedListId()).isNull();

        SupplierPriceListResponse second = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3270.00", MORNING.plusHours(6)));

        assertThat(second.status()).isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        assertThat(second.archivedListId()).isEqualTo(first.id());

        entityManager.flush();
        entityManager.clear();
        assertThat(listRepository.findById(first.id()).orElseThrow().getStatus())
                .isEqualTo(SupplierPriceList.STATUS_ARCHIVED);
        assertThat(listRepository.findById(second.id()).orElseThrow().getStatus())
                .isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        Integer activeCount = jdbc.queryForObject("""
                select count(*) from public.mk_supplier_price_list
                where supplier_id = ? and brand_name = ? and deleted_flag = false and status = 'ACTIVE'
                """, Integer.class, SUPPLIER_ID, BRAND);
        assertThat(activeCount).isEqualTo(1);
    }

    /** 2) 不同 brand / 不同 supplier 各自都能 ACTIVE。 */
    @Test
    void activeVersionsOfDifferentBrandsAndSuppliersCoexist() {
        when(idGenerator.nextId()).thenReturn(942000000000000211L, 942000000000000212L,
                942000000000000213L, 942000000000000214L,
                942000000000000215L, 942000000000000216L);
        SupplierPriceListStore store = store();

        store.create(request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        store.create(request(SUPPLIER_ID, OTHER_BRAND, "9米", "3300.00", MORNING));
        store.create(request(OTHER_SUPPLIER_ID, BRAND, "9米", "3400.00", MORNING));

        Integer activeCount = jdbc.queryForObject("""
                select count(*) from public.mk_supplier_price_list
                where deleted_flag = false and status = 'ACTIVE'
                  and supplier_id in (?, ?) and brand_name in (?, ?)
                """, Integer.class, SUPPLIER_ID, OTHER_SUPPLIER_ID, BRAND, OTHER_BRAND);
        assertThat(activeCount).isEqualTo(3);
    }

    /** 3) 软删 ACTIVE 版本后, 同一 (supplier, brand) 可再建 ACTIVE。 */
    @Test
    void softDeletedActiveVersion_allowsCreatingNewActiveVersion() {
        when(idGenerator.nextId()).thenReturn(942000000000000221L, 942000000000000222L,
                942000000000000223L, 942000000000000224L);
        SupplierPriceListStore store = store();

        SupplierPriceListResponse first = store.create(request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        store.delete(first.id());

        SupplierPriceListResponse second = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3280.00", MORNING.plusHours(3)));

        assertThat(second.archivedListId()).isNull();
        assertThat(second.status()).isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        assertThat(listRepository.findById(first.id()).orElseThrow().isDeletedFlag()).isTrue();
    }

    /** 4) 同一版本内条目键重复: 应用层 422, 不落到唯一键 500; 数据库唯一键也确实存在。 */
    @Test
    void duplicateItemKey_isRejectedByServiceBeforeUniqueConstraint() {
        when(idGenerator.nextId()).thenReturn(942000000000000231L, 942000000000000232L);
        SupplierPriceListStore store = store();
        SupplierPriceListRequest duplicated = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, MORNING,
                null, null, null, null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                                new BigDecimal("3220.00"), "NORMAL", null, 0),
                        new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                                new BigDecimal("3230.00"), "NORMAL", null, 1)));

        assertThatThrownBy(() -> store.create(duplicated))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("价格条目键重复");

        Integer uniqueIndex = jdbc.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = 'public' and tablename = 'mk_supplier_price_item'
                  and indexname = 'uk_supplier_price_item_key'
                """, Integer.class);
        assertThat(uniqueIndex).isEqualTo(1);
    }

    /** 数据库层兜底: 绕过应用层直接插入重复键必须被唯一索引拦住(而非静默写入两行)。 */
    @Test
    void duplicateItemKey_isAlsoEnforcedByDatabaseUniqueIndex() {
        when(idGenerator.nextId()).thenReturn(942000000000000241L, 942000000000000242L);
        SupplierPriceListResponse created = store().create(request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        Long listId = created.id();

        assertThatThrownBy(() -> jdbc.update("""
                insert into public.mk_supplier_price_item
                    (id, list_id, category, material, spec, length, price, price_status, sort_order)
                values (?, ?, '螺纹钢', '抗震钢E', 12, '9米', 1.00, 'NORMAL', 9)
                """, 942000000000000299L, listId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 5) 加减: price=0 参与, price IS NULL 跳过。 */
    @Test
    void adjust_skipsNullPriceAndAdjustsZeroPrice() {
        when(idGenerator.nextId()).thenReturn(942000000000000251L, 942000000000000252L,
                942000000000000253L, 942000000000000254L);
        SupplierPriceListStore store = store();
        SupplierPriceListRequest base = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, MORNING,
                null, null, null, null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                                new BigDecimal("100.00"), "NORMAL", null, 0),
                        new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "12米",
                                BigDecimal.ZERO, "NORMAL", null, 1)));
        SupplierPriceListResponse created = store.create(base);
        assertThat(created.items()).hasSize(2);

        PriceAdjustmentResponse response = store.adjust(created.id(),
                new PriceAdjustmentRequest("ADD", new BigDecimal("50.00"), null), 7L, "PG测试");

        assertThat(response.affectedCount()).isEqualTo(2);
        assertThat(response.skippedCount()).isZero();

        // 追加一个不报价条目后再次整表加减: 只有非 NULL 条目参与
        SupplierPriceItem noQuote = new SupplierPriceItem();
        noQuote.setId(942000000000000260L);
        noQuote.setList(listRepository.findById(created.id()).orElseThrow());
        noQuote.setCategory("盘螺");
        noQuote.setMaterial("HRB400E");
        noQuote.setSpec(8);
        noQuote.setLength("");
        noQuote.setPrice(null);
        noQuote.setSortOrder(2);
        itemRepository.saveAndFlush(noQuote);

        PriceAdjustmentResponse second = store.adjust(created.id(),
                new PriceAdjustmentRequest("SUBTRACT", new BigDecimal("10.00"), null), 7L, "PG测试");

        assertThat(second.skippedCount()).isEqualTo(1);
        assertThat(second.affectedCount()).isEqualTo(2);
        assertThat(itemRepository.findById(noQuote.getId()).orElseThrow().getPrice()).isNull();
        assertThat(itemRepository.findById(noQuote.getId()).orElseThrow().getPrice()).isNotEqualTo(BigDecimal.ZERO);
    }
}
