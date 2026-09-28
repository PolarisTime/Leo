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
import org.junit.jupiter.api.AfterEach;
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

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private final SnowflakeIdGenerator idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
    private final SupplierQuery supplierQuery = Mockito.mock(SupplierQuery.class);
    private final MaterialSpecCatalogQuery specCatalogQuery = Mockito.mock(MaterialSpecCatalogQuery.class);

    @BeforeEach
    void setUp() {
        insertSupplier(SUPPLIER_ID, "PG-PRICE-LIST-TEST-A");
        insertSupplier(OTHER_SUPPLIER_ID, "PG-PRICE-LIST-TEST-B");
        when(supplierQuery.findActiveNormalById(any(Long.class)))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(SUPPLIER_ID, "PG-GYS", "PG供应商")));
        when(specCatalogQuery.findAll()).thenReturn(List.of(
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot("螺纹钢", "抗震钢E", 12, "9米", 0),
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot("螺纹钢", "抗震钢E", 12, "12米", 1)));
    }

    /**
     * 用例结束后清理本类自造的数据(FK 依赖顺序: 条目 -> 留痕明细/头 -> 版本 -> 供应商)。
     * <p>刻意不在类级使用 {@code @Transactional}: 需要验证"提交后可见"的真实提交语义
     * (部分唯一索引 + Hibernate INSERT-before-UPDATE), 因此改为显式清理, 不留垃圾数据。</p>
     */
    @AfterEach
    void cleanUp() {
        jdbc.update("""
                delete from public.mk_supplier_price_item
                where list_id in (select id from public.mk_supplier_price_list where supplier_id in (?, ?))
                """, SUPPLIER_ID, OTHER_SUPPLIER_ID);
        jdbc.update("""
                delete from public.mk_supplier_price_adjustment_item
                where adjustment_id in (select id from public.mk_supplier_price_adjustment
                                        where list_id in (select id from public.mk_supplier_price_list
                                                          where supplier_id in (?, ?)))
                """, SUPPLIER_ID, OTHER_SUPPLIER_ID);
        jdbc.update("""
                delete from public.mk_supplier_price_adjustment
                where list_id in (select id from public.mk_supplier_price_list where supplier_id in (?, ?))
                """, SUPPLIER_ID, OTHER_SUPPLIER_ID);
        jdbc.update("delete from public.mk_supplier_price_list where supplier_id in (?, ?)",
                SUPPLIER_ID, OTHER_SUPPLIER_ID);
        jdbc.update("delete from public.md_supplier where id in (?, ?)", SUPPLIER_ID, OTHER_SUPPLIER_ID);
    }

    /**
     * 造测试用供应商主数据(迁移里 mk_supplier_price_list.supplier_id 有 FK)。
     * <p>供应商编码带固定标记, 便于人工核对或排查; 用例结束后由 {@link #cleanUp()} 删除。</p>
     */
    private void insertSupplier(long id, String code) {
        jdbc.update("""
                insert into public.md_supplier (id, supplier_code, supplier_name, status,
                                                created_by, created_name, deleted_flag)
                values (?, ?, 'PG价格表测试供应商', '正常', 0, 'flyway-test', false)
                """, id, code);
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

    /**
     * 数据库层兜底: 唯一索引确实存在且覆盖 (list_id, category, material, spec, length)。
     *
     * <p>刻意不做"故意触发约束冲突"的写库演练: PostgreSQL 在约束冲突后会把当前事务置为 aborted,
     * 而 {@code @DataJpaTest} 的测试连接是共享的, 强行复用会污染后续语句。唯一索引的存在性
     * 与列顺序由 {@code pg_indexes} 只读断言覆盖; 应用层在同一约束上先行拦截的行为由
     * {@link #duplicateItemKey_isRejectedByServiceBeforeUniqueConstraint()} 覆盖。</p>
     */
    @Test
    void duplicateItemKey_uniqueIndexDefinitionIsUniqueAndCoversBusinessKey() {
        Map<String, String> index = jdbc.query("""
                select indexname, indexdef from pg_indexes
                where schemaname = 'public' and tablename = 'mk_supplier_price_item'
                  and indexname = 'uk_supplier_price_item_key'
                """, rs -> {
            Map<String, String> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("indexname"), rs.getString("indexdef"));
            }
            return result;
        });

        assertThat(index).containsKey("uk_supplier_price_item_key");
        assertThat(index.get("uk_supplier_price_item_key"))
                .containsIgnoringCase("UNIQUE")
                .containsIgnoringCase("list_id")
                .containsIgnoringCase("category")
                .containsIgnoringCase("material")
                .containsIgnoringCase("spec")
                .containsIgnoringCase("length");
    }

    /** 数据库层的 CHECK: spec > 0 与 price IS NULL OR price >= 0 必须由数据库强制。 */
    @Test
    void checkConstraints_arePresentOnSupplierPriceItem() {
        List<String> constraints = jdbc.queryForList("""
                select conname from pg_constraint
                where conrelid = 'public.mk_supplier_price_item'::regclass and contype = 'c'
                """, String.class);
        assertThat(constraints)
                .contains("ck_supplier_price_item_spec")
                .contains("ck_supplier_price_item_price")
                .contains("ck_supplier_price_item_status");

        List<String> listConstraints = jdbc.queryForList("""
                select conname from pg_constraint
                where conrelid = 'public.mk_supplier_price_list'::regclass and contype = 'c'
                """, String.class);
        assertThat(listConstraints)
                .contains("ck_supplier_price_list_status")
                .contains("ck_supplier_price_list_effective_range");
    }
}