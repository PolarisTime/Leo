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
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商价格表存储层极端情况测试(版本冲突/自动归档/条目校验/整表加减)。
 */
@ExtendWith(MockitoExtension.class)
class SupplierPriceListStoreTest {

    private static final long SUPPLIER_ID = 700000000000000001L;
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
    private EntityManager entityManager;

    private SupplierPriceListStore store() {
        return new SupplierPriceListStore(listRepository, itemRepository, adjustmentRepository,
                adjustmentItemRepository, snowflakeIdGenerator, supplierQuery, specCatalogQuery, entityManager);
    }

    private void stubSupplier() {
        when(supplierQuery.findActiveNormalById(SUPPLIER_ID))
                .thenReturn(Optional.of(new SupplierQuery.SupplierSnapshot(SUPPLIER_ID, "GYS001", "杭州中金钢铁")));
    }

    private void stubSupplierAndCatalog() {
        stubSupplier();
        when(specCatalogQuery.findAll()).thenReturn(List.of(
                new MaterialSpecCatalogQuery.MaterialSpecSnapshot("螺纹钢", "抗震钢E", 12, "9米", 0)));
    }

    private static SupplierPriceListRequest.ItemRequest item(Integer spec, BigDecimal price) {
        return new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", spec, "9米", price, "NORMAL", null, 0);
    }

    private static SupplierPriceListRequest request(Integer spec, BigDecimal price, LocalDateTime releasedAt) {
        return new SupplierPriceListRequest(SUPPLIER_ID, "安徽富鑫", releasedAt, null, null, "钢联新安库", null,
                List.of(item(spec, price)));
    }

