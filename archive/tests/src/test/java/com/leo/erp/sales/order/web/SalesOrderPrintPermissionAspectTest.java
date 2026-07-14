package com.leo.erp.sales.order.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.PermissionAspect;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderPrintExportService;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.service.print.SalesOrderPrintDocumentFactory;
import com.leo.erp.system.printtemplate.service.PrintXlsxExportLayoutProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOrderPrintPermissionAspectTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @Test
    void getPrintEndpointShouldPassThroughPermissionAspectAndRecordGuard() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.controller().exportPrintXlsx(1L, fixture.request()))
                .isSameAs(fixture.denied());

        verifyPermissionAndRecordChecks(fixture);
    }

    @Test
    void postPrintEndpointShouldPassThroughPermissionAspectAndRecordGuard() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.controller().exportPrintXlsx(1L, null, fixture.request()))
                .isSameAs(fixture.denied());

        verifyPermissionAndRecordChecks(fixture);
    }

    private Fixture fixture() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        BusinessException denied = new BusinessException(ErrorCode.FORBIDDEN, "无权打印该销售订单");
        doThrow(denied).when(accessGuard).assertCurrentUserCanAccess("sales-order", "print", order);
        PrintXlsxExportLayoutProvider layoutProvider = mock(PrintXlsxExportLayoutProvider.class);
        SalesOrderPrintExportService printService = new SalesOrderPrintExportService(
                repository,
                new SalesOrderPrintDocumentFactory(),
                layoutProvider,
                accessGuard
        );

        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(7L, "sales-order", "print")).thenReturn(true);
        when(permissionService.getUserDataScope(7L, "sales-order", "print"))
                .thenReturn(ResourcePermissionCatalog.SCOPE_ALL);
        when(permissionService.getDataScopeOwnerUserIds(7L, ResourcePermissionCatalog.SCOPE_ALL))
                .thenReturn(Set.of(7L));

        SalesOrderController target = new SalesOrderController(
                mock(SalesOrderService.class),
                printService,
                mock(com.leo.erp.sales.order.service.SalesOrderSourceCandidateService.class)
        );
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new PermissionAspect(permissionService));

        SecurityPrincipal principal = SecurityPrincipal.authenticated(7L, "print-user", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, "", principal.getAuthorities())
        );
        return new Fixture(
                proxyFactory.getProxy(),
                mock(HttpServletRequest.class),
                permissionService,
                accessGuard,
                layoutProvider,
                order,
                denied
        );
    }

    private void verifyPermissionAndRecordChecks(Fixture fixture) {
        verify(fixture.permissionService()).can(7L, "sales-order", "print");
        verify(fixture.accessGuard()).assertCurrentUserCanAccess("sales-order", "print", fixture.order());
        verifyNoInteractions(fixture.layoutProvider());
    }

    private record Fixture(
            SalesOrderController controller,
            HttpServletRequest request,
            PermissionService permissionService,
            ResourceRecordAccessGuard accessGuard,
            PrintXlsxExportLayoutProvider layoutProvider,
            SalesOrder order,
            BusinessException denied
    ) {
    }
}
