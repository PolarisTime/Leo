package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderServiceTest {

    @Test
    void shouldLoadOnlyRequestedInboundItemsAndAllocationsWhenCreatingOrder() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                salesOrderItemRepository,
                warehouseSelectionSupport
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "PI-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(
                        new SalesOrderItemRequest(
                                "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                                101L, "一号库", "B1", 4, "支",
                                new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                                new BigDecimal("4000.00"), new BigDecimal("1600.00")
                        )
                )
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setWarehouseName("一号库");
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(101L);
        inboundItem.setPurchaseInbound(inbound);
        inboundItem.setQuantity(10);

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(inboundItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(101L), 1L))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-001", "PI-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("0.400"), new BigDecimal("1600.00"), "草稿", null, List.of()
        ));

        SalesOrderResponse response = service.create(request);

        assertThat(response.orderNo()).isEqualTo("SO-001");
        verify(purchaseInboundItemQueryService).findAllActiveByIdIn(List.of(101L));
        verify(salesOrderItemRepository).summarizeAllocatedQuantityBySourceInboundItemIds(List.of(101L), 1L);
    }
}
