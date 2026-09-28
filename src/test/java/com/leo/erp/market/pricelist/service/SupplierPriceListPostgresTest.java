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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * <p>覆盖 V170 取消版本语义后最容易出错的部分:</p>
 * <ol>
 *   <li><b>唯一索引真库守卫</b>: {@code uk_supplier_price_list_supplier_brand}
 *       存在且只覆盖 {@code deleted_flag = false} 行; 旧的 {@code uk_supplier_price_list_active} 已删除;
 *       全表不存在同 (supplier_id, brand_name) 的多条未删除行(V170 归并历史多版本的结果);</li>
 *   <li>同键重复建表 → 应用层 409(BusinessException), 不再自动归档旧版;</li>
 *   <li>不同品牌/不同供应商互不影响; 软删后可重建;</li>
 *   <li>PUT 全量替换条目幂等(条目ID复用);</li>
 *   <li>{@code effective_from} 放宽为可空、{@code released_at} 保留 NOT NULL 且补
 *       {@code CURRENT_TIMESTAMP} 默认值;</li>
 *   <li>{@code uk_supplier_price_item_key} 冲突在应用层被拦为业务异常(422)而不是 500;
 *       {@code price IS NULL}(不报价)条目在整表加减中被跳过, {@code price = 0} 正常参与。</li>
 * </ol>
 *
 * <p>用例自带隔离数据(显式清理, 见 {@link #cleanUp()}), 不依赖开发库既有业务数据, 也不手工执行 DDL。</p>
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
    private JdbcTemplate jdbc;

    /** 真库的值映射仓储(含 V171 的 CATEGORY 种子行), 归一化口径与生产一致。 */
    @Autowired
    private com.leo.erp.market.pricelist.repository.ValueAliasRepository valueAliasRepository;

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
     * 用例结束后清理本类自造的数据(FK 依赖顺序: 条目 -> 留痕明细/头 -> 价格表 -> 供应商)。
     * <p>刻意不在类级使用 {@code @Transactional}: 需要验证"提交后可见"的真实提交语义
     * (部分唯一索引 + 应用层 409), 因此改为显式清理, 不留垃圾数据。</p>
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
                adjustmentItemRepository, idGenerator, supplierQuery, specCatalogQuery,
                new ValueAliasQuery(new ValueAliasMappings(valueAliasRepository)));
    }

    private static SupplierPriceListRequest request(long supplierId, String brandName, String length,
                                                    String price, LocalDateTime releasedAt) {
        return new SupplierPriceListRequest(supplierId, brandName, releasedAt, null, null, "PG库", null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, length,
                        price == null ? null : new BigDecimal(price), "NORMAL", null, 0)));
    }

    /** V170 结构断言: 唯一索引换成 (supplier_id, brand_name) WHERE deleted_flag = false, 旧索引已删除。 */
    @Test
    void migration_definesSupplierBrandUniqueIndexAndDropsVersionIndex() {
        List<String> indexes = jdbc.queryForList("""
                select indexdef from pg_indexes
                where schemaname = 'public' and tablename = 'mk_supplier_price_list'
                """, String.class);

        assertThat(indexes)
                .noneSatisfy(def -> assertThat(def).containsIgnoringCase("uk_supplier_price_list_active"))
                .anySatisfy(def -> assertThat(def)
                        .containsIgnoringCase("uk_supplier_price_list_supplier_brand")
                        .containsIgnoringCase("UNIQUE")
                        .containsIgnoringCase("supplier_id")
                        .containsIgnoringCase("brand_name")
                        .containsIgnoringCase("deleted_flag = false"))
                // 新唯一索引不得再依赖已作废的 status
                .noneSatisfy(def -> assertThat(def)
                        .containsIgnoringCase("uk_supplier_price_list_supplier_brand")
                        .containsIgnoringCase("status"));
    }

    /** V170 放宽已作废列: effective_from 可空; released_at 保留 NOT NULL 且补默认值; status 保留。 */
    @Test
    void migration_relaxesDeprecatedColumns() {
        Map<String, String[]> columns = jdbc.query("""
                select column_name, is_nullable, column_default from information_schema.columns
                where table_schema = 'public' and table_name = 'mk_supplier_price_list'
                """, rs -> {
            Map<String, String[]> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("column_name"),
                        new String[] {rs.getString("is_nullable"), String.valueOf(rs.getString("column_default"))});
            }
            return result;
        });

        assertThat(columns).containsKeys("released_at", "status", "effective_from", "effective_to",
                "deleted_flag", "supplier_id", "brand_name");
        assertThat(columns.get("effective_from")[0]).isEqualTo("YES");
        assertThat(columns.get("effective_to")[0]).isEqualTo("YES");
        assertThat(columns.get("released_at")[0]).isEqualTo("NO");
        assertThat(columns.get("released_at")[1]).contains("CURRENT_TIMESTAMP");
    }

    /** V172 结构断言: quoted_on 为 date、NOT NULL、默认当天, 且历史行已全部回填(无 NULL)。 */
    @Test
    void migration_addsQuotedOnAsRequiredDateWithTodayDefault() {
        Map<String, String[]> columns = jdbc.query("""
                select column_name, is_nullable, column_default, data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'mk_supplier_price_list'
                  and column_name = 'quoted_on'
                """, rs -> {
            Map<String, String[]> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("column_name"), new String[] {rs.getString("is_nullable"),
                        String.valueOf(rs.getString("column_default")), rs.getString("data_type")});
            }
            return result;
        });

        assertThat(columns).containsKey("quoted_on");
        assertThat(columns.get("quoted_on")[0]).isEqualTo("NO");
        assertThat(columns.get("quoted_on")[1]).contains("CURRENT_DATE");
        assertThat(columns.get("quoted_on")[2]).isEqualTo("date");

        Integer nullRows = jdbc.queryForObject(
                "select count(*) from public.mk_supplier_price_list where quoted_on is null", Integer.class);
        assertThat(nullRows).isZero();
    }

    /** 业务报价日期: 缺省 = 当天落库; 更新可直接改(不是版本), 与 updated_at 互不影响。 */
    @Test
    void quotedOn_defaultsToTodayAndIsChangeableOnRealSchema() {
        when(idGenerator.nextId()).thenReturn(942000000000000241L, 942000000000000242L);
        SupplierPriceListStore store = store();

        SupplierPriceListResponse created = store.create(request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));

        assertThat(created.quotedOn()).isEqualTo(LocalDate.now());
        LocalDate persisted = jdbc.queryForObject(
                "select quoted_on from public.mk_supplier_price_list where id = ?",
                LocalDate.class, created.id());
        assertThat(persisted).isEqualTo(created.quotedOn());

        SupplierPriceListRequest changed = new SupplierPriceListRequest(SUPPLIER_ID, BRAND,
                MORNING, null, null, "PG库", null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                        new BigDecimal("3220.00"), "NORMAL", null, 0)), "2026-08-01");
        SupplierPriceListResponse updated = store.update(created.id(), changed, null);

        assertThat(updated.quotedOn()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(updated.updatedAt()).isNotNull();
    }

    /** 列表按业务报价日期排序可用(白名单 quotedOn + 真库 order by)。 */
    @Test
    void page_sortsByQuotedOnOnRealSchema() {
        when(idGenerator.nextId()).thenReturn(942000000000000251L, 942000000000000252L,
                942000000000000253L, 942000000000000254L);
        SupplierPriceListStore store = store();
        store.create(new SupplierPriceListRequest(SUPPLIER_ID, BRAND, MORNING, null, null, "PG库", null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                        new BigDecimal("3220.00"), "NORMAL", null, 0)), "2026-09-20"));
        store.create(new SupplierPriceListRequest(SUPPLIER_ID, OTHER_BRAND, MORNING, null, null, "PG库", null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                        new BigDecimal("3300.00"), "NORMAL", null, 0)), "2026-08-01"));

        List<SupplierPriceListResponse.SummaryResponse> rows = store.page(
                        com.leo.erp.common.api.PageQuery.of(0, 20, "quotedOn", "desc",
                                com.leo.erp.common.api.PageSortFieldCatalog.fields("supplier-price-list")),
                        SUPPLIER_ID, null)
                .getContent();

        assertThat(rows).extracting(SupplierPriceListResponse.SummaryResponse::quotedOn)
                .containsExactly(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 8, 1));
    }

    /** 归并结果守卫: 全表不存在同 (supplier_id, brand_name) 的多条未删除行。 */
    @Test
    void migration_mergeLeftNoDuplicateActiveRows() {
        Integer duplicates = jdbc.queryForObject("""
                select count(*) from (
                    select supplier_id, brand_name
                    from public.mk_supplier_price_list
                    where deleted_flag = false
                    group by supplier_id, brand_name
                    having count(*) > 1
                ) duplicated
                """, Integer.class);

        assertThat(duplicates).isZero();
    }

    /** 1) 同键重复建表: 应用层 409, 且库里仍只有一张未删除表。 */
    @Test
    void createDuplicateKey_isRejectedWithConflict() {
        when(idGenerator.nextId()).thenReturn(942000000000000201L, 942000000000000202L);
        SupplierPriceListStore store = store();

        SupplierPriceListResponse first = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        assertThat(first.status()).isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        assertThat(first.archivedListId()).isNull();

        assertThatThrownBy(() -> store.create(request(SUPPLIER_ID, BRAND, "9米", "3270.00", MORNING)))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("已存在价格表");

        Integer currentCount = jdbc.queryForObject("""
                select count(*) from public.mk_supplier_price_list
                where supplier_id = ? and brand_name = ? and deleted_flag = false
                """, Integer.class, SUPPLIER_ID, BRAND);
        assertThat(currentCount).isEqualTo(1);
    }

    /** 2) 不同 brand / 不同 supplier 各自都能建表。 */
    @Test
    void currentListsOfDifferentBrandsAndSuppliersCoexist() {
        when(idGenerator.nextId()).thenReturn(942000000000000211L, 942000000000000212L,
                942000000000000213L, 942000000000000214L,
                942000000000000215L, 942000000000000216L);
        SupplierPriceListStore store = store();

        store.create(request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        store.create(request(SUPPLIER_ID, OTHER_BRAND, "9米", "3300.00", MORNING));
        store.create(request(OTHER_SUPPLIER_ID, BRAND, "9米", "3400.00", MORNING));

        Integer currentCount = jdbc.queryForObject("""
                select count(*) from public.mk_supplier_price_list
                where deleted_flag = false
                  and supplier_id in (?, ?) and brand_name in (?, ?)
                """, Integer.class, SUPPLIER_ID, OTHER_SUPPLIER_ID, BRAND, OTHER_BRAND);
        assertThat(currentCount).isEqualTo(3);
    }

    /** 3) 软删后可重建同键表(唯一索引只看未删除行)。 */
    @Test
    void softDeletedList_allowsCreatingAgain() {
        when(idGenerator.nextId()).thenReturn(942000000000000221L, 942000000000000222L,
                942000000000000223L, 942000000000000224L);
        SupplierPriceListStore store = store();

        SupplierPriceListResponse first = store.create(request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        store.delete(first.id());

        SupplierPriceListResponse second = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3280.00", MORNING.plusHours(3)));

        assertThat(second.status()).isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        assertThat(listRepository.findById(first.id()).orElseThrow().isDeletedFlag()).isTrue();
        Integer currentCount = jdbc.queryForObject("""
                select count(*) from public.mk_supplier_price_list
                where supplier_id = ? and brand_name = ? and deleted_flag = false
                """, Integer.class, SUPPLIER_ID, BRAND);
        assertThat(currentCount).isEqualTo(1);
    }

    /** 4) PUT 全量替换条目幂等: 条目ID复用、无重复行、价格被整体替换。 */
    @Test
    void update_replacesItemsIdempotentlyOnRealSchema() {
        when(idGenerator.nextId()).thenReturn(942000000000000231L, 942000000000000232L,
                942000000000000233L);
        SupplierPriceListStore store = store();
        SupplierPriceListResponse created = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));

        SupplierPriceListRequest replace = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, MORNING,
                null, null, "PG库", "整表替换",
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                                new BigDecimal("3300.00"), "NORMAL", null, 0),
                        new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "12米",
                                new BigDecimal("3350.00"), "NORMAL", null, 1)));

        SupplierPriceListResponse firstUpdate = store.update(created.id(), replace, null);
        SupplierPriceListResponse secondUpdate = store.update(created.id(), replace, null);

        assertThat(firstUpdate.items()).hasSize(2);
        assertThat(secondUpdate.items()).extracting(SupplierPriceListResponse.ItemResponse::id)
                .containsExactlyElementsOf(firstUpdate.items().stream()
                        .map(SupplierPriceListResponse.ItemResponse::id).toList());
        Integer itemCount = jdbc.queryForObject("""
                select count(*) from public.mk_supplier_price_item where list_id = ?
                """, Integer.class, created.id());
        assertThat(itemCount).isEqualTo(2);
    }

    /** 5) 同一价格表内条目键重复: 应用层 422, 不落到唯一键 500; 数据库唯一键也确实存在。 */
    @Test
    void duplicateItemKey_isRejectedByServiceBeforeUniqueConstraint() {
        when(idGenerator.nextId()).thenReturn(942000000000000241L, 942000000000000242L);
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

    /** 整表加减真库回归: 不报价条目被跳过, 报价条目按加减更新。 */
    @Test
    void adjust_skipsUnquotedItemsOnRealSchema() {
        when(idGenerator.nextId()).thenReturn(942000000000000251L, 942000000000000252L,
                942000000000000253L, 942000000000000254L, 942000000000000255L);
        SupplierPriceListStore store = store();
        SupplierPriceListRequest request = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, MORNING,
                null, null, "PG库", null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                                new BigDecimal("3220.00"), "NORMAL", null, 0),
                        new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "12米",
                                null, "NEGOTIABLE", null, 1)));
        SupplierPriceListResponse created = store.create(request);

        PriceAdjustmentResponse adjusted = store.adjust(created.id(),
                new PriceAdjustmentRequest("ADD", new BigDecimal("50.00"), null), 7L, "张三");

        assertThat(adjusted.affectedCount()).isEqualTo(1);
        assertThat(adjusted.skippedCount()).isEqualTo(1);
        List<BigDecimal> prices = jdbc.queryForList("""
                select price from public.mk_supplier_price_item where list_id = ? order by sort_order
                """, BigDecimal.class, created.id());
        assertThat(prices.get(0)).isEqualByComparingTo("3270.00");
        assertThat(prices.get(1)).isNull();
    }

    /** 条目集合的按需加载(详情)在真库上不得 N+1 失败。 */
    @Test
    void detail_loadsItemsOnRealSchema() {
        when(idGenerator.nextId()).thenReturn(942000000000000261L, 942000000000000262L);
        SupplierPriceListStore store = store();
        SupplierPriceListResponse created = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));

        SupplierPriceListResponse detail = store.detail(created.id());

        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().get(0).price()).isEqualByComparingTo("3220.00");
        assertThat(detail.updatedAt()).isNotNull();
        assertThat(detail.archivedListId()).isNull();
    }

    /** 写库定尺归一(真库): {@code 9m} 落库为 {@code 9米}, 与字典键同口径。 */
    @Test
    void create_normalizesLengthSpellingOnRealSchema() {
        when(idGenerator.nextId()).thenReturn(942000000000000281L, 942000000000000282L);
        SupplierPriceListStore store = store();

        SupplierPriceListResponse created = store.create(
                request(SUPPLIER_ID, BRAND, "9m", "3220.00", MORNING));

        String stored = jdbc.queryForObject("""
                select length from public.mk_supplier_price_item where list_id = ?
                """, String.class, created.id());
        assertThat(stored).isEqualTo("9米");
    }

    /**
     * 可读性守卫(真库): 字典(商品信息 ∪ 比价单行键)之外的<b>历史条目</b>仍必须能通过
     * {@code GET /supplier-price-lists/{id}/items} 读出, 不得被静默丢弃(前端会单列一个区块展示)。
     */
    @Test
    void items_returnsHistoricalEntryOutsideDictionary() {
        when(idGenerator.nextId()).thenReturn(942000000000000291L, 942000000000000292L);
        SupplierPriceListStore store = store();
        SupplierPriceListResponse created = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));
        // 直接落一条字典外的历史条目(应用层写入会被 422 拦截, 模拟历史数据)
        jdbc.update("""
                insert into public.mk_supplier_price_item
                    (id, list_id, category, material, spec, length, price, price_status, sort_order)
                values (?, ?, '非常规类', '历史材质', 999, '', 1234.00, 'NORMAL', 9)
                """, 942000000000000293L, created.id());

        List<SupplierPriceListResponse.ItemResponse> items = store.items(created.id());

        assertThat(items).hasSize(2);
        assertThat(items).extracting(SupplierPriceListResponse.ItemResponse::category)
                .contains("非常规类");
        assertThat(items).extracting(SupplierPriceListResponse.ItemResponse::spec)
                .contains(999);
    }

    /** 条目实体外键关系正确(避免 orphanRemoval 外的脏写)。 */
    @Test
    void itemBelongsToSavedList() {
        when(idGenerator.nextId()).thenReturn(942000000000000271L, 942000000000000272L);
        SupplierPriceListStore store = store();
        SupplierPriceListResponse created = store.create(
                request(SUPPLIER_ID, BRAND, "9米", "3220.00", MORNING));

        List<SupplierPriceItem> items = itemRepository.findByListIdOrderBySortOrderAscIdAsc(created.id());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getList().getId()).isEqualTo(created.id());
    }
}
