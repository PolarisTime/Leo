package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderApplyServiceTest {

    @Test
    void shouldApplyOrderWithSourceInboundAndDerivedHeaderValues() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                purchaseItemQueryAppService,
                pieceWeightAppService,
                salesOrderItemRepository,
                workflowTransitionGuard
        );

        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setStatus(StatusConstants.DRAFT);
        SalesOrderItem oldItem = new SalesOrderItem();
        oldItem.setId(99L);
        order.getItems().add(oldItem);

        SalesOrderRequest request = request(List.of(itemRequest(101L, null, 4)));

        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L)))
                .thenReturn(List.of(sourceInboundRecord(101L)));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(
                eq(List.of(101L)),
                eq(1L)
        )).thenReturn(List.of());

        AtomicLong nextId = new AtomicLong(11L);
        service.apply(order, request, nextId::getAndIncrement);

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "sales-order",
                StatusConstants.DRAFT,
                StatusConstants.AUDITED,
                StatusConstants.AUDITED,
                StatusConstants.SALES_COMPLETED
        );
        verify(pieceWeightAppService).releaseSalesOrderItems(List.of(99L));
        assertThat(order.getPurchaseInboundNo()).isEqualTo("PI-001");
        assertThat(order.getPurchaseOrderNo()).isEqualTo("PO-001");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.400");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1600.00");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(11L);
            assertThat(item.getLineNo()).isEqualTo(1);
            assertThat(item.getSourceInboundItemId()).isEqualTo(101L);
            assertThat(item.getAmount()).isEqualByComparingTo("1600.00");
        });
    }

    @Test
    void shouldApplyOrderWithoutSourceDocuments() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService,
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class)
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request(List.of(itemRequest(null, null, 2)));

        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        service.apply(order, request, new AtomicLong(21L)::getAndIncrement);

        assertThat(order.getPurchaseInboundNo()).isEqualTo("REQ-PI");
        assertThat(order.getPurchaseOrderNo()).isEqualTo("REQ-PO");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.200");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("800.00");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceInboundItemId()).isNull();
            assertThat(item.getSourcePurchaseOrderItemId()).isNull();
            assertThat(item.getWeightTon()).isEqualByComparingTo("0.200");
        });
        verify(pieceWeightAppService).releaseSalesOrderItems(List.of());
    }

    @Test
    void shouldUseExplicitSettlementCompanyInsteadOfCustomerDefault() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CompanySettingService companySettingService = mock(CompanySettingService.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService,
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                companySettingService
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                "C001",
                "客户A",
                1001L,
                "项目A",
                9L,
                null,
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                List.of(itemRequest(null, null, 2))
        );
        Customer customer = new Customer();
        customer.setDefaultSettlementCompanyId(7L);
        customer.setDefaultSettlementCompanyName("嘉兴颖捷建材有限公司");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer));
        when(companySettingService.requireActiveSettlementCompany(9L))
                .thenReturn(companySetting(9L, "TEST9"));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        service.apply(order, request, new AtomicLong(31L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(9L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("TEST9");
    }

    @Test
    void shouldUseCustomerDefaultSettlementCompanyWhenRequestHasNoSettlementCompany() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request(List.of(itemRequest(null, null, 2)));
        Customer customer = new Customer();
        customer.setDefaultSettlementCompanyId(7L);
        customer.setDefaultSettlementCompanyName("客户默认结算主体");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        service.apply(order, request, new AtomicLong(41L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(7L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("客户默认结算主体");
    }

    @Test
    void shouldLookupCustomerDefaultByCustomerAndProjectWhenCustomerCodeMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                null,
                " 客户A ",
                1001L,
                " 项目A ",
                null,
                null,
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = new Customer();
        customer.setDefaultSettlementCompanyId(8L);
        customer.setDefaultSettlementCompanyName("按客户项目匹配主体");
        when(customerRepository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc("客户A", "项目A"))
                .thenReturn(Optional.of(customer));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        service.apply(order, request, new AtomicLong(51L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(8L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("按客户项目匹配主体");
    }

    @Test
    void shouldLookupCustomerDefaultByNameWhenCustomerCodeIsBlank() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request("   ", " 客户A ", " 项目A ", null, null, List.of(itemRequest(null, null, 1)));
        Customer customer = new Customer();
        customer.setDefaultSettlementCompanyId(18L);
        customer.setDefaultSettlementCompanyName("空编码匹配主体");
        when(customerRepository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc("客户A", "项目A"))
                .thenReturn(Optional.of(customer));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(131L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(18L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("空编码匹配主体");
        verify(customerRepository, org.mockito.Mockito.never()).findByCustomerCodeAndDeletedFlagFalse(any());
    }

    @Test
    void shouldPreserveExistingSettlementCompanyForAuditedOrder() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.AUDITED);
        order.setSettlementCompanyId(3L);
        order.setSettlementCompanyName("原结算主体");
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        service.apply(order, request(List.of(itemRequest(null, null, 1))), new AtomicLong(61L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(3L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("原结算主体");
        verify(customerRepository, org.mockito.Mockito.never()).findByCustomerCodeAndDeletedFlagFalse(any());
    }

    @Test
    void shouldUseRequestSettlementCompanyNameWhenCompanySettingServiceMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                "C001",
                "客户A",
                1001L,
                "项目A",
                12L,
                "  手填结算主体  ",
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                List.of(itemRequest(null, null, 1))
        );
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        service.apply(order, request, new AtomicLong(71L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(12L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("手填结算主体");
    }

    @Test
    void shouldPreserveExistingSettlementCompanyForSalesCompletedOrder() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.SALES_COMPLETED);
        order.setSettlementCompanyId(13L);
        order.setSettlementCompanyName("已完成结算主体");
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request(List.of(itemRequest(null, null, 1))), new AtomicLong(81L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(13L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("已完成结算主体");
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldClearSettlementCompanyWhenCustomerRepositoryMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(14L);
        order.setSettlementCompanyName("旧结算主体");
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request(List.of(itemRequest(null, null, 1))), new AtomicLong(91L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isNull();
        assertThat(order.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldSkipCustomerLookupWhenCustomerNameBlank() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(15L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request(null, " ", "项目A", null, null, List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(101L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isNull();
        assertThat(order.getSettlementCompanyName()).isNull();
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldSkipCustomerLookupWhenCustomerNameMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(20L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request(null, null, "项目A", null, null, List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(151L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isNull();
        assertThat(order.getSettlementCompanyName()).isNull();
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldSkipCustomerLookupWhenProjectNameMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(16L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request(null, "客户A", null, null, null, List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(111L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isNull();
        assertThat(order.getSettlementCompanyName()).isNull();
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldSkipCustomerLookupWhenProjectNameBlank() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(21L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request(null, "客户A", " ", null, null, List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(161L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isNull();
        assertThat(order.getSettlementCompanyName()).isNull();
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldSetNullSettlementCompanyNameWhenRequestNameBlankAndCompanySettingServiceMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request("C001", "客户A", "项目A", 17L, "   ", List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(121L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(17L);
        assertThat(order.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldSetNullSettlementCompanyNameWhenRequestNameMissingAndCompanySettingServiceMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request("C001", "客户A", "项目A", 19L, null, List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(141L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(19L);
        assertThat(order.getSettlementCompanyName()).isNull();
    }

    private SalesOrderApplyService service(TradeItemMaterialSupport materialSupport,
                                           WarehouseSelectionSupport warehouseSelectionSupport,
                                           PurchaseItemQueryAppService purchaseItemQueryAppService,
                                           PurchaseItemPieceWeightAppService pieceWeightAppService,
                                           SalesOrderItemRepository salesOrderItemRepository,
                                           WorkflowTransitionGuard workflowTransitionGuard) {
        return service(
                materialSupport,
                warehouseSelectionSupport,
                purchaseItemQueryAppService,
                pieceWeightAppService,
                salesOrderItemRepository,
                workflowTransitionGuard,
                null,
                null
        );
    }

    private SalesOrderApplyService service(TradeItemMaterialSupport materialSupport,
                                           WarehouseSelectionSupport warehouseSelectionSupport,
                                           PurchaseItemQueryAppService purchaseItemQueryAppService,
                                           PurchaseItemPieceWeightAppService pieceWeightAppService,
                                           SalesOrderItemRepository salesOrderItemRepository,
                                           WorkflowTransitionGuard workflowTransitionGuard,
                                           CustomerRepository customerRepository,
                                           CompanySettingService companySettingService) {
        SalesOrderPurchaseAllocationService purchaseAllocationService =
                new SalesOrderPurchaseAllocationService(purchaseItemQueryAppService, pieceWeightAppService);
        return new SalesOrderApplyService(
                materialSupport,
                new SalesOrderSourceAllocationService(purchaseItemQueryAppService, salesOrderItemRepository),
                new SalesOrderWeightResolver(pieceWeightAppService),
                purchaseAllocationService,
                new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport),
                workflowTransitionGuard,
                customerRepository,
                companySettingService
        );
    }

    private SalesOrderRequest request(List<SalesOrderItemRequest> items) {
        return request("C001", "客户A", "项目A", null, null, items);
    }

    private SalesOrderRequest request(String customerCode,
                                      String customerName,
                                      String projectName,
                                      Long settlementCompanyId,
                                      String settlementCompanyName,
                                      List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                customerCode,
                customerName,
                1001L,
                projectName,
                settlementCompanyId,
                settlementCompanyName,
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                items
        );
    }

    private void stubSingleItemApply(TradeItemMaterialSupport materialSupport,
                                     WarehouseSelectionSupport warehouseSelectionSupport) {
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
    }

    private SalesOrderItemRequest itemRequest(Long sourceInboundItemId,
                                              Long sourcePurchaseOrderItemId,
                                              Integer quantity) {
        BigDecimal pieceWeightTon = new BigDecimal("0.100");
        BigDecimal weightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity));
        return new SalesOrderItemRequest(
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                sourceInboundItemId,
                sourcePurchaseOrderItemId,
                "一号库",
                "B1",
                quantity,
                "支",
                pieceWeightTon,
                1,
                weightTon,
                new BigDecimal("4000.00"),
                weightTon.multiply(new BigDecimal("4000.00"))
        );
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundRecord(Long id) {
        return new PurchaseItemQueryAppService.SourceInboundItemRecord(
                id,
                "PI-001",
                StatusConstants.AUDITED,
                "PO-001",
                10,
                null,
                "宝钢",
                "HRB400",
                "18",
                "M1",
                "螺纹钢",
                "吨",
                "一号库",
                "B1"
        );
    }

    private TradeMaterialSnapshot material() {
        return new TradeMaterialSnapshot("M1", Boolean.TRUE);
    }

    private CompanySetting companySetting(Long id, String companyName) {
        CompanySetting companySetting = new CompanySetting();
        companySetting.setId(id);
        companySetting.setCompanyName(companyName);
        return companySetting;
    }
}
