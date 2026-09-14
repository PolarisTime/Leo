package com.leo.erp.sales.returns.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundItemRepository;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.web.dto.SalesReturnItemRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SalesReturnApplyService 测试：金额口径（重量 × 单价）、总金额汇总与表头来源推导。
 */
@ExtendWith(MockitoExtension.class)
class SalesReturnApplyServiceTest {

    @Mock
    private SalesOutboundItemRepository salesOutboundItemRepository;

    @Mock
    private SalesOrderItemQueryService salesOrderItemQueryService;

    @Mock
    private FreightBillRepository freightBillRepository;

    private SalesReturnApplyService service;

    @BeforeEach
    void setUp() {
        SalesReturnSourceService sourceService = new SalesReturnSourceService(
                salesOutboundItemRepository, salesOrderItemQueryService, freightBillRepository);
        service = new SalesReturnApplyService(sourceService);
    }

    @Test
    void applyItems_shouldComputeAmountByWeightTonTimesUnitPrice() {
        SalesOutboundItem outboundItem = outboundItem(100L, 500L, 5, "库房A", 30L);
        when(salesOutboundItemRepository.findAllByIdInWithOutbound(any())).thenReturn(List.of(outboundItem));
        when(salesOrderItemQueryService.findActiveByIdIn(any()))
                .thenReturn(List.of(orderItem(500L, "客户A", 10L, "项目A", 20L)));

        SalesReturn entity = new SalesReturn();
        entity.setId(1L);
        SalesReturnRequest request = request(List.of(itemRequest(100L, 5, "1.25", "6.25", "4000")),
                "客户A", "项目A", "库房A");

        service.applyItems(entity, request, new AtomicLong(1000)::incrementAndGet);

        SalesReturnItem applied = entity.getItems().get(0);
        assertThat(applied.getQuantity()).isEqualTo(5);
        assertThat(applied.getWeightTon()).isEqualByComparingTo("6.25");
        assertThat(applied.getAmount()).isEqualByComparingTo("25000.00");
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("6.25");
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("25000.00");
    }

    @Test
    void applyItems_shouldSumTotalAmountAcrossLines() {
        SalesOutboundItem first = outboundItem(100L, 500L, 5, "库房A", 30L);
        SalesOutboundItem second = outboundItem(101L, 500L, 2, "库房A", 30L);
        when(salesOutboundItemRepository.findAllByIdInWithOutbound(any())).thenReturn(List.of(first, second));
        when(salesOrderItemQueryService.findActiveByIdIn(any()))
                .thenReturn(List.of(orderItem(500L, "客户A", 10L, "项目A", 20L)));

        SalesReturn entity = new SalesReturn();
        entity.setId(1L);
        SalesReturnRequest request = request(
                List.of(itemRequest(100L, 5, "1.25", "6.25", "4000"),
                        itemRequest(101L, 2, "1.5", "3.0", "1000")),
                "客户A", "项目A", "库房A");

        service.applyItems(entity, request, new AtomicLong(1000)::incrementAndGet);

        assertThat(entity.getItems().get(0).getAmount()).isEqualByComparingTo("25000.00");
        assertThat(entity.getItems().get(1).getAmount()).isEqualByComparingTo("3000.00");
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("28000.00");
    }

    @Test
    void applyItems_shouldDeriveHeaderTextsFromSourceWhenRequestBlank() {
        SalesOutboundItem outboundItem = outboundItem(100L, 500L, 5, "库房A", 30L);
        when(salesOutboundItemRepository.findAllByIdInWithOutbound(any())).thenReturn(List.of(outboundItem));
        when(salesOrderItemQueryService.findActiveByIdIn(any()))
                .thenReturn(List.of(orderItem(500L, "来源客户", 10L, "来源项目", 20L)));

        SalesReturn entity = new SalesReturn();
        entity.setId(1L);
        SalesReturnRequest request = request(List.of(itemRequest(100L, 5, "1.25", "6.25", "4000")),
                null, null, null);

        service.applyItems(entity, request, new AtomicLong(1000)::incrementAndGet);

        assertThat(entity.getCustomerId()).isEqualTo(10L);
        assertThat(entity.getCustomerName()).isEqualTo("来源客户");
        assertThat(entity.getProjectId()).isEqualTo(20L);
        assertThat(entity.getProjectName()).isEqualTo("来源项目");
        assertThat(entity.getWarehouseId()).isEqualTo(30L);
        assertThat(entity.getWarehouseName()).isEqualTo("库房A");
    }

