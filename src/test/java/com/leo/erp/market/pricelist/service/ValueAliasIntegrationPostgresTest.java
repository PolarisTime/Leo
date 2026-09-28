package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.ValueAlias;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import com.leo.erp.market.pricelist.repository.SupplierPriceAdjustmentItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceAdjustmentRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceListRepository;
import com.leo.erp.market.pricelist.repository.ValueAliasRepository;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListRequest;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListResponse;
import com.leo.erp.master.api.SupplierQuery;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 值映射四处接入的真库行为守卫(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖"一处实现、四处复用"里的三处真实匹配:</p>
 * <ol>
 *   <li><b>品牌匹配</b>: 价格表 {@code brand_name} × 单据品牌列 —— 新增 BRAND 映射后能命中,
 *       未配置映射时行为与改造前一致(取不到价格表 → {@code NO_LIST});</li>
 *   <li><b>保存校验</b>: 价格表条目用别名写法提交时, 未配置映射 → 422(不在规格全集内),
 *       配置 CATEGORY 映射后通过并按目标写法入库;</li>
 *   <li><b>推导匹配</b>: 比价行用别名写法(类别/材质/定尺)时, 配置映射后能命中价格表条目价,
 *       未配置映射时 {@code NO_ITEM}。</li>
 * </ol>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ValueAliasIntegrationPostgresTest {

    private static final long SUPPLIER_ID = 943000000000000101L;
    private static final long LIST_ID = 943000000000000201L;
    private static final long ITEM_ID = 943000000000000202L;
    private static final String SUPPLIER_CODE = "PG-VALUE-ALIAS-INT-A";

    /** 价格表里的规范写法。 */
    private static final String BRAND = "PG富鑫";
    private static final String CATEGORY = "PG目标类别";
    private static final String MATERIAL = "PG集成材质";
    private static final String LENGTH_STORED = "PG长定尺A";
    /** 单据/条目里的别名写法。 */
    private static final String BRAND_ALIAS = "PG富鑫别名";
    private static final String CATEGORY_ALIAS = "PG别名类别";
    private static final String MATERIAL_ALIAS = "PG材质别名";
    private static final String LENGTH_ALIAS = "PG长定尺B";
    private static final String PRICE = "3220.00";

    @Autowired
    private ValueAliasRepository valueAliasRepository;

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

    private final AtomicLong idSequence = new AtomicLong(944000000000000000L);
    private final SnowflakeIdGenerator idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
    private final SupplierQuery supplierQuery = Mockito.mock(SupplierQuery.class);

    /** 归一化入口(手动装配: 不经缓存, 保证"改完映射立刻生效")。 */
    private ValueAliasQuery aliases() {
        return new ValueAliasQuery(new ValueAliasMappings(valueAliasRepository));
    }

    private MaterialSpecCatalogQuery specCatalogQuery() {
        return new MaterialSpecCatalogQuery(jdbc, aliases());
    }

    private SupplierPriceListQueryService queryService() {
        return new SupplierPriceListQueryService(listRepository, itemRepository, specCatalogQuery(), aliases());
    }

    private QuoteSheetPriceDeriver deriver() {
        return new QuoteSheetPriceDeriver(queryService(), aliases());
    }

    private SupplierPriceListStore store() {
        when(idGenerator.nextId()).thenAnswer(invocation -> idSequence.incrementAndGet());
        return new SupplierPriceListStore(listRepository, itemRepository, adjustmentRepository,
                adjustmentItemRepository, idGenerator, supplierQuery, specCatalogQuery(), aliases());
    }

    @BeforeEach
    void setUp() {
        insertSupplier();
        when(supplierQuery.findActiveNormalById(any(Long.class)))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(SUPPLIER_ID, SUPPLIER_CODE, "PG集成供应商")));
    }

    // ------------------------------------------------------------------ 1. 品牌匹配

    /** 未配置 BRAND 映射: 单据品牌与价格表 brand_name 写法不同 → 取不到价格表(NO_LIST)。 */
    @Test
    void withoutBrandMapping_quoteBrandDoesNotMatchPriceList() {
        insertPriceList();
        insertPriceItem(PRICE);

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of(BRAND_ALIAS), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf(BRAND_ALIAS), CATEGORY, MATERIAL, 12, LENGTH_STORED);
        assertThat(spot.matched()).isFalse();
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_LIST);
    }

    /** 配置 BRAND 映射后: 别名写法命中价格表, 并推导出条目单价。 */
    @Test
    void withBrandMapping_quoteBrandMatchesPriceList() {
        insertPriceList();
        insertPriceItem(PRICE);
        insertAlias(ValueAliasDimension.BRAND, BRAND_ALIAS, BRAND);

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver().selectBrands(List.of(BRAND_ALIAS), List.of());

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                selection.entryOf(BRAND_ALIAS), CATEGORY, MATERIAL, 12, LENGTH_STORED);
        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo(PRICE);
        assertThat(spot.priceListId()).isEqualTo(LIST_ID);
        // 精确写法仍按原样命中(未配置映射时行为不变)
        assertThat(deriver().selectBrands(List.of(BRAND), List.of()).entryOf(BRAND).list()).isNotNull();
    }

    /** 矩阵投影的品牌筛选同样按映射召回: 用别名筛选也能取到该价格表。 */
    @Test
    void withBrandMapping_matrixBrandFilterMatches() {
        insertPriceList();
        insertPriceItem(PRICE);
        insertAlias(ValueAliasDimension.BRAND, BRAND_ALIAS, BRAND);

        var matrix = queryService().matrix(null, List.of(BRAND_ALIAS), null, null);

        assertThat(matrix.columns()).hasSize(1);
        assertThat(matrix.columns().get(0).brandName()).isEqualTo(BRAND);
        assertThat(matrix.columns().get(0).supplierId()).isEqualTo(SUPPLIER_ID);
    }

    // ------------------------------------------------------------------ 2. 保存校验

    /** 未配置映射: 条目用别名类别提交 → 422(不在规格全集内)。 */
    @Test
    void withoutCategoryMapping_saveValidationRejectsAliasCategory() {
        insertMaterialRow(CATEGORY);

        assertThatThrownBy(() -> store().create(priceListRequest(CATEGORY_ALIAS)))
                .hasMessageContaining("不在规格全集内");
    }

    /** 配置 CATEGORY 映射后: 同一请求通过校验, 并按目标写法(规范类别)入库。 */
    @Test
    void withCategoryMapping_saveValidationAcceptsAliasAndStoresCanonical() {
        insertMaterialRow(CATEGORY);
        insertAlias(ValueAliasDimension.CATEGORY, CATEGORY_ALIAS, CATEGORY);

        SupplierPriceListResponse response = store().create(priceListRequest(CATEGORY_ALIAS));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).category()).isEqualTo(CATEGORY);
        assertThat(response.items().get(0).length()).isEqualTo("9米");
    }

    // ------------------------------------------------------------------ 3. 推导匹配

    /** 未配置映射: 比价行的类别/材质/定尺别名都取不到价格表条目 → NO_ITEM。 */
    @Test
    void withoutValueMappings_derivationFallsToNoItem() {
        insertPriceList();
        insertPriceItem(PRICE);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                deriver().selectBrands(List.of(BRAND), List.of()).entryOf(BRAND),
                CATEGORY_ALIAS, MATERIAL_ALIAS, 12, LENGTH_ALIAS);

        assertThat(spot.matched()).isFalse();
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_ITEM);
    }

    /** 配置类别/材质/定尺映射后: 三个维度的别名写法都能命中价格表条目。 */
    @Test
    void withValueMappings_derivationMatchesAliasedRow() {
        insertPriceList();
        insertPriceItem(PRICE);
        insertAlias(ValueAliasDimension.CATEGORY, CATEGORY_ALIAS, CATEGORY);
        insertAlias(ValueAliasDimension.MATERIAL, MATERIAL_ALIAS, MATERIAL);
        insertAlias(ValueAliasDimension.LENGTH, LENGTH_ALIAS, LENGTH_STORED);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                deriver().selectBrands(List.of(BRAND), List.of()).entryOf(BRAND),
                CATEGORY_ALIAS, MATERIAL_ALIAS, 12, LENGTH_ALIAS);

        assertThat(spot.matched()).isTrue();
        assertThat(spot.price()).isEqualByComparingTo(PRICE);
        assertThat(spot.supplierName()).isEqualTo("PG集成供应商");
    }

    /** 不报价条目(price IS NULL)在映射命中后仍返回 NO_PRICE, 不被当成 0。 */
    @Test
    void withValueMappings_unpricedItemStillReportsNoPrice() {
        insertPriceList();
        insertPriceItem(null);
        insertAlias(ValueAliasDimension.CATEGORY, CATEGORY_ALIAS, CATEGORY);
        insertAlias(ValueAliasDimension.MATERIAL, MATERIAL_ALIAS, MATERIAL);
        insertAlias(ValueAliasDimension.LENGTH, LENGTH_ALIAS, LENGTH_STORED);

        QuoteSheetPriceDeriver.DerivedSpot spot = QuoteSheetPriceDeriver.derive(
                deriver().selectBrands(List.of(BRAND), List.of()).entryOf(BRAND),
                CATEGORY_ALIAS, MATERIAL_ALIAS, 12, LENGTH_ALIAS);

        assertThat(spot.matched()).isFalse();
        assertThat(spot.reason()).isEqualTo(SpotReason.NO_PRICE);
    }

    // ------------------------------------------------------------------ 夹具

    private SupplierPriceListRequest priceListRequest(String itemCategory) {
        return new SupplierPriceListRequest(SUPPLIER_ID, BRAND, LocalDateTime.of(2026, 9, 28, 8, 0),
                null, null, "PG库", null,
                List.of(new SupplierPriceListRequest.ItemRequest(itemCategory, MATERIAL, 12, "9m",
                        new BigDecimal(PRICE), "NORMAL", null, 0)));
    }

    private void insertSupplier() {
        jdbc.update("""
                insert into public.md_supplier (id, supplier_code, supplier_name, status,
                                                created_by, created_name, deleted_flag)
                values (?, ?, 'PG集成供应商', '正常', 0, 'flyway-test', false)
                """, SUPPLIER_ID, SUPPLIER_CODE);
    }

    private void insertMaterialRow(String category) {
        jdbc.update("""
                insert into public.md_material (id, material_code, brand, material, category, spec, length,
                                                unit, piece_weight_ton, pieces_per_bundle, unit_price,
                                                created_by, created_name, deleted_flag)
                values (?, ?, 'PG集成品牌', ?, ?, '12', '9m', '吨', 1.5, 100, 3000, 0, 'flyway-test', false)
                """, idSequence.incrementAndGet(), "PG-VALUE-ALIAS-INT-" + category, MATERIAL, category);
    }

    private void insertPriceList() {
        jdbc.update("""
                insert into public.mk_supplier_price_list (id, supplier_id, supplier_name, brand_name,
                                                           released_at, status, created_by, created_name,
                                                           deleted_flag)
                values (?, ?, 'PG集成供应商', ?, ?, 'ACTIVE', 0, 'flyway-test', false)
                """, LIST_ID, SUPPLIER_ID, BRAND, LocalDateTime.of(2026, 9, 28, 8, 0));
    }

    private void insertPriceItem(String price) {
        jdbc.update("""
                insert into public.mk_supplier_price_item (id, list_id, category, material, spec, length,
                                                           price, price_status, sort_order)
                values (?, ?, ?, ?, 12, ?, ?, 'NORMAL', 0)
                """, ITEM_ID, LIST_ID, CATEGORY, MATERIAL, LENGTH_STORED,
                price == null ? null : new BigDecimal(price));
    }

    private void insertAlias(ValueAliasDimension dimension, String source, String target) {
        ValueAlias alias = new ValueAlias();
        alias.setId(idSequence.incrementAndGet());
        alias.setDimension(dimension);
        alias.setSourceValue(source);
        alias.setTargetValue(target);
        valueAliasRepository.saveAndFlush(alias);
    }
}
