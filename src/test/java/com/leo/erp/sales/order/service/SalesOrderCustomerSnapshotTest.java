package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSelectionSupportTestDoubles;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderCustomerSnapshotTest {

    @Test
    void shouldWriteCanonicalCustomerSnapshotByCodeWhenCreatingOrder() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户标准名称", "项目标准名称")));
        Fixture fixture = fixture(customerRepository);

        fixture.service().create(request(
                " C001 ",
                " 客户标准名称 ",
                " 项目标准名称 ",
                null,
                null
        ));

        SalesOrder saved = fixture.savedOrder();
        assertThat(saved.getCustomerCode()).isEqualTo("C001");
        assertThat(saved.getCustomerName()).isEqualTo("客户标准名称");
        assertThat(saved.getProjectName()).isEqualTo("项目标准名称");
        verify(customerRepository).findByCustomerCodeAndDeletedFlagFalse("C001");
        verify(customerRepository, never())
                .findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(any(), any());
    }

    @Test
    void shouldRejectMissingCustomerCodeInsteadOfFallingBackToNames() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(
                "客户标准名称",
                "项目标准名称"
        )).thenReturn(Optional.of(customer("C001", "客户标准名称", "项目标准名称")));
        Fixture fixture = fixture(customerRepository);

        assertThatThrownBy(() -> fixture.service().create(request(
                " ",
                "客户标准名称",
                "项目标准名称",
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码不能为空");

        verify(fixture.saveService(), never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRejectUnknownCustomerCodeEvenWhenSettlementCompanyProvided() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("UNKNOWN"))
                .thenReturn(Optional.empty());
        Fixture fixture = fixture(customerRepository);

        assertThatThrownBy(() -> fixture.service().create(request(
                "UNKNOWN",
                "客户端客户名称",
                "客户端项目名称",
                9L,
                "客户端结算主体"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码不存在");

        verify(fixture.saveService(), never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRejectCustomerNameMismatchWhenCreatingOrder() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户标准名称", "项目标准名称")));
        Fixture fixture = fixture(customerRepository);

        assertThatThrownBy(() -> fixture.service().create(request(
                "C001",
                "客户端伪造客户名称",
                "项目标准名称",
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与客户主数据不一致");

        verify(fixture.saveService(), never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRejectProjectNameMismatchWhenUpdatingDraftOrder() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户标准名称", "项目标准名称")));
        Fixture fixture = fixture(customerRepository);
        SalesOrder existing = order("C001", "客户标准名称", "项目标准名称", StatusConstants.DRAFT);
        when(fixture.repository().findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> fixture.service().update(1L, request(
                "C001",
                "客户标准名称",
                "客户端伪造项目名称",
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目名称与客户主数据不一致");

        verify(fixture.saveService(), never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRejectCustomerMismatchDuringAuditedPricingUpdate() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户标准名称", "项目标准名称")));
        Fixture fixture = fixture(customerRepository);
        SalesOrder existing = order("C001", "已失效客户名称", "项目标准名称", StatusConstants.AUDITED);
        SalesOrderRequest request = request(
                "C001",
                "已失效客户名称",
                "项目标准名称",
                null,
                null,
                StatusConstants.AUDITED
        );
        when(fixture.repository().findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(fixture.protectedUpdatePolicy().allowsProtectedUpdate(existing, request)).thenReturn(true);
        when(fixture.auditedPricingService().isAuditedPricingUpdate(existing, request)).thenReturn(true);

        assertThatThrownBy(() -> fixture.service().update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与客户主数据不一致");

        verify(fixture.saveService(), never()).saveAuditedPricingUpdate(any(SalesOrder.class));
    }

    @Test
    void shouldRejectStaleCustomerSnapshotWhenAuditingDraftOrder() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户标准名称", "项目标准名称")));
        Fixture fixture = fixture(customerRepository);
        SalesOrder existing = order("C001", "已失效客户名称", "项目标准名称", StatusConstants.DRAFT);
        when(fixture.repository().findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> fixture.service().updateStatus(1L, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与客户主数据不一致");

        verify(fixture.saveService(), never()).saveStatus(any(SalesOrder.class));
    }

    private Fixture fixture(CustomerRepository customerRepository) {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        WarehouseSelectionSupportTestDoubles.stubWarehouseResolution(warehouseSelectionSupport);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService =
                new SalesOrderPurchaseAllocationService(purchaseItemQueryAppService, pieceWeightAppService);
        SalesOrderApplyService applyService = new SalesOrderApplyService(
                materialSupport,
                new SalesOrderSourceAllocationService(purchaseItemQueryAppService, itemRepository),
                new SalesOrderWeightResolver(pieceWeightAppService),
                purchaseAllocationService,
                new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport),
                mock(WorkflowTransitionGuard.class),
                customerRepository
        );
        SalesOrderSaveService saveService = mock(SalesOrderSaveService.class);
        when(saveService.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saveService.saveStatus(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saveService.saveAuditedPricingUpdate(any(SalesOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SalesOrderAuditedPricingService auditedPricingService = mock(SalesOrderAuditedPricingService.class);
        SalesOrderProtectedUpdatePolicy protectedUpdatePolicy = mock(SalesOrderProtectedUpdatePolicy.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1L, 11L, 12L);
        SalesOrderService service = new SalesOrderService(
                repository,
                idGenerator,
                mock(SalesOrderResponseAssembler.class),
                applyService,
                purchaseAllocationService,
                auditedPricingService,
                protectedUpdatePolicy,
                saveService,
                itemRepository,
                mock(SourceAllocationLockService.class)
        );
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);
        return new Fixture(
                service,
                repository,
                saveService,
                auditedPricingService,
                protectedUpdatePolicy
        );
    }

    private void stubSingleItemApply(TradeItemMaterialSupport materialSupport,
                                     WarehouseSelectionSupport warehouseSelectionSupport) {
        when(materialSupport.loadMaterialMap(List.of("M1")))
                .thenReturn(Map.of("M1", new TradeMaterialSnapshot("M1", Boolean.TRUE)));
        when(materialSupport.normalizeMaterialCode(any(), anyInt()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
    }

    private SalesOrderRequest request(String customerCode,
                                      String customerName,
                                      String projectName,
                                      Long settlementCompanyId,
                                      String settlementCompanyName) {
        return request(
                customerCode,
                customerName,
                projectName,
                settlementCompanyId,
                settlementCompanyName,
                StatusConstants.DRAFT
        );
    }

    private SalesOrderRequest request(String customerCode,
                                      String customerName,
                                      String projectName,
                                      Long settlementCompanyId,
                                      String settlementCompanyName,
                                      String status) {
        return new SalesOrderRequest(
                "SO-CUSTOMER-001",
                null,
                null,
                customerCode,
                customerName,
                1001L,
                projectName,
                settlementCompanyId,
                settlementCompanyName,
                LocalDate.of(2026, 7, 11),
                "销售A",
                status,
                null,
                List.of(itemRequest())
        );
    }

    private SalesOrderItemRequest itemRequest() {
        return new SalesOrderItemRequest(
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                null,
                null,
                "一号库",
                "B1",
                1,
                "支",
                new BigDecimal("0.100"),
                1,
                new BigDecimal("0.100"),
                new BigDecimal("4000.00"),
                new BigDecimal("400.00")
        );
    }

    private Customer customer(String customerCode, String customerName, String projectName) {
        Customer customer = new Customer();
        customer.setCustomerCode(customerCode);
        customer.setCustomerName(customerName);
        customer.setProjectName(projectName);
        customer.setDefaultSettlementCompanyId(7L);
        customer.setDefaultSettlementCompanyName("客户默认结算主体");
        return customer;
    }

    private SalesOrder order(String customerCode, String customerName, String projectName, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-CUSTOMER-001");
        order.setCustomerCode(customerCode);
        order.setCustomerName(customerName);
        order.setProjectId(1001L);
        order.setProjectName(projectName);
        order.setDeliveryDate(LocalDate.of(2026, 7, 11));
        order.setSalesName("销售A");
        order.setStatus(status);
        order.setItems(new ArrayList<>());
        return order;
    }

    private record Fixture(
            SalesOrderService service,
            SalesOrderRepository repository,
            SalesOrderSaveService saveService,
            SalesOrderAuditedPricingService auditedPricingService,
            SalesOrderProtectedUpdatePolicy protectedUpdatePolicy
    ) {
        SalesOrder savedOrder() {
            org.mockito.ArgumentCaptor<SalesOrder> captor = org.mockito.ArgumentCaptor.forClass(SalesOrder.class);
            verify(saveService).save(captor.capture());
            return captor.getValue();
        }
    }
}
