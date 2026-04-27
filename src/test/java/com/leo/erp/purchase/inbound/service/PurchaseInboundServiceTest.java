package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundServiceTest {

    @Test
    void shouldLoadAllocatedQuantitiesOnlyForCurrentInboundItemsWhenShowingDetail() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseInboundService service = newService(repository, mapper, salesOrderItemQueryService);

        PurchaseInbound inbound = inbound();
        PurchaseInboundItem item = inboundItem(101L, inbound, 10);
        inbound.getItems().add(item);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(inbound));
        when(mapper.toResponse(inbound)).thenReturn(response());
        when(salesOrderItemQueryService.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(101L), null))
                .thenReturn(Map.of(101L, 4L));

        PurchaseInboundResponse response = service.detail(1L);

        assertThat(response.items()).singleElement().satisfies(detailItem ->
                assertThat(detailItem.remainingQuantity()).isEqualTo(6)
        );
        verify(salesOrderItemQueryService).summarizeAllocatedQuantityBySourceInboundItemIds(List.of(101L), null);
    }

    @Test
    void shouldLoadOnlyRequestedPurchaseOrderItemsAndAllocationsWhenCreatingInbound() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseInboundService service = new PurchaseInboundService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                purchaseInboundItemRepository,
                purchaseOrderItemQueryService,
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        PurchaseInboundRequest request = request();
        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(10);

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary allocationSummary =
                mock(PurchaseInboundItemRepository.PurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(allocationSummary.getTotalQuantity()).thenReturn(3L);
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(201L),
                1L
        )).thenReturn(List.of(allocationSummary));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response());

        PurchaseInboundResponse response = service.create(request);

        assertThat(response.inboundNo()).isEqualTo("PI-001");
        verify(purchaseOrderItemQueryService).findActiveByIdIn(List.of(201L));
        verify(purchaseInboundItemRepository).summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(201L),
                1L
        );
    }

    private PurchaseInboundService newService(PurchaseInboundRepository repository,
                                              PurchaseInboundMapper mapper,
                                              SalesOrderItemQueryService salesOrderItemQueryService) {
        return new PurchaseInboundService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(PurchaseInboundItemRepository.class),
                mock(PurchaseOrderItemQueryService.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );
    }

    private PurchaseInbound inbound() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("PI-001");
        inbound.setPurchaseOrderNo("PO-001");
        inbound.setSupplierName("供应商A");
        inbound.setWarehouseName("一号库");
        inbound.setInboundDate(LocalDate.of(2026, 4, 26));
        inbound.setSettlementMode("月结");
        inbound.setTotalWeight(new BigDecimal("1.000"));
        inbound.setTotalAmount(new BigDecimal("4000.00"));
        inbound.setStatus("草稿");
        return inbound;
    }

    private PurchaseInboundItem inboundItem(Long id, PurchaseInbound inbound, Integer quantity) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        item.setPurchaseInbound(inbound);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourcePurchaseOrderItemId(201L);
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

    private PurchaseInboundRequest request() {
        return new PurchaseInboundRequest(
                "PI-001",
                "PO-001",
                "供应商A",
                "一号库",
                LocalDate.of(2026, 4, 26),
                "月结",
                "草稿",
                null,
                List.of(new PurchaseInboundItemRequest(
                        "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        201L, null, "B1", 4, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                        new BigDecimal("4000.00"), new BigDecimal("1600.00")
                ))
        );
    }

    private PurchaseInboundResponse response() {
        return new PurchaseInboundResponse(
                1L, "PI-001", "PO-001", "供应商A", "一号库",
                LocalDate.of(2026, 4, 26), "月结",
                new BigDecimal("1.000"), new BigDecimal("4000.00"),
                "草稿", null, List.of()
        );
    }
}
