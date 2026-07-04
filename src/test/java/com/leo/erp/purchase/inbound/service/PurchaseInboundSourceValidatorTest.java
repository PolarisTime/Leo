package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundSourceValidatorTest {

    @Test
    void shouldPrepareContextWithDistinctRequestedAndAffectedSourceIds() {
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundSourceValidator validator = new PurchaseInboundSourceValidator(
                itemQueryService,
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        PurchaseOrderItem previousSourceItem = sourcePurchaseOrderItem(202L, 8);
        PurchaseInboundRequest request = request(List.of(itemRequest(201L, 4), itemRequest(201L, 3)));
        when(itemQueryService.findActiveByIdIn(List.of(202L, 201L)))
                .thenReturn(List.of(previousSourceItem, sourceItem));
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                eq(1L)
        )).thenReturn(List.of());

        PurchaseInboundSourceValidator.SourceValidationContext context =
                validator.prepareContext(request, 1L, List.of(202L));

        assertThat(context.sourcePurchaseOrderItemIds()).containsExactly(201L);
        assertThat(context.affectedSourcePurchaseOrderItemIds()).containsExactly(202L, 201L);
        assertThat(context.sourcePurchaseOrderItemMap()).containsOnlyKeys(201L, 202L);
        verify(inboundItemRepository).summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                eq(1L)
        );
    }

    @Test
    void shouldRejectWhenSameRequestAllocatesMoreThanSourceQuantity() {
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundSourceValidator validator = new PurchaseInboundSourceValidator(
                itemQueryService,
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 6);
        PurchaseInboundRequest request = request(List.of(itemRequest(201L, 4), itemRequest(201L, 3)));
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                any()
        )).thenReturn(List.of());
        PurchaseInboundSourceValidator.SourceValidationContext context =
                validator.prepareContext(request, null, List.of());

        validator.validateLine(request.items().get(0), 1, request, context);

        assertThatThrownBy(() -> validator.validateLine(request.items().get(1), 2, request, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可关联数量不足")
                .hasMessageContaining("剩余可用 2 件");
    }

    @Test
    void shouldUseHeaderWarehouseWhenLineWarehouseIsBlank() {
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundSourceValidator validator = new PurchaseInboundSourceValidator(
                itemQueryService,
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        PurchaseInboundItemRequest line = itemRequest(201L, 4, null);
        PurchaseInboundRequest request = request(List.of(line));
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                any()
        )).thenReturn(List.of());
        PurchaseInboundSourceValidator.SourceValidationContext context =
                validator.prepareContext(request, null, List.of());

        validator.validateLine(line, 1, request, context);

        assertThat(context.requestAllocatedQuantityMap()).containsEntry(201L, 4);
    }

    @Test
    void shouldDelegateAllocatedQuantityMapLoading() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundSourceValidator validator = new PurchaseInboundSourceValidator(
                mock(PurchaseOrderItemQueryService.class),
                new PurchaseInboundAllocationService(inboundItemRepository)
        );
        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary summary =
                mock(PurchaseInboundItemRepository.PurchaseOrderAllocationSummary.class);
        when(summary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(summary.getTotalQuantity()).thenReturn(2L);
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(201L),
                1L
        )).thenReturn(List.of(summary));

        Map<Long, Integer> result = validator.loadAllocatedQuantityMap(List.of(201L), 1L);

        assertThat(result).containsEntry(201L, 2);
    }

    @Test
    void shouldRejectWhenSourcePurchaseOrderItemIsNotLoaded() {
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundSourceValidator validator = new PurchaseInboundSourceValidator(
                itemQueryService,
                new PurchaseInboundAllocationService(inboundItemRepository)
        );
        PurchaseInboundRequest request = request(List.of(itemRequest(999L, 4)));
        when(itemQueryService.findActiveByIdIn(List.of(999L))).thenReturn(List.of());
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(999L)),
                any()
        )).thenReturn(List.of());
        PurchaseInboundSourceValidator.SourceValidationContext context =
                validator.prepareContext(request, null, List.of());

        assertThatThrownBy(() -> validator.validateLine(request.items().get(0), 1, request, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单明细不存在");
    }

    @Test
    void shouldRejectWhenSourcePurchaseOrderIsMissing() {
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundSourceValidator validator = new PurchaseInboundSourceValidator(
                itemQueryService,
                new PurchaseInboundAllocationService(inboundItemRepository)
        );
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        sourceItem.setPurchaseOrder(null);
        PurchaseInboundRequest request = request(List.of(itemRequest(201L, 4)));
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                any()
        )).thenReturn(List.of());
        PurchaseInboundSourceValidator.SourceValidationContext context =
                validator.prepareContext(request, null, List.of());

        assertThatThrownBy(() -> validator.validateLine(request.items().get(0), 1, request, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单未审核");
    }

    @Test
    void shouldExposeAllocatedQuantityMapFromSourceValidationContext() {
        PurchaseInboundAllocationService.AllocationContext allocationContext =
                new PurchaseInboundAllocationService.AllocationContext(
                        Map.of(201L, 2),
                        new java.util.HashMap<>()
                );
        PurchaseInboundSourceValidator.SourceValidationContext context =
                new PurchaseInboundSourceValidator.SourceValidationContext(
                        List.of(201L),
                        List.of(201L),
                        Map.of(),
                        allocationContext
                );

        assertThat(context.allocatedQuantityMap()).containsEntry(201L, 2);
    }

    private PurchaseOrderItem sourcePurchaseOrderItem(Long id, Integer quantity) {
        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setId(301L);
        sourceOrder.setOrderNo("PO-001");
        sourceOrder.setStatus("已审核");
        sourceOrder.setSupplierName("供应商A");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(sourceOrder);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(quantity);
        item.setQuantityUnit("支");
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4000.00"));
        return item;
    }

    private PurchaseInboundRequest request(List<PurchaseInboundItemRequest> items) {
        return new PurchaseInboundRequest(
                "PI-001",
                "PO-001",
                "供应商A",
                "一号库",
                LocalDate.of(2026, 4, 26),
                "理算",
                "草稿",
                null,
                items
        );
    }

    private PurchaseInboundItemRequest itemRequest(Long sourcePurchaseOrderItemId, int quantity) {
        return itemRequest(sourcePurchaseOrderItemId, quantity, "一号库");
    }

    private PurchaseInboundItemRequest itemRequest(
            Long sourcePurchaseOrderItemId,
            int quantity,
            String warehouseName
    ) {
        return new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                sourcePurchaseOrderItemId, warehouseName, "理算", "B1", quantity, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                null, null, null,
                new BigDecimal("4000.00"), new BigDecimal("1600.00")
        );
    }
}
