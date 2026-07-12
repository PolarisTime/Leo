package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSelectionSupportTestDoubles;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseInboundApplyServiceTest {

    @Test
    void shouldApplyItemsAndDeriveHeaderValuesFromSourceLines() {
        TestFixture fixture = fixture();
        PurchaseInboundApplyService service = fixture.service;

        PurchaseOrder order1 = purchaseOrder(301L, "PO-001");
        PurchaseOrder order2 = purchaseOrder(302L, "PO-002");
        PurchaseOrderItem sourceItem1 = sourcePurchaseOrderItem(order1, 201L, "M1", "B1", 10,
                new BigDecimal("0.100"), new BigDecimal("4000.00"));
        PurchaseOrderItem sourceItem2 = sourcePurchaseOrderItem(order2, 202L, "M2", "B2", 6,
                new BigDecimal("0.200"), new BigDecimal("5000.00"));
        order1.getItems().add(sourceItem1);
        order2.getItems().add(sourceItem2);

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-001",
                null,
                "供应商A",
                null,
                LocalDate.of(2026, 4, 26),
                null,
                "草稿",
                null,
                List.of(
                        itemRequest(201L, "M1", "一号库", "理算", "B1", 4,
                                new BigDecimal("0.100"), new BigDecimal("4000.00")),
                        itemRequest(202L, "M2", "二号库", "月结", "B2", 3,
                                new BigDecimal("0.200"), new BigDecimal("5000.00"))
                )
        );

        when(fixture.materialSupport.loadMaterialMap(List.of("M1", "M2"))).thenReturn(Map.of(
                "M1", new TradeMaterialSnapshot("M1", true),
                "M2", new TradeMaterialSnapshot("M2", true)
        ));
        when(fixture.materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(fixture.materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(fixture.materialSupport.normalizeBatchNo(any(), eq("B2"), eq(2), eq(true))).thenReturn("B2");
        when(fixture.warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(fixture.warehouseSelectionSupport.normalizeWarehouseName("二号库", 2, true)).thenReturn("二号库");
        when(fixture.materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        when(fixture.purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L, 202L)))
                .thenReturn(List.of(sourceItem1, sourceItem2));
        when(fixture.inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L, 202L)),
                eq(1L)
        )).thenReturn(List.of());
        when(fixture.inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L, 202L)),
                eq(1L)
        )).thenReturn(List.of());
        when(fixture.purchaseOrderRepository.findByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(order1, order2));

        AtomicLong nextId = new AtomicLong(101L);
        service.applyItems(inbound, request, nextId::getAndIncrement);

        assertThat(inbound.getPurchaseOrderNo()).isEqualTo("PO-001, PO-002");
        assertThat(inbound.getWarehouseName()).isEqualTo("一号库");
        assertThat(inbound.getSettlementMode()).isEqualTo("混合");
        assertThat(inbound.getTotalWeight()).isEqualByComparingTo("1.000");
        assertThat(inbound.getTotalAmount()).isEqualByComparingTo("4600.00");
        assertThat(inbound.getItems()).hasSize(2);
        assertThat(inbound.getItems()).extracting(PurchaseInboundItem::getId)
                .containsExactly(101L, 102L);
        assertThat(inbound.getItems()).extracting(PurchaseInboundItem::getLineNo)
                .containsExactly(1, 2);
        assertThat(inbound.getItems().get(0).getAmount()).isEqualByComparingTo("1600.00");
        assertThat(inbound.getItems().get(1).getAmount()).isEqualByComparingTo("3000.00");
        verify(fixture.purchaseOrderRepository).saveAll(any());
    }

    @Test
    void shouldFallbackHeaderValuesWhenLineWarehouseAndSettlementAreBlank() {
        TestFixture fixture = fixture();
        PurchaseOrder order = purchaseOrder(303L, "PO-003");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(order, 203L, "M3", "B3", 5,
                new BigDecimal("0.300"), new BigDecimal("6000.00"));
        sourceItem.setWarehouseName("总仓");
        order.setSettlementCompanyId(501L);
        order.setSettlementCompanyName("结算主体A");
        order.getItems().add(sourceItem);
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(2L);
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-002",
                "PO-FALLBACK",
                "供应商A",
                "  总仓  ",
                LocalDate.of(2026, 4, 27),
                "  月结  ",
                "草稿",
                null,
                List.of(itemRequest(203L, "M3", " ", null, "B3", 2,
                        new BigDecimal("0.300"), new BigDecimal("6000.00")))
        );

        stubSingleLine(fixture, "M3", "B3", "总仓", sourceItem, order);

        fixture.service.applyItems(inbound, request, new AtomicLong(201L)::getAndIncrement);

        assertThat(inbound.getPurchaseOrderNo()).isEqualTo("PO-003");
        assertThat(inbound.getWarehouseName()).isEqualTo("总仓");
        assertThat(inbound.getSettlementMode()).isEqualTo("月结");
        assertThat(inbound.getSettlementCompanyId()).isEqualTo(501L);
        assertThat(inbound.getSettlementCompanyName()).isEqualTo("结算主体A");
        assertThat(inbound.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getWarehouseName()).isEqualTo("总仓");
            assertThat(item.getSettlementMode()).isEqualTo("  月结  ");
            assertThat(item.getSettlementCompanyId()).isEqualTo(501L);
            assertThat(item.getSettlementCompanyName()).isEqualTo("结算主体A");
        });
    }

    @Test
    void shouldClearHeaderSettlementCompanyWhenSourceOrderHasNoEffectiveCompany() {
        TestFixture fixture = fixture();
        PurchaseOrder order = purchaseOrder(304L, "PO-004");
        order.setSettlementCompanyId(null);
        order.setSettlementCompanyName(" ");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(order, 204L, "M4", "B4", 4,
                new BigDecimal("0.250"), new BigDecimal("5000.00"));
        sourceItem.setWarehouseName("一号库");
        order.getItems().add(sourceItem);
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(3L);
        inbound.setSettlementCompanyId(999L);
        inbound.setSettlementCompanyName("旧结算主体");
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-003",
                null,
                "供应商A",
                "一号库",
                LocalDate.of(2026, 4, 28),
                "理算",
                "草稿",
                null,
                List.of(itemRequest(204L, "M4", "一号库", null, "B4", 1,
                        new BigDecimal("0.250"), new BigDecimal("5000.00")))
        );

        stubSingleLine(fixture, "M4", "B4", "一号库", sourceItem, order);

        fixture.service.applyItems(inbound, request, new AtomicLong(301L)::getAndIncrement);

        assertThat(inbound.getSettlementCompanyId()).isNull();
        assertThat(inbound.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldMarkHeaderSettlementCompanyAsMixedWhenSourceOrdersDiffer() {
        TestFixture fixture = fixture();
        PurchaseOrder order1 = purchaseOrder(305L, "PO-005");
        order1.setSettlementCompanyId(601L);
        order1.setSettlementCompanyName("结算主体A");
        PurchaseOrder order2 = purchaseOrder(306L, "PO-006");
        order2.setSettlementCompanyId(602L);
        order2.setSettlementCompanyName("结算主体B");
        PurchaseOrderItem sourceItem1 = sourcePurchaseOrderItem(order1, 205L, "M5", "B5", 4,
                new BigDecimal("0.250"), new BigDecimal("5000.00"));
        sourceItem1.setWarehouseName("一号库");
        PurchaseOrderItem sourceItem2 = sourcePurchaseOrderItem(order2, 206L, "M6", "B6", 4,
                new BigDecimal("0.500"), new BigDecimal("3000.00"));
        order1.getItems().add(sourceItem1);
        order2.getItems().add(sourceItem2);
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(4L);
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-004",
                null,
                "供应商A",
                "一号库",
                LocalDate.of(2026, 4, 29),
                null,
                "草稿",
                null,
                List.of(
                        itemRequest(205L, "M5", "一号库", "理算", "B5", 1,
                                new BigDecimal("0.250"), new BigDecimal("5000.00")),
                        itemRequest(206L, "M6", "二号库", "理算", "B6", 1,
                                new BigDecimal("0.500"), new BigDecimal("3000.00"))
                )
        );

        when(fixture.materialSupport.loadMaterialMap(List.of("M5", "M6"))).thenReturn(Map.of(
                "M5", new TradeMaterialSnapshot("M5", true),
                "M6", new TradeMaterialSnapshot("M6", true)
        ));
        when(fixture.materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(fixture.materialSupport.normalizeBatchNo(any(), eq("B5"), eq(1), eq(true))).thenReturn("B5");
        when(fixture.materialSupport.normalizeBatchNo(any(), eq("B6"), eq(2), eq(true))).thenReturn("B6");
        when(fixture.warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(fixture.warehouseSelectionSupport.normalizeWarehouseName("二号库", 2, true)).thenReturn("二号库");
        when(fixture.materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        when(fixture.purchaseOrderItemQueryService.findActiveByIdIn(List.of(205L, 206L)))
                .thenReturn(List.of(sourceItem1, sourceItem2));
        when(fixture.inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(205L, 206L)),
                eq(4L)
        )).thenReturn(List.of());
        when(fixture.inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(205L, 206L)),
                eq(4L)
        )).thenReturn(List.of());
        when(fixture.purchaseOrderRepository.findByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(order1, order2));

        fixture.service.applyItems(inbound, request, new AtomicLong(401L)::getAndIncrement);

        assertThat(inbound.getSettlementCompanyId()).isNull();
        assertThat(inbound.getSettlementCompanyName()).isEqualTo("多结算主体");
    }

    @Test
    void shouldReturnWithoutWeightWriteBackWhenSourceIdsAreEmpty() {
        TestFixture fixture = fixture();
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(5L);
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-005",
                "PO-MANUAL",
                "供应商A",
                "一号库",
                LocalDate.of(2026, 4, 30),
                "理算",
                "草稿",
                null,
                List.of()
        );
        when(fixture.materialSupport.loadMaterialMap(List.of())).thenReturn(Map.of());
        when(fixture.materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of())).thenReturn(List.of());

        fixture.service.applyItems(inbound, request, new AtomicLong(501L)::getAndIncrement);

        assertThat(inbound.getPurchaseOrderNo()).isEqualTo("PO-MANUAL");
        assertThat(inbound.getWarehouseName()).isEqualTo("一号库");
        assertThat(inbound.getSettlementMode()).isEqualTo("理算");
        assertThat(inbound.getSettlementCompanyId()).isNull();
        assertThat(inbound.getSettlementCompanyName()).isNull();
        assertThat(inbound.getTotalWeight()).isEqualByComparingTo("0.000");
        assertThat(inbound.getTotalAmount()).isEqualByComparingTo("0.00");
        verifyNoInteractions(fixture.purchaseOrderItemQueryService);
        verifyNoInteractions(fixture.purchaseOrderRepository);
    }

    @Test
    void shouldFallbackOrderNoAndKeepFirstWarehouseWhenSourceOrderNoMissing() {
        TestFixture fixture = fixture();
        PurchaseOrder order = purchaseOrder(307L, null);
        order.setSettlementCompanyId(null);
        order.setSettlementCompanyName("结算主体A");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(order, 207L, "M7", "B7", 5,
                new BigDecimal("0.300"), new BigDecimal("6000.00"));
        sourceItem.setWarehouseName("明细仓");
        order.getItems().add(sourceItem);
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(6L);
        PurchaseInboundItem existingItem = new PurchaseInboundItem();
        existingItem.setId(901L);
        existingItem.setSourcePurchaseOrderItemId(null);
        inbound.getItems().add(existingItem);
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-006",
                "PO-FALLBACK",
                "供应商A",
                " ",
                LocalDate.of(2026, 5, 1),
                null,
                "草稿",
                null,
                List.of(itemRequest(207L, "M7", "明细仓", "理算", "B7", 2,
                        new BigDecimal("0.300"), new BigDecimal("6000.00")))
        );

        stubSingleLine(fixture, "M7", "B7", "明细仓", sourceItem, order);

        fixture.service.applyItems(inbound, request, new AtomicLong(601L)::getAndIncrement);

        assertThat(inbound.getPurchaseOrderNo()).isEqualTo("PO-FALLBACK");
        assertThat(inbound.getWarehouseName()).isEqualTo("明细仓");
        assertThat(inbound.getSettlementMode()).isEqualTo("理算");
        assertThat(inbound.getSettlementCompanyId()).isNull();
        assertThat(inbound.getSettlementCompanyName()).isEqualTo("结算主体A");
        assertThat(inbound.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(601L);
            assertThat(item.getSourcePurchaseOrderItemId()).isEqualTo(207L);
        });
    }

    @Test
    void shouldUseDefaultSettlementModeAndSkipAccumulatorWhenMapperReturnsNoSourceLine() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        PurchaseInboundSourceValidator sourceValidator = mock(PurchaseInboundSourceValidator.class);
        PurchaseInboundWeightSettlementService weightSettlementService = mock(PurchaseInboundWeightSettlementService.class);
        PurchaseInboundWeightWriteBackService weightWriteBackService = mock(PurchaseInboundWeightWriteBackService.class);
        InboundItemMapper inboundItemMapper = mock(InboundItemMapper.class);
        PurchaseInboundApplyService service = new PurchaseInboundApplyService(
                materialSupport,
                sourceValidator,
                weightSettlementService,
                weightWriteBackService,
                inboundItemMapper
        );
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(7L);
        PurchaseInboundItem existingItem = new PurchaseInboundItem();
        existingItem.setId(902L);
        existingItem.setSourcePurchaseOrderItemId(null);
        inbound.getItems().add(existingItem);
        PurchaseInboundItemRequest itemRequest = itemRequest(207L, "M8", "明细仓", null, "B8", 2,
                new BigDecimal("0.300"), new BigDecimal("6000.00"));
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-007",
                "PO-FALLBACK",
                "供应商A",
                null,
                LocalDate.of(2026, 5, 2),
                null,
                "草稿",
                null,
                List.of(itemRequest)
        );
        WeightSettlementResult weightSettlement = new WeightSettlementResult(
                new BigDecimal("0.600"),
                new BigDecimal("0.600"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.300"),
                new BigDecimal("0.600")
        );
        PurchaseInboundSourceValidator.SourceValidationContext sourceContext =
                new PurchaseInboundSourceValidator.SourceValidationContext(
                        List.of(207L),
                        List.of(207L),
                        Map.of(),
                        new PurchaseInboundAllocationService.AllocationContext(Map.of(), new java.util.HashMap<>())
                );
        when(materialSupport.loadMaterialMap(List.of("M8")))
                .thenReturn(Map.of("M8", new TradeMaterialSnapshot("M8", true)));
        when(materialSupport.normalizeMaterialCode("M8", 1)).thenReturn("M8");
        when(sourceValidator.prepareContext(request, 7L, List.of())).thenReturn(sourceContext);
        when(weightSettlementService.loadPurchaseWeighCategoryRules(request)).thenReturn(Map.of());
        when(weightSettlementService.resolveLineSettlementMode(itemRequest, request, 1)).thenReturn("理算");
        when(weightSettlementService.resolveWeightSettlement(itemRequest, 1, Map.of(), "理算"))
                .thenReturn(weightSettlement);
        when(inboundItemMapper.applyItemFields(
                eq(inbound),
                eq(itemRequest),
                any(PurchaseInboundItem.class),
                eq(1),
                eq("M8"),
                any(),
                eq(Map.of()),
                any()
        )).thenAnswer(invocation -> {
            PurchaseInboundItem item = invocation.getArgument(2);
            item.setSettlementMode(null);
            item.setSettlementCompanyId(null);
            item.setSettlementCompanyName(null);
            return new InboundItemMapper.ItemMappingResult(
                    null,
                    "明细仓",
                    new BigDecimal("0.600"),
                    new BigDecimal("3600.00"),
                    null,
                    BigDecimal.ZERO,
                    new BigDecimal("0.600"),
                    2,
                    new BigDecimal("0.600")
            );
        });

        service.applyItems(inbound, request, new AtomicLong(701L)::getAndIncrement);

        assertThat(inbound.getPurchaseOrderNo()).isEqualTo("PO-FALLBACK");
        assertThat(inbound.getWarehouseName()).isEqualTo("明细仓");
        assertThat(inbound.getSettlementMode()).isEqualTo("理算");
        assertThat(inbound.getTotalWeight()).isEqualByComparingTo("0.600");
        assertThat(inbound.getTotalAmount()).isEqualByComparingTo("3600.00");
        verify(weightWriteBackService).writeBackPurchaseOrderWeights(List.of(207L), 7L, Map.of(), Map.of());
    }

    private PurchaseOrder purchaseOrder(Long id, String orderNo) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus("已审核");
        order.setSupplierName("供应商A");
        order.setSettlementCompanyId(500L);
        order.setSettlementCompanyName("默认结算主体");
        return order;
    }

    private PurchaseOrderItem sourcePurchaseOrderItem(PurchaseOrder order,
                                                       Long id,
                                                       String materialCode,
                                                       String batchNo,
                                                       Integer quantity,
                                                       BigDecimal pieceWeightTon,
                                                       BigDecimal unitPrice) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(order);
        item.setMaterialCode(materialCode);
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseName(id.equals(201L) ? "一号库" : "二号库");
        item.setBatchNo(batchNo);
        item.setQuantity(quantity);
        item.setQuantityUnit("支");
        item.setPieceWeightTon(pieceWeightTon);
        item.setPiecesPerBundle(1);
        item.setWeightTon(pieceWeightTon.multiply(BigDecimal.valueOf(quantity)));
        item.setUnitPrice(unitPrice);
        item.setAmount(item.getWeightTon().multiply(unitPrice));
        return item;
    }

    private PurchaseInboundItemRequest itemRequest(Long sourcePurchaseOrderItemId,
                                                   String materialCode,
                                                   String warehouseName,
                                                   String settlementMode,
                                                   String batchNo,
                                                   Integer quantity,
                                                   BigDecimal pieceWeightTon,
                                                   BigDecimal unitPrice) {
        BigDecimal weightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity));
        return new PurchaseInboundItemRequest(
                null,
                materialCode,
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                sourcePurchaseOrderItemId,
                warehouseName,
                settlementMode,
                batchNo,
                quantity,
                "支",
                pieceWeightTon,
                1,
                weightTon,
                null,
                null,
                null,
                unitPrice,
                weightTon.multiply(unitPrice)
        );
    }

    private TestFixture fixture() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        WarehouseSelectionSupportTestDoubles.stubWarehouseResolution(warehouseSelectionSupport);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundWeightWriteBackService weightWriteBackService = new PurchaseInboundWeightWriteBackService(
                inboundItemRepository,
                purchaseOrderRepository,
                mock(PurchaseOrderItemPieceWeightService.class)
        );
        PurchaseInboundApplyService service = new PurchaseInboundApplyService(
                materialSupport,
                new PurchaseInboundSourceValidator(
                        purchaseOrderItemQueryService,
                        new PurchaseInboundAllocationService(inboundItemRepository)
                ),
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService,
                new InboundItemMapper(materialSupport, warehouseSelectionSupport)
        );
        return new TestFixture(
                service,
                materialSupport,
                warehouseSelectionSupport,
                inboundItemRepository,
                purchaseOrderItemQueryService,
                purchaseOrderRepository,
                materialCategoryRepository
        );
    }

    private void stubSingleLine(TestFixture fixture,
                                String materialCode,
                                String batchNo,
                                String normalizedWarehouseName,
                                PurchaseOrderItem sourceItem,
                                PurchaseOrder order) {
        when(fixture.materialSupport.loadMaterialMap(List.of(materialCode))).thenReturn(Map.of(
                materialCode, new TradeMaterialSnapshot(materialCode, true)
        ));
        when(fixture.materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(fixture.materialSupport.normalizeBatchNo(any(), eq(batchNo), eq(1), eq(true))).thenReturn(batchNo);
        when(fixture.warehouseSelectionSupport.normalizeWarehouseName(any(), eq(1), eq(true)))
                .thenReturn(normalizedWarehouseName);
        when(fixture.materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢")))
                .thenReturn(List.of());
        when(fixture.purchaseOrderItemQueryService.findActiveByIdIn(List.of(sourceItem.getId())))
                .thenReturn(List.of(sourceItem));
        when(fixture.inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(sourceItem.getId())),
                any()
        )).thenReturn(List.of());
        when(fixture.inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(sourceItem.getId())),
                any()
        )).thenReturn(List.of());
        when(fixture.purchaseOrderRepository.findByIdInAndDeletedFlagFalse(anyList()))
                .thenReturn(List.of(order));
    }

    private record TestFixture(
            PurchaseInboundApplyService service,
            TradeItemMaterialSupport materialSupport,
            WarehouseSelectionSupport warehouseSelectionSupport,
            PurchaseInboundItemRepository inboundItemRepository,
            PurchaseOrderItemQueryService purchaseOrderItemQueryService,
            PurchaseOrderRepository purchaseOrderRepository,
            MaterialCategoryRepository materialCategoryRepository
    ) {
    }
}