    @Test
    void applyItems_shouldRejectZeroQuantity() {
        SalesOutboundItem outboundItem = outboundItem(100L, 500L, 5, "库房A", 30L);
        when(salesOutboundItemRepository.findAllByIdInWithOutbound(any())).thenReturn(List.of(outboundItem));
        when(salesOrderItemQueryService.findActiveByIdIn(any()))
                .thenReturn(List.of(orderItem(500L, "客户A", 10L, "项目A", 20L)));
        SalesReturn entity = new SalesReturn();
        entity.setId(1L);
        SalesReturnRequest request = request(List.of(itemRequest(100L, 0, "1.25", "0", "4000")),
                "客户A", "项目A", "库房A");

        assertThatThrownBy(() -> service.applyItems(entity, request, new AtomicLong(1000)::incrementAndGet))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退货数量必须大于0");
    }

    @Test
    void applyItems_shouldRejectNegativeQuantity() {
        SalesOutboundItem outboundItem = outboundItem(100L, 500L, 5, "库房A", 30L);
        when(salesOutboundItemRepository.findAllByIdInWithOutbound(any())).thenReturn(List.of(outboundItem));
        when(salesOrderItemQueryService.findActiveByIdIn(any()))
                .thenReturn(List.of(orderItem(500L, "客户A", 10L, "项目A", 20L)));
        SalesReturn entity = new SalesReturn();
        entity.setId(1L);
        SalesReturnRequest request = request(List.of(itemRequest(100L, -3, "1.25", "-3.75", "4000")),
                "客户A", "项目A", "库房A");

        assertThatThrownBy(() -> service.applyItems(entity, request, new AtomicLong(1000)::incrementAndGet))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退货数量必须大于0");
    }

    private SalesOutboundItem outboundItem(Long id, Long orderItemId, int quantity,
                                           String warehouseName, Long warehouseId) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(900L);
        outbound.setSalesOrderNo("SO001");
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(id);
        item.setSourceSalesOrderItemId(orderItemId);
        item.setQuantity(quantity);
        item.setWarehouseId(warehouseId);
        item.setWarehouseName(warehouseName);
        item.setSalesOutbound(outbound);
        return item;
    }

    private SalesOrderItem orderItem(Long id, String customerName, Long customerId,
                                     String projectName, Long projectId) {
        SalesOrder order = new SalesOrder();
        order.setId(700L);
        order.setCustomerId(customerId);
        order.setCustomerName(customerName);
        order.setProjectId(projectId);
        order.setProjectName(projectName);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(order);
        return item;
    }

    private SalesReturnItemRequest itemRequest(Long sourceOutboundItemId, int quantity,
                                               String pieceWeightTon, String weightTon, String unitPrice) {
        return new SalesReturnItemRequest(
                null, sourceOutboundItemId, null, null, null, null, null, null, null, null, null,
                null, null, null, quantity, "件",
                new BigDecimal(pieceWeightTon), 100,
                new BigDecimal(weightTon), new BigDecimal(unitPrice), null);
    }

    private SalesReturnRequest request(List<SalesReturnItemRequest> items,
                                       String customerName, String projectName, String warehouseName) {
        return new SalesReturnRequest(
                "SR001", "SO001", 10L, customerName, 20L, projectName, 30L, warehouseName,
                java.time.LocalDate.of(2026, 9, 1), "草稿", null, items, false);
    }
}
