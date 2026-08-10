package com.leo.erp.sales.order.service;

import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderLogisticsSourceQueryService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderLogisticsSourceQueryServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @InjectMocks
    private SalesOrderLogisticsSourceQueryService service;

    private SalesOrder order(Long id, String orderNo) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setPurchaseInboundNo("IB001");
        order.setPurchaseOrderNo("PO001");
        order.setCustomerCode("CUST001");
        order.setCustomerId(10L);
        order.setCustomerName("客户A");
        order.setProjectId(20L);
        order.setProjectName("项目A");
        order.setSettlementCompanyId(30L);
        order.setSettlementCompanyName("结算公司A");
        order.setDeliveryDate(LocalDate.of(2026, 8, 1));
        order.setSalesName("销售员A");
        order.setTotalWeight(new BigDecimal("1000.50"));
        order.setTotalAmount(new BigDecimal("50500.00"));
        order.setStatus("SALES_COMPLETED");
        order.setRemark("备注");
        return order;
    }

    private SalesOrderItem item(Long id) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setLineNo(1);
        item.setMaterialId(500L);
        item.setMaterialCode("M001");
        item.setBrand("品牌A");
        item.setCategory("型钢");
        item.setMaterial("螺纹钢");
        item.setSpec("HRB400");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourceInboundItemId(700L);
        item.setSourcePurchaseOrderItemId(800L);
        item.setSettlementCompanyId(30L);
        item.setSettlementCompanyName("结算公司A");
        item.setWarehouseId(1L);
        item.setWarehouseName("库房A");
        item.setBatchNo("B001");
        item.setBatchNoNormalized("b001");
        item.setQuantity(10);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.250"));
        item.setPiecesPerBundle(100);
        item.setWeightTon(new BigDecimal("12.500"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("50000.00"));
        item.setOriginalWeightTon(new BigDecimal("13.000"));
        return item;
    }

    // ---------- findByOrderIds ----------

    @Test
    void findByOrderIds_shouldReturnEmptyForNullInput() {
        assertThat(service.findByOrderIds(null)).isEmpty();
        verifyNoInteractions(salesOrderRepository);
    }

    @Test
    void findByOrderIds_shouldReturnEmptyForEmptyInput() {
        assertThat(service.findByOrderIds(List.of())).isEmpty();
        verifyNoInteractions(salesOrderRepository);
    }

    @Test
    void findByOrderIds_shouldMapOrderAndItems() {
        SalesOrder order = order(1L, "SO001");
        order.setItems(List.of(item(11L), item(12L)));
        when(salesOrderRepository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(order));

        List<SalesOrderSourceSnapshot> result = service.findByOrderIds(List.of(1L));

        assertThat(result).hasSize(1);
        SalesOrderSourceSnapshot snapshot = result.get(0);
        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.orderNo()).isEqualTo("SO001");
        assertThat(snapshot.purchaseInboundNo()).isEqualTo("IB001");
        assertThat(snapshot.status()).isEqualTo("SALES_COMPLETED");
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("50500.00");
        assertThat(snapshot.items()).hasSize(2);
        SalesOrderSourceItemSnapshot item = snapshot.items().get(0);
        assertThat(item.lineNo()).isEqualTo(1);
        assertThat(item.materialCode()).isEqualTo("M001");
        assertThat(item.sourceInboundItemId()).isEqualTo(700L);
        assertThat(item.originalWeightTon()).isEqualByComparingTo("13.000");
    }

    @Test
    void findByOrderIds_shouldHandleMaxSnowflakeId() {
        Long max = Long.MAX_VALUE;
        SalesOrder order = order(max, "SO-MAX");
        order.setItems(List.of(item(max)));
        when(salesOrderRepository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(order));

        List<SalesOrderSourceSnapshot> result = service.findByOrderIds(List.of(max));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(max);
        assertThat(result.get(0).items().get(0).id()).isEqualTo(max);
    }

    // ---------- findBySourceItemIds ----------

    @Test
    void findBySourceItemIds_shouldReturnEmptyForNullInput() {
        assertThat(service.findBySourceItemIds(null)).isEmpty();
        verifyNoInteractions(salesOrderRepository);
    }

    @Test
    void findBySourceItemIds_shouldReturnEmptyForEmptyInput() {
        assertThat(service.findBySourceItemIds(List.of())).isEmpty();
        verifyNoInteractions(salesOrderRepository);
    }

    @Test
    void findBySourceItemIds_shouldMapViaSourceItemIds() {
        SalesOrder order = order(2L, "SO002");
        order.setItems(List.of(item(11L)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));

        List<SalesOrderSourceSnapshot> result = service.findBySourceItemIds(List.of(11L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderNo()).isEqualTo("SO002");
        verify(salesOrderRepository).findAllWithItemsBySourceItemIds(any());
    }
}
