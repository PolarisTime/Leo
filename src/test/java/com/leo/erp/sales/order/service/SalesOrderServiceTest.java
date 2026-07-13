package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.sales.order.service.SalesOrderItemMapper;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRequireSourceAllocationLockServiceAsConstructorDependency() {
        boolean hasRequiredDependency = java.util.Arrays.stream(SalesOrderService.class.getConstructors())
                .anyMatch(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                        .contains(SourceAllocationLockService.class));

        assertThat(hasRequiredDependency).isTrue();
    }

    @Test
    void shouldRequireDeliveryVerificationGuardAsConstructorDependency() {
        boolean hasRequiredDependency = java.util.Arrays.stream(SalesOrderService.class.getConstructors())
                .anyMatch(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                        .contains(SalesOrderDeliveryVerificationGuard.class));

        assertThat(hasRequiredDependency).isTrue();
    }

    @Test
    void shouldLockPurchaseSourcesBeforeCreatingSalesOrderItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderService service = lockAwareService(
                repository,
                idGenerator,
                applyService,
                mock(SalesOrderPurchaseAllocationService.class),
                lockService
        );
        SalesOrderRequest request = sourceBackedSalesOrderRequest(102L, 202L);

        when(idGenerator.nextId()).thenReturn(1L);

        service.create(request);

        InOrder flow = inOrder(lockService, applyService);
        flow.verify(lockService).lockTradeItemSources(List.of(202L), List.of(102L), List.of());
        flow.verify(applyService).apply(any(), eq(request), any());
    }

    @Test
    void shouldKeepCustomerIdWhenNormalizingCreateRequest() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SalesOrderService service = lockAwareService(
                repository,
                idGenerator,
                applyService,
                mock(SalesOrderPurchaseAllocationService.class),
                mock(SourceAllocationLockService.class)
        );
        SalesOrderRequest sourceRequest = sourceBackedSalesOrderRequest(102L, 202L);
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-LOCK-001", null, null, "C001", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                sourceRequest.items()
        );
        when(idGenerator.nextId()).thenReturn(1L);

        service.create(request);

        var requestCaptor = forClass(SalesOrderRequest.class);
        verify(applyService).apply(any(), requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().customerId()).isEqualTo(1001L);
    }

    @Test
    void shouldKeepCustomerIdWhenNormalizingUpdateRequest() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SalesOrderService service = lockAwareService(
                repository,
                mock(SnowflakeIdGenerator.class),
                applyService,
                mock(SalesOrderPurchaseAllocationService.class),
                mock(SourceAllocationLockService.class)
        );
        SalesOrder existing = salesOrderWithSources(101L, 201L);
        SalesOrderRequest sourceRequest = sourceBackedSalesOrderRequest(102L, 202L);
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-LOCK-001", null, null, "C001", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                sourceRequest.items()
        );
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        service.update(1L, request);

        var requestCaptor = forClass(SalesOrderRequest.class);
        verify(applyService).apply(eq(existing), requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().customerId()).isEqualTo(1001L);
    }

    @Test
    void shouldLockOldAndNewPurchaseSourcesBeforeUpdatingSalesOrderItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderService service = lockAwareService(
                repository,
                mock(SnowflakeIdGenerator.class),
                applyService,
                mock(SalesOrderPurchaseAllocationService.class),
                lockService
        );
        com.leo.erp.sales.order.domain.entity.SalesOrder existing = salesOrderWithSources(101L, 201L);
        SalesOrderRequest request = sourceBackedSalesOrderRequest(102L, 202L);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        service.update(1L, request);

        InOrder flow = inOrder(lockService, applyService);
        flow.verify(lockService).lockTradeItemSources(
                List.of(201L, 202L),
                List.of(101L, 102L),
                List.of()
        );
        flow.verify(applyService).apply(eq(existing), eq(request), any());
    }

    @Test
    void shouldLockExistingPurchaseSourcesBeforeDeletingSalesOrderItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService =
                mock(SalesOrderPurchaseAllocationService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderService service = lockAwareService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(SalesOrderApplyService.class),
                purchaseAllocationService,
                lockService
        );
        com.leo.erp.sales.order.domain.entity.SalesOrder existing = salesOrderWithSources(101L, 201L);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        InOrder flow = inOrder(lockService, purchaseAllocationService);
        flow.verify(lockService).lockTradeItemSources(List.of(201L), List.of(101L), List.of());
        flow.verify(purchaseAllocationService).releaseSalesOrderItems(existing);
    }

    @Test
    void shouldLockExistingPurchaseSourcesBeforeUpdatingSalesOrderStatus() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderService service = lockAwareService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(SalesOrderApplyService.class),
                mock(SalesOrderPurchaseAllocationService.class),
                lockService
        );
        com.leo.erp.sales.order.domain.entity.SalesOrder existing = spy(salesOrderWithSources(101L, 201L));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        service.updateStatus(1L, "已审核");

        InOrder flow = inOrder(lockService, existing);
        flow.verify(lockService).lockTradeItemSources(List.of(201L), List.of(101L), List.of());
        flow.verify(existing).setStatus("已审核");
    }

    @Test
    void shouldLoadOnlyRequestedInboundItemsAndAllocationsWhenCreatingOrder() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, pieceWeightAppService,
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "PI-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                        101L, "一号库", "B1", 4, "支",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                        new BigDecimal("4000.00"), new BigDecimal("1600.00")
                ))
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setWarehouseName("一号库");
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(101L);
        inboundItem.setPurchaseInbound(inbound);
        inboundItem.setQuantity(10);

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L))).thenReturn(List.of(
                sourceInboundRecord(101L, inbound.getWarehouseName(), inboundItem.getQuantity(),
                        "M1", "宝钢", "螺纹钢", "HRB400", "18", "吨", "B1")));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-001", "PI-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("0.400"), new BigDecimal("1600.00"), "草稿", null, List.of()
        ));

        SalesOrderResponse response = service.create(request);

        assertThat(response.orderNo()).isEqualTo("SO-001");
        verify(purchaseItemQueryAppService).findSourceInboundItemsByIds(List.of(101L));
        verify(salesOrderItemRepository).summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any());
    }

    @Test
    void shouldAllocateSalesOrderQuantityAgainstPurchaseOrderItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, pieceWeightAppService,
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-003", null, "PO-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
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
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(201L))).thenReturn(List.of(
                sourcePurchaseOrderRecord(sourceItem.getId(), sourceItem.getQuantity(), sourceItem.getWeightTon(),
                        "M1", "宝钢", "螺纹钢", "HRB400", "18", "吨", "一号库", "B1")));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of(allocationSummary));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightAppService.allocateForSalesOrderItem(eq(sourceItem.getId()), eq(3), any(), eq(1)))
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
        InOrder saveFlow = inOrder(repository, pieceWeightAppService);
        saveFlow.verify(repository).saveAndFlush(any());
        saveFlow.verify(pieceWeightAppService).allocateForSalesOrderItem(eq(sourceItem.getId()), eq(3), any(), eq(1));
        saveFlow.verify(repository).save(any());
    }

    @Test
    void shouldRejectDuplicateOrderNoOnCreate() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "DUP-001", null, "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, null, "一号库", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("DUP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单号已存在");
    }

    @Test
    void shouldExposeOnlyAuditedOutboundImportCandidatesWithUnoccupiedItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                salesOrderItemRepository,
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        var auditedOpen = auditedSalesOrder("SO-001", StatusConstants.AUDITED, new BigDecimal("4000.00"));
        auditedOpen.getItems().get(0).setId(101L);
        SalesOrderItem occupiedItemOnOpenOrder = new SalesOrderItem();
        occupiedItemOnOpenOrder.setId(104L);
        occupiedItemOnOpenOrder.setSalesOrder(auditedOpen);
        occupiedItemOnOpenOrder.setLineNo(2);
        occupiedItemOnOpenOrder.setMaterialCode("M2");
        occupiedItemOnOpenOrder.setBrand("宝钢");
        occupiedItemOnOpenOrder.setCategory("螺纹钢");
        occupiedItemOnOpenOrder.setMaterial("HRB400");
        occupiedItemOnOpenOrder.setSpec("20");
        occupiedItemOnOpenOrder.setUnit("吨");
        occupiedItemOnOpenOrder.setQuantity(1);
        occupiedItemOnOpenOrder.setQuantityUnit("件");
        occupiedItemOnOpenOrder.setPieceWeightTon(new BigDecimal("1.000"));
        occupiedItemOnOpenOrder.setPiecesPerBundle(1);
        occupiedItemOnOpenOrder.setWeightTon(new BigDecimal("1.000"));
        occupiedItemOnOpenOrder.setUnitPrice(new BigDecimal("4000.00"));
        occupiedItemOnOpenOrder.setAmount(new BigDecimal("4000.00"));
        auditedOpen.getItems().add(occupiedItemOnOpenOrder);
        SalesOrderItem nullIdItemOnOpenOrder = new SalesOrderItem();
        nullIdItemOnOpenOrder.setSalesOrder(auditedOpen);
        nullIdItemOnOpenOrder.setLineNo(3);
        nullIdItemOnOpenOrder.setMaterialCode("M3");
        nullIdItemOnOpenOrder.setBrand("宝钢");
        nullIdItemOnOpenOrder.setCategory("螺纹钢");
        nullIdItemOnOpenOrder.setMaterial("HRB400");
        nullIdItemOnOpenOrder.setSpec("22");
        nullIdItemOnOpenOrder.setUnit("吨");
        nullIdItemOnOpenOrder.setQuantity(1);
        nullIdItemOnOpenOrder.setQuantityUnit("件");
        nullIdItemOnOpenOrder.setPieceWeightTon(new BigDecimal("1.000"));
        nullIdItemOnOpenOrder.setPiecesPerBundle(1);
        nullIdItemOnOpenOrder.setWeightTon(new BigDecimal("1.000"));
        nullIdItemOnOpenOrder.setUnitPrice(new BigDecimal("4000.00"));
        nullIdItemOnOpenOrder.setAmount(new BigDecimal("4000.00"));
        auditedOpen.getItems().add(nullIdItemOnOpenOrder);

        var auditedOccupied = auditedSalesOrder("SO-002", StatusConstants.AUDITED, new BigDecimal("4000.00"));
        auditedOccupied.setId(2L);
        auditedOccupied.getItems().get(0).setId(102L);

        var draftOpen = auditedSalesOrder("SO-003", "草稿", new BigDecimal("4000.00"));
        draftOpen.setId(3L);
        draftOpen.getItems().get(0).setId(103L);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(auditedOpen, auditedOccupied, draftOpen)));
        when(salesOrderItemRepository.findOccupiedSourceSalesOrderItemIds(anyCollection(), eq(9001L)))
                .thenReturn(List.of(102L, 104L));
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            com.leo.erp.sales.order.domain.entity.SalesOrder order = invocation.getArgument(0);
            return new SalesOrderResponse(
                    order.getId(),
                    order.getOrderNo(),
                    order.getPurchaseInboundNo(),
                    order.getPurchaseOrderNo(),
                    order.getCustomerCode(),
                    order.getCustomerName(),
                    order.getProjectId(),
                    order.getProjectName(),
                    order.getSettlementCompanyId(),
                    order.getSettlementCompanyName(),
                    order.getDeliveryDate(),
                    order.getSalesName(),
                    order.getTotalWeight(),
                    order.getTotalAmount(),
                    order.getStatus(),
                    order.getRemark(),
                    List.of()
            );
        });

        var page = service.outboundImportCandidates(
                com.leo.erp.common.api.PageQuery.of(0, 20, null, null),
                com.leo.erp.common.api.PageFilter.of("", null, null, null, null)
                        .withIdentity(null, null, null, null, 9001L)
        );

        assertThat(page.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo(1L);
            assertThat(candidate.orderNo()).isEqualTo("SO-001");
            assertThat(candidate.status()).isEqualTo(StatusConstants.AUDITED);
            assertThat(candidate.items()).extracting("id").containsExactly(101L);
        });
        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(salesOrderItemRepository)
                .findOccupiedSourceSalesOrderItemIds(anyCollection(), eq(9001L));
    }

    @Test
    void shouldPageSalesOrdersWithFilter() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        var order = auditedSalesOrder("SO-PAGE-001", StatusConstants.AUDITED, new BigDecimal("3000.00"));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            com.leo.erp.sales.order.domain.entity.SalesOrder source = invocation.getArgument(0);
            return new SalesOrderResponse(
                    source.getId(),
                    source.getOrderNo(),
                    source.getPurchaseInboundNo(),
                    source.getPurchaseOrderNo(),
                    source.getCustomerCode(),
                    source.getCustomerName(),
                    source.getProjectId(),
                    source.getProjectName(),
                    source.getSettlementCompanyId(),
                    source.getSettlementCompanyName(),
                    source.getDeliveryDate(),
                    source.getSalesName(),
                    source.getTotalWeight(),
                    source.getTotalAmount(),
                    source.getStatus(),
                    source.getRemark(),
                    List.of()
            );
        });

        var page = service.page(
                PageQuery.of(0, 20, "deliveryDate", "desc"),
                com.leo.erp.common.api.PageFilter.of(
                        "SO",
                        "客户A",
                        "项目A",
                        9L,
                        StatusConstants.AUDITED,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 30)
                )
        );

        assertThat(page.getContent()).singleElement().satisfies(response -> {
            assertThat(response.orderNo()).isEqualTo("SO-PAGE-001");
            assertThat(response.status()).isEqualTo(StatusConstants.AUDITED);
        });
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldFilterSalesOrderPageByStableCustomerAndProjectIds() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.page(
                PageQuery.of(0, 20, null, null),
                PageFilter.of(null, null, null, null, null)
                        .withIdentity(101L, 102L, null, null, null)
        );

        var specCaptor = forClass(Specification.class);
        verify(repository).findAll(specCaptor.capture(), any(Pageable.class));
        assertStableIdentityPredicates(specCaptor.getValue(), 101L, 102L);
    }

    @Test
    void shouldFilterOutboundImportCandidatesByStableCustomerAndProjectIds() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.outboundImportCandidates(
                PageQuery.of(0, 20, null, null),
                PageFilter.of(null, null, null, null, null)
                        .withIdentity(101L, 102L, null, null, null)
        );

        var specCaptor = forClass(Specification.class);
        verify(repository).findAll(specCaptor.capture(), any(Pageable.class));
        assertStableIdentityPredicates(specCaptor.getValue(), 101L, 102L);
    }

    @Test
    void shouldReturnNoOutboundCandidatesWhenOrdersHaveNoItemIds() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                salesOrderItemRepository,
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        var order = auditedSalesOrder("SO-NO-ITEM-ID", StatusConstants.AUDITED, new BigDecimal("3000.00"));
        order.getItems().get(0).setId(null);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        var page = service.outboundImportCandidates(
                PageQuery.of(0, 20, null, null),
                com.leo.erp.common.api.PageFilter.of("", null, null, null, null)
        );

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(salesOrderItemRepository, never())
                .findOccupiedSourceSalesOrderItemIds(anyCollection(), any());
    }

    @Test
    void shouldReadAllEntityPagesWhenBuildingOutboundCandidates() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                salesOrderItemRepository,
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        var firstOrder = auditedSalesOrder("SO-CAND-001", StatusConstants.AUDITED, new BigDecimal("3000.00"));
        firstOrder.getItems().get(0).setId(101L);
        var secondOrder = auditedSalesOrder("SO-CAND-002", StatusConstants.AUDITED, new BigDecimal("3000.00"));
        secondOrder.setId(2L);
        secondOrder.getItems().get(0).setId(102L);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(
                                List.of(firstOrder),
                                org.springframework.data.domain.PageRequest.of(0, 200),
                                201
                        ),
                        new PageImpl<>(
                                List.of(secondOrder),
                                org.springframework.data.domain.PageRequest.of(1, 200),
                                201
                        )
                );
        when(salesOrderItemRepository.findOccupiedSourceSalesOrderItemIds(anyCollection(), any()))
                .thenReturn(List.of());
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            com.leo.erp.sales.order.domain.entity.SalesOrder order = invocation.getArgument(0);
            return new SalesOrderResponse(
                    order.getId(),
                    order.getOrderNo(),
                    order.getPurchaseInboundNo(),
                    order.getPurchaseOrderNo(),
                    order.getCustomerCode(),
                    order.getCustomerName(),
                    order.getProjectId(),
                    order.getProjectName(),
                    order.getSettlementCompanyId(),
                    order.getSettlementCompanyName(),
                    order.getDeliveryDate(),
                    order.getSalesName(),
                    order.getTotalWeight(),
                    order.getTotalAmount(),
                    order.getStatus(),
                    order.getRemark(),
                    List.of()
            );
        });

        var page = service.outboundImportCandidates(
                PageQuery.of(0, 20, null, null),
                com.leo.erp.common.api.PageFilter.of("", null, null, null, null)
        );

        assertThat(page.getContent()).extracting(SalesOrderResponse::orderNo)
                .containsExactly("SO-CAND-001", "SO-CAND-002");
        verify(repository, org.mockito.Mockito.times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldUseVisibleEntityLookupForAdminDetailIncludingDeletedRecords() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mapper,
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        CrudRuntimeSettings runtimeSettings = mock(CrudRuntimeSettings.class);
        when(runtimeSettings.shouldAdminSeeDeletedRecords()).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service, "setCrudRuntimeSettings", runtimeSettings);
        var principal = new SecurityPrincipal(
                1L,
                "admin",
                "",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        var deletedOrder = auditedSalesOrder("SO-DELETED-001", StatusConstants.AUDITED, new BigDecimal("3000.00"));
        deletedOrder.setDeletedFlag(true);
        when(repository.findById(1L)).thenReturn(Optional.of(deletedOrder), Optional.empty());
        when(mapper.toResponse(deletedOrder)).thenReturn(new SalesOrderResponse(
                1L,
                "SO-DELETED-001",
                null,
                null,
                null,
                "客户A",
                null,
                "项目A",
                null,
                null,
                LocalDate.of(2026, 4, 26),
                "张三",
                new BigDecimal("4.500"),
                new BigDecimal("13500.00"),
                StatusConstants.AUDITED,
                true,
                "备注",
                List.of()
        ));

        SalesOrderResponse response = service.detail(1L);

        assertThat(response.orderNo()).isEqualTo("SO-DELETED-001");
        assertThat(response.status()).isEqualTo(StatusConstants.AUDITED);
        assertThat(response.deletedFlag()).isTrue();
        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单不存在");
        verify(repository, never()).findByIdAndDeletedFlagFalse(1L);
    }

    @Test
    void shouldDeleteOrderAndReleasePieceWeights() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService, mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        com.leo.erp.sales.order.domain.entity.SalesOrder order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-DEL-001");
        order.setDeletedFlag(false);
        order.setCustomerName("C");
        order.setProjectName("P");
        order.setDeliveryDate(LocalDate.now());
        order.setSalesName("S");
        order.setTotalWeight(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);

        SalesOrderItem item = new SalesOrderItem();
        item.setId(11L);
        item.setSalesOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mock(SalesOrderMapper.class).toResponse(any())).thenReturn(
                new SalesOrderResponse(1L, "SO-DEL-001", null, "C", "P",
                        LocalDate.now(), "S", BigDecimal.ZERO, BigDecimal.ZERO, "草稿", null, List.of()));

        service.delete(1L);

        assertThat(order.isDeletedFlag()).isTrue();
        verify(pieceWeightAppService).releaseSalesOrderItems(List.of(11L));
        verify(repository).save(order);
    }

    @Test
    void shouldUpdateStatusToAudited() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        com.leo.erp.sales.order.domain.entity.SalesOrder order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-STS-001");
        order.setStatus("草稿");
        order.setCustomerName("C");
        order.setProjectName("P");
        order.setDeliveryDate(LocalDate.now());
        order.setSalesName("S");
        order.setTotalWeight(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-STS-001", null, "C", "P",
                LocalDate.now(), "S", BigDecimal.ZERO, BigDecimal.ZERO, "已审核", null, List.of()));

        SalesOrderResponse response = service.updateStatus(1L, StatusConstants.AUDITED);

        assertThat(order.getStatus()).isEqualTo("已审核");
        verify(repository).save(order);
    }

    @Test
    void shouldCompleteDeliveryVerificationWithoutPricingAdjustment() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        com.leo.erp.sales.order.domain.entity.SalesOrder order =
                new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-DIRECT-COMPLETE");
        order.setStatus(StatusConstants.DELIVERY_VERIFICATION);
        order.setItems(new ArrayList<>());
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(mock(SalesOrderResponse.class));

        service.updateStatus(1L, StatusConstants.SALES_COMPLETED);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.SALES_COMPLETED);
        verify(repository).save(order);
    }

    @Test
    void shouldRejectWhenSourcePurchaseOrderItemNotFound() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), purchaseItemQueryAppService,
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-NF-001", null, "PO-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 999L, "一号库", "B1", 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-NF-001")).thenReturn(false);
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(999L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单明细不存在");
    }

    @Test
    void shouldRejectWhenSourceInboundItemNotFound() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), purchaseItemQueryAppService,
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-NF-002", "PI-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        888L, "一号库", "B1", 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-NF-002")).thenReturn(false);
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(888L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库明细不存在");
    }

    @Test
    void shouldRejectWhenAllocatedQuantityExceedsSource() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), purchaseItemQueryAppService,
                mock(PurchaseItemPieceWeightAppService.class), salesOrderItemRepository,
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-OVER-001", "PI-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        101L, "一号库", "B1", 8, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("16.000"),
                        new BigDecimal("3000.00"), new BigDecimal("48000.00")
                ))
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setWarehouseName("一号库");
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(101L);
        inboundItem.setPurchaseInbound(inbound);
        inboundItem.setQuantity(10);

        SalesOrderItemRepository.SourceInboundAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourceInboundAllocationSummary.class);
        when(allocationSummary.getSourceInboundItemId()).thenReturn(101L);
        when(allocationSummary.getTotalQuantity()).thenReturn(5L);

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-OVER-001")).thenReturn(false);
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L))).thenReturn(List.of(sourceInboundRecord(101L, "一号库", 10)));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any()))
                .thenReturn(List.of(allocationSummary));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可关联数量不足");
    }

    @Test
    void shouldNotChangeStatusWhenUpdateStatusSameValue() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        com.leo.erp.sales.order.domain.entity.SalesOrder order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-SAME-001");
        order.setStatus("已审核");
        order.setCustomerName("C");
        order.setProjectName("P");
        order.setDeliveryDate(LocalDate.now());
        order.setSalesName("S");
        order.setTotalWeight(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-SAME-001", null, "C", "P",
                LocalDate.now(), "S", BigDecimal.ZERO, BigDecimal.ZERO, "已审核", null, List.of()));

        SalesOrderResponse response = service.updateStatus(1L, StatusConstants.AUDITED);

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldCreateOrderWithoutSourceItemIds() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                mock(PurchaseItemQueryAppService.class), mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class), warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-NO-SRC-001", null, null, "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, null, "一号库", null, 2, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("4.000"),
                        new BigDecimal("3000.00"), new BigDecimal("12000.00")
                ))
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-NO-SRC-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq(null), eq(1), eq(true))).thenReturn("AUTO");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-NO-SRC-001", null, "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", new BigDecimal("4.000"),
                new BigDecimal("12000.00"), "草稿", null, List.of()));

        SalesOrderResponse response = service.create(request);

        assertThat(response.orderNo()).isEqualTo("SO-NO-SRC-001");
        verify(repository).save(any());
    }

    @Test
    void shouldAllowReverseAuditWhenOnlySalesOrderStatusChanges() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper, materialSupport,
                mock(PurchaseItemQueryAppService.class), mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class), warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), workflowTransitionGuard
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
        order.setTotalWeight(new BigDecimal("4.496"));
        order.setTotalAmount(new BigDecimal("13488.00"));
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
                "SO-REV-001", null, null, "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", "备注",
                List.of(new SalesOrderItemRequest(
                        11L, "M1", "宝钢", "盘螺", "HRB400", "8", "12m", "吨",
                        null, null, "一号库", "B1", 2, "件",
                        new BigDecimal("2.248"), 1, new BigDecimal("4.496"),
                        new BigDecimal("3000.00"), new BigDecimal("13488.00")
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-REV-001", null, null, "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("4.496"), new BigDecimal("13488.00"), "草稿", "备注", List.of()
        ));

        service.update(1L, request);

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "sales-order", "已审核", "草稿", "已审核", "完成销售"
        );
        verify(repository).save(any());
        assertThat(order.getStatus()).isEqualTo("草稿");
    }

    @Test
    void shouldRejectReverseAuditWhenSalesOrderPayloadChangesOtherFields() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        var order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-REV-002");
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 4, 26));
        order.setSalesName("张三");
        order.setStatus("已审核");
        order.setTotalWeight(new BigDecimal("4.496"));
        order.setTotalAmount(new BigDecimal("13488.00"));
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
                "SO-REV-002", null, null, "客户A", "项目A",
                LocalDate.of(2026, 4, 27), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        11L, "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
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

    @Test
    void shouldAllowAuditedSalesOrderPricingUpdateAndSyncAuditedOutboundAmounts() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderOutboundPricingSyncService outboundPricingSyncService = mock(SalesOrderOutboundPricingSyncService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService, mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class), outboundPricingSyncService, completionSyncService
        );

        var order = auditedSalesOrder("SO-PRICE-002", StatusConstants.AUDITED, BigDecimal.ZERO);
        SalesOrderRequest request = pricingUpdateRequest(order, new BigDecimal("3888.00"), StatusConstants.AUDITED);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-PRICE-002", null, null, "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("4.500"), new BigDecimal("17496.00"), StatusConstants.AUDITED, "备注", List.of()
        ));

        service.update(1L, request);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("3888.00");
        assertThat(order.getItems().get(0).getAmount()).isEqualByComparingTo("17496.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("17496.00");
        verify(outboundPricingSyncService).syncAuditedOutboundPricing(
                eq(List.of(order.getItems().get(0).getId())),
                eq(Map.of(order.getItems().get(0).getId(), new BigDecimal("3888.00")))
        );
        verify(completionSyncService).syncBySalesOrderId(1L);
        verify(pieceWeightAppService, never()).releaseSalesOrderItems(any());
    }

    @Test
    void shouldAllowAuditedSalesOrderDateRemarkAndPricingUpdateWithoutReallocatingPurchasePieces() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderOutboundPricingSyncService outboundPricingSyncService = mock(SalesOrderOutboundPricingSyncService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService, mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class), outboundPricingSyncService, completionSyncService
        );

        var order = auditedSalesOrder("SO-PRICE-005", StatusConstants.AUDITED, new BigDecimal("3300.00"));
        SalesOrderItem item = order.getItems().get(0);
        item.setSourcePurchaseOrderItemId(501L);
        order.setPurchaseOrderNo("PO-005");
        SalesOrderRequest baseRequest = pricingUpdateRequest(order, new BigDecimal("3500.00"), StatusConstants.AUDITED);
        SalesOrderRequest request = new SalesOrderRequest(
                baseRequest.orderNo(), baseRequest.purchaseInboundNo(), baseRequest.purchaseOrderNo(),
                baseRequest.customerCode(), baseRequest.customerName(), baseRequest.projectId(),
                baseRequest.projectName(), LocalDate.of(2026, 4, 28),
                baseRequest.salesName(), baseRequest.status(), "改价备注",
                baseRequest.items()
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-PRICE-005", null, "PO-005", "客户A", "项目A",
                LocalDate.of(2026, 4, 28), "张三", new BigDecimal("4.500"),
                new BigDecimal("15750.00"), StatusConstants.AUDITED, "改价备注", List.of()
        ));

        service.update(1L, request);

        assertThat(order.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 4, 28));
        assertThat(order.getRemark()).isEqualTo("改价备注");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("3500.00");
        assertThat(item.getAmount()).isEqualByComparingTo("15750.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("15750.00");
        verify(repository).save(order);
        verify(outboundPricingSyncService).syncAuditedOutboundPricing(
                eq(List.of(item.getId())),
                eq(Map.of(item.getId(), new BigDecimal("3500.00")))
        );
        verify(repository, never()).saveAndFlush(any());
        verify(pieceWeightAppService, never()).releaseSalesOrderItems(any());
        verify(pieceWeightAppService, never()).allocateForSalesOrderItem(any(), any(), any(), anyInt());
        verify(completionSyncService).syncBySalesOrderId(1L);
    }

    @Test
    void shouldRejectAuditedSalesOrderPricingUpdateWhenStructureChanges() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class), mock(SalesOrderOutboundPricingSyncService.class),
                mock(SalesOrderCompletionSyncService.class)
        );

        var order = auditedSalesOrder("SO-PRICE-003", StatusConstants.AUDITED, BigDecimal.ZERO);
        SalesOrderRequest request = pricingUpdateRequest(order, new BigDecimal("3888.00"), StatusConstants.AUDITED);
        SalesOrderRequest changedQuantity = new SalesOrderRequest(
                request.orderNo(), request.purchaseInboundNo(), request.purchaseOrderNo(), request.customerCode(),
                request.customerName(), request.projectId(), request.projectName(), request.deliveryDate(),
                request.salesName(), request.status(), request.remark(),
                List.of(new SalesOrderItemRequest(
                        request.items().get(0).id(),
                        request.items().get(0).materialCode(),
                        request.items().get(0).brand(),
                        request.items().get(0).category(),
                        request.items().get(0).material(),
                        request.items().get(0).spec(),
                        request.items().get(0).length(),
                        request.items().get(0).unit(),
                        request.items().get(0).sourceInboundItemId(),
                        request.items().get(0).sourcePurchaseOrderItemId(),
                        request.items().get(0).warehouseName(),
                        request.items().get(0).batchNo(),
                        9,
                        request.items().get(0).quantityUnit(),
                        request.items().get(0).pieceWeightTon(),
                        request.items().get(0).piecesPerBundle(),
                        request.items().get(0).weightTon(),
                        request.items().get(0).unitPrice(),
                        request.items().get(0).amount()
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(1L, changedQuantity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前单据状态为「已审核」，不能编辑");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCompleteDeliveryVerificationAfterPricingUpdateWithoutRedundantCompletionSync() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderOutboundPricingSyncService outboundPricingSyncService = mock(SalesOrderOutboundPricingSyncService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class), outboundPricingSyncService,
                completionSyncService
        );

        var order = auditedSalesOrder("SO-PRICE-004", StatusConstants.DELIVERY_VERIFICATION, new BigDecimal("3000.00"));
        SalesOrderRequest baseRequest = pricingUpdateRequest(
                order,
                new BigDecimal("3888.00"),
                StatusConstants.DELIVERY_VERIFICATION
        );
        SalesOrderRequest request = new SalesOrderRequest(
                baseRequest.orderNo(), baseRequest.purchaseInboundNo(), baseRequest.purchaseOrderNo(),
                baseRequest.customerCode(), baseRequest.customerName(), baseRequest.projectId(),
                baseRequest.projectName(), LocalDate.of(2026, 4, 28), baseRequest.salesName(),
                baseRequest.status(), "完成销售后调整", baseRequest.items()
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(mock(SalesOrderResponse.class));

        service.update(1L, request);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.SALES_COMPLETED);
        assertThat(order.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 4, 28));
        assertThat(order.getRemark()).isEqualTo("完成销售后调整");
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("3888.00");
        verify(repository).save(order);
        verify(outboundPricingSyncService).syncAuditedOutboundPricing(
                eq(List.of(order.getItems().get(0).getId())),
                eq(Map.of(order.getItems().get(0).getId(), new BigDecimal("3888.00")))
        );
        verify(completionSyncService, never()).syncBySalesOrderId(1L);
    }

    @Test
    void shouldSearchByKeyword() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper,
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-001", null, null, "客户A", "项目A", LocalDate.now(),
                "张三", BigDecimal.ZERO, BigDecimal.ZERO, "草稿", null, List.of()));

        service.search("test", 10);

        verify(repository).findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void shouldRejectDuplicateOrderNoOnUpdateWhenOrderNoChanges() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-NEW", null, null, "C", "P",
                LocalDate.now(), "S", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, null, "一号库", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-NEW")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单号已存在");
    }

    @Test
    void shouldRejectDuplicateOrderNoWhenValidateUpdateReceivesChangedOrderNo() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        var order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setOrderNo("SO-OLD");
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-NEW",
                null,
                null,
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.DRAFT,
                null,
                List.of(new SalesOrderItemRequest(
                        "M1",
                        "宝钢",
                        "盘螺",
                        "HRB400",
                        "8",
                        null,
                        "吨",
                        null,
                        null,
                        "一号库",
                        null,
                        1,
                        "件",
                        new BigDecimal("2.000"),
                        1,
                        new BigDecimal("2.000"),
                        new BigDecimal("3000.00"),
                        new BigDecimal("6000.00")
                ))
        );
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-NEW")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单号已存在");
    }

    @Test
    void shouldAllowChangedOrderNoWhenNoDuplicateExists() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class),
                stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );
        var order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setOrderNo("SO-OLD");
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-NEW",
                null,
                null,
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.DRAFT,
                null,
                List.of(new SalesOrderItemRequest(
                        "M1",
                        "宝钢",
                        "盘螺",
                        "HRB400",
                        "8",
                        null,
                        "吨",
                        null,
                        null,
                        "一号库",
                        null,
                        1,
                        "件",
                        new BigDecimal("2.000"),
                        1,
                        new BigDecimal("2.000"),
                        new BigDecimal("3000.00"),
                        new BigDecimal("6000.00")
                ))
        );
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-NEW")).thenReturn(false);

        service.validateUpdate(order, request);

        verify(repository).existsByOrderNoAndDeletedFlagFalse("SO-NEW");
    }

    @Test
    void shouldAllowUpdateWhenOrderNoUnchanged() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mapper, materialSupport,
                mock(PurchaseItemQueryAppService.class), mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class), warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        com.leo.erp.sales.order.domain.entity.SalesOrder order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-SAME");
        order.setStatus("草稿");
        order.setCustomerName("C");
        order.setProjectName("P");
        order.setDeliveryDate(LocalDate.now());
        order.setSalesName("S");
        order.setTotalWeight(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setItems(new ArrayList<>());

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-SAME", null, null, "C", "P",
                LocalDate.now(), "S", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, null, "一号库", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq(null), eq(1), eq(true))).thenReturn("AUTO");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-SAME", null, null, "C", "P", LocalDate.now(),
                "S", BigDecimal.ZERO, BigDecimal.ZERO, "草稿", null, List.of()));

        service.update(1L, request);

        verify(repository).save(any());
    }

    @Test
    void shouldHandleMatchesStatusOnlyUpdateWithNullEntity() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class), mock(SalesOrderItemRepository.class),
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        var order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-NULL");
        order.setStatus("已审核");
        order.setCustomerName("C");
        order.setProjectName("P");
        order.setDeliveryDate(LocalDate.now());
        order.setSalesName("S");
        order.setTotalWeight(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setItems(new ArrayList<>());

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-NULL", null, null, "C", "P",
                LocalDate.now(), "S", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, null, "一号库", null, 1, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("2.000"),
                        new BigDecimal("3000.00"), new BigDecimal("6000.00")
                ))
        );

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(1L, request))
                .hasMessageContaining("当前单据状态为「已审核」，不能编辑");
    }

    @Test
    void shouldCreateOrderWithSourcePurchaseOrderAndRemainingWeight() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, pieceWeightAppService,
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-REM-001", null, "PO-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 201L, "一号库", "B1", 3, "件",
                        new BigDecimal("0.100"), 1, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(201L);
        sourceItem.setQuantity(10);
        sourceItem.setWeightTon(new BigDecimal("1.000"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-REM-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(201L))).thenReturn(
                List.of(sourcePurchaseOrderRecord(sourceItem.getId(), sourceItem.getQuantity(), sourceItem.getWeightTon())));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(pieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(201L)))
                .thenReturn(Map.of(201L, new BigDecimal("0.300")));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightAppService.allocateForSalesOrderItem(eq(201L), eq(3), any(), eq(1)))
                .thenReturn(new BigDecimal("0.300"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-REM-001", null, "PO-001", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("0.300"), new BigDecimal("1200.00"), "草稿", null, List.of()));

        service.create(request);

        verify(repository).saveAndFlush(any());
        verify(pieceWeightAppService).allocateForSalesOrderItem(eq(201L), eq(3), any(), eq(1));
    }

    @Test
    void shouldSaveDirectlyWhenNoPurchaseOrderBackedItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, mock(PurchaseItemPieceWeightAppService.class),
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-DIRECT-001", "PI-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        101L, "一号库", "B1", 2, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("4.000"),
                        new BigDecimal("3000.00"), new BigDecimal("12000.00")
                ))
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setWarehouseName("一号库");
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(101L);
        inboundItem.setPurchaseInbound(inbound);
        inboundItem.setQuantity(10);

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-DIRECT-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L))).thenReturn(
                List.of(sourceInboundRecord(101L, "一号库", 10)));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-DIRECT-001", "PI-001", null, "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("4.000"), new BigDecimal("12000.00"), "草稿", null, List.of()));

        service.create(request);

        verify(repository).save(any());
        verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void shouldFinalizePurchaseOrderAllocationsWhenSourceItemNotFound() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, pieceWeightAppService,
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-FINALIZE-ERR", null, "PO-ERR", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 999L, "一号库", "B1", 3, "件",
                        new BigDecimal("0.100"), 1, new BigDecimal("0.300"),
                        new BigDecimal("4000.00"), new BigDecimal("1200.00")
                ))
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-FINALIZE-ERR")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(999L))).thenReturn(List.of());
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(999L)), any()))
                .thenReturn(List.of());
        when(pieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(999L)))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单明细不存在");
    }

    @Test
    void shouldHandleMultipleItemsWithMixedSourceTypes() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, pieceWeightAppService,
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-MIX-001", "PI-MIX", "PO-MIX", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(
                        new SalesOrderItemRequest(
                                "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                                101L, "一号库", "B1", 2, "件",
                                new BigDecimal("2.000"), 1, new BigDecimal("4.000"),
                                new BigDecimal("3000.00"), new BigDecimal("12000.00")
                        ),
                        new SalesOrderItemRequest(
                                "M2", "沙钢", "螺纹钢", "HRB400", "16", null, "吨",
                                null, 201L, "二号库", "B2", 3, "件",
                                new BigDecimal("1.500"), 1, new BigDecimal("4.500"),
                                new BigDecimal("4000.00"), new BigDecimal("18000.00")
                        )
                )
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setWarehouseName("一号库");
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(101L);
        inboundItem.setPurchaseInbound(inbound);
        inboundItem.setQuantity(10);

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-MIX-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 2L, 11L, 12L);
        when(materialSupport.loadMaterialMap(List.of("M1", "M2"))).thenReturn(materialMap("M1", "M2"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(materialSupport.normalizeBatchNo(any(), eq("B2"), eq(2), eq(true))).thenReturn("B2");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(warehouseSelectionSupport.normalizeWarehouseName("二号库", 2, true)).thenReturn("二号库");
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L))).thenReturn(
                List.of(sourceInboundRecord(101L, "一号库", 10)));
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(201L))).thenReturn(
                List.of(sourcePurchaseOrderRecord(201L, 10, new BigDecimal("1.500"),
                        "M2", "沙钢", "螺纹钢", "HRB400", "16", "吨", "二号库", "B2")));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(101L)), any()))
                .thenReturn(List.of());
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(201L)), any()))
                .thenReturn(List.of());
        when(pieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(201L)))
                .thenReturn(Map.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightAppService.allocateForSalesOrderItem(eq(201L), eq(3), any(), eq(2)))
                .thenReturn(new BigDecimal("0.450"));
        doAnswer(invocation -> null).when(pieceWeightAppService).releaseSalesOrderItems(any());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-MIX-001", "PI-MIX", "PO-MIX", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("8.500"), new BigDecimal("30000.00"), "草稿", null, List.of()));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getPurchaseInboundNo()).isEqualTo("PI-MIX");
        assertThat(savedOrder.getPurchaseOrderNo()).isEqualTo("PO-MIX");
        assertThat(savedOrder.getItems()).hasSize(2);
    }

    @Test
    void shouldResolvePieceWeightTonFromPurchaseOrderSource() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, pieceWeightAppService,
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-PW-001", null, "PO-PW", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 301L, "一号库", "B1", 5, "件",
                        new BigDecimal("0.200"), 1, null,
                        new BigDecimal("4000.00"), null
                ))
        );

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-PW-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(301L))).thenReturn(
                List.of(sourcePurchaseOrderRecord(301L, 10, new BigDecimal("2.000"))));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(301L)), any()))
                .thenReturn(List.of());
        when(pieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(301L)))
                .thenReturn(Map.of(301L, new BigDecimal("1.000")));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pieceWeightAppService.allocateForSalesOrderItem(eq(301L), eq(5), any(), eq(1)))
                .thenReturn(new BigDecimal("1.000"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-PW-001", null, "PO-PW", "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("1.000"), new BigDecimal("4000.00"), "草稿", null, List.of()));

        service.create(request);

        verify(repository).saveAndFlush(any());
    }

    @Test
    void shouldResolveWeightTonFromInboundWeighWeight() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, mock(PurchaseItemPieceWeightAppService.class),
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-WEIGH-001", "PI-WEIGH", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        401L, "一号库", "B1", 10, "件",
                        new BigDecimal("1.000"), 1, new BigDecimal("10.000"),
                        new BigDecimal("3000.00"), new BigDecimal("30000.00")
                ))
        );

        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(401L);
        inboundItem.setQuantity(10);
        inboundItem.setWeighWeightTon(new BigDecimal("9.876"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-WEIGH-001")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(401L))).thenReturn(
                List.of(new PurchaseItemQueryAppService.SourceInboundItemRecord(
                        401L, null, StatusConstants.AUDITED, null, 10, new BigDecimal("9.876"),
                        "宝钢", "HRB400", "8", "M1", "盘螺", "吨", "一号库", "B1")));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(401L)), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new SalesOrderResponse(
                1L, "SO-WEIGH-001", "PI-WEIGH", null, "客户A", "项目A", LocalDate.of(2026, 4, 26),
                "张三", new BigDecimal("9.876"), new BigDecimal("29628.00"), "草稿", null, List.of()));

        service.create(request);

        var orderCaptor = forClass(com.leo.erp.sales.order.domain.entity.SalesOrder.class);
        verify(repository).save(orderCaptor.capture());
        var savedItem = orderCaptor.getValue().getItems().get(0);
        assertThat(savedItem.getWeightTon()).isEqualByComparingTo("9.876");
    }

    @Test
    void shouldRejectWhenAllocatedQuantityExceedsPurchaseOrderSource() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderService service = service(
                repository, mock(SnowflakeIdGenerator.class), mock(SalesOrderMapper.class),
                mock(TradeItemMaterialSupport.class), purchaseItemQueryAppService,
                mock(PurchaseItemPieceWeightAppService.class), salesOrderItemRepository,
                mock(WarehouseSelectionSupport.class), stubbedSalesOrderItemMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-OVER-PO", null, "PO-OVER", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 501L, "一号库", "B1", 8, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("16.000"),
                        new BigDecimal("3000.00"), new BigDecimal("48000.00")
                ))
        );

        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(501L);
        when(allocationSummary.getTotalQuantity()).thenReturn(5L);
        when(allocationSummary.getTotalWeightTon()).thenReturn(new BigDecimal("1.000"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-OVER-PO")).thenReturn(false);
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(501L))).thenReturn(
                List.of(sourcePurchaseOrderRecord(501L, 10, new BigDecimal("2.000"))));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(501L)), any()))
                .thenReturn(List.of(allocationSummary));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可关联数量不足");
    }

    @Test
    void shouldNotAllocateWhenSourcePurchaseOrderItemQuantityZero() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderMapper mapper = mock(SalesOrderMapper.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderService service = service(
                repository, idGenerator, mapper, materialSupport,
                purchaseItemQueryAppService, mock(PurchaseItemPieceWeightAppService.class),
                salesOrderItemRepository, warehouseSelectionSupport,
                stubbedSalesOrderItemMapper(), mock(WorkflowTransitionGuard.class)
        );

        SalesOrderRequest request = new SalesOrderRequest(
                "SO-ZERO-QTY", null, "PO-ZERO", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(new SalesOrderItemRequest(
                        "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                        null, 601L, "一号库", "B1", 2, "件",
                        new BigDecimal("2.000"), 1, new BigDecimal("4.000"),
                        new BigDecimal("3000.00"), new BigDecimal("12000.00")
                ))
        );

        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary allocationSummary =
                mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(allocationSummary.getSourcePurchaseOrderItemId()).thenReturn(601L);
        when(allocationSummary.getTotalQuantity()).thenReturn(2L);
        when(allocationSummary.getTotalWeightTon()).thenReturn(new BigDecimal("0.400"));

        when(repository.existsByOrderNoAndDeletedFlagFalse("SO-ZERO-QTY")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(1L, 11L);
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(materialMap("M1"));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(List.of(601L))).thenReturn(
                List.of(sourcePurchaseOrderRecord(601L, 2, new BigDecimal("0.400"))));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(601L)), any()))
                .thenReturn(List.of(allocationSummary));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可关联数量不足");
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundRecord(
            Long id, String warehouseName, Integer quantity) {
        return sourceInboundRecord(id, warehouseName, quantity,
                "M1", "宝钢", "盘螺", "HRB400", "8", "吨", "B1");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertStableIdentityPredicates(
            Specification specification,
            Long customerId,
            Long projectId
    ) {
        Root root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Object> customerIdPath = mock(Path.class);
        Path<Object> projectIdPath = mock(Path.class);
        when(root.get("customerId")).thenReturn(customerIdPath);
        when(root.get("projectId")).thenReturn(projectIdPath);
        when(criteriaBuilder.conjunction()).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.equal(customerIdPath, customerId)).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.equal(projectIdPath, projectId)).thenReturn(mock(Predicate.class));

        specification.toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).equal(customerIdPath, customerId);
        verify(criteriaBuilder).equal(projectIdPath, projectId);
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundRecord(
            Long id,
            String warehouseName,
            Integer quantity,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String unit,
            String batchNo) {
        return new PurchaseItemQueryAppService.SourceInboundItemRecord(
                id, null, StatusConstants.AUDITED, null, quantity, null,
                brand, material, spec, materialCode, category, unit, warehouseName, batchNo);
    }

    private PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord sourcePurchaseOrderRecord(
            Long id, Integer quantity, java.math.BigDecimal weightTon) {
        return sourcePurchaseOrderRecord(id, quantity, weightTon,
                "M1", "宝钢", "盘螺", "HRB400", "8", "吨", "一号库", "B1");
    }

    private PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord sourcePurchaseOrderRecord(
            Long id,
            Integer quantity,
            java.math.BigDecimal weightTon,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String unit,
            String warehouseName,
            String batchNo) {
        java.math.BigDecimal pieceWeightTon = quantity == null || quantity <= 0 || weightTon == null
                ? null
                : weightTon.divide(java.math.BigDecimal.valueOf(quantity), 8, java.math.RoundingMode.HALF_UP);
        return new PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord(
                id, quantity, weightTon, pieceWeightTon, null, StatusConstants.AUDITED,
                brand, material, spec, materialCode, category, unit, warehouseName, batchNo);
    }

    private SalesOrderService service(SalesOrderRepository repository,
                                      SnowflakeIdGenerator idGenerator,
                                      SalesOrderMapper salesOrderMapper,
                                      TradeItemMaterialSupport tradeItemMaterialSupport,
                                      PurchaseItemQueryAppService purchaseItemQueryAppService,
                                      PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService,
                                      SalesOrderItemRepository salesOrderItemRepository,
                                      WarehouseSelectionSupport warehouseSelectionSupport,
                                      SalesOrderItemMapper salesOrderItemMapper,
                                      WorkflowTransitionGuard workflowTransitionGuard) {
        return service(
                repository,
                idGenerator,
                salesOrderMapper,
                tradeItemMaterialSupport,
                purchaseItemQueryAppService,
                purchaseItemPieceWeightAppService,
                salesOrderItemRepository,
                warehouseSelectionSupport,
                salesOrderItemMapper,
                workflowTransitionGuard,
                null,
                null
        );
    }

    private SalesOrderService lockAwareService(
            SalesOrderRepository repository,
            SnowflakeIdGenerator idGenerator,
            SalesOrderApplyService applyService,
            SalesOrderPurchaseAllocationService purchaseAllocationService,
            SourceAllocationLockService lockService
    ) {
        SalesOrderAuditedPricingService auditedPricingService = mock(SalesOrderAuditedPricingService.class);
        return new SalesOrderService(
                repository,
                idGenerator,
                mock(SalesOrderResponseAssembler.class),
                applyService,
                purchaseAllocationService,
                auditedPricingService,
                mock(SalesOrderProtectedUpdatePolicy.class),
                mock(SalesOrderSaveService.class),
                mock(SalesOrderItemRepository.class),
                lockService
        );
    }

    private SalesOrderRequest sourceBackedSalesOrderRequest(Long sourceInboundItemId,
                                                            Long sourcePurchaseOrderItemId) {
        return new SalesOrderRequest(
                "SO-LOCK-001", null, null, "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "张三", "草稿", null,
                List.of(
                        sourceBackedSalesOrderItemRequest(sourceInboundItemId, null),
                        sourceBackedSalesOrderItemRequest(null, sourcePurchaseOrderItemId)
                )
        );
    }

    private SalesOrderItemRequest sourceBackedSalesOrderItemRequest(Long sourceInboundItemId,
                                                                    Long sourcePurchaseOrderItemId) {
        return new SalesOrderItemRequest(
                "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                sourceInboundItemId, sourcePurchaseOrderItemId, "一号库", "B1", 1, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("0.100"),
                new BigDecimal("4000.00"), new BigDecimal("400.00")
        );
    }

    private com.leo.erp.sales.order.domain.entity.SalesOrder salesOrderWithSources(
            Long sourceInboundItemId,
            Long sourcePurchaseOrderItemId
    ) {
        com.leo.erp.sales.order.domain.entity.SalesOrder order =
                new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-LOCK-001");
        order.setStatus("草稿");
        SalesOrderItem inboundItem = new SalesOrderItem();
        inboundItem.setId(11L);
        inboundItem.setSalesOrder(order);
        inboundItem.setSourceInboundItemId(sourceInboundItemId);
        SalesOrderItem purchaseOrderItem = new SalesOrderItem();
        purchaseOrderItem.setId(12L);
        purchaseOrderItem.setSalesOrder(order);
        purchaseOrderItem.setSourcePurchaseOrderItemId(sourcePurchaseOrderItemId);
        order.setItems(new ArrayList<>(List.of(inboundItem, purchaseOrderItem)));
        return order;
    }

    private SalesOrderService service(SalesOrderRepository repository,
                                      SnowflakeIdGenerator idGenerator,
                                      SalesOrderMapper salesOrderMapper,
                                      TradeItemMaterialSupport tradeItemMaterialSupport,
                                      PurchaseItemQueryAppService purchaseItemQueryAppService,
                                      PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService,
                                      SalesOrderItemRepository salesOrderItemRepository,
                                      WarehouseSelectionSupport warehouseSelectionSupport,
                                      SalesOrderItemMapper salesOrderItemMapper,
                                      WorkflowTransitionGuard workflowTransitionGuard,
                                      SalesOrderOutboundPricingSyncService outboundPricingSyncService,
                                      SalesOrderCompletionSyncService completionSyncService) {
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(tradeItemMaterialSupport);
        SalesOrderPurchaseAllocationService purchaseAllocationService =
                new SalesOrderPurchaseAllocationService(purchaseItemQueryAppService, purchaseItemPieceWeightAppService);
        SalesOrderAuditedPricingService auditedPricingService =
                new SalesOrderAuditedPricingService(outboundPricingSyncService);
        SalesOrderApplyService applyService = new SalesOrderApplyService(
                tradeItemMaterialSupport,
                new SalesOrderSourceAllocationService(purchaseItemQueryAppService, salesOrderItemRepository),
                new SalesOrderWeightResolver(purchaseItemPieceWeightAppService),
                purchaseAllocationService,
                salesOrderItemMapper,
                workflowTransitionGuard
        );
        return new SalesOrderService(
                repository,
                idGenerator,
                new SalesOrderResponseAssembler(salesOrderMapper),
                applyService,
                purchaseAllocationService,
                auditedPricingService,
                new SalesOrderProtectedUpdatePolicy(auditedPricingService),
                new SalesOrderSaveService(
                        repository,
                        purchaseAllocationService,
                        completionSyncService,
                        new SalesOrderCompletionPolicy()
                ),
                salesOrderItemRepository,
                mock(SourceAllocationLockService.class)
        );
    }

    private com.leo.erp.sales.order.domain.entity.SalesOrder auditedSalesOrder(
            String orderNo,
            String status,
            BigDecimal unitPrice
    ) {
        var order = new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 4, 26));
        order.setSalesName("张三");
        order.setStatus(status);
        order.setRemark("备注");
        order.setTotalWeight(new BigDecimal("4.500"));
        order.setTotalAmount(new BigDecimal("4.500").multiply(unitPrice));

        SalesOrderItem item = new SalesOrderItem();
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
        item.setQuantity(3);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.500"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("4.500"));
        item.setUnitPrice(unitPrice);
        item.setAmount(new BigDecimal("4.500").multiply(unitPrice));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private SalesOrderRequest pricingUpdateRequest(
            com.leo.erp.sales.order.domain.entity.SalesOrder order,
            BigDecimal unitPrice,
            String status
    ) {
        SalesOrderItem item = order.getItems().get(0);
        return new SalesOrderRequest(
                order.getOrderNo(), order.getPurchaseInboundNo(), order.getPurchaseOrderNo(), order.getCustomerCode(),
                order.getCustomerName(), order.getProjectId(), order.getProjectName(), order.getDeliveryDate(),
                order.getSalesName(), status, order.getRemark(),
                List.of(new SalesOrderItemRequest(
                        item.getId(), item.getMaterialCode(), item.getBrand(), item.getCategory(),
                        item.getMaterial(), item.getSpec(), item.getLength(), item.getUnit(),
                        item.getSourceInboundItemId(), item.getSourcePurchaseOrderItemId(),
                        item.getWarehouseName(), item.getBatchNo(), item.getQuantity(),
                        item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle(),
                        item.getWeightTon(), unitPrice, null
                ))
        );
    }

    private SalesOutbound auditedOutbound(
            String salesOrderNo,
            Long sourceSalesOrderItemId,
            BigDecimal weightTon,
            BigDecimal unitPrice
    ) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(21L);
        outbound.setOutboundNo("OB-PRICE-001");
        outbound.setSalesOrderNo(salesOrderNo);
        outbound.setCustomerName("客户A");
        outbound.setProjectName("项目A");
        outbound.setWarehouseName("一号库");
        outbound.setOutboundDate(LocalDate.of(2026, 4, 27));
        outbound.setStatus(StatusConstants.AUDITED);
        outbound.setTotalWeight(weightTon);
        outbound.setTotalAmount(weightTon.multiply(unitPrice));

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(22L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("8");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(3);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.500"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(weightTon);
        item.setUnitPrice(unitPrice);
        item.setAmount(weightTon.multiply(unitPrice));
        outbound.setItems(new ArrayList<>(List.of(item)));
        return outbound;
    }

    private SalesOrderItemMapper stubbedSalesOrderItemMapper() {
        SalesOrderItemMapper mapper = mock(SalesOrderItemMapper.class);
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(2);
            SalesOrderItemRequest source = invocation.getArgument(1);
            String materialCode = invocation.getArgument(4);
            Long effectiveWarehouseId = invocation.getArgument(6);
            java.math.BigDecimal weightTon = invocation.getArgument(7);
            java.math.BigDecimal pieceWeightTon = invocation.getArgument(8);
            item.setLineNo(invocation.getArgument(3));
            item.setMaterialCode(materialCode);
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setSourceInboundItemId(source.sourceInboundItemId());
            item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
            item.setWarehouseId(effectiveWarehouseId);
            item.setWarehouseName(source.warehouseName());
            item.setBatchNo(source.batchNo());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(source.quantityUnit());
            item.setPieceWeightTon(pieceWeightTon);
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setWeightTon(weightTon);
            item.setUnitPrice(source.unitPrice());
            return null;
        }).when(mapper).applyItemFields(any(), any(), any(), anyInt(), anyString(), any(), any(), any(), any());
        return mapper;
    }

    private Map<String, TradeMaterialSnapshot> materialMap(String... materialCodes) {
        Map<String, TradeMaterialSnapshot> materialMap = new java.util.LinkedHashMap<>();
        for (String materialCode : materialCodes) {
            materialMap.put(materialCode, new TradeMaterialSnapshot(materialCode, Boolean.FALSE));
        }
        return materialMap;
    }
}
