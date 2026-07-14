package com.leo.erp.sales.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOrderDownstreamMutationGuardTest {

    @Test
    void shouldLockSourceLinesAndRejectMutationWhenActiveOutboundExists() {
        SalesOutboundRepository outboundRepository = mock(SalesOutboundRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderDownstreamMutationGuard guard =
                new SalesOrderDownstreamMutationGuard(outboundRepository, freightBillRepository, lockService);
        SalesOrder order = orderWithItem(31L);
        when(outboundRepository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(List.of(31L), null))
                .thenReturn(List.of(mock(SalesOutbound.class)));

        assertThatThrownBy(() -> guard.assertMutable(order, "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售出库")
                .hasMessageContaining("不能反审核");

        InOrder flow = inOrder(lockService, outboundRepository);
        flow.verify(lockService).lockTradeItemSources(List.of(), List.of(), List.of(31L));
        flow.verify(outboundRepository)
                .findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(List.of(31L), null);
    }

    @Test
    void shouldRejectMutationWhenActiveFreightBillExistsBeforeOutboundGeneration() {
        SalesOutboundRepository outboundRepository = mock(SalesOutboundRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderDownstreamMutationGuard guard =
                new SalesOrderDownstreamMutationGuard(outboundRepository, freightBillRepository, lockService);
        SalesOrder order = orderWithItem(31L);
        when(freightBillRepository.findOccupiedSourceSalesOrderIds(List.of(1L), null))
                .thenReturn(List.of(1L));

        assertThatThrownBy(() -> guard.assertMutable(order, "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单")
                .hasMessageContaining("不能反审核");

        InOrder flow = inOrder(lockService, freightBillRepository);
        flow.verify(lockService).lockTradeItemSources(List.of(), List.of(), List.of(31L));
        flow.verify(freightBillRepository).findOccupiedSourceSalesOrderIds(List.of(1L), null);
        verifyNoInteractions(outboundRepository);
    }

    @Test
    void shouldCheckDownstreamWhenSourceLineQuantityChanges() {
        SalesOutboundRepository outboundRepository = mock(SalesOutboundRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderDownstreamMutationGuard guard =
                new SalesOrderDownstreamMutationGuard(outboundRepository, freightBillRepository, lockService);
        SalesOrder order = orderWithItem(31L);
        when(outboundRepository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(List.of(31L), null))
                .thenReturn(List.of(mock(SalesOutbound.class)));

        assertThatThrownBy(() -> guard.assertSourceLineMutationAllowed(
                order,
                List.of(itemRequest(31L, 2)),
                "修改"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售出库")
                .hasMessageContaining("不能修改");

        verify(lockService).lockTradeItemSources(List.of(), List.of(), List.of(31L));
    }

    @Test
    void shouldAllowHeaderOnlyUpdateWithoutCheckingDownstream() {
        SalesOutboundRepository outboundRepository = mock(SalesOutboundRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderDownstreamMutationGuard guard =
                new SalesOrderDownstreamMutationGuard(outboundRepository, freightBillRepository, lockService);
        SalesOrder order = orderWithItem(31L);

        guard.assertSourceLineMutationAllowed(order, List.of(itemRequest(31L, 1)), "修改");

        verify(lockService, never()).lockTradeItemSources(List.of(), List.of(), List.of(31L));
        verifyNoInteractions(outboundRepository);
    }

    private SalesOrder orderWithItem(Long itemId) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(itemId);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialId(101L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourceInboundItemId(401L);
        item.setSourcePurchaseOrderItemId(501L);
        item.setWarehouseId(201L);
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.10000000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("0.10000000"));
        item.setUnitPrice(new BigDecimal("4500.00"));
        item.setAmount(new BigDecimal("450.00"));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private SalesOrderItemRequest itemRequest(Long itemId, Integer quantity) {
        return new SalesOrderItemRequest(
                itemId,
                101L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                401L,
                501L,
                201L,
                "一号库",
                "B1",
                quantity,
                "件",
                new BigDecimal("0.10000000"),
                1,
                new BigDecimal("0.10000000"),
                new BigDecimal("4500.00"),
                new BigDecimal("450.00")
        );
    }
}
