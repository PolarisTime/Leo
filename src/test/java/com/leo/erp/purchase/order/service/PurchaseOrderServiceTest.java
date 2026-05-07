package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceTest {

    @BeforeEach
    void setUpIdGenerator() {
        ReflectionTestUtils.invokeMethod(new SnowflakeIdGenerator(0L), "registerInstance");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldLoadAllocatedQuantitiesOnlyForCurrentOrderItemsWhenShowingDetail() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                salesOrderItemQueryService,
                pieceWeightService,
                workflowTransitionGuard
        );

        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDate.of(2026, 4, 26));
        order.setBuyerName("李四");
        order.setTotalWeight(new BigDecimal("2.000"));
        order.setTotalAmount(new BigDecimal("8000.00"));
        order.setStatus("草稿");
        order.setRemark(null);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(7L);
        item.setLineNo(1);
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
        item.setPurchaseOrder(order);
        order.getItems().add(item);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(mapper.toResponse(order)).thenReturn(new PurchaseOrderResponse(
                1L, "PO-001", "供应商A", LocalDate.of(2026, 4, 26), "李四",
                new BigDecimal("2.000"), new BigDecimal("8000.00"), "草稿", null, List.of()
        ));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of(7L, 4L));
        when(salesOrderItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L), null))
                .thenReturn(Map.of(7L, 3L));
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of(7L, new BigDecimal("0.700")));

        PurchaseOrderResponse response = service.detail(1L);

        assertThat(response.items()).singleElement().satisfies(detailItem ->
                assertThat(detailItem.remainingQuantity()).isEqualTo(6)
        );
        assertThat(response.items()).singleElement().satisfies(detailItem ->
                assertThat(detailItem.salesRemainingQuantity()).isEqualTo(7)
        );
        assertThat(response.items()).singleElement().satisfies(detailItem ->
                assertThat(detailItem.salesRemainingWeightTon()).isEqualByComparingTo("0.700")
        );
        verify(purchaseInboundItemQueryService).summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L));
        verify(salesOrderItemQueryService).summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L), null);
    }

    @Test
    void shouldPreserveExistingItemIdWhenUpdatingOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                salesOrderItemQueryService,
                pieceWeightService,
                workflowTransitionGuard
        );

        PurchaseOrder order = buildOrder();
        PurchaseOrderItem existingItem = buildItem(11L, order);
        order.getItems().add(existingItem);
        PurchaseOrderRequest request = buildRequest(11L, "草稿");

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("草稿"));

        service.update(1L, request);

        assertThat(order.getItems()).singleElement()
                .extracting(PurchaseOrderItem::getId)
                .isEqualTo(11L);
        verify(idGenerator, never()).nextId();
    }

    @Test
    void shouldCheckAuditPermissionWhenCreatingAuditedOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                salesOrderItemQueryService,
                pieceWeightService,
                workflowTransitionGuard
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(false);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", new Material()));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("已审核"));

        service.create(buildRequest(null, "已审核"));

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "purchase-orders",
                null,
                "已审核",
                "已审核",
                "完成采购"
        );
    }

    @Test
    void shouldRejectDeletingAuditedOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                salesOrderItemQueryService,
                pieceWeightService,
                workflowTransitionGuard
        );
        PurchaseOrder order = buildOrder();
        order.setStatus("已审核");

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.delete(1L))
                .hasMessageContaining("当前单据状态为「已审核」，不能删除");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectSupplierNameMissingFromMasterDataWhenCreatingOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                salesOrderItemQueryService,
                pieceWeightService,
                workflowTransitionGuard
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(false);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(buildRequest(null, "草稿")))
                .hasMessageContaining("供应商不存在");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldBuildImportCandidatesWithUsageSpecificRemainingQuantity() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                salesOrderItemQueryService,
                pieceWeightService,
                workflowTransitionGuard
        );

        PurchaseOrder order = buildOrder();
        PurchaseOrderItem item1 = buildItem(11L, order);
        item1.setQuantity(10);
        PurchaseOrderItem item2 = buildItem(12L, order);
        item2.setQuantity(6);
        item2.setLineNo(2);
        order.getItems().add(item1);
        order.getItems().add(item2);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(repository.findByIdInAndDeletedFlagFalse(List.of(1L))).thenReturn(List.of(order));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L, 12L)))
                .thenReturn(Map.of(11L, 4L, 12L, 1L));
        when(salesOrderItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L, 12L), null))
                .thenReturn(Map.of(11L, 7L, 12L, 3L));

        var inboundPage = service.importCandidates(PageQuery.of(0, 20, null, null), "", "purchase-inbound");
        var salesPage = service.importCandidates(PageQuery.of(0, 20, null, null), "", "sales-order");

        assertThat(inboundPage.getContent()).singleElement().satisfies(candidate ->
                assertThat(candidate.importableQuantity()).isEqualTo(11)
        );
        assertThat(salesPage.getContent()).singleElement().satisfies(candidate ->
                assertThat(candidate.importableQuantity()).isEqualTo(6)
        );
        assertThat(salesPage.getContent()).singleElement().satisfies(candidate ->
                assertThat(candidate.status()).isEqualTo("草稿")
        );
        verify(repository, times(2)).findByIdInAndDeletedFlagFalse(List.of(1L));
        verify(purchaseInboundItemQueryService).summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L, 12L));
        verify(salesOrderItemQueryService).summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L, 12L), null);
    }

    @Test
    void shouldSearchNormallyWhenAdminViewsDeletedRecordsAndBaseSpecIsNull() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                salesOrderItemQueryService,
                pieceWeightService,
                workflowTransitionGuard
        );
        PurchaseOrder order = buildOrder();

        when(systemSwitchService.shouldAdminSeeDeletedRecords()).thenReturn(true);
        when(systemSwitchService.getHiddenAuditedStatuses()).thenReturn(Set.of());
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(mapper.toResponse(order)).thenReturn(summaryResponse("草稿"));
        ReflectionTestUtils.setField(service, "systemSwitchService", systemSwitchService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        List<PurchaseOrderResponse> response = service.search("", 200);

        assertThat(response).singleElement().satisfies(item ->
                assertThat(item.orderNo()).isEqualTo("PO-001")
        );
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    private PurchaseOrder buildOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDate.of(2026, 4, 26));
        order.setBuyerName("李四");
        order.setTotalWeight(new BigDecimal("2.000"));
        order.setTotalAmount(new BigDecimal("8000.00"));
        order.setStatus("草稿");
        return order;
    }

    private PurchaseOrderItem buildItem(Long id, PurchaseOrder order) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(order);
        item.setLineNo(1);
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

    private PurchaseOrderRequest buildRequest(Long itemId, String status) {
        return new PurchaseOrderRequest(
                "PO-001",
                "供应商A",
                LocalDate.of(2026, 4, 26),
                "李四",
                status,
                null,
                List.of(new PurchaseOrderItemRequest(
                        itemId,
                        "M1",
                        "宝钢",
                        "螺纹钢",
                        "HRB400",
                        "18",
                        "12m",
                        "吨",
                        "一号库",
                        "B1",
                        10,
                        "支",
                        new BigDecimal("0.100"),
                        1,
                        new BigDecimal("1.000"),
                        new BigDecimal("4000.00"),
                        new BigDecimal("4000.00")
                ))
        );
    }

    private Supplier supplier(String supplierName) {
        Supplier supplier = new Supplier();
        supplier.setSupplierName(supplierName);
        return supplier;
    }

    private PurchaseOrderResponse summaryResponse(String status) {
        return new PurchaseOrderResponse(
                1L,
                "PO-001",
                "供应商A",
                LocalDate.of(2026, 4, 26),
                "李四",
                new BigDecimal("1.000"),
                new BigDecimal("4000.00"),
                status,
                null,
                List.of()
        );
    }
}
