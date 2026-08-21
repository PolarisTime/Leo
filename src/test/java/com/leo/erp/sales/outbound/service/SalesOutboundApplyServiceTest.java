package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundApplyService 极端情况测试。
 * <p>
 * 通过 @Spy 注入真实 SalesOutboundSourceService/WeightService，覆盖 applyItems 编排
 * 与 header 聚合（客户/项目/仓库/结算主体）的完整校验链。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundApplyServiceTest {

    @Mock
    private TradeItemMaterialSupport tradeItemMaterialSupport;

    @Mock
    private WarehouseSelectionSupport warehouseSelectionSupport;

    @Mock
    private SalesOrderItemQueryService salesOrderItemQueryService;

    @Mock
    private SalesOutboundRepository repository;

    private SalesOutboundSourceService sourceService;
    private SalesOutboundWeightService weightService;
    private SalesOutboundApplyService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 手动装配真实校验链，避免 @Spy 字段初始化顺序导致依赖 mock 为 null
        sourceService = new SalesOutboundSourceService(salesOrderItemQueryService, repository);
        weightService = new SalesOutboundWeightService();
        service = new SalesOutboundApplyService(
                tradeItemMaterialSupport, warehouseSelectionSupport, sourceService, weightService);
    }

    // ---------- 测试数据 ----------

    private SalesOrder salesOrder(Long id, String orderNo, String status, Long customerId, String customerName,
                                  Long projectId, String projectName, Long settlementCompanyId, String settlementCompanyName) {
        SalesOrder o = new SalesOrder();
        o.setId(id);
        o.setOrderNo(orderNo);
        o.setStatus(status);
        o.setCustomerId(customerId);
        o.setCustomerName(customerName);
        o.setProjectId(projectId);
        o.setProjectName(projectName);
        o.setSettlementCompanyId(settlementCompanyId);
        o.setSettlementCompanyName(settlementCompanyName);
        return o;
    }

    private SalesOrderItem sourceItem(Long id, Integer quantity, SalesOrder order) {
        SalesOrderItem i = new SalesOrderItem();
        i.setId(id);
        i.setQuantity(quantity);
        i.setSalesOrder(order);
        i.setMaterialId(500L);
        i.setMaterialCode("M001");
        i.setBrand("品牌A");
        i.setCategory("型钢");
        i.setMaterial("螺纹钢");
        i.setSpec("HRB400");
        i.setLength("12m");
        i.setUnit("吨");
        i.setSettlementCompanyId(30L);
        i.setSettlementCompanyName("结算公司A");
        i.setWarehouseId(1L);
        i.setWarehouseName("库房A");
        i.setBatchNo("B001");
        i.setUnitPrice(new BigDecimal("4000"));
        i.setWeightTon(new BigDecimal("12.500"));
        return i;
    }

    private SalesOutboundItemRequest itemRequest(Long sourceId, Integer qty, Long warehouseId) {
        return new SalesOutboundItemRequest(
                null, "SO001", sourceId, 500L, null, null, null, null, null, null, null,
                warehouseId, null, null, qty, "件", new BigDecimal("1.250"), 100, null,
                new BigDecimal("4000"), null);
    }

    private SalesOutboundRequest request(Long customerId, String customerName, Long projectId, String projectName,
                                         Long warehouseId, String warehouseName, List<SalesOutboundItemRequest> items) {
        return new SalesOutboundRequest(
                "OB001", null, customerId, customerName, projectId, projectName, warehouseId, warehouseName,
                LocalDate.of(2026, 8, 1), "DRAFT", null, items, false);
    }

    private void stubSingleSource(SalesOrder order, SalesOrderItem item) {
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(item));
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of());
    }

    // ---------- applyItems 正常路径 ----------

    @Test
    void applyItems_shouldApplyItemAndResolveHeader() {
        SalesOrder order = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrderItem item = sourceItem(11L, 10, order);
        stubSingleSource(order, item);
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);

        service.applyItems(entity,
                request(10L, "客户A", 20L, "项目A", null, null, List.of(itemRequest(11L, 5, 1L))),
                () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        SalesOutboundItem applied = entity.getItems().get(0);
        assertThat(applied.getSourceSalesOrderItemId()).isEqualTo(11L);
        assertThat(applied.getLineNo()).isEqualTo(1);
        assertThat(applied.getMaterialCode()).isEqualTo("M001");
        // 来源重量 12.5/数量 10 → 单件 1.25；请求数量 5 → 6.25
        assertThat(applied.getWeightTon()).isEqualByComparingTo("6.250");
        assertThat(applied.getAmount()).isEqualByComparingTo("25000"); // 6.25 * 4000
        assertThat(entity.getSalesOrderNo()).isEqualTo("SO001");
        assertThat(entity.getCustomerId()).isEqualTo(10L);
        assertThat(entity.getProjectId()).isEqualTo(20L);
        assertThat(entity.getWarehouseId()).isEqualTo(1L);
        assertThat(entity.getWarehouseName()).isEqualTo("库房A");
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("6.250");
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("25000");
    }

    @Test
    void applyItems_shouldResolveWarehouseFromSelectionWhenSourceMissing() {
        SalesOrder order = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrderItem item = sourceItem(11L, 10, order);
        item.setWarehouseId(null); // 来源无仓库 → 走 warehouseSelectionSupport
        item.setWarehouseName(null);
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(item));
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), anyBoolean()))
                .thenReturn(new WarehouseSnapshot(9L, null, "选择仓库"));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of());
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);

        // warehouseId 传 null：validate 跳过仓库一致性比对；resolveWarehouse 用 source 无仓库场景
        service.applyItems(entity,
                request(10L, "客户A", 20L, "项目A", null, null, List.of(itemRequest(11L, 5, null))),
                () -> 100L);

        assertThat(entity.getItems().get(0).getWarehouseId()).isEqualTo(9L);
        assertThat(entity.getItems().get(0).getWarehouseName()).isEqualTo("选择仓库");
    }

    // ---------- 多来源聚合边界 ----------

    @Test
    void applyItems_shouldRejectMultipleCustomers() {
        // 两个订单客户名一致但客户 ID 不同 → 触发 resolveSingleIdentity 的不同客户校验
        SalesOrder orderA = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrder orderB = salesOrder(2L, "SO002", StatusConstants.AUDITED, 99L, "客户A", 20L, "项目A", 30L, "结算公司A");
        when(salesOrderItemQueryService.findActiveByIdIn(any()))
                .thenReturn(List.of(sourceItem(11L, 10, orderA), sourceItem(12L, 10, orderB)));
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.applyItems(new SalesOutbound(),
                request(null, "客户A", null, "项目A", null, null,
                        List.of(itemRequest(11L, 5, 1L), itemRequest(12L, 5, 1L))),
                () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同客户");
    }

    @Test
    void applyItems_shouldRejectMultipleSettlementCompanies() {
        SalesOrder orderA = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrder orderB = salesOrder(2L, "SO002", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 99L, "结算公司B");
        when(salesOrderItemQueryService.findActiveByIdIn(any()))
                .thenReturn(List.of(sourceItem(11L, 10, orderA), sourceItem(12L, 10, orderB)));
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.applyItems(new SalesOutbound(),
                request(null, "客户A", null, "项目A", null, null,
                        List.of(itemRequest(11L, 5, 1L), itemRequest(12L, 5, 1L))),
                () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同客户结算主体");
    }

    @Test
    void applyItems_shouldResolveMultipleWarehousesAsMultiName() {
        SalesOrder order = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrderItem itemA = sourceItem(11L, 10, order);
        SalesOrderItem itemB = sourceItem(12L, 10, order);
        itemB.setWarehouseId(2L);
        itemB.setWarehouseName("库房B");
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(itemA, itemB));
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of());
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);

        service.applyItems(entity,
                request(10L, "客户A", 20L, "项目A", null, null,
                        List.of(itemRequest(11L, 5, 1L), itemRequest(12L, 5, 2L))),
                () -> 100L);

        assertThat(entity.getWarehouseId()).isNull();   // 多仓库 → id null
        assertThat(entity.getWarehouseName()).isEqualTo("多仓库");
    }

    @Test
    void applyItems_shouldRejectOccupiedSourceOrder() {
        SalesOrder order = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrderItem item = sourceItem(11L, 10, order);
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(item));
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        SalesOutbound occupied = new SalesOutbound();
        occupied.setOutboundNo("OB999");
        SalesOutboundItem occupiedItem = new SalesOutboundItem();
        occupiedItem.setSourceSalesOrderItemId(11L);
        occupied.setItems(List.of(occupiedItem));
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of(occupied));
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);

        assertThatThrownBy(() -> service.applyItems(entity,
                request(10L, "客户A", 20L, "项目A", null, null, List.of(itemRequest(11L, 5, 1L))),
                () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OB999");
    }

    // ---------- sourceSalesOrderIds ----------

    @Test
    void sourceSalesOrderIds_shouldReturnDistinctOrderIds() {
        SalesOrder order = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrderItem itemA = sourceItem(11L, 10, order);
        SalesOrderItem itemB = sourceItem(12L, 10, order);
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(itemA, itemB));
        SalesOutbound entity = new SalesOutbound();
        SalesOutboundItem a = new SalesOutboundItem();
        a.setSourceSalesOrderItemId(11L);
        SalesOutboundItem b = new SalesOutboundItem();
        b.setSourceSalesOrderItemId(12L);
        entity.setItems(List.of(a, b));

        assertThat(service.sourceSalesOrderIds(entity)).containsExactly(1L); // 两个明细同一订单 → 去重
    }

    @Test
    void sourceSalesOrderIds_shouldFilterItemsWithoutOrder() {
        SalesOrderItem item = sourceItem(11L, 10, null); // 无订单
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(item));
        SalesOutbound entity = new SalesOutbound();
        SalesOutboundItem a = new SalesOutboundItem();
        a.setSourceSalesOrderItemId(11L);
        entity.setItems(List.of(a));

        assertThat(service.sourceSalesOrderIds(entity)).isEmpty();
    }

    // ---------- header 聚合边界 ----------

    @Test
    void applyItems_shouldResolveNullSettlementWhenSourceHasNone() {
        SalesOrder order = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", null, null);
        SalesOrderItem item = sourceItem(11L, 10, order);
        item.setSettlementCompanyId(null);
        item.setSettlementCompanyName(null);
        stubSingleSource(order, item);
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);

        service.applyItems(entity,
                request(10L, "客户A", 20L, "项目A", null, null, List.of(itemRequest(11L, 5, 1L))),
                () -> 100L);

        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
    }

    @Test
    void applyItems_shouldFallbackWarehouseWhenNoneResolved() {
        SalesOrder order = salesOrder(1L, "SO001", StatusConstants.AUDITED, 10L, "客户A", 20L, "项目A", 30L, "结算公司A");
        SalesOrderItem item = sourceItem(11L, 10, order);
        item.setWarehouseId(null);
        item.setWarehouseName(null);
        when(salesOrderItemQueryService.findActiveByIdIn(any())).thenReturn(List.of(item));
        when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), anyBoolean()))
                .thenReturn(new WarehouseSnapshot(null, null, null)); // 未解析出仓库
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(any(), any()))
                .thenReturn(List.of());
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);

        service.applyItems(entity,
                request(10L, "客户A", 20L, "项目A", null, null, List.of(itemRequest(11L, 5, null))),
                () -> 100L);

        assertThat(entity.getWarehouseId()).isNull();       // 空集合 → 回退（此处 fallback 也 null）
        assertThat(entity.getWarehouseName()).isNull();
    }
}