    @Test
    void create_assignsSnowflakeIdAndSupplierNameSnapshot() {
        stubSupplierAndCatalog();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListResponse response = store().create(request(12, new BigDecimal("3220.00"), RELEASED));

        assertThat(response.id()).isEqualTo(900L);
        assertThat(response.supplierName()).isEqualTo("杭州中金钢铁");
        assertThat(response.effectiveFrom()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(response.status()).isEqualTo(SupplierPriceList.STATUS_ACTIVE);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).priceStatus()).isEqualTo("NORMAL");
        assertThat(response.archivedListId()).isNull();
    }

    @Test
    void create_doesNotApplyPriceCatalogValidationToEmptyItems() {
        stubSupplier();
        when(snowflakeIdGenerator.nextId()).thenReturn(900L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListRequest empty = new SupplierPriceListRequest(SUPPLIER_ID, "安徽富鑫", RELEASED,
                null, null, null, null, List.of());
        SupplierPriceListResponse response = store().create(empty);

        assertThat(response.items()).isEmpty();
    }

    /** 同 (供应商, 品牌) 已存在 ACTIVE 且 released_at 相同时必须 409, 不得静默覆盖。 */
    @Test
    void create_rejectsSameReleasedAtConflict() {
        stubSupplierAndCatalog();
        when(listRepository.findVersions(SUPPLIER_ID, "安徽富鑫")).thenReturn(List.of(active(500L, RELEASED)));
        when(listRepository.findActiveVersions(SUPPLIER_ID, "安徽富鑫"))
                .thenReturn(List.of(active(500L, RELEASED)));

        assertThatThrownBy(() -> store().create(request(12, new BigDecimal("3220.00"), RELEASED)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
        verify(listRepository, never()).saveAndFlush(any());
    }

    /** 已存在更早的 ACTIVE 版本: 自动归档并返回 archivedListId, 归档必须先落库再插入新版本。 */
    @Test
    void create_archivesEarlierActiveVersionAndReturnsArchivedListId() {
        stubSupplierAndCatalog();
        SupplierPriceList older = active(500L, RELEASED.minusHours(1));
        when(listRepository.findVersions(SUPPLIER_ID, "安徽富鑫")).thenReturn(List.of(older));
        when(listRepository.findActiveVersions(SUPPLIER_ID, "安徽富鑫")).thenReturn(List.of(older));
        when(snowflakeIdGenerator.nextId()).thenReturn(900L, 901L);
        when(listRepository.saveAndFlush(any(SupplierPriceList.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierPriceListResponse response = store().create(request(12, new BigDecimal("3220.00"), RELEASED));

        assertThat(response.archivedListId()).isEqualTo(500L);
        assertThat(older.getStatus()).isEqualTo(SupplierPriceList.STATUS_ARCHIVED);
        // 归档必须先 flush, 否则部分唯一索引仍被旧 ACTIVE 行占用
        verify(listRepository).flush();
    }

    @Test
    void create_rejectsSpecNotPositive() {
        stubSupplierAndCatalog();
        assertThatThrownBy(() -> store().create(request(0, BigDecimal.TEN, RELEASED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格必须大于 0")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> store().create(request(-1, BigDecimal.TEN, RELEASED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格必须大于 0");
        verify(listRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsNegativePrice() {
        stubSupplierAndCatalog();
        assertThatThrownBy(() -> store().create(request(12, new BigDecimal("-0.01"), RELEASED)))
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

        SupplierPriceListResponse response = store().create(request(12, null, RELEASED));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).price()).isNull();
    }

    @Test
    void create_rejectsInvalidPriceStatus() {
        stubSupplierAndCatalog();
        SupplierPriceListRequest invalid = new SupplierPriceListRequest(SUPPLIER_ID, "安徽富鑫", RELEASED,
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
        SupplierPriceListRequest duplicated = new SupplierPriceListRequest(SUPPLIER_ID, "安徽富鑫", RELEASED,
                null, null, null, null,
                List.of(item(12, BigDecimal.TEN), item(12, BigDecimal.ONE)));

        assertThatThrownBy(() -> store().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价格条目键重复")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrors())
                        .hasSize(1)
                        .allSatisfy(error -> assertThat(error.message()).contains("螺纹钢|抗震钢E|12|9米")));
        verify(listRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsItemKeyOutsideSpecCatalog() {
        stubSupplierAndCatalog();
        SupplierPriceListRequest outside = new SupplierPriceListRequest(SUPPLIER_ID, "安徽富鑫", RELEASED,
                null, null, null, null,
                List.of(new SupplierPriceListRequest.ItemRequest("螺纹钢", "抗震钢E", 11, "9米",
                        BigDecimal.TEN, "NORMAL", null, 0)));

        assertThatThrownBy(() -> store().create(outside))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在规格全集内");
    }

    /** ARCHIVED 版本不可改 → 409。 */
    @Test
    void update_rejectsArchivedVersion() {
        SupplierPriceList archived = active(500L, RELEASED);
        archived.setStatus(SupplierPriceList.STATUS_ARCHIVED);
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> store().update(500L, request(12, BigDecimal.TEN, RELEASED), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
        verify(listRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_rejectsStaleVersionWithPreconditionFailed() {
        SupplierPriceList list = active(500L, RELEASED);
        list.setVersion(4L);
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().update(500L, request(12, BigDecimal.TEN, RELEASED), 3L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_FAILED);
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
        SupplierPriceList list = active(500L, RELEASED);
        list.getItems().addAll(List.of(quotedItem(11L, "3220.00"), nullPricedItem(12L)));
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
        SupplierPriceList list = active(500L, RELEASED);
        list.getItems().add(quotedItem(11L, "30.00"));
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
        SupplierPriceList list = active(500L, RELEASED);
        list.getItems().add(quotedItem(11L, "30.00"));
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
        SupplierPriceList list = active(500L, RELEASED);
        list.getItems().addAll(List.of(quotedItem(11L, "100.00"), nullPricedItem(12L), quotedItem(13L, "200.00")));
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
        SupplierPriceList list = active(500L, RELEASED);
        list.getItems().add(quotedItem(11L, "100.00"));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().adjust(500L,
                new PriceAdjustmentRequest("ADD", new BigDecimal("10.00"), List.of(999L)), 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条目不属于该价格表版本");
    }

    @Test
    void adjust_rejectsWhenAllTargetsAreUnquoted() {
        SupplierPriceList list = active(500L, RELEASED);
        list.getItems().add(nullPricedItem(12L));
        when(listRepository.findWithItemsByIdAndDeletedFlagFalse(500L)).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> store().adjust(500L,
                new PriceAdjustmentRequest("ADD", new BigDecimal("10.00"), null), 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("全部为不报价");
        verify(adjustmentRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- 构造工具

    private static SupplierPriceList active(Long id, LocalDateTime releasedAt) {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(id);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName("安徽富鑫");
        list.setReleasedAt(releasedAt);
        list.setEffectiveFrom(releasedAt.toLocalDate());
        list.setStatus(SupplierPriceList.STATUS_ACTIVE);
        list.setVersion(0L);
        list.setItems(new ArrayList<>());
        return list;
    }

    private static SupplierPriceItem quotedItem(Long id, String price) {
        SupplierPriceItem item = baseItem(id);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private static SupplierPriceItem nullPricedItem(Long id) {
        SupplierPriceItem item = baseItem(id);
        item.setPrice(null);
        return item;
    }

    private static SupplierPriceItem baseItem(Long id) {
        SupplierPriceItem item = new SupplierPriceItem();
        item.setId(id);
        item.setCategory("螺纹钢");
        item.setMaterial("抗震钢E");
        item.setSpec(12);
        item.setLength("9米");
        item.setSortOrder(0);
        return item;
    }
}
