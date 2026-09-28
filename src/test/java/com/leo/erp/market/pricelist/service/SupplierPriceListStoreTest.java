package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceAdjustment;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商价格表存储层极端情况测试(重复建表 409/条目校验/PUT 幂等/整表加减)。
 *
 * <p>已取消版本语义: 不再断言自动归档、同刻 409、ARCHIVED 不可改。</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierPriceListStoreTest {

    private static final long SUPPLIER_ID = 700000000000000001L;
    private static final long OTHER_SUPPLIER_ID = 700000000000000002L;
    private static final String BRAND = "安徽富鑫";
    private static final LocalDateTime RELEASED = LocalDateTime.of(2026, 9, 28, 14, 35);
    private static final String CATALOG_KEY = "螺纹钢|抗震钢E|12|9米";

    @Mock
    private SupplierPriceListRepository listRepository;

    @Mock
    private SupplierPriceItemRepository itemRepository;

    @Mock
    private SupplierPriceAdjustmentRepository adjustmentRepository;

    @Mock
    private SupplierPriceAdjustmentItemRepository adjustmentItemRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private MaterialSpecCatalogQuery specCatalogQuery;

    @Mock
    private com.leo.erp.market.pricelist.repository.ValueAliasRepository valueAliasRepository;

    private SupplierPriceListStore store() {
        return new SupplierPriceListStore(listRepository, itemRepository, adjustmentRepository,
                adjustmentItemRepository, snowflakeIdGenerator, supplierQuery, specCatalogQuery,
                new ValueAliasQuery(new ValueAliasMappings(valueAliasRepository)));
    }

    private void stubSupplier() {
        when(supplierQuery.findActiveNormalById(SUPPLIER_ID))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(SUPPLIER_ID, "GYS001", "杭州中金钢铁")));
    }

    private void stubSupplierAndCatalog() {
        stubSupplier();
        when(specCatalogQuery.findAll()).thenReturn(List.of(
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot("螺纹钢", "抗震钢E", 12, "9米", 0),
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot("螺纹钢", "抗震钢E", 12, "12米", 1)));
    }

    private static SupplierPriceListRequest.ItemRequest item(Integer spec, BigDecimal price) {
        return new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", spec, "9米", price, "NORMAL", null, 0);
    }

    private static SupplierPriceListRequest.ItemRequest item(Integer spec, String length, BigDecimal price) {
        return new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", spec, length, price, "NORMAL", null, 0);
    }

    private static SupplierPriceListRequest request(Integer spec, BigDecimal price) {
        return request(spec, price, RELEASED);
    }

    private static SupplierPriceListRequest request(Integer spec, BigDecimal price, LocalDateTime releasedAt) {
        return new SupplierPriceListRequest(SUPPLIER_ID, BRAND, releasedAt, null, null, "钢联新安库", null,
                List.of(item(spec, price)));
    }

    // ---------------------------------------------------------------- 创建

    @Test
    void create_assignsSnowflakeIdAndSupplierNameSnapshot() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListResponse response = store().create(request(12, new BigDecimal("3220.00")));

        assertThat(response.id()).isEqualTo(900L);
        assertThat(response.supplierName()).isEqualTo("杭州中金钢铁");
        assertThat(response.status()).isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).priceStatus()).isEqualTo("NORMAL");
        // 已取消版本语义: 不再自动归档旧版
        assertThat(response.archivedListId()).isNull();
        // effectiveFrom 兼容保留且允许为空(不再由 releasedAt 推导)
        assertThat(response.effectiveFrom()).isNull();
    }

    @Test
    void create_echoesCompatibilityHeaderFieldsWhenProvided() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SupplierPriceListRequest withRange = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, "备注",
                List.of(item(12, BigDecimal.TEN)));

        SupplierPriceListResponse response = store().create(withRange);

        assertThat(response.effectiveFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.effectiveTo()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    /** releasedAt 仅为兼容保留: 请求不带时必须回落到当前时刻, 不得 422(NOT NULL 列)。 */
    @Test
    void create_defaultsReleasedAtWhenRequestOmitsIt() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime before = LocalDateTime.now();

        SupplierPriceListResponse response = store().create(request(12, BigDecimal.TEN, null));

        assertThat(response.releasedAt()).isNotNull().isAfterOrEqualTo(before);
    }

    /** 业务报价日期缺省 = 当天, 与系统 updatedAt(最后修改)区分。 */
    @Test
    void create_defaultsQuotedOnToTodayWhenRequestOmitsIt() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListResponse response = store().create(request(12, BigDecimal.TEN));

        assertThat(response.quotedOn()).isEqualTo(LocalDate.now());
    }

    /** 显式传入的报价日期原样生效, 且与系统字段互不影响(两者可以不同)。 */
    @Test
    void create_usesExplicitQuotedOnAndKeepsSystemTimestampIndependent() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SupplierPriceListRequest explicit = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null, List.of(item(12, BigDecimal.TEN)), "2026-08-01");

        SupplierPriceListResponse response = store().create(explicit);

        assertThat(response.quotedOn()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.releasedAt()).isEqualTo(RELEASED);
    }

    /** 格式合法但语义非法(2 月 30 日) → 422(VALIDATION_ERROR), 不得 500, 也不得写库。 */
    @Test
    void create_rejectsSemanticallyInvalidQuotedOnWith422() {
        stubSupplierAndCatalog();
        SupplierPriceListRequest invalid = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null, List.of(item(12, BigDecimal.TEN)), "2026-02-30");

        assertThatThrownBy(() -> store().create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报价日期不合法")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(listRepository, never()).saveAndFlush(any());
    }

    /**
     * 写库类别与 spec-catalog 返回的类别必须是同一个值(都经 CategoryNormalizer 归一):
     * 请求写 直条 也能通过全集校验, 落库/响应统一为 螺纹钢, 不会出现同一键两行别名。
     */
    @Test
    void create_normalizesAliasCategoryToSameValueAsSpecCatalog() {
        stubSupplier();
        when(specCatalogQuery.findAll()).thenReturn(List.of(
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot(
                        CategoryNormalizer.QUOTE_CATEGORY_REBAR, "抗震钢E", 12, "9米", 0)));
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SupplierPriceListRequest alias = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null,
                List.of(new SupplierPriceListRequest.ItemRequest(
                        CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "抗震钢E", 12, "9米",
                        BigDecimal.TEN, "NORMAL", null, 0)));

        SupplierPriceListResponse response = store().create(alias);

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.category())
                        .isEqualTo(CategoryNormalizer.QUOTE_CATEGORY_REBAR));
    }

    /** 写库定尺归一: {@code 9m} / {@code 9 米} / {@code 9米} 落库为同一个键。 */
    @Test
    void create_normalizesLengthSpellingsToSingleKey() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListResponse response = store().create(new SupplierPriceListRequest(SUPPLIER_ID, BRAND,
                RELEASED, null, null, null, null, List.of(item(12, "9 m", BigDecimal.TEN))));

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.length()).isEqualTo("9米"));
    }

    /** 请求内定尺写法不同但归一后同键({@code 9m} 与 {@code 9 米})必须判重 422, 不得落两行。 */
    @Test
    void create_rejectsDuplicateKeysDifferingOnlyByLengthSpelling() {
        stubSupplierAndCatalog();
        SupplierPriceListRequest duplicated = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null,
                List.of(item(12, "9m", BigDecimal.TEN), item(12, "9 米", BigDecimal.ONE)));

        assertThatThrownBy(() -> store().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价格条目键重复");
        verify(listRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_doesNotApplyPriceCatalogValidationToEmptyItems() {
        stubSupplier();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListRequest empty = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null, List.of());
        SupplierPriceListResponse response = store().create(empty);

        assertThat(response.items()).isEmpty();
    }

    /** 同 (供应商, 品牌) 已存在未删除价格表 → 409, 不得静默覆盖或自动归档旧版。 */
    @Test
    void create_rejectsDuplicateSupplierBrandWithConflict() {
        stubSupplierAndCatalog();
        when(listRepository.findCurrentByKey(SUPPLIER_ID, BRAND)).thenReturn(List.of(list(500L, RELEASED)));

        assertThatThrownBy(() -> store().create(request(12, new BigDecimal("3220.00"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
        verify(listRepository, never()).saveAndFlush(any());
    }

    /** 同品牌不同供应商不受影响: 唯一性只在 (供应商, 品牌) 组合上。 */
    @Test
    void create_allowsSameBrandForDifferentSupplier() {
        stubSupplierAndCatalog();
        SupplierPriceList otherSupplierList = list(600L, RELEASED);
        otherSupplierList.setSupplierId(OTHER_SUPPLIER_ID);
        when(listRepository.findCurrentByKey(SUPPLIER_ID, BRAND)).thenReturn(List.of());
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListResponse response = store().create(request(12, BigDecimal.TEN));

        assertThat(response.id()).isEqualTo(900L);
        assertThat(otherSupplierList.getId()).isEqualTo(600L);
    }

    @Test
    void create_rejectsSpecNotPositive() {
        stubSupplierAndCatalog();
        assertThatThrownBy(() -> store().create(request(0, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格必须大于 0")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> store().create(request(-1, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格必须大于 0");
        verify(listRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsNegativePrice() {
        stubSupplierAndCatalog();
        assertThatThrownBy(() -> store().create(request(12, new BigDecimal("-0.01"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单价不能为负");
    }

    /** price 为 null = 不报价, 必须允许创建。 */
    @Test
    void create_allowsNullPriceMeaningNoQuote() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListResponse response = store().create(request(12, null));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).price()).isNull();
    }

    @Test
    void create_rejectsInvalidPriceStatus() {
        stubSupplierAndCatalog();
        SupplierPriceListRequest invalid = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 12, "9米",
                        BigDecimal.TEN, "WHATEVER", null, 0)));

        assertThatThrownBy(() -> store().create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报价状态不合法");
    }

    /** 请求内重复 (category, material, spec, length) → 422 且 errors 指明重复键。 */
    @Test
    void create_rejectsDuplicateItemKeysWithFieldErrors() {
        stubSupplierAndCatalog();
        SupplierPriceListRequest duplicated = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null,
                List.of(item(12, BigDecimal.TEN), item(12, BigDecimal.ONE)));

        assertThatThrownBy(() -> store().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价格条目键重复")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrors())
                        .hasSize(1)
                        .allSatisfy(error -> assertThat(error.message()).contains(CATALOG_KEY)));
        verify(listRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsItemKeyOutsideSpecCatalog() {
        stubSupplierAndCatalog();
        SupplierPriceListRequest outside = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 11, "9米",
                        BigDecimal.TEN, "NORMAL", null, 0)));

        assertThatThrownBy(() -> store().create(outside))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在规格全集内");
    }

    // ---------------------------------------------------------------- 更新(全量替换, 幂等)

    /** PUT 全量替换条目且幂等: 同请求重复执行, 条目ID复用、无重复行、取值一致。 */
    @Test
    void update_replacesItemsAndIsIdempotent() {
        stubSupplierAndCatalog();
        SupplierPriceList entity = list(500L, RELEASED);
        entity.getItems().addAll(List.of(quotedItem(11L, "9米", "100.00")));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(entity));
        when(snowflakeIdGenerator.nextId()).thenReturn(12L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SupplierPriceListRequest replace = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null,
                List.of(item(12, "9米", new BigDecimal("200.00")),
                        item(12, "12米", new BigDecimal("300.00"))));

        SupplierPriceListResponse first = store().update(500L, replace, null);
        SupplierPriceListResponse second = store().update(500L, replace, null);

        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(second.items().get(0).id()).isEqualTo(first.items().get(0).id());
        assertThat(second.items().get(1).id()).isEqualTo(first.items().get(1).id());
        assertThat(second.items().get(0).id()).isEqualTo(11L);
        assertThat(second.items().get(1).id()).isEqualTo(12L);
        assertThat(second.items()).extracting(SupplierPriceListResponse.ItemResponse::price)
                .containsExactly(new BigDecimal("200.00"), new BigDecimal("300.00"));
        verify(listRepository, times(2)).saveAndFlush(any(SupplierPriceList.class));
    }

    /**
     * 业务报价日期可反复改(不是版本): 携带则改写, 未携带则保持原值(改价/改条目不得让它漂移到今天)。
     */
    @Test
    void update_changesQuotedOnWhenProvidedAndKeepsItWhenOmitted() {
        stubSupplierAndCatalog();
        SupplierPriceList entity = list(500L, RELEASED);
        entity.setQuotedOn(LocalDate.of(2026, 8, 1));
        entity.getItems().addAll(List.of(quotedItem(11L, "9米", "100.00")));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(entity));
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SupplierPriceListRequest omitted = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null, List.of(item(12, "9米", new BigDecimal("200.00"))));

        SupplierPriceListResponse kept = store().update(500L, omitted, null);
        assertThat(kept.quotedOn()).isEqualTo(LocalDate.of(2026, 8, 1));

        SupplierPriceListRequest changed = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null, List.of(item(12, "9米", new BigDecimal("200.00"))), "2026-09-15");
        SupplierPriceListResponse updated = store().update(500L, changed, null);

        assertThat(updated.quotedOn()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    /** 更新路径的非法报价日期同样 422(与创建同一口径)。 */
    @Test
    void update_rejectsInvalidQuotedOnWith422() {
        stubSupplier();
        SupplierPriceList entity = list(500L, RELEASED);
        entity.setQuotedOn(LocalDate.of(2026, 8, 1));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(entity));
        SupplierPriceListRequest invalid = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                null, null, null, null, List.of(item(12, BigDecimal.TEN)), "2026-13-01");

        assertThatThrownBy(() -> store().update(500L, invalid, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(listRepository, never()).saveAndFlush(any());
    }

    /** 改键到空闲的 (供应商, 品牌) 允许; 目标被占用 → 409 且不写库。 */
    @Test
    void update_rejectsKeyConflictWithConflict() {
        stubSupplier();
        SupplierPriceList entity = list(500L, RELEASED);
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(entity));
        SupplierPriceList occupied = list(600L, RELEASED);
        when(listRepository.findCurrentByKey(SUPPLIER_ID, "武钢汉钢")).thenReturn(List.of(occupied));
        SupplierPriceListRequest move = new SupplierPriceListRequest(SUPPLIER_ID, "武钢汉钢", RELEASED,
                null, null, null, null, List.of(item(12, BigDecimal.TEN)));

        assertThatThrownBy(() -> store().update(500L, move, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在另一张价格表")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
        verify(listRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_allowsKeyChangeWhenTargetIsFree() {
        stubSupplierAndCatalog();
        SupplierPriceList entity = list(500L, RELEASED);
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(entity));
        when(listRepository.findCurrentByKey(SUPPLIER_ID, "武钢汉钢")).thenReturn(List.of());
        when(snowflakeIdGenerator.nextId()).thenReturn(11L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SupplierPriceListRequest move = new SupplierPriceListRequest(SUPPLIER_ID, "武钢汉钢", RELEASED,
                null, null, null, null, List.of(item(12, BigDecimal.TEN)));

        SupplierPriceListResponse response = store().update(500L, move, null);

        assertThat(response.brandName()).isEqualTo("武钢汉钢");
    }

    @Test
    void update_rejectsStaleVersionWithPreconditionFailed() {
        SupplierPriceList list = list(500L, RELEASED);
        list.setVersion(4L);
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().update(500L, request(12, BigDecimal.TEN), 3L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_FAILED);
    }

    @Test
    void update_rejectsEndDateBeforeStartDate() {
        stubSupplier();
        SupplierPriceList entity = list(500L, RELEASED);
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(entity));
        SupplierPriceListRequest invalid = new SupplierPriceListRequest(SUPPLIER_ID, BRAND, RELEASED,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null, null,
                List.of(item(12, BigDecimal.TEN)));

        assertThatThrownBy(() -> store().update(500L, invalid, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生效截止日期不能早于生效起始日期");
        verify(listRepository, never()).saveAndFlush(any());
    }

    /**
     * 已存条目即使后来不在规格全集内(项目可选商品键被改/比价单被删)也必须能正常读出:
     * 读取只按需匹配, 不做全集校验。
     */
    @Test
    void items_returnsStoredEntriesEvenWhenKeyLeftTheSpecCatalog() {
        SupplierPriceList list = list(500L, RELEASED);
        list.getItems().add(quotedItem(11L, "9米", "3220.00"));
        when(listRepository.findByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));
        when(itemRepository.findByListIdOrderBySortOrderAscIdAsc(500L))
                .thenReturn(List.of(list.getItems().get(0)));
        List<SupplierPriceListResponse.ItemResponse> items = store().items(500L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).price()).isEqualByComparingTo("3220.00");
        // 读取路径根本不查规格全集(不做全集校验)
        verifyNoInteractions(specCatalogQuery);
    }

    @Test
    void detail_throwsNotFoundWhenMissing() {
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store().detail(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---------------------------------------------------------------- 整表加减

    @Test
    void adjust_addsAmountToQuotedItemsOnlyAndSkipsNullPrices() {
        SupplierPriceList list = list(500L, RELEASED);
        list.getItems().addAll(List.of(quotedItem(11L, "9米", "3220.00"), nullPricedItem(12L)));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));
        when(snowflakeIdGenerator.nextId()).thenReturn(1000L, 1001L);
        when(adjustmentRepository.saveAndFlush(any(SupplierPriceAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PriceAdjustmentResponse response = store().adjust(500L,
                new PriceAdjustmentRequest("ADD", new BigDecimal("50.00"), null), 7L, "张三");

        assertThat(response.affectedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).price()).isEqualByComparingTo("3270.00");
        assertThat(list.getItems().get(0).getPrice()).isEqualByComparingTo("3270.00");
        // 不报价条目必须保持 null, 绝不能被写成 0 或 50
        assertThat(list.getItems().get(1).getPrice()).isNull();

        ArgumentCaptor<SupplierPriceAdjustment> captor = ArgumentCaptor.forClass(SupplierPriceAdjustment.class);
        verify(adjustmentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getItemCount()).isEqualTo(1);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(captor.getValue().getCreatedName()).isEqualTo("张三");
    }

    @Test
    void adjust_subtractToNegativePriceIsRejectedWithoutTruncation() {
        SupplierPriceList list = list(500L, RELEASED);
        list.getItems().add(quotedItem(11L, "9米", "30.00"));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().adjust(500L,
                new PriceAdjustmentRequest("SUBTRACT", new BigDecimal("50.00"), null), 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调整后价格为负")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        // 拒绝后价格必须保持原值(不做静默截断)
        assertThat(list.getItems().get(0).getPrice()).isEqualByComparingTo("30.00");
        verify(adjustmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void adjust_rejectsNonPositiveAmountAndInvalidMode() {
        SupplierPriceList list = list(500L, RELEASED);
        list.getItems().add(quotedItem(11L, "9米", "30.00"));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().adjust(500L,
                new PriceAdjustmentRequest("ADD", BigDecimal.ZERO, null), 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("加减金额必须大于 0");
        assertThatThrownBy(() -> store().adjust(500L,
                new PriceAdjustmentRequest("MULTIPLY", BigDecimal.ONE, null), 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("加减方向不合法");
    }

    /** 显式指定 itemIds 时, 其中的 NULL 条目计入 skippedCount, 未指定的条目不受影响。 */
    @Test
    void adjust_explicitNullItemCountsAsSkippedAndLeavesOtherItemsUntouched() {
        SupplierPriceList list = list(500L, RELEASED);
        list.getItems().addAll(List.of(quotedItem(11L, "9米", "100.00"), nullPricedItem(12L),
                quotedItem(13L, "12米", "200.00")));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));
        when(snowflakeIdGenerator.nextId()).thenReturn(1000L, 1001L);
        when(adjustmentRepository.saveAndFlush(any(SupplierPriceAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PriceAdjustmentResponse response = store().adjust(500L,
                new PriceAdjustmentRequest("ADD", new BigDecimal("10.00"), List.of(11L, 12L)), 7L, "张三");

        assertThat(response.affectedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(list.getItems().get(0).getPrice()).isEqualByComparingTo("110.00");
        assertThat(list.getItems().get(1).getPrice()).isNull();
        assertThat(list.getItems().get(2).getPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void adjust_rejectsItemIdNotBelongingToList() {
        SupplierPriceList list = list(500L, RELEASED);
        list.getItems().add(quotedItem(11L, "9米", "100.00"));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().adjust(500L,
                new PriceAdjustmentRequest("ADD", new BigDecimal("10.00"), List.of(999L)), 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条目不属于该价格表");
    }

    @Test
    void adjust_rejectsWhenAllTargetsAreUnquoted() {
        SupplierPriceList list = list(500L, RELEASED);
        list.getItems().add(nullPricedItem(12L));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().adjust(500L,
                new PriceAdjustmentRequest("ADD", new BigDecimal("10.00"), null), 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("全部为不报价");
        verify(adjustmentRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- 构造工具

    private static SupplierPriceList list(Long id, LocalDateTime releasedAt) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(id);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(BRAND);
        list.setReleasedAt(releasedAt);
        list.setUpdatedAt(releasedAt);
        list.setStatus(SupplierPriceList.STATUS_ACTIVE);
        list.setVersion(0L);
        list.setItems(new ArrayList<>());
        return list;
    }

    private static SupplierPriceItem quotedItem(Long id, String length, String price) {
        SupplierPriceItem item = baseItem(id, length);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private static SupplierPriceItem nullPricedItem(Long id) {
        SupplierPriceItem item = baseItem(id, "9米");
        item.setPrice(null);
        return item;
    }

    private static SupplierPriceItem baseItem(Long id, String length) {
        SupplierPriceItem item = new SupplierPriceItem();
        item.setId(id);
        item.setCategory("螺纹钢");
        item.setMaterial("抗震钢E");
        item.setSpec(12);
        item.setLength(length);
        item.setSortOrder(0);
        return item;
    }
}
