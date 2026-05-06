package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderServiceTest {

    @BeforeEach
    void setUpIdGenerator() {
        ReflectionTestUtils.invokeMethod(new SnowflakeIdGenerator(0L), "registerInstance");
    }

    @Test
    void shouldLoadOnlyRequestedInboundItemsAndAllocationsWhenCreatingOrder() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
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
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-001", "PI-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("0.400"), new BigDecimal("1600.00"), "草稿", null, List.of()
        ));

        SalesOrderResponse response = service.create(request);

        assertThat(response.orderNo()).isEqualTo("SO-001");
        verify(purchaseInboundItemQueryService).findAllActiveByIdIn(List.of(101L));
        verify(salesOrderItemRepository).summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any());
    }

    @Test
    void shouldAllocateSalesOrderQuantityAgainstPurchaseOrderItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-003",
                null,
                "PO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        null, 201L, "一号库", "B1", 3, "件",
                        new BigDecimal("0.100"), 1, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(10);
        sourceItem.setWeightTon(new BigDecimal("1.000"));

        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(allocationSummary.getTotalQuantity()).thenReturn(7L);
        when(allocationSummary.getTotalWeightTon()).thenReturn(new BigDecimal("0.699"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-003")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of(allocationSummary));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightService.allocateForSalesOrderItem(eq(sourceItem), eq(3), any(), eq(1)))
                .thenReturn(new BigDecimal("0.301"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-003", null, "PO-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("0.301"), new BigDecimal("1204.00"), "草稿", null, List.of()
        ));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedOrder = orderCaptor.getValue();
        var savedItem = savedOrder.getItems().get(0);
        assertThat(savedOrder.getPurchaseOrderNo()).isEqualTo("PO-001");
        assertThat(savedItem.getSourcePurchaseOrderItemId()).isEqualTo(201L);
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("0.301");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("1204.00");
        InOrder saveFlow = inOrder(repository, pieceWeightService);
        saveFlow.verify(repository).saveAndFlush(any());
        saveFlow.verify(pieceWeightService).allocateForSalesOrderItem(eq(sourceItem), eq(3), any(), eq(1));
        saveFlow.verify(repository).save(any());
    }

    @Test
    void shouldUsePurchaseOrderActualAverageWeightForPartialSalesAllocation() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-004",
                null,
                "PO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 201L, "一号库", "B1", 2, "件",
                        new BigDecimal("2.400"), 1, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(10);
        sourceItem.setWeightTon(new BigDecimal("23.550"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-004")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightService.allocateForSalesOrderItem(eq(sourceItem), eq(2), any(), eq(1)))
                .thenReturn(new BigDecimal("4.710"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-004", null, "PO-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("4.710"), new BigDecimal("14130.00"), "草稿", null, List.of()
        ));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedItem = orderCaptor.getValue().getItems().get(0);
        assertThat(savedItem.getPieceWeightTon()).isEqualByComparingTo("2.355");
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("4.710");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("14130.00");
    }

    @Test
    void shouldUsePurchaseOrderResidualAverageWeightWhenExactlyRepresentable() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-004A",
                null,
                "PO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 201L, "一号库", "B1", 2, "件",
                        new BigDecimal("2.249"), 1, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(5);
        sourceItem.setWeightTon(new BigDecimal("11.243"));

        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(allocationSummary.getTotalQuantity()).thenReturn(3L);
        when(allocationSummary.getTotalWeightTon()).thenReturn(new BigDecimal("6.747"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-004A")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of(allocationSummary));
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(201L)))
                .thenReturn(Map.of(201L, new BigDecimal("4.496")));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightService.allocateForSalesOrderItem(eq(sourceItem), eq(2), any(), eq(1)))
                .thenReturn(new BigDecimal("4.496"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-004A", null, "PO-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("4.496"), new BigDecimal("13488.00"), "草稿", null, List.of()
        ));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedItem = orderCaptor.getValue().getItems().get(0);
        assertThat(savedItem.getPieceWeightTon()).isEqualByComparingTo("2.248");
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("4.496");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("13488.00");
    }

    @Test
    void shouldKeepSourceAverageWeightWhenResidualAverageCannotBeRepresentedExactly() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-004B",
                null,
                "PO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 201L, "一号库", "B1", 2, "件",
                        new BigDecimal("0.333"), 1, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(3);
        sourceItem.setWeightTon(new BigDecimal("1.000"));

        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(allocationSummary.getTotalQuantity()).thenReturn(1L);
        when(allocationSummary.getTotalWeightTon()).thenReturn(new BigDecimal("0.333"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-004B")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of(allocationSummary));
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(201L)))
                .thenReturn(Map.of(201L, new BigDecimal("0.667")));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightService.allocateForSalesOrderItem(eq(sourceItem), eq(2), any(), eq(1)))
                .thenReturn(new BigDecimal("0.667"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-004B", null, "PO-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("0.667"), new BigDecimal("2001.00"), "草稿", null, List.of()
        ));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedItem = orderCaptor.getValue().getItems().get(0);
        assertThat(savedItem.getPieceWeightTon()).isEqualByComparingTo("0.333");
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("0.667");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("2001.00");
    }

    @Test
    void shouldUseAllocatedPieceWeightForSinglePiecePurchaseOrderSalesAllocation() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-004C",
                null,
                "PO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 201L, "一号库", "B1", 1, "件",
                        new BigDecimal("2.249"), 1, null,
                        new BigDecimal("3111.00"), null
                ))
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(5);
        sourceItem.setWeightTon(new BigDecimal("11.243"));

        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(allocationSummary.getTotalQuantity()).thenReturn(3L);
        when(allocationSummary.getTotalWeightTon()).thenReturn(new BigDecimal("6.747"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-004C")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of(allocationSummary));
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(201L)))
                .thenReturn(Map.of(201L, new BigDecimal("4.496")));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightService.allocateForSalesOrderItem(eq(sourceItem), eq(1), any(), eq(1)))
                .thenReturn(new BigDecimal("2.248"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-004C", null, "PO-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("2.248"), new BigDecimal("6993.53"), "草稿", null, List.of()
        ));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedItem = orderCaptor.getValue().getItems().get(0);
        assertThat(savedItem.getPieceWeightTon()).isEqualByComparingTo("2.248");
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("2.248");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("6993.53");
    }

    @Test
    void shouldUsePurchaseOrderActualTotalWeightForFullSalesAllocation() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-005",
                null,
                "PO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 201L, "一号库", "B1", 7, "件",
                        new BigDecimal("2.037"), 1, null,
                        new BigDecimal("3000.00"), null
                ))
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(7);
        sourceItem.setWeightTon(new BigDecimal("14.258"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-005")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightService.allocateForSalesOrderItem(eq(sourceItem), eq(7), any(), eq(1)))
                .thenReturn(new BigDecimal("14.258"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-005", null, "PO-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("14.258"), new BigDecimal("42774.00"), "草稿", null, List.of()
        ));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedOrder = orderCaptor.getValue();
        var savedItem = savedOrder.getItems().get(0);
        assertThat(savedItem.getPieceWeightTon()).isEqualByComparingTo("2.037");
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("14.258");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("42774.00");
        assertThat(savedOrder.getTotalWeight()).isEqualByComparingTo("14.258");
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("42774.00");
    }

    @Test
    void shouldUseActualWeighResidualForLastSalesAllocation() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-002",
                "PI-002",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "18", "12m", "吨",
                        101L, "一号库", "B1", 1, "件",
                        new BigDecimal("0.108"), 1, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setWarehouseName("一号库");
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(101L);
        inboundItem.setPurchaseInbound(inbound);
        inboundItem.setQuantity(4);
        inboundItem.setPieceWeightTon(new BigDecimal("0.108"));
        inboundItem.setWeightTon(new BigDecimal("0.432"));
        inboundItem.setWeighWeightTon(new BigDecimal("0.430"));

        SalesOrderItemRepository.SourceInboundAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourceInboundAllocationSummary.class);
        when(allocationSummary.getSourceInboundItemId()).thenReturn(101L);
        when(allocationSummary.getTotalQuantity()).thenReturn(3L);
        when(allocationSummary.getTotalWeightTon()).thenReturn(new BigDecimal("0.324"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-002")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(inboundItem));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any()))
                .thenReturn(List.of(allocationSummary));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-002", "PI-002", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("0.106"), new BigDecimal("424.00"), "草稿", null, List.of()
        ));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedOrder = orderCaptor.getValue();
        var savedItem = savedOrder.getItems().get(0);
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("0.106");
        assertThat(savedItem.getAmount()).isEqualByComparingTo("424.00");
        assertThat(savedOrder.getTotalWeight()).isEqualByComparingTo("0.106");
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("424.00");
    }

    @Test
    void shouldAllowReverseAuditWhenOnlySalesOrderStatusChanges() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                workflowTransitionGuard
        );

        var order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-REV-001");
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 4, 26));
        order.setSalesName("张三");
        order.setStatus("已审核");
        order.setRemark("备注");
        var item = new com.leo.erp.sales.order.domain.entity.SalesOrderItem();
        item.setId(11L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("8");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(2);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.248"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("4.496"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("13488.00"));
        order.setItems(new java.util.ArrayList<>(List.of(item)));

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-REV-001",
                null,
                null,
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                "草稿",
                "备注",
                List.of(new SalesOrderItemRequest(
                        11L,
                        "M1", "宝钢", "盘螺", "HRB400", "8", "12m", "吨",
                        null, null, "一号库", "B1", 2, "件",
                        new BigDecimal("2.248"), 1, new BigDecimal("4.496"),
                        new BigDecimal("3000.00"), new BigDecimal("13488.00")
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-REV-001", null, null, "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("4.496"), new BigDecimal("13488.00"), "草稿", "备注", List.of()
        ));

        service.update(1L, request);

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "sales-orders",
                "已审核",
                "草稿",
                "已审核",
                "完成销售"
        );
        verify(repository).save(any());
        assertThat(order.getStatus()).isEqualTo("草稿");
    }

    @Test
    void shouldRejectReverseAuditWhenSalesOrderPayloadChangesOtherFields() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                purchaseInboundItemQueryService,
                purchaseOrderItemQueryService,
                pieceWeightService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                workflowTransitionGuard
        );

        var order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-REV-002");
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 4, 26));
        order.setSalesName("张三");
        order.setStatus("已审核");
        var item = new com.leo.erp.sales.order.domain.entity.SalesOrderItem();
        item.setId(11L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("8");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setQuantity(2);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.248"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("4.496"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("13488.00"));
        order.setItems(new java.util.ArrayList<>(List.of(item)));

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-REV-002",
                null,
                null,
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 27),
                "张三",
                "草稿",
                null,
                List.of(new SalesOrderItemRequest(
                        11L,
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, null, "一号库", null, 2, "件",
                        new BigDecimal("2.248"), 1, new BigDecimal("4.496"),
                        new BigDecimal("3000.00"), new BigDecimal("13488.00")
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(1L, request))
                .hasMessageContaining("当前单据状态为「已审核」，不能编辑");
        verify(repository, never()).save(any());
    }
}
