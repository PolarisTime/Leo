package com.leo.erp.purchase.inbound.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.purchase.inbound.service.InboundItemMapper;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.error.BusinessException;
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
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
        var itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        var inboundItemMapper = stubbedInboundItemMapper();
        PurchaseInboundService service = newService(repository, mapper, itemAllocationRepo);

        PurchaseInbound inbound = inbound();
        PurchaseInboundItem item = inboundItem(101L, inbound, 10);
        inbound.getItems().add(item);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(inbound));
        when(mapper.toResponse(inbound)).thenReturn(response());
        when(itemAllocationRepo.summarizeSalesByInboundItems(List.of(101L), null))
                .thenReturn(List.of(allocationProjection(101L, 4L)));

        PurchaseInboundResponse response = service.detail(1L);

        assertThat(response.items()).singleElement().satisfies(detailItem ->
                assertThat(detailItem.remainingQuantity()).isEqualTo(6)
        );
        verify(itemAllocationRepo).summarizeSalesByInboundItems(List.of(101L), null);
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
        var itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        var inboundItemMapper = stubbedInboundItemMapper();
        PurchaseInboundService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository),
                itemAllocationRepo,
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInboundRequest request = request();
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem();

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
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
        when(purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
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
    void shouldRejectInboundFromUnauditedPurchaseOrderItem() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem();
        sourceItem.getPurchaseOrder().setStatus("草稿");

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单未审核");
    }

    @Test
    void shouldRejectInboundWhenSourcePurchaseOrderItemFieldsMismatch() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem();
        sourceItem.setMaterialCode("M2");

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单明细物料编码与请求不一致");
    }

    @Test
    void shouldRejectInboundWhenSourcePurchaseOrderItemPiecesPerBundleMismatch() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem();
        sourceItem.setPiecesPerBundle(2);

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单明细每捆支数与请求不一致");
    }

    @Test
    void shouldRejectInboundWhenSourcePurchaseOrderSupplierMismatch() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );
        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setSupplierName("供应商B");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder);

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单供应商与请求不一致");
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
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrder sourceOrderReference = new PurchaseOrder();
        sourceOrderReference.setId(301L);
        PurchaseOrderItem sourceItem = sourcePurchaseOrderWeighItem(sourceOrderReference);
        sourceOrderReference.getItems().add(sourceItem);

        PurchaseOrder loadedPurchaseOrder = sourceOrderReference;
        PurchaseOrderItem loadedSourceItem = sourceItem;

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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺"))).thenReturn(List.of(category));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(loadedPurchaseOrder));
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
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
        assertThat(savedItem.getPieceWeightTon()).isEqualByComparingTo("0.10750000");
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("0.400");
        assertThat(savedItem.getWeighWeightTon()).isEqualByComparingTo("0.430");
        assertThat(savedItem.getWeightAdjustmentTon()).isEqualByComparingTo("0.030");
        assertThat(savedItem.getWeightAdjustmentAmount()).isEqualByComparingTo("120.00");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("1600.00");
        assertThat(loadedSourceItem.getPieceWeightTon()).isEqualByComparingTo("0.100");
        assertThat(loadedSourceItem.getWeightTon()).isEqualByComparingTo("1.07500000");
        assertThat(loadedSourceItem.getAmount()).isEqualByComparingTo("4300.00");
        assertThat(loadedPurchaseOrder.getTotalWeight()).isEqualByComparingTo("1.07500000");
        assertThat(loadedPurchaseOrder.getTotalAmount()).isEqualByComparingTo("4300.00");
        verify(purchaseOrderRepository).saveAll(any());
    }

    @Test
    void shouldRejectManualInboundWithoutSourcePurchaseOrderItem() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-004",
                null,
                "供应商A",
                null,
                LocalDate.of(2026, 4, 30),
                null,
                "草稿",
                null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        null, "一号库", "过磅", "B1", 3, "件",
                        new BigDecimal("4.700"), 1, new BigDecimal("14.258"),
                        new BigDecimal("14.258"), null, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-004")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单明细不能为空");
    }

    @Test
    void shouldWriteBackActualWeighTotalWhenPurchaseOrderItemFullyInbound() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrder sourceOrderReference = new PurchaseOrder();
        sourceOrderReference.setId(301L);
        PurchaseOrderItem sourceItem = sourcePurchaseOrderWeighItem(sourceOrderReference);
        sourceItem.setQuantity(7);
        sourceItem.setPieceWeightTon(new BigDecimal("2.100"));
        sourceItem.setUnitPrice(new BigDecimal("3000.00"));
        sourceItem.setWeightTon(new BigDecimal("14.700"));
        sourceItem.setAmount(new BigDecimal("44100.00"));

        PurchaseOrder loadedPurchaseOrder = new PurchaseOrder();
        loadedPurchaseOrder.setId(301L);
        PurchaseOrderItem loadedSourceItem = new PurchaseOrderItem();
        loadedSourceItem.setId(201L);
        loadedSourceItem.setPurchaseOrder(loadedPurchaseOrder);
        loadedSourceItem.setQuantity(7);
        loadedSourceItem.setPieceWeightTon(new BigDecimal("2.100"));
        loadedSourceItem.setUnitPrice(new BigDecimal("3000.00"));
        loadedSourceItem.setWeightTon(new BigDecimal("14.700"));
        loadedSourceItem.setAmount(new BigDecimal("44100.00"));
        loadedPurchaseOrder.getItems().add(loadedSourceItem);

        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-005",
                "PO-001",
                "供应商A",
                null,
                LocalDate.of(2026, 4, 30),
                null,
                "草稿",
                null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "过磅", "B1", 7, "件",
                        new BigDecimal("2.100"), 1, new BigDecimal("14.258"),
                        new BigDecimal("14.258"), null, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-005")).thenReturn(false);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺"))).thenReturn(List.of(purchaseWeighCategory("盘螺")));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(loadedPurchaseOrder));
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response());

        service.create(request);

        assertThat(loadedSourceItem.getPieceWeightTon()).isEqualByComparingTo("2.100");
        assertThat(loadedSourceItem.getWeightTon()).isEqualByComparingTo("14.258");
        assertThat(loadedSourceItem.getAmount()).isEqualByComparingTo("42774.00");
        assertThat(loadedPurchaseOrder.getTotalWeight()).isEqualByComparingTo("14.258");
        assertThat(loadedPurchaseOrder.getTotalAmount()).isEqualByComparingTo("42774.00");
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
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderWeighItem();
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
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

    @Test
    void shouldRejectDuplicateInboundNoWhenCreating() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundService service = newService(repository, mock(PurchaseInboundMapper.class),
                mock(ItemAllocationNativeRepository.class));

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购入库单号已存在");
    }

    @Test
    void shouldReturnPieceWeightsDistributedEvenly() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("PI-001");
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setQuantity(4);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setPurchaseInbound(inbound);
        inbound.getItems().add(item);

        when(inboundItemRepository.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(item));

        List<com.leo.erp.purchase.order.web.dto.PieceWeightResponse> pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).hasSize(4);
        assertThat(pieceWeights).extracting(pw -> pw.weightTon())
                .allMatch(w -> ((java.math.BigDecimal) w).compareTo(new BigDecimal("0.250")) == 0);
    }

    @Test
    void shouldReturnEmptyPieceWeightsWhenQuantityIsZero() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                mock(PurchaseInboundRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setQuantity(0);
        item.setWeightTon(BigDecimal.ZERO);

        when(inboundItemRepository.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(item));

        List<com.leo.erp.purchase.order.web.dto.PieceWeightResponse> pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).isEmpty();
    }

    @Test
    void shouldThrowWhenGettingPieceWeightsForNonexistentItem() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                mock(PurchaseInboundRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        when(inboundItemRepository.findAllActiveByIdIn(List.of(999L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getPieceWeights(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购入库明细不存在");
    }

    @Test
    void shouldSearchWithWeightSummary() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound inbound = inbound();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(inbound)));
        when(mapper.toResponse(inbound)).thenReturn(response());
        when(inboundItemRepository.summarizeWeightByInboundIds(List.of(1L))).thenReturn(List.of());

        List<PurchaseInboundResponse> results = service.search("PI", 100);

        assertThat(results).singleElement().satisfies(r ->
                assertThat(r.inboundNo()).isEqualTo("PI-001")
        );
    }

    private PurchaseInboundService newService(PurchaseInboundRepository repository,
                                              PurchaseInboundMapper mapper,
                                              ItemAllocationNativeRepository itemAllocationRepo) {
        return service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                itemAllocationRepo,
                stubbedInboundItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
    }

    private PurchaseInboundService service(PurchaseInboundRepository repository,
                                           SnowflakeIdGenerator idGenerator,
                                           PurchaseInboundMapper purchaseInboundMapper,
                                           TradeItemMaterialSupport tradeItemMaterialSupport,
                                           WarehouseSelectionSupport warehouseSelectionSupport,
                                           PurchaseInboundWeightSettlementService weightSettlementService,
                                           PurchaseInboundWeightWriteBackService weightWriteBackService,
                                           PurchaseInboundCompletionSyncService completionSyncService,
                                           PurchaseInboundItemRepository purchaseInboundItemRepository,
                                           PurchaseInboundSourceValidator sourceValidator,
                                           ItemAllocationNativeRepository itemAllocationRepo,
                                           InboundItemMapper inboundItemMapper,
                                           WorkflowTransitionGuard workflowTransitionGuard) {
        return new PurchaseInboundService(
                repository,
                idGenerator,
                purchaseInboundMapper,
                new PurchaseInboundApplyService(
                        tradeItemMaterialSupport,
                        sourceValidator,
                        weightSettlementService,
                        weightWriteBackService,
                        inboundItemMapper
                ),
                new PurchaseInboundDeleteService(sourceValidator, weightWriteBackService),
                completionSyncService,
                new PurchaseInboundResponseAssembler(
                        purchaseInboundMapper,
                        purchaseInboundItemRepository,
                        itemAllocationRepo
                ),
                new PurchaseInboundPieceWeightService(new PurchaseInboundItemQueryService(purchaseInboundItemRepository, null)),
                workflowTransitionGuard
        );
    }

    private PurchaseInboundSourceValidator sourceValidator(PurchaseOrderItemQueryService purchaseOrderItemQueryService) {
        return sourceValidator(purchaseOrderItemQueryService, mock(PurchaseInboundItemRepository.class));
    }

    private PurchaseInboundSourceValidator sourceValidator(PurchaseOrderItemQueryService purchaseOrderItemQueryService,
                                                           PurchaseInboundItemRepository purchaseInboundItemRepository) {
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                any(),
                any()
        )).thenReturn(List.of());
        return new PurchaseInboundSourceValidator(
                purchaseOrderItemQueryService,
                new PurchaseInboundAllocationService(purchaseInboundItemRepository)
        );
    }

    private PurchaseInboundWeightWriteBackService weightWriteBackService(
            PurchaseInboundItemRepository purchaseInboundItemRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService
    ) {
        return new PurchaseInboundWeightWriteBackService(
                purchaseInboundItemRepository,
                purchaseOrderRepository,
                purchaseOrderItemPieceWeightService
        );
    }

    private PurchaseInboundCompletionSyncService completionSyncService(
            PurchaseInboundRepository repository,
            PurchaseInboundSourceValidator sourceValidator
    ) {
        return new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator,
                sourceValidator.allocationService()
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

    private PurchaseOrderItem sourcePurchaseOrderItem() {
        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setId(301L);
        sourceOrder.setOrderNo("PO-001");
        sourceOrder.setStatus("已审核");
        return sourcePurchaseOrderItem(sourceOrder);
    }

    private PurchaseOrderItem sourcePurchaseOrderItem(PurchaseOrder sourceOrder) {
        if (sourceOrder.getOrderNo() == null) {
            sourceOrder.setOrderNo("PO-001");
        }
        if (sourceOrder.getStatus() == null) {
            sourceOrder.setStatus("已审核");
        }
        if (sourceOrder.getSupplierName() == null) {
            sourceOrder.setSupplierName("供应商A");
        }
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
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
        item.setQuantity(10);
        item.setQuantityUnit("支");
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4000.00"));
        return item;
    }

    private PurchaseOrderItem sourcePurchaseOrderWeighItem() {
        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setId(301L);
        sourceOrder.setOrderNo("PO-001");
        sourceOrder.setStatus("已审核");
        return sourcePurchaseOrderWeighItem(sourceOrder);
    }

    private PurchaseOrderItem sourcePurchaseOrderWeighItem(PurchaseOrder sourceOrder) {
        PurchaseOrderItem item = sourcePurchaseOrderItem(sourceOrder);
        item.setCategory("盘螺");
        item.setQuantityUnit("件");
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
                "草稿", null, null, null, List.of()
        );
    }

    private ItemAllocationNativeRepository.AllocationProjection allocationProjection(Long sourceItemId, Long totalQuantity) {
        return new ItemAllocationNativeRepository.AllocationProjection() {
            @Override public Long getSourceItemId() { return sourceItemId; }
            @Override public Long getTotalQuantity() { return totalQuantity; }
            @Override public java.math.BigDecimal getTotalWeightTon() { return java.math.BigDecimal.ZERO; }
        };
    }

    @Test
    void shouldRejectUpdateWithDuplicateInboundNo() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundService service = newService(repository, mock(PurchaseInboundMapper.class),
                mock(ItemAllocationNativeRepository.class));

        PurchaseInbound inbound = inbound();
        inbound.setId(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(inbound));
        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-002")).thenReturn(true);

        PurchaseInboundRequest updateRequest = new PurchaseInboundRequest(
                "PI-002", "PO-001", "供应商A", "一号库",
                LocalDate.of(2026, 4, 26), "理算", "草稿", null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        null, "一号库", "理算", "B1", 4, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                        null, null, null,
                        new BigDecimal("4000.00"), new BigDecimal("1600.00")
                ))
        );

        assertThatThrownBy(() -> service.update(1L, updateRequest))
                .isInstanceOf(RuntimeException.class)
                ;
    }

    @Test
    void shouldPageInboundsWithWeightSummary() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound inbound = inbound();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(inbound)));
        when(mapper.toResponse(inbound)).thenReturn(response());
        when(inboundItemRepository.summarizeWeightByInboundIds(List.of(1L))).thenReturn(List.of());

        var page = service.page(
                com.leo.erp.common.api.PageQuery.of(0, 20, null, null),
                com.leo.erp.common.api.PageFilter.of("PI", null, null, null)
        );

        assertThat(page.getContent()).singleElement().satisfies(r ->
                assertThat(r.inboundNo()).isEqualTo("PI-001")
        );
    }

    @Test
    void shouldUpdateInboundSuccessfully() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound existingInbound = inbound();
        existingInbound.setId(1L);
        PurchaseInboundItem existingItem = inboundItem(101L, existingInbound, 4);
        existingInbound.getItems().add(existingItem);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existingInbound));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        PurchaseOrderItem sourceOrderItem = sourcePurchaseOrderItem();
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceOrderItem));
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(any(), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(any(), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response());

        PurchaseInboundResponse response = service.update(1L, request());

        assertThat(response.inboundNo()).isEqualTo("PI-001");
        verify(repository).save(any());
    }

    @Test
    void shouldDeleteDraftInboundAndWriteBackWeights() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, pieceWeightService),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound existingInbound = inbound();
        existingInbound.setId(1L);
        PurchaseInboundItem existingItem = inboundItem(101L, existingInbound, 4);
        existingItem.setSourcePurchaseOrderItemId(201L);
        existingInbound.getItems().add(existingItem);

        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setId(301L);
        sourceOrder.setOrderNo("PO-001");
        sourceOrder.setStatus("已审核");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder);
        sourceOrder.getItems().add(sourceItem);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existingInbound));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(sourceOrder));
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), eq(1L)))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), eq(1L)))
                .thenReturn(List.of());

        service.delete(1L);

        assertThat(existingInbound.isDeletedFlag()).isTrue();
        verify(repository).save(existingInbound);
        verify(purchaseOrderRepository).saveAll(any());
    }

    @Test
    void shouldRejectInboundWhenCategoryRequiresWeighButSettlementIsNotWeigh() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderWeighItem();
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-003", "PO-001", "供应商A", null,
                LocalDate.of(2026, 4, 26), null, "草稿", null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "理算", "B1", 4, "件",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                        null, null, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-003")).thenReturn(false);
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺")))
                .thenReturn(List.of(purchaseWeighCategory("盘螺")));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品类别需按过磅入库，请将本行结算方式改为过磅");
    }

    @Test
    void shouldRejectNegativeWeighWeight() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderWeighItem();
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-003", "PO-001", "供应商A", null,
                LocalDate.of(2026, 4, 26), null, "草稿", null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "过磅", "B1", 4, "件",
                        new BigDecimal("0.100"), 1, null,
                        new BigDecimal("-0.500"), null, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-003")).thenReturn(false);
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺")))
                .thenReturn(List.of(purchaseWeighCategory("盘螺")));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过磅重量不能小于0");
    }

    @Test
    void shouldRejectZeroWeighWeightWithNonZeroQuantity() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderWeighItem();
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-003", "PO-001", "供应商A", null,
                LocalDate.of(2026, 4, 26), null, "草稿", null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "过磅", "B1", 4, "件",
                        new BigDecimal("0.100"), 1, null,
                        BigDecimal.ZERO, null, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-003")).thenReturn(false);
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺")))
                .thenReturn(List.of(purchaseWeighCategory("盘螺")));
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过磅重量必须大于0");
    }

    @Test
    void shouldRejectWhenLineSettlementModeAndHeaderSettlementModeBothNull() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(mock(PurchaseInboundItemRepository.class), mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                mock(PurchaseInboundItemRepository.class),
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-003", null, "供应商A", null,
                LocalDate.of(2026, 4, 26), null, "草稿", null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        201L, "一号库", null, "B1", 4, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                        null, null, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-003")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourcePurchaseOrderItem()));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行请选择结算方式");
    }

    @Test
    void shouldReturnPieceWeightsWithResidualDistribution() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                mock(PurchaseInboundRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("PI-001");
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setQuantity(3);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setPurchaseInbound(inbound);

        when(inboundItemRepository.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(item));

        var pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).hasSize(3);
        BigDecimal totalWeight = pieceWeights.stream()
                .map(com.leo.erp.purchase.order.web.dto.PieceWeightResponse::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalWeight).isEqualByComparingTo("1.000");
    }

    @Test
    void shouldReturnPieceWeightsWithWeighWeightTon() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                mock(PurchaseInboundRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("PI-001");
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setQuantity(3);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setWeighWeightTon(new BigDecimal("1.050"));
        item.setPurchaseInbound(inbound);

        when(inboundItemRepository.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(item));

        var pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).hasSize(3);
        BigDecimal totalWeight = pieceWeights.stream()
                .map(com.leo.erp.purchase.order.web.dto.PieceWeightResponse::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalWeight).isEqualByComparingTo("1.050");
    }

    @Test
    void shouldReturnPieceWeightsWithNullQuantity() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                mock(PurchaseInboundRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setQuantity(null);
        item.setWeightTon(new BigDecimal("1.000"));

        when(inboundItemRepository.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(item));

        var pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).isEmpty();
    }

    @Test
    void shouldReturnPieceWeightsWithNullPurchaseInbound() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                mock(PurchaseInboundRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setQuantity(2);
        item.setWeightTon(new BigDecimal("0.500"));
        item.setPurchaseInbound(null);

        when(inboundItemRepository.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(item));

        var pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).hasSize(2);
        assertThat(pieceWeights).allSatisfy(pw -> assertThat(pw.salesOrderNo()).isEmpty());
    }

    @Test
    void shouldCompletePurchaseOrderWhenAllInboundsCompletedWithinTolerance() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, pieceWeightService),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrder sourceOrder = new PurchaseOrder();
        sourceOrder.setId(301L);
        sourceOrder.setOrderNo("PO-001");
        sourceOrder.setStatus("已审核");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder);
        sourceOrder.getItems().add(sourceItem);

        PurchaseInbound completedInbound = new PurchaseInbound();
        completedInbound.setId(1L);
        completedInbound.setInboundNo("PI-001");
        completedInbound.setPurchaseOrderNo("PO-001");
        completedInbound.setStatus("草稿");
        PurchaseInboundItem completedItem = new PurchaseInboundItem();
        completedItem.setId(101L);
        completedItem.setSourcePurchaseOrderItemId(201L);
        completedItem.setQuantity(10);
        completedItem.setWeightTon(new BigDecimal("1.000"));
        completedItem.setUnitPrice(new BigDecimal("4000.00"));
        completedItem.setAmount(new BigDecimal("4000.00"));
        completedInbound.getItems().add(completedItem);

        PurchaseInboundRequest updateRequest = new PurchaseInboundRequest(
                "PI-001", "PO-001", "供应商A", "一号库",
                LocalDate.of(2026, 4, 26), "理算", "已审核", null,
                List.of(new PurchaseInboundItemRequest(
                        101L, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "理算", "B1", 10, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("1.000"),
                        null, null, null,
                        new BigDecimal("4000.00"), new BigDecimal("4000.00")
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(completedInbound));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(sourceOrder));
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(repository.findByPurchaseOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(List.of(completedInbound));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response());

        service.update(1L, updateRequest);

        assertThat(completedInbound.getStatus()).isEqualTo("完成入库");
        assertThat(sourceOrder.getStatus()).isEqualTo("完成采购");
        verify(purchaseOrderRepository).saveAll(any());
    }

    @Test
    void shouldRejectDirectCompleteInboundFromUpdateRequest() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, purchaseOrderRepository, mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound inbound = inbound();
        inbound.setId(1L);
        PurchaseInboundItem item = inboundItem(101L, inbound, 4);
        inbound.getItems().add(item);

        PurchaseInboundRequest updateRequest = new PurchaseInboundRequest(
                "PI-001", "PO-001", "供应商A", "一号库",
                LocalDate.of(2026, 4, 26), "理算", "完成入库", null,
                List.of(new PurchaseInboundItemRequest(
                        101L, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "理算", "B1", 4, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                        null, null, null,
                        new BigDecimal("4000.00"), new BigDecimal("1600.00")
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(inbound));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        PurchaseOrderItem sourceOrderItem = sourcePurchaseOrderItem();
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceOrderItem));
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)), any()
        )).thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)), any()
        )).thenReturn(List.of());
        when(purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)), any()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.update(1L, updateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
    }

    @Test
    void shouldSearchWithWeightSummaryData() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundMapper mapper = mock(PurchaseInboundMapper.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class)),
                weightWriteBackService(inboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(mock(PurchaseInboundRepository.class), sourceValidator(mock(PurchaseOrderItemQueryService.class))),
                inboundItemRepository,
                sourceValidator(mock(PurchaseOrderItemQueryService.class)),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseInbound inbound = inbound();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(inbound)));
        when(mapper.toResponse(inbound)).thenReturn(response());

        PurchaseInboundItemRepository.InboundWeightSummary weightSummary =
                mock(PurchaseInboundItemRepository.InboundWeightSummary.class);
        when(weightSummary.getInboundId()).thenReturn(1L);
        when(weightSummary.getTotalWeighWeightTon()).thenReturn(new BigDecimal("1.050"));
        when(weightSummary.getTotalWeightAdjustmentTon()).thenReturn(new BigDecimal("0.050"));
        when(inboundItemRepository.summarizeWeightByInboundIds(List.of(1L))).thenReturn(List.of(weightSummary));

        List<PurchaseInboundResponse> results = service.search("PI", 100);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.totalWeighWeightTon()).isEqualByComparingTo("1.050");
            assertThat(r.totalWeightAdjustmentTon()).isEqualByComparingTo("0.050");
        });
    }

    @Test
    void shouldRejectAllocatedQuantityExceedingAvailable() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundItemRepository purchaseInboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseInboundMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService(purchaseInboundItemRepository, mock(PurchaseOrderRepository.class), mock(PurchaseOrderItemPieceWeightService.class)),
                completionSyncService(repository, sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository)),
                purchaseInboundItemRepository,
                sourceValidator(purchaseOrderItemQueryService, purchaseInboundItemRepository),
                mock(ItemAllocationNativeRepository.class),
                stubbedInboundItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem();

        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary allocationSummary =
                mock(PurchaseInboundItemRepository.PurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(allocationSummary.getTotalQuantity()).thenReturn(8L);

        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-003", "PO-001", "供应商A", null,
                LocalDate.of(2026, 4, 26), "理算", "草稿", null,
                List.of(new PurchaseInboundItemRequest(
                        null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        201L, "一号库", "理算", "B1", 5, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.500"),
                        null, null, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-003")).thenReturn(false);
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)), any()
        )).thenReturn(List.of(allocationSummary));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可关联数量不足");
    }

    private InboundItemMapper stubbedInboundItemMapper() {
        InboundItemMapper mapper = mock(InboundItemMapper.class);
        when(mapper.applyItemFields(any(), any(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            PurchaseInboundItemRequest source = invocation.getArgument(1);
            PurchaseInboundItem item = invocation.getArgument(2);
            int lineNo = invocation.getArgument(3);
            InboundItemMapper.ItemMappingContext ctx = invocation.getArgument(6);
            WeightSettlementResult ws = ctx.weightSettlement();

            item.setLineNo(lineNo);
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
            item.setWarehouseName(source.warehouseName() != null ? source.warehouseName() : "一号库");
            item.setSettlementMode(source.settlementMode() != null ? source.settlementMode() : "理算");
            item.setBatchNo(source.batchNo());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(source.quantityUnit());
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setUnitPrice(source.unitPrice());

            item.setPieceWeightTon(ws.pieceWeightTon());
            item.setWeightTon(ws.weightTon());
            item.setWeighWeightTon(ws.weighWeightTon());
            item.setWeightAdjustmentTon(ws.weightAdjustmentTon());
            item.setWeightAdjustmentAmount(ws.weightAdjustmentAmount());
            item.setAmount(ws.weightTon().multiply(source.unitPrice() != null ? source.unitPrice() : java.math.BigDecimal.ZERO));

            return new InboundItemMapper.ItemMappingResult(
                    "PO-001",
                    item.getWarehouseName(),
                    ws.weightTon(),
                    item.getAmount(),
                    source.sourcePurchaseOrderItemId(),
                    ws.weightAdjustmentTon(),
                    ws.weighWeightTon(),
                    source.quantity(),
                    ws.calculatedWeightTon()
            );
        });
        return mapper;
    }

    private Map<String, TradeMaterialSnapshot> materialMap(String materialCode) {
        return Map.of(materialCode, new TradeMaterialSnapshot(materialCode, Boolean.FALSE));
    }
}
