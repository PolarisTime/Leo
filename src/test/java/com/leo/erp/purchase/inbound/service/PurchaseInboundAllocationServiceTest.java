package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseInboundAllocationServiceTest {

    @Test
    void shouldExtractDistinctSourceItemIds() {
        PurchaseInboundAllocationService service =
                new PurchaseInboundAllocationService(mock(PurchaseInboundItemRepository.class));

        List<Long> ids = service.extractSourcePurchaseOrderItemIds(request(
                itemRequest(201L, 4),
                itemRequest(201L, 3),
                itemRequest(202L, 1)
        ));

        assertThat(ids).containsExactly(201L, 202L);
    }

    @Test
    void shouldLoadAllocatedQuantityMap() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundAllocationService service = new PurchaseInboundAllocationService(repository);
        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary summary =
                mock(PurchaseInboundItemRepository.PurchaseOrderAllocationSummary.class);
        when(summary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(summary.getTotalQuantity()).thenReturn(6L);
        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(List.of(201L), 1L))
                .thenReturn(List.of(summary));

        Map<Long, Integer> result = service.loadAllocatedQuantityMap(List.of(201L), 1L);

        assertThat(result).containsEntry(201L, 6);
    }

    @Test
    void shouldRejectWhenRequestAllocatesMoreThanAvailableQuantity() {
        PurchaseInboundAllocationService service =
                new PurchaseInboundAllocationService(mock(PurchaseInboundItemRepository.class));
        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(6);
        PurchaseInboundAllocationService.AllocationContext context =
                new PurchaseInboundAllocationService.AllocationContext(Map.of(201L, 1), new java.util.HashMap<>());

        service.validateAvailableQuantity(itemRequest(201L, 4), sourceItem, 1, context);

        assertThatThrownBy(() -> service.validateAvailableQuantity(itemRequest(201L, 2), sourceItem, 2, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可关联数量不足")
                .hasMessageContaining("剩余可用 1 件");
    }

    @Test
    void shouldRejectMissingSourcePurchaseOrderItemIdBeforeAvailabilityMath() {
        PurchaseInboundAllocationService service =
                new PurchaseInboundAllocationService(mock(PurchaseInboundItemRepository.class));
        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setQuantity(6);
        PurchaseInboundAllocationService.AllocationContext context =
                new PurchaseInboundAllocationService.AllocationContext(Map.of(), new java.util.HashMap<>());

        assertThatThrownBy(() -> service.validateAvailableQuantity(itemRequest(null, 1), sourceItem, 3, context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行来源采购订单明细不能为空");
    }

    @Test
    void shouldTreatNullQuantitiesAsZeroWhenValidatingAvailableQuantity() {
        PurchaseInboundAllocationService service =
                new PurchaseInboundAllocationService(mock(PurchaseInboundItemRepository.class));
        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(null);
        PurchaseInboundAllocationService.AllocationContext context =
                new PurchaseInboundAllocationService.AllocationContext(Map.of(), new java.util.HashMap<>());

        service.validateAvailableQuantity(itemRequest(201L, null), sourceItem, 4, context);

        assertThat(context.requestAllocatedQuantityMap()).containsEntry(201L, 0);
    }

    private PurchaseInboundRequest request(PurchaseInboundItemRequest... items) {
        return new PurchaseInboundRequest(
                "PI-001",
                "PO-001",
                "供应商A",
                "一号库",
                LocalDate.of(2026, 4, 26),
                "理算",
                "草稿",
                null,
                List.of(items)
        );
    }

    private PurchaseInboundItemRequest itemRequest(Long sourcePurchaseOrderItemId, Integer quantity) {
        return new PurchaseInboundItemRequest(
                null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                sourcePurchaseOrderItemId, "一号库", "理算", "B1", quantity, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                null, null, null,
                new BigDecimal("4000.00"), new BigDecimal("1600.00")
        );
    }
}
