package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseInboundServiceTest {

    @BeforeEach
    void setUpIdGenerator() {
        ReflectionTestUtils.invokeMethod(new SnowflakeIdGenerator(0L), "registerInstance");
    }

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
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseInboundService service = new PurchaseInboundService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                materialCategoryRepository,
                purchaseInboundItemRepository,
                purchaseOrderRepository,
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
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary allocationSummary =
                mock(PurchaseInboundItemRepository.PurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(allocationSummary.getTotalQuantity()).thenReturn(3L);
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                any()
        )).thenReturn(List.of(allocationSummary));
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                any()
        )).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response());

        PurchaseInboundResponse response = service.create(request);

        assertThat(response.inboundNo()).isEqualTo("PI-001");
        verify(purchaseOrderItemQueryService).findActiveByIdIn(List.of(201L));
        verify(purchaseInboundItemRepository).summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                any()
        );
    }

    @Test
    void shouldUseWeighWeightAndWriteBackPurchaseOrderAdjustmentWhenCategoryRequiresWeigh() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = new PurchaseInboundService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                materialCategoryRepository,
                purchaseInboundItemRepository,
                purchaseOrderRepository,
                purchaseOrderItemQueryService,
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrder sourceOrderReference = new PurchaseOrder();
        sourceOrderReference.setId(301L);
        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setPurchaseOrder(sourceOrderReference);
        sourceItem.setQuantity(10);
        sourceItem.setPieceWeightTon(new BigDecimal("0.100"));
        sourceItem.setUnitPrice(new BigDecimal("4000.00"));
        sourceItem.setWeightTon(new BigDecimal("1.000"));
        sourceItem.setAmount(new BigDecimal("4000.00"));

        PurchaseOrder loadedPurchaseOrder = new PurchaseOrder();
        loadedPurchaseOrder.setId(301L);
        PurchaseOrderItem loadedSourceItem = new PurchaseOrderItem();
        loadedSourceItem.setId(201L);
        loadedSourceItem.setPurchaseOrder(loadedPurchaseOrder);
        loadedSourceItem.setQuantity(10);
        loadedSourceItem.setPieceWeightTon(new BigDecimal("0.100"));
        loadedSourceItem.setUnitPrice(new BigDecimal("4000.00"));
        loadedSourceItem.setWeightTon(new BigDecimal("1.000"));
        loadedSourceItem.setAmount(new BigDecimal("4000.00"));
        loadedPurchaseOrder.getItems().add(loadedSourceItem);

        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-002",
                "PO-001",
                "供应商A",
                null,
                LocalDate.of(2026, 4, 26),
                null,
                "草稿",
                null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "过磅", "B1", 4, "件",
                        new BigDecimal("0.100"), 1, null,
                        new BigDecimal("0.430"), null, null,
                        new BigDecimal("4000.00"), null
                ))
        );
        MaterialCategory category = purchaseWeighCategory("盘螺");

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-002")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺"))).thenReturn(List.of(category));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(loadedPurchaseOrder));
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response());

        service.create(request);

        var inboundCaptor = forClass(PurchaseInbound.class);
        verify(repository).save(inboundCaptor.capture());
        PurchaseInboundItem savedItem = inboundCaptor.getValue().getItems().get(0);
        assertThat(inboundCaptor.getValue().getWarehouseName()).isEqualTo("一号库");
        assertThat(inboundCaptor.getValue().getSettlementMode()).isEqualTo("过磅");
        assertThat(savedItem.getSettlementMode()).isEqualTo("过磅");
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("0.430");
        assertThat(savedItem.getWeighWeightTon()).isEqualByComparingTo("0.430");
        assertThat(savedItem.getWeightAdjustmentTon()).isEqualByComparingTo("0.030");
        assertThat(savedItem.getWeightAdjustmentAmount()).isEqualByComparingTo("120.00");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("1720.00");
        assertThat(loadedSourceItem.getWeightTon()).isEqualByComparingTo("1.030");
        assertThat(loadedSourceItem.getAmount()).isEqualByComparingTo("4120.00");
        assertThat(loadedPurchaseOrder.getTotalWeight()).isEqualByComparingTo("1.030");
        assertThat(loadedPurchaseOrder.getTotalAmount()).isEqualByComparingTo("4120.00");
        verify(purchaseOrderRepository).saveAll(any());
    }

    @Test
    void shouldRejectPurchaseInboundWhenWeighRequiredCategoryMissingWeighWeight() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = new PurchaseInboundService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                materialCategoryRepository,
                purchaseInboundItemRepository,
                purchaseOrderRepository,
                purchaseOrderItemQueryService,
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(10);
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-003",
                "PO-001",
                "供应商A",
                null,
                LocalDate.of(2026, 4, 26),
                null,
                "草稿",
                null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "过磅", "B1", 4, "件",
                        new BigDecimal("0.100"), 1, null,
                        null, null, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-003")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺"))).thenReturn(List.of(purchaseWeighCategory("盘螺")));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行需填写过磅重量");
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
                mock(MaterialCategoryRepository.class),
                mock(PurchaseInboundItemRepository.class),
                mock(PurchaseOrderRepository.class),
                mock(PurchaseOrderItemQueryService.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );
    }

    private MaterialCategory purchaseWeighCategory(String categoryName) {
        MaterialCategory category = new MaterialCategory();
        category.setCategoryName(categoryName);
        category.setPurchaseWeighRequired(Boolean.TRUE);
        return category;
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
                null,
                LocalDate.of(2026, 4, 26),
                null,
                "草稿",
                null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "理算", "B1", 4, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                        null, null, null,
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
