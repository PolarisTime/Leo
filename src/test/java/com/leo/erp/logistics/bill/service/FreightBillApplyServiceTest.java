package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.logistics.bill.repository.FreightBillItemRepository;
import com.leo.erp.logistics.bill.repository.FreightBillItemRepository.FreightBillItemOccupancySummary;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FreightBillApplyService 极端情况测试：行级拆分/部分导入、数量与重量换算、占用校验。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillApplyServiceTest {

    @Mock
    private SalesOrderLogisticsSourceQuery salesOrderSourceQuery;

    @Mock
    private FreightBillItemRepository itemRepository;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @InjectMocks
    private FreightBillApplyService service;

    private static final int SOURCE_QUANTITY = 10;

    private SalesOrderSourceItemSnapshot srcItem(Long id, String pieceWeightTon) {
        return srcItem(id, pieceWeightTon, null);
    }

    private SalesOrderSourceItemSnapshot srcItem(Long id, String pieceWeightTon, Integer remainingQuantity) {
        return new SalesOrderSourceItemSnapshot(
                id, 1, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 30L, "结算公司A", 1L, "库房A", "B001", "b001", SOURCE_QUANTITY, "件",
                pieceWeightTon == null ? null : new BigDecimal(pieceWeightTon), 100,
                new BigDecimal("12.500"), new BigDecimal("4000"), new BigDecimal("50000"),
                new BigDecimal("12.500"), remainingQuantity);
    }

    private SalesOrderSourceSnapshot srcOrder(Long id, String orderNo, String status,
                                              List<SalesOrderSourceItemSnapshot> items) {
        return new SalesOrderSourceSnapshot(
                id, orderNo, "IB001", "PO001", "CUST001", 10L, "客户A", 20L, "项目A", 30L, "结算公司A",
                LocalDate.of(2026, 8, 1), "销售员A", new BigDecimal("100"), new BigDecimal("5000"),
                status, false, null, items);
    }

    private FreightBillItemRequest itemReq(Long id, Long sourceId) {
        return itemReq(id, sourceId, null, null);
    }

    private FreightBillItemRequest itemReq(Long id, Long sourceId, Integer quantity, BigDecimal weightTon) {
        return itemReq(id, sourceId, quantity, weightTon, new BigDecimal("1.250"));
    }

    private FreightBillItemRequest itemReq(Long id, Long sourceId, Integer quantity, BigDecimal weightTon,
                                           BigDecimal pieceWeightTon) {
        return new FreightBillItemRequest(
                id, "SO001", 30L, "结算公司A", 10L, "客户A", 20L, "项目A", 500L, "M001", "螺纹钢",
                "品牌A", "型钢", "螺纹钢", "HRB400", "12m", quantity, "件", pieceWeightTon, 100,
                "B001", weightTon, 1L, "库房A", null, null, sourceId);
    }

    private FreightBillRequest request(List<FreightBillItemRequest> items) {
        return new FreightBillRequest("FB001", 1L, "C001", "承运商A", 30L, "结算公司A", null, null,
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), "DRAFT", null, items, List.of(), false);
    }

    private void stubSources(SalesOrderSourceSnapshot order) {
        when(salesOrderSourceQuery.findOrderIdsBySourceItemIds(any())).thenReturn(List.of(order.id()));
        when(salesOrderSourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        lenient().when(itemRepository.summarizeOccupiedQuantities(any(), any())).thenReturn(List.of());
    }

    private FreightBillItemOccupancySummary occupancy(Long sourceItemId, int quantity) {
        return new FreightBillItemOccupancySummary() {
            @Override
            public Long getSourceSalesOrderItemId() {
                return sourceItemId;
            }

            @Override
            public Long getTotalQuantity() {
                return (long) quantity;
            }
        };
    }

    // ---------- 正常路径 ----------

    @Test
    void applyItems_shouldApplyItemsAndSyncSources() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L))), () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getLineNo()).isEqualTo(1);
        // 缺省数量=来源数量10, 重量=1.250*10=12.500
        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(SOURCE_QUANTITY);
        assertThat(entity.getItems().get(0).getWeightTon()).isEqualByComparingTo("12.500");
        assertThat(entity.getItems().get(0).getMaterialName()).isEqualTo("品牌A");
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("12.500");
        assertThat(entity.getTotalFreight()).isEqualByComparingTo("1250.00"); // 12.5 * 100
        assertThat(entity.getSourceOrders()).hasSize(1);
        assertThat(entity.getSourceOrders().iterator().next().getSourceSalesOrderId()).isEqualTo(1L);
    }

    @Test
    void applyItems_shouldApplyPartialQuantityAndRecomputeWeightAndFreight() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L, 4, null))), () -> 100L);

        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(4);
        assertThat(entity.getItems().get(0).getWeightTon()).isEqualByComparingTo("5.000");
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("5.000");
        assertThat(entity.getTotalFreight()).isEqualByComparingTo("500.00");
    }

    @Test
    void applyItems_shouldPreferExplicitWeighedWeightTon() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L, 4, new BigDecimal("4.800")))), () -> 100L);

        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(4);
        assertThat(entity.getItems().get(0).getWeightTon()).isEqualByComparingTo("4.800");
        assertThat(entity.getTotalFreight()).isEqualByComparingTo("480.00");
    }

    @Test
    void applyItems_shouldIgnoreNonPositiveExplicitWeightAndFallbackToPieceWeight() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L, 2, BigDecimal.ZERO))), () -> 100L);

        assertThat(entity.getItems().get(0).getWeightTon()).isEqualByComparingTo("2.500");
    }

    @Test
    void applyItems_shouldSupportPartialImportOfOrderSubset() {
        SalesOrderSourceSnapshot order = srcOrder(1L, "SO001", StatusConstants.AUDITED,
                List.of(srcItem(11L, "1.000"), srcItem(12L, "2.000")));
        stubSources(order);
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(
                itemReq(null, 12L, 3, null, new BigDecimal("2.000")))), () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getSourceSalesOrderItemId()).isEqualTo(12L);
        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(entity.getItems().get(0).getWeightTon()).isEqualByComparingTo("6.000");
        assertThat(entity.getSourceOrders()).hasSize(1);
    }

    @Test
    void applyItems_shouldReuseExistingSourceOrderRelationWithoutDuplicating() {
        FreightBill entity = new FreightBill();
        FreightBillSourceOrder existingOrder = new FreightBillSourceOrder();
        existingOrder.setSourceSalesOrderId(1L);
        existingOrder.setSourceSalesOrderNo("SO001");
        existingOrder.setActiveFlag(true);
        entity.getSourceOrders().add(existingOrder);
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));

        service.applyItems(entity, request(List.of(itemReq(null, 11L, 2, null))), () -> 100L);

        assertThat(entity.getSourceOrders()).hasSize(1);
        assertThat(existingOrder.isActiveFlag()).isTrue();
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void applyItems_shouldReleaseRemovedSourceItemLine() {
        FreightBill entity = new FreightBill();
        FreightBillSourceOrder oldOrder = new FreightBillSourceOrder();
        oldOrder.setSourceSalesOrderId(88L);
        oldOrder.setActiveFlag(true);
        entity.getSourceOrders().add(oldOrder);
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));

        service.applyItems(entity, request(List.of(itemReq(null, 11L, 2, null))), () -> 100L);

        // 被移除的来源行随明细行一起释放：占用改为按 lg_freight_bill_item 聚合，行删除即释放。
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getSourceSalesOrderItemId()).isEqualTo(11L);
        assertThat(oldOrder.isActiveFlag()).isFalse();
    }

    // ---------- 校验失败 ----------

    @Test
    void applyItems_shouldRejectNullOrDuplicateSourceItemId() {
        FreightBillRequest request = request(List.of(itemReq(null, null), itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空或重复");
    }

    @Test
    void applyItems_shouldRejectDuplicateSourceItemWithinSameBill() {
        FreightBillRequest request = request(List.of(itemReq(null, 11L), itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空或重复");
    }

    @Test
    void applyItems_shouldRejectSourceItemOutsideRequestedOrders() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBillRequest request = request(List.of(itemReq(null, 999L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不存在");
    }

    @Test
    void applyItems_shouldRejectEmptyOrders() {
        when(salesOrderSourceQuery.findOrderIdsBySourceItemIds(any())).thenReturn(List.of());
        when(salesOrderSourceQuery.findBySourceItemIds(any())).thenReturn(List.of());
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要导入");
    }

    @Test
    void applyItems_shouldRejectInvalidSourceStatus() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.DRAFT, List.of(srcItem(11L, "12.500"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不能生成物流单");
    }

    @Test
    void applyItems_shouldRejectQuantityExceedingSourceQuantity() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L, SOURCE_QUANTITY + 1, null)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过来源销售订单明细数量");
    }

    @Test
    void applyItems_shouldRejectCumulativeOverOccupancy() {
        // 其他物流单已占用 8 件，本单请求 3 件 → 累计超过来源数量 10
        when(salesOrderSourceQuery.findOrderIdsBySourceItemIds(any())).thenReturn(List.of(1L));
        when(salesOrderSourceQuery.findBySourceItemIds(any()))
                .thenReturn(List.of(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250")))));
        when(itemRepository.summarizeOccupiedQuantities(any(), any()))
                .thenReturn(List.of(occupancy(11L, 8)));
        FreightBillRequest request = request(List.of(itemReq(null, 11L, 3, null)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可导入数量不足");
    }

    @Test
    void applyItems_shouldAllowWithinRemainingOccupancy() {
        when(salesOrderSourceQuery.findOrderIdsBySourceItemIds(any())).thenReturn(List.of(1L));
        when(salesOrderSourceQuery.findBySourceItemIds(any()))
                .thenReturn(List.of(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250")))));
        when(itemRepository.summarizeOccupiedQuantities(any(), any()))
                .thenReturn(List.of(occupancy(11L, 8)));
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L, 2, null))), () -> 100L);

        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void applyItems_shouldRejectZeroQuantity() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L, 0, null)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须大于0");
    }

    @Test
    void applyItems_shouldRejectNegativeQuantity() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBillRequest request = request(List.of(itemReq(null, 11L, -1, null)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须大于0");
    }

    @Test
    void applyItems_shouldRejectNonPositiveWeightWhenPieceWeightMissing() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "0"))));
        FreightBillRequest request = request(List.of(
                itemReq(null, 11L, null, null, BigDecimal.ZERO)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重量");
    }

    @Test
    void applyItems_shouldRejectFixedFieldMismatch() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBillItemRequest mismatched = new FreightBillItemRequest(
                null, "SO001", 30L, "结算公司A", 10L, "客户A", 20L, "项目A", 500L, "M001", "螺纹钢",
                "品牌B", "型钢", "螺纹钢", "HRB400", "12m", 2, "件", new BigDecimal("1.250"), 100,
                "B001", null, 1L, "库房A", null, null, 11L);
        FreightBillRequest request = request(List.of(mismatched));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌与请求不一致");
    }

    /**
     * 回归(A1): 加锁前不得加载销售订单实体快照, 只投影父订单主键,
     * 否则加锁后重查会命中一级缓存的旧状态。
     */
    @Test
    void resolveSources_shouldLocateOrdersViaScalarProjectionBeforeLock() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));

        service.applyItems(new FreightBill(), request(List.of(itemReq(null, 11L))), () -> 100L);

        var order = org.mockito.Mockito.inOrder(
                salesOrderSourceQuery, sourceAllocationLockService);
        order.verify(salesOrderSourceQuery).findOrderIdsBySourceItemIds(any());
        order.verify(sourceAllocationLockService).lockDocumentSources(any(), any(), any(), any());
        order.verify(salesOrderSourceQuery).findBySourceItemIds(any());
    }

    @Test
    void applyItems_shouldExcludeCurrentBillWhenLoadingOccupancy() {
        stubSources(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250"))));
        FreightBill entity = new FreightBill();
        entity.setId(77L);

        service.applyItems(entity, request(List.of(itemReq(null, 11L))), () -> 100L);

        verify(itemRepository).summarizeOccupiedQuantities(any(), eq(77L));
    }

    @Test
    void applyItems_shouldNotQueryOccupancyWhenNoSourceItems() {
        when(salesOrderSourceQuery.findOrderIdsBySourceItemIds(any())).thenReturn(List.of());
        when(salesOrderSourceQuery.findBySourceItemIds(any())).thenReturn(List.of());
        FreightBillRequest request = request(List.of(itemReq(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(new FreightBill(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldTolerateNullOccupancySummary() {
        when(salesOrderSourceQuery.findOrderIdsBySourceItemIds(any())).thenReturn(List.of(1L));
        when(salesOrderSourceQuery.findBySourceItemIds(any()))
                .thenReturn(List.of(srcOrder(1L, "SO001", StatusConstants.AUDITED, List.of(srcItem(11L, "1.250")))));
        lenient().when(itemRepository.summarizeOccupiedQuantities(any(), anyLong())).thenReturn(List.of());
        FreightBill entity = new FreightBill();

        service.applyItems(entity, request(List.of(itemReq(null, 11L, 1, null))), () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
    }
}
