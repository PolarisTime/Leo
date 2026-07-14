package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemPieceWeightRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseItemPieceWeightAppServiceImplTest {

    @Test
    void shouldDelegateAllocateForSalesOrderItemToPieceWeightService() {
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemPieceWeightAppServiceImpl service = new PurchaseItemPieceWeightAppServiceImpl(
                pieceWeightService, orderItemQueryService
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(10);
        sourceItem.setWeightTon(new BigDecimal("1.000"));

        when(orderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(pieceWeightService.allocateForSalesOrderItem(sourceItem, 5, 301L, 1))
                .thenReturn(new BigDecimal("0.500"));

        BigDecimal result = service.allocateForSalesOrderItem(201L, 5, 301L, 1);

        assertThat(result).isEqualByComparingTo("0.500");
        verify(orderItemQueryService).findActiveByIdIn(List.of(201L));
        verify(pieceWeightService).allocateForSalesOrderItem(sourceItem, 5, 301L, 1);
    }

    @Test
    void shouldReturnZeroWhenSourcePurchaseOrderItemIdIsNull() {
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemPieceWeightAppServiceImpl service = new PurchaseItemPieceWeightAppServiceImpl(
                pieceWeightService, orderItemQueryService
        );

        BigDecimal result = service.allocateForSalesOrderItem(null, 5, 301L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldReturnZeroWhenSourceItemNotFound() {
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemPieceWeightAppServiceImpl service = new PurchaseItemPieceWeightAppServiceImpl(
                pieceWeightService, orderItemQueryService
        );

        when(orderItemQueryService.findActiveByIdIn(List.of(999L))).thenReturn(List.of());

        BigDecimal result = service.allocateForSalesOrderItem(999L, 5, 301L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldDelegateReleaseSalesOrderItems() {
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemPieceWeightAppServiceImpl service = new PurchaseItemPieceWeightAppServiceImpl(
                pieceWeightService, orderItemQueryService
        );

        service.releaseSalesOrderItems(List.of(301L, 302L));

        verify(pieceWeightService).releaseSalesOrderItems(List.of(301L, 302L));
    }

    @Test
    void shouldDelegateSummarizeRemainingWeight() {
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderItemQueryService orderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseItemPieceWeightAppServiceImpl service = new PurchaseItemPieceWeightAppServiceImpl(
                pieceWeightService, orderItemQueryService
        );

        Map<Long, BigDecimal> expected = Map.of(1L, new BigDecimal("0.500"), 2L, new BigDecimal("0.300"));
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(expected);

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(1L, 2L));

        assertThat(result).isEqualTo(expected);
        verify(pieceWeightService).summarizeRemainingWeightByPurchaseOrderItemIds(List.of(1L, 2L));
    }
}
