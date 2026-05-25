package com.leo.erp.search.service;

import com.leo.erp.contract.purchase.service.PurchaseContractService;
import com.leo.erp.contract.sales.service.SalesContractService;
import com.leo.erp.finance.invoiceissue.service.InvoiceIssueService;
import com.leo.erp.finance.invoicereceipt.service.InvoiceReceiptService;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.statement.customer.service.CustomerStatementService;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.statement.supplier.service.SupplierStatementService;
import com.leo.erp.logistics.bill.service.FreightBillService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GlobalSearchServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @Test
    void searchAppliesPerModulePermissionAndDataScope() {
        PermissionService permissionService = mock(PermissionService.class);
        PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
        SalesOrderService salesOrderService = mock(SalesOrderService.class);
        GlobalSearchService service = createService(permissionService, purchaseOrderService, salesOrderService);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT))
                .thenReturn(Set.of(1L, 2L));

        when(purchaseOrderService.search("CG20260001", 6)).thenAnswer(invocation -> {
            DataScopeContext.Context context = DataScopeContext.current();
            assertThat(context).isNotNull();
            assertThat(context.resource()).isEqualTo("purchase-order");
            assertThat(context.scope()).isEqualTo(ResourcePermissionCatalog.SCOPE_DEPARTMENT);
            assertThat(context.ownerUserIds()).containsExactlyInAnyOrder(1L, 2L);
            return List.of(new PurchaseOrderResponse(
                    1L,
                    "CG20260001",
                    "供应商甲",
                    null,
                    "采购A",
                    null,
                    null,
                    "已审核",
                    null,
                    List.of()
            ));
        });

        List<GlobalSearchResponse> results = service.search("CG20260001", 20);

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.moduleKey()).isEqualTo("purchase-order");
            assertThat(item.primaryNo()).isEqualTo("CG20260001");
            assertThat(item.summary()).isEqualTo("供应商甲 / 采购A / 已审核");
            assertThat(item.matchedByTrackId()).isFalse();
        });
        verify(purchaseOrderService).search("CG20260001", 6);
        verifyNoInteractions(salesOrderService);
        assertThat(DataScopeContext.current()).isNull();
    }

    @Test
    void searchUsesDetailLookupForTrackId() {
        PermissionService permissionService = mock(PermissionService.class);
        PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
        SalesOrderService salesOrderService = mock(SalesOrderService.class);
        GlobalSearchService service = createService(
                permissionService,
                purchaseOrderService,
                salesOrderService
        );
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_SELF);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF))
                .thenReturn(Set.of(1L));
        when(purchaseOrderService.detail(1914876201459236001L)).thenReturn(new PurchaseOrderResponse(
                1914876201459236001L,
                "CG20260001",
                "供应商甲",
                null,
                "采购A",
                null,
                null,
                "已审核",
                null,
                List.of()
        ));

        List<GlobalSearchResponse> results = service.search("1914876201459236001", 20);

        verify(purchaseOrderService, never()).search(anyString(), anyInt());
        verify(purchaseOrderService).detail(1914876201459236001L);
        verifyNoInteractions(salesOrderService);
        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.trackId()).isEqualTo("1914876201459236001");
            assertThat(item.primaryNo()).isEqualTo("CG20260001");
            assertThat(item.matchedByTrackId()).isTrue();
        });
        assertThat(DataScopeContext.current()).isNull();
    }

    @Test
    void searchHonorsRequestedModuleKeys() {
        PermissionService permissionService = mock(PermissionService.class);
        PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
        SalesOrderService salesOrderService = mock(SalesOrderService.class);
        GlobalSearchService service = createService(permissionService, purchaseOrderService, salesOrderService);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_SELF);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF))
                .thenReturn(Set.of(1L));
        when(purchaseOrderService.search("CG20260001", 6)).thenReturn(List.of(new PurchaseOrderResponse(
                1L,
                "CG20260001",
                "供应商甲",
                null,
                "采购A",
                null,
                null,
                "已审核",
                null,
                List.of()
        )));

        List<GlobalSearchResponse> results = service.search("CG20260001", 20, List.of("purchase-order"));

        assertThat(results).singleElement().satisfies(item ->
                assertThat(item.moduleKey()).isEqualTo("purchase-order"));
        verify(purchaseOrderService).search("CG20260001", 6);
        verifyNoInteractions(salesOrderService);
    }

    private GlobalSearchService createService(PermissionService permissionService,
                                              PurchaseOrderService purchaseOrderService,
                                              SalesOrderService salesOrderService) {
        return new GlobalSearchService(
                permissionService,
                new ModulePermissionGuard(permissionService),
                purchaseOrderService,
                mock(PurchaseInboundService.class),
                salesOrderService,
                mock(SalesOutboundService.class),
                mock(FreightBillService.class),
                mock(PurchaseContractService.class),
                mock(SalesContractService.class),
                mock(SupplierStatementService.class),
                mock(CustomerStatementService.class),
                mock(FreightStatementService.class),
                mock(ReceiptService.class),
                mock(PaymentService.class),
                mock(InvoiceReceiptService.class),
                mock(InvoiceIssueService.class)
        );
    }

    private void authenticate(Long userId) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                userId,
                "leo",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );
    }
}
