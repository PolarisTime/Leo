package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOrderPurchaseAllocationServiceTest {

    @Test
    void shouldReleaseOnlyPersistedSalesOrderItemIds() {
        PurchaseItemQueryAppService queryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderPurchaseAllocationService service = new SalesOrderPurchaseAllocationService(
                queryAppService,
                pieceWeightAppService
        );
        SalesOrder order = salesOrder(List.of(
                salesOrderItem(null, 1, null, 1, "0.100", "4000.00"),
                salesOrderItem(11L, 2, null, 1, "0.200", "5000.00")
        ));

        service.releaseSalesOrderItems(order);

        verify(pieceWeightAppService).releaseSalesOrderItems(List.of(11L));
        verifyNoInteractions(queryAppService);
    }

    @Test
    void shouldDetectPurchaseOrderBackedItemsByPositiveQuantity() {
        SalesOrderPurchaseAllocationService service = new SalesOrderPurchaseAllocationService(
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class)
        );
        SalesOrder order = salesOrder(List.of(
                salesOrderItem(11L, 1, 201L, null, "0.100", "4000.00"),
                salesOrderItem(12L, 2, 202L, 0, "0.200", "5000.00"),
                salesOrderItem(13L, 3, 203L, 1, "0.300", "6000.00")
        ));

        boolean result = service.hasPurchaseOrderBackedItems(order);

        assertThat(result).isTrue();
    }

    @Test
    void shouldFinalizeOrderWithoutPurchaseOrderSources() {
        PurchaseItemQueryAppService queryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderPurchaseAllocationService service = new SalesOrderPurchaseAllocationService(
                queryAppService,
                pieceWeightAppService
        );
        SalesOrder order = salesOrder(List.of(
                salesOrderItem(11L, 1, null, 2, "0.200", "4000.00")
        ));

        service.finalizePurchaseOrderAllocations(order);

        assertThat(order.getItems().get(0).getAmount()).isEqualByComparingTo("800.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.200");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("800.00");
        verify(queryAppService, never()).findSourcePurchaseOrderItemsByIds(any());
        verify(pieceWeightAppService, never()).allocateForSalesOrderItem(any(), any(), any(), anyInt());
    }

    @Test
    void shouldFinalizeNonPositivePurchaseOrderQuantitiesWithoutAllocation() {
        PurchaseItemQueryAppService queryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderPurchaseAllocationService service = new SalesOrderPurchaseAllocationService(
                queryAppService,
                pieceWeightAppService
        );
        SalesOrder order = salesOrder(List.of(
                salesOrderItem(11L, 1, 201L, null, "0.100", "4000.00"),
                salesOrderItem(12L, 2, 202L, 0, "0.200", "5000.00")
        ));
        when(queryAppService.findSourcePurchaseOrderItemsByIds(List.of(201L, 202L))).thenReturn(List.of());

        service.finalizePurchaseOrderAllocations(order);

        assertThat(order.getItems().get(0).getAmount()).isEqualByComparingTo("400.00");
        assertThat(order.getItems().get(1).getAmount()).isEqualByComparingTo("1000.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.300");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1400.00");
        verify(pieceWeightAppService, never()).allocateForSalesOrderItem(any(), any(), any(), anyInt());
    }

    @Test
    void shouldUseZeroLineNoWhenAllocatingPurchaseOrderItemWithoutLineNo() {
        PurchaseItemQueryAppService queryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderPurchaseAllocationService service = new SalesOrderPurchaseAllocationService(
                queryAppService,
                pieceWeightAppService
        );
        SalesOrderItem item = salesOrderItem(11L, null, 201L, 3, "0.100", "4000.00");
        SalesOrder order = salesOrder(List.of(item));
        when(queryAppService.findSourcePurchaseOrderItemsByIds(List.of(201L))).thenReturn(List.of(
                sourcePurchaseOrderRecord(201L)
        ));
        when(pieceWeightAppService.allocateForSalesOrderItem(201L, 3, 11L, 0))
                .thenReturn(new BigDecimal("0.300"));

        service.finalizePurchaseOrderAllocations(order);

        assertThat(item.getWeightTon()).isEqualByComparingTo("0.300");
        assertThat(item.getAmount()).isEqualByComparingTo("1200.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.300");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1200.00");
        verify(pieceWeightAppService).allocateForSalesOrderItem(201L, 3, 11L, 0);
    }

    @Test
    void shouldRejectMissingSourcePurchaseOrderItem() {
        PurchaseItemQueryAppService queryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderPurchaseAllocationService service = new SalesOrderPurchaseAllocationService(
                queryAppService,
                pieceWeightAppService
        );
        SalesOrder order = salesOrder(List.of(
                salesOrderItem(11L, 5, 404L, 2, "0.200", "4000.00")
        ));
        when(queryAppService.findSourcePurchaseOrderItemsByIds(List.of(404L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.finalizePurchaseOrderAllocations(order))
                .isInstanceOf(BusinessException.class)
                .hasMessage("第5行来源采购订单明细不存在")
                .satisfies(throwable ->
                        assertThat(((BusinessException) throwable).getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR));
        verify(pieceWeightAppService, never()).allocateForSalesOrderItem(any(), any(), any(), anyInt());
    }

    private SalesOrder salesOrder(List<SalesOrderItem> items) {
        SalesOrder order = new SalesOrder();
        order.getItems().addAll(items);
        return order;
    }

    private SalesOrderItem salesOrderItem(
            Long id,
            Integer lineNo,
            Long sourcePurchaseOrderItemId,
            Integer quantity,
            String weightTon,
            String unitPrice
    ) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setLineNo(lineNo);
        item.setSourcePurchaseOrderItemId(sourcePurchaseOrderItemId);
        item.setQuantity(quantity);
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }

    private PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord sourcePurchaseOrderRecord(Long id) {
        return new PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord(
                id,
                10,
                new BigDecimal("1.000"),
                "PO-001",
                "宝钢",
                "HRB400",
                "8",
                "M1",
                "盘螺",
                "吨",
                "一号库",
                "B1"
        );
    }
}
