package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSelectionSupportTestDoubles;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.jdbc.core.JdbcTemplate;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReopenCompletedPurchaseOrderToAudited() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );
        PurchaseOrder order = buildOrder();
        order.setStatus("完成采购");

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(order)).thenReturn(order);
        when(mapper.toResponse(order)).thenReturn(summaryResponse("已审核"));

        service.updateStatus(1L, "已审核");

        assertThat(order.getStatus()).isEqualTo("已审核");
        verify(repository).save(order);
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
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDateTime.of(2026, 4, 26, 0, 0));
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
                1L, "PO-001", "供应商A", LocalDateTime.of(2026, 4, 26, 0, 0), "李四",
                new BigDecimal("2.000"), new BigDecimal("8000.00"), "草稿", null, List.of()
        ));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of(7L, 4L));
        when(itemAllocationRepo.summarizeSalesByPurchaseOrderItems(List.of(7L), null))
                .thenReturn(List.of(allocationProjection(7L, 3L)));
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
        verify(itemAllocationRepo).summarizeSalesByPurchaseOrderItems(List.of(7L), null);
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
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        PurchaseOrderItem existingItem = buildItem(11L, order);
        order.getItems().add(existingItem);
        PurchaseOrderRequest request = buildRequest(11L, "草稿");

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
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
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class)
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(false);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("已审核"));

        service.create(buildRequest(null, "已审核"));

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "purchase-order",
                null,
                "已审核",
                "已审核",
                "完成采购"
        );
    }

    @Test
    void shouldSaveExplicitSettlementCompanyFromRequest() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        CompanySettingService companySettingService = mock(CompanySettingService.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class),
                companySettingService
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-TEST9")).thenReturn(false);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(companySettingService.requireActiveSettlementCompany(9L))
                .thenReturn(companySetting(9L, "TEST9"));
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("草稿"));
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO-TEST9",
                "供应商A",
                LocalDateTime.of(2026, 4, 26, 0, 0),
                "李四",
                9L,
                "草稿",
                "备注",
                List.of(new PurchaseOrderItemRequest(
                        null,
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

        service.create(request);

        ArgumentCaptor<PurchaseOrder> orderCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(repository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getSettlementCompanyId()).isEqualTo(9L);
        assertThat(orderCaptor.getValue().getSettlementCompanyName()).isEqualTo("TEST9");
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
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class)
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
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class)
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(false);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(buildRequest(null, "草稿")))
                ;
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
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        order.setStatus("已审核");
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
        when(itemAllocationRepo.summarizeSalesByPurchaseOrderItems(List.of(11L, 12L), null))
                .thenReturn(List.of(allocationProjection(11L, 7L), allocationProjection(12L, 3L)));

        var inboundPage = service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "purchase-inbound"
        );
        var salesPage = service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "sales-order"
        );

        assertThat(inboundPage.getContent()).singleElement().satisfies(candidate ->
                assertThat(candidate.importableQuantity()).isEqualTo(11)
        );
        assertThat(salesPage.getContent()).singleElement().satisfies(candidate ->
                assertThat(candidate.importableQuantity()).isEqualTo(6)
        );
        assertThat(salesPage.getContent()).singleElement().satisfies(candidate ->
                assertThat(candidate.status()).isEqualTo("已审核")
        );
        verify(purchaseInboundItemQueryService).summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L, 12L));
        verify(itemAllocationRepo).summarizeSalesByPurchaseOrderItems(List.of(11L, 12L), null);
    }

    @Test
    void shouldExposeTotalsOnImportCandidates() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                purchaseInboundItemQueryService,
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        order.setStatus("已审核");
        order.setTotalWeight(new BigDecimal("12.345"));
        order.setTotalAmount(new BigDecimal("67890.12"));
        PurchaseOrderItem item = buildItem(11L, order);
        order.getItems().add(item);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(repository.findByIdInAndDeletedFlagFalse(List.of(1L))).thenReturn(List.of(order));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of());

        var page = service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "purchase-inbound"
        );

        assertThat(page.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.totalWeight()).isEqualByComparingTo("12.345");
            assertThat(candidate.totalAmount()).isEqualByComparingTo("67890.12");
        });
    }

    @Test
    void shouldPageEligiblePurchasePrepaymentCandidatesWithoutAvailabilityAggregation() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository itemAllocationRepository = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                inboundItemQueryService,
                itemAllocationRepository,
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );
        PurchaseOrder completedOrder = buildOrder();
        completedOrder.setStatus("完成采购");
        completedOrder.setTotalAmount(new BigDecimal("67890.12"));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(completedOrder)));

        var page = service.prepaymentCandidates(
                PageQuery.of(0, 15, null, null),
                PageFilter.of(" PO-001 ", "供应商A", 7L, null, null, null)
        );

        assertThat(page.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo(completedOrder.getId());
            assertThat(candidate.status()).isEqualTo("完成采购");
            assertThat(candidate.totalAmount()).isEqualByComparingTo("67890.12");
            assertThat(candidate.importableQuantity()).isNull();
        });
        verify(repository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verify(inboundItemQueryService, never())
                .summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(any());
        verify(itemAllocationRepository, never()).summarizeSalesByPurchaseOrderItems(any(), nullable(Long.class));
    }

    @Test
    void shouldExposeOnlyAuditedPurchaseInboundCandidatesWithRemainingQuantity() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                purchaseInboundItemQueryService,
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder auditedOpen = buildOrder();
        auditedOpen.setStatus("已审核");
        PurchaseOrderItem auditedOpenItem = buildItem(11L, auditedOpen);
        auditedOpen.getItems().add(auditedOpenItem);

        PurchaseOrder auditedFull = buildOrder();
        auditedFull.setId(2L);
        auditedFull.setOrderNo("PO-002");
        auditedFull.setStatus("已审核");
        PurchaseOrderItem auditedFullItem = buildItem(12L, auditedFull);
        auditedFull.getItems().add(auditedFullItem);

        PurchaseOrder draftOpen = buildOrder();
        draftOpen.setId(3L);
        draftOpen.setOrderNo("PO-003");
        draftOpen.setStatus("草稿");
        PurchaseOrderItem draftOpenItem = buildItem(13L, draftOpen);
        draftOpen.getItems().add(draftOpenItem);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(auditedOpen, auditedFull, draftOpen)));
        when(repository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(auditedOpen, auditedFull, draftOpen));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(
                List.of(11L, 12L, 13L)
        )).thenReturn(Map.of(11L, 9L, 12L, 10L));

        var page = service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "purchase-inbound"
        );

        assertThat(page.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo(1L);
            assertThat(candidate.status()).isEqualTo("已审核");
            assertThat(candidate.importableQuantity()).isEqualTo(1);
        });
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldExposeAuditedAndCompletedPurchaseOrdersForSalesOrderImport() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                itemAllocationRepo,
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder auditedOpen = buildOrder();
        auditedOpen.setStatus("已审核");
        PurchaseOrderItem auditedOpenItem = buildItem(11L, auditedOpen);
        auditedOpen.getItems().add(auditedOpenItem);

        PurchaseOrder auditedFull = buildOrder();
        auditedFull.setId(2L);
        auditedFull.setOrderNo("PO-002");
        auditedFull.setStatus("已审核");
        PurchaseOrderItem auditedFullItem = buildItem(12L, auditedFull);
        auditedFull.getItems().add(auditedFullItem);

        PurchaseOrder draftOpen = buildOrder();
        draftOpen.setId(3L);
        draftOpen.setOrderNo("PO-003");
        draftOpen.setStatus("草稿");
        PurchaseOrderItem draftOpenItem = buildItem(13L, draftOpen);
        draftOpen.getItems().add(draftOpenItem);

        PurchaseOrder completedOpen = buildOrder();
        completedOpen.setId(4L);
        completedOpen.setOrderNo("PO-004");
        completedOpen.setStatus("完成采购");
        PurchaseOrderItem completedOpenItem = buildItem(14L, completedOpen);
        completedOpen.getItems().add(completedOpenItem);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(auditedOpen, auditedFull, draftOpen, completedOpen)));
        when(itemAllocationRepo.summarizeSalesByPurchaseOrderItems(List.of(11L, 12L, 13L, 14L), null))
                .thenReturn(List.of(
                        allocationProjection(11L, 9L),
                        allocationProjection(12L, 10L)
                ));

        var page = service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "sales-order"
        );

        assertThat(page.getContent())
                .extracting(
                        PurchaseOrderImportCandidateResponse::id,
                        PurchaseOrderImportCandidateResponse::status,
                        PurchaseOrderImportCandidateResponse::importableQuantity
                )
                .containsExactly(
                        tuple(1L, "已审核", 1),
                        tuple(4L, "完成采购", 10)
                );
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void pageShouldIncludeWholeEndDateWhenFilteringOrderDateTime() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.page(
                PageQuery.of(0, 20, null, null),
                PageFilter.of(null, null, null, null,
                        LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 8))
        );

        ArgumentCaptor<Specification<PurchaseOrder>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        assertOrderDateHalfOpenBoundary(captor.getValue());
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
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        PurchaseOrderService service = service(
                repository,
                idGenerator,
                mapper,
                materialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                workflowTransitionGuard,
                mock(JdbcTemplate.class)
        );
        PurchaseOrder order = buildOrder();

        when(systemSwitchService.shouldAdminSeeDeletedRecords()).thenReturn(true);
        when(systemSwitchService.getHiddenAuditedStatuses()).thenReturn(Set.of());
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(mapper.toResponse(order)).thenReturn(summaryResponse("草稿"));
        ReflectionTestUtils.setField(service, "crudRuntimeSettings", systemSwitchService);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void assertOrderDateHalfOpenBoundary(Specification<PurchaseOrder> specification) {
        Root<PurchaseOrder> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get(anyString())).thenReturn(path);
        when(criteriaBuilder.conjunction()).thenReturn(predicate);
        when(criteriaBuilder.equal(any(Expression.class), any())).thenReturn(predicate);
        when(criteriaBuilder.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.lessThan(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.lessThanOrEqualTo(any(Expression.class), any(LocalDate.class))).thenReturn(predicate);
        when(criteriaBuilder.isFalse(any(Expression.class))).thenReturn(predicate);
        when(criteriaBuilder.and(nullable(Predicate.class), nullable(Predicate.class))).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);
        when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(predicate);

        assertThat(specification.toPredicate(root, query, criteriaBuilder)).isNotNull();

        verify(criteriaBuilder).greaterThanOrEqualTo(path, LocalDateTime.of(2026, 7, 8, 0, 0));
        verify(criteriaBuilder).lessThan(path, LocalDateTime.of(2026, 7, 9, 0, 0));
    }

    private PurchaseOrder buildOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDateTime.of(2026, 4, 26, 0, 0));
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
                LocalDateTime.of(2026, 4, 26, 0, 0),
                "李四",
                1L,
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
        return summaryResponse(status, false);
    }

    private PurchaseOrderResponse summaryResponse(String status, boolean deletedFlag) {
        return new PurchaseOrderResponse(
                1L,
                "PO-001",
                "供应商A",
                LocalDateTime.of(2026, 4, 26, 0, 0),
                "李四",
                null,
                null,
                new BigDecimal("1.000"),
                new BigDecimal("4000.00"),
                status,
                deletedFlag,
                null,
                List.of()
        );
    }

    @Test
    void shouldRejectDuplicateOrderNoWhenCreating() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(buildRequest(null, "草稿")))
                ;
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderService service = service(
                repository, idGenerator, mapper, materialSupport, warehouseSelectionSupport,
                supplierRepository, purchaseInboundItemQueryService,
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(false);
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("草稿"));

        PurchaseOrderResponse response = service.create(buildRequest(null, "草稿"));

        assertThat(response.status()).isEqualTo("草稿");
        verify(repository).save(any());
    }

    @Test
    void shouldDeleteDraftOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        service.delete(1L);

        assertThat(order.isDeletedFlag()).isTrue();
        verify(repository).save(order);
    }

    @Test
    void shouldRejectDeletingNonDraftOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        order.setStatus("已审核");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.delete(1L))
                .hasMessageContaining("当前单据状态为「已审核」，不能删除");
    }

    @Test
    void shouldDetailWithEmptyItems() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(mapper.toResponse(order)).thenReturn(new PurchaseOrderResponse(
                1L, "PO-001", "供应商A", LocalDateTime.of(2026, 4, 26, 0, 0), "李四",
                new BigDecimal("1.000"), new BigDecimal("4000.00"), "草稿", null, List.of()
        ));

        PurchaseOrderResponse response = service.detail(1L);

        assertThat(response.orderNo()).isEqualTo("PO-001");
        assertThat(response.items()).isEmpty();
    }

    @Test
    void shouldRejectUpdateWithDuplicateOrderNo() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-002")).thenReturn(true);

        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO-002", "供应商A",
                LocalDateTime.of(2026, 4, 26, 0, 0), "李四",
                1L,
                "草稿", null, List.of()
        );

        assertThatThrownBy(() -> service.update(1L, request))
                ;
        verify(repository, never()).save(any());
    }

    @Test
    void shouldReturnEmptyPageWhenImportCandidatesHaveNoResults() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var page = service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "purchase-inbound"
        );

        assertThat(page.getContent()).isEmpty();
        verify(repository, never()).findByIdInAndDeletedFlagFalse(any());
    }

    @Test
    void shouldRejectInvalidImportCandidateUsage() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        assertThatThrownBy(() -> service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "invalid"
        ))
                .hasMessageContaining("usage 不支持当前导入场景");
    }

    @Test
    void shouldQueryPieceWeightsByJdbc() {
        PurchaseOrderService service = service(
                mock(PurchaseOrderRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        service = service(
                mock(PurchaseOrderRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                jdbc
        );

        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(7L)))
                .thenReturn(List.of(
                        new com.leo.erp.purchase.order.web.dto.PieceWeightResponse(1, new BigDecimal("2.037"), "SO-001"),
                        new com.leo.erp.purchase.order.web.dto.PieceWeightResponse(2, new BigDecimal("2.037"), "")
                ));

        var result = service.getPieceWeights(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).pieceNo()).isEqualTo(1);
        assertThat(result.get(0).salesOrderNo()).isEqualTo("SO-001");
    }

    @Test
    void shouldQueryPieceWeightsBySalesOrderItemId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PurchaseOrderService service = service(
                mock(PurchaseOrderRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                jdbc
        );

        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(301L)))
                .thenReturn(List.of(
                        new com.leo.erp.purchase.order.web.dto.PieceWeightResponse(1, new BigDecimal("2.037"), "SO-001")
                ));

        var result = service.getPieceWeightsBySalesOrderItemId(301L);

        assertThat(result).singleElement().satisfies(pw ->
                assertThat(pw.weightTon()).isEqualByComparingTo("2.037")
        );
    }

    @Test
    void shouldFallbackToZeroSalesRemainingWeightWhenNoPieceWeightMap() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        PurchaseOrderItem item = buildItem(7L, order);
        order.getItems().add(item);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(mapper.toResponse(order)).thenReturn(new PurchaseOrderResponse(
                1L, "PO-001", "供应商A", LocalDateTime.of(2026, 4, 26, 0, 0), "李四",
                new BigDecimal("2.000"), new BigDecimal("8000.00"), "草稿", null, List.of()
        ));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of());
        when(itemAllocationRepo.summarizeSalesByPurchaseOrderItems(List.of(7L), null))
                .thenReturn(List.of());
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of());

        PurchaseOrderResponse response = service.detail(1L);

        assertThat(response.items()).singleElement().satisfies(detailItem ->
                assertThat(detailItem.salesRemainingWeightTon()).isEqualByComparingTo("1.000")
        );
    }

    @Test
    void shouldCalculateSalesRemainingWeightWhenPartialAllocation() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository itemAllocationRepo = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                pieceWeightService,
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        PurchaseOrderItem item = buildItem(7L, order);
        order.getItems().add(item);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(mapper.toResponse(order)).thenReturn(new PurchaseOrderResponse(
                1L, "PO-001", "供应商A", LocalDateTime.of(2026, 4, 26, 0, 0), "李四",
                new BigDecimal("2.000"), new BigDecimal("8000.00"), "草稿", null, List.of()
        ));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of());
        when(itemAllocationRepo.summarizeSalesByPurchaseOrderItems(List.of(7L), null))
                .thenReturn(List.of(allocationProjection(7L, 3L)));
        when(pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(7L)))
                .thenReturn(Map.of());

        PurchaseOrderResponse response = service.detail(1L);

        assertThat(response.items()).singleElement().satisfies(detailItem -> {
            assertThat(detailItem.salesRemainingQuantity()).isEqualTo(7);
            assertThat(detailItem.salesRemainingWeightTon()).isEqualByComparingTo("0.700");
        });
    }

    @Test
    void shouldPageOrdersWithFilters() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(mapper.toResponse(order)).thenReturn(summaryResponse("草稿"));

        var page = service.page(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("PO", null, null, null)
        );

        assertThat(page.getContent()).singleElement().satisfies(r ->
                assertThat(r.orderNo()).isEqualTo("PO-001")
        );
    }

    @Test
    void shouldSearchOrders() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(mapper.toResponse(order)).thenReturn(summaryResponse("草稿"));

        List<PurchaseOrderResponse> results = service.search("PO", 100);

        assertThat(results).singleElement().satisfies(r ->
                assertThat(r.orderNo()).isEqualTo("PO-001")
        );
    }

    @Test
    void shouldCheckAuditPermissionWhenUpdatingToAuditedStatus() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository, idGenerator, mapper, materialSupport, warehouseSelectionSupport,
                supplierRepository, purchaseInboundItemQueryService,
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                workflowTransitionGuard, mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        order.setStatus("草稿");
        PurchaseOrderItem existingItem = buildItem(11L, order);
        order.getItems().add(existingItem);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("已审核"));

        service.update(1L, buildRequest(11L, "已审核"));

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "purchase-order", "草稿", "已审核", "已审核", "完成采购"
        );
    }

    @Test
    void shouldApplyWeightAdjustmentFromInboundWhenUpdating() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository, idGenerator, mapper, materialSupport, warehouseSelectionSupport,
                supplierRepository, purchaseInboundItemQueryService,
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                workflowTransitionGuard, mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        PurchaseOrderItem existingItem = buildItem(11L, order);
        existingItem.setWeightTon(new BigDecimal("1.000"));
        order.getItems().add(existingItem);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of(11L)))
                .thenReturn(Map.of(11L, new BigDecimal("0.030")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("草稿"));

        service.update(1L, buildRequest(11L, "草稿"));

        var captor = org.mockito.ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(repository).save(captor.capture());
        PurchaseOrderItem savedItem = captor.getValue().getItems().get(0);
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("1.000");
    }

    @Test
    void shouldCreateNewItemsWhenIdIsNull() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        PurchaseOrderService service = service(
                repository, idGenerator, mapper, materialSupport, warehouseSelectionSupport,
                supplierRepository, purchaseInboundItemQueryService,
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                workflowTransitionGuard, mock(JdbcTemplate.class)
        );

        PurchaseOrder order = buildOrder();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier("供应商A")));
        when(idGenerator.nextId()).thenReturn(100L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), anyString(), anyInt(), eq(false))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(any()))
                .thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(summaryResponse("草稿"));

        service.update(1L, buildRequest(null, "草稿"));

        assertThat(order.getItems()).singleElement()
                .extracting(PurchaseOrderItem::getId)
                .isEqualTo(100L);
    }

    @Test
    void shouldValidateChangedOrderNoAgainstDuplicates() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );
        PurchaseOrder order = buildOrder();
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO-002",
                "供应商A",
                LocalDateTime.of(2026, 4, 26, 0, 0),
                "李四",
                1L,
                "草稿",
                null,
                List.of()
        );
        when(repository.existsByOrderNoAndDeletedFlagFalse("PO-002")).thenReturn(false, true);

        service.validateUpdate(order, request);
        assertThatThrownBy(() -> service.validateUpdate(order, request))
                .hasMessageContaining("采购订单号已存在");
        verify(repository, times(2)).existsByOrderNoAndDeletedFlagFalse("PO-002");
    }

    @Test
    void shouldResolveDeletedDetailForAdminAndReportMissingOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderMapper mapper = mock(PurchaseOrderMapper.class);
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        PurchaseOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );
        PurchaseOrder deleted = buildOrder();
        deleted.setDeletedFlag(true);
        deleted.setStatus("草稿");

        when(systemSwitchService.shouldAdminSeeDeletedRecords()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(deleted));
        when(repository.findById(404L)).thenReturn(Optional.empty());
        when(mapper.toResponse(deleted)).thenReturn(summaryResponse("草稿", true));
        ReflectionTestUtils.setField(service, "crudRuntimeSettings", systemSwitchService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        PurchaseOrderResponse response = service.detail(1L);

        assertThat(response.status()).isEqualTo("草稿");
        assertThat(response.deletedFlag()).isTrue();
        assertThatThrownBy(() -> service.detail(404L))
                .hasMessageContaining("采购订单不存在");
        verify(repository).findById(1L);
        verify(repository).findById(404L);
        verify(repository, never()).findByIdAndDeletedFlagFalse(any());
    }

    @Test
    void shouldPageImportCandidatesAcrossBatches() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderAvailabilityService availabilityService = mock(PurchaseOrderAvailabilityService.class);
        PurchaseOrderService service = new PurchaseOrderService(
                repository,
                mock(SnowflakeIdGenerator.class),
                availabilityService,
                null,
                null,
                null,
                null,
                null,
                null
        );
        PurchaseOrder fullyImportedOrder = buildOrder();
        fullyImportedOrder.setStatus("已审核");
        PurchaseOrder importableOrder = buildOrder();
        importableOrder.setId(2L);
        importableOrder.setOrderNo("PO-002");
        importableOrder.setStatus("已审核");

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(
                                List.of(fullyImportedOrder),
                                org.springframework.data.domain.PageRequest.of(0, 200),
                                201
                        ),
                        new PageImpl<>(
                                List.of(importableOrder),
                                org.springframework.data.domain.PageRequest.of(1, 200),
                                201
                        )
                );
        Map<Long, Integer> importableQuantityMap = new java.util.HashMap<>();
        importableQuantityMap.put(1L, 0);
        importableQuantityMap.put(2L, 3);
        when(availabilityService.buildImportableQuantityMap(
                any(),
                eq(PurchaseOrderAvailabilityService.ImportCandidateUsage.PURCHASE_INBOUND),
                isNull()
        )).thenReturn(importableQuantityMap);

        var page = service.importCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("", null, null, null, null),
                "purchase-inbound"
        );

        assertThat(page.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo(2L);
            assertThat(candidate.importableQuantity()).isEqualTo(3);
        });
        verify(repository, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldEnforceSettlementCompanyMutableRules() {
        PurchaseOrderService service = service(
                mock(PurchaseOrderRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(PurchaseOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(SupplierRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(JdbcTemplate.class)
        );
        PurchaseOrder newOrder = buildOrder();
        newOrder.setId(null);
        newOrder.setSettlementCompanyId(1L);
        newOrder.setStatus("已审核");
        PurchaseOrder draftOrder = buildOrder();
        draftOrder.setSettlementCompanyId(1L);
        draftOrder.setStatus("草稿");
        PurchaseOrder auditedOrder = buildOrder();
        auditedOrder.setSettlementCompanyId(1L);
        auditedOrder.setStatus("已审核");

        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "assertSettlementCompanyMutable", newOrder, 2L)).isNull();
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "assertSettlementCompanyMutable", draftOrder, 2L)).isNull();
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "assertSettlementCompanyMutable", auditedOrder, 1L)).isNull();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "assertSettlementCompanyMutable",
                auditedOrder,
                2L
        )).hasMessageContaining("已审核采购订单不允许修改采购结算主体");
    }

    private ItemAllocationNativeRepository.AllocationProjection allocationProjection(Long sourceItemId, Long totalQuantity) {
        return new ItemAllocationNativeRepository.AllocationProjection() {
            @Override public Long getSourceItemId() { return sourceItemId; }
            @Override public Long getTotalQuantity() { return totalQuantity; }
            @Override public java.math.BigDecimal getTotalWeightTon() { return java.math.BigDecimal.ZERO; }
        };
    }

    private PurchaseOrderService service(PurchaseOrderRepository purchaseOrderRepository,
                                         SnowflakeIdGenerator snowflakeIdGenerator,
                                         PurchaseOrderMapper purchaseOrderMapper,
                                         TradeItemMaterialSupport tradeItemMaterialSupport,
                                         WarehouseSelectionSupport warehouseSelectionSupport,
                                         SupplierRepository supplierRepository,
                                         PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                         ItemAllocationNativeRepository itemAllocationRepo,
                                         PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService,
                                         WorkflowTransitionGuard workflowTransitionGuard,
                                         JdbcTemplate jdbc) {
        return service(
                purchaseOrderRepository,
                snowflakeIdGenerator,
                purchaseOrderMapper,
                tradeItemMaterialSupport,
                warehouseSelectionSupport,
                supplierRepository,
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                purchaseOrderItemPieceWeightService,
                workflowTransitionGuard,
                jdbc,
                null
        );
    }

    private PurchaseOrderService service(PurchaseOrderRepository purchaseOrderRepository,
                                         SnowflakeIdGenerator snowflakeIdGenerator,
                                         PurchaseOrderMapper purchaseOrderMapper,
                                         TradeItemMaterialSupport tradeItemMaterialSupport,
                                         WarehouseSelectionSupport warehouseSelectionSupport,
                                         SupplierRepository supplierRepository,
                                         PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                         ItemAllocationNativeRepository itemAllocationRepo,
                                         PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService,
                                         WorkflowTransitionGuard workflowTransitionGuard,
                                         JdbcTemplate jdbc,
                                         CompanySettingService companySettingService) {
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(tradeItemMaterialSupport);
        WarehouseSelectionSupportTestDoubles.stubWarehouseResolution(warehouseSelectionSupport);
        PurchaseOrderAvailabilityService availabilityService = new PurchaseOrderAvailabilityService(
                purchaseInboundItemQueryService,
                itemAllocationRepo,
                purchaseOrderItemPieceWeightService
        );
        return new PurchaseOrderService(
                purchaseOrderRepository,
                snowflakeIdGenerator,
                availabilityService,
                new PurchaseOrderResponseAssembler(purchaseOrderMapper, availabilityService),
                new PurchaseOrderSupplierResolver(supplierRepository),
                new PurchaseOrderApplyService(
                        tradeItemMaterialSupport,
                        warehouseSelectionSupport,
                        purchaseInboundItemQueryService
                ),
                new PurchaseOrderPieceWeightQueryService(jdbc),
                workflowTransitionGuard,
                companySettingService
        );
    }

    private Map<String, TradeMaterialSnapshot> materialMap(String materialCode) {
        return Map.of(materialCode, new TradeMaterialSnapshot(materialCode, Boolean.FALSE));
    }

    private CompanySetting companySetting(Long id, String companyName) {
        CompanySetting companySetting = new CompanySetting();
        companySetting.setId(id);
        companySetting.setCompanyName(companyName);
        return companySetting;
    }
}
