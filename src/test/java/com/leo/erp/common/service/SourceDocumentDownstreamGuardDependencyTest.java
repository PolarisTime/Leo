package com.leo.erp.common.service;

import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.inbound.service.PurchaseInboundStatementGuard;
import com.leo.erp.purchase.order.service.PurchaseOrderDownstreamMutationGuard;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.sales.order.service.SalesOrderDownstreamMutationGuard;
import com.leo.erp.sales.order.service.SalesOrderService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDocumentDownstreamGuardDependencyTest {

    @Test
    void purchaseOrderServiceShouldRequireDownstreamMutationGuard() {
        assertThat(hasConstructorDependency(
                PurchaseOrderService.class,
                PurchaseOrderDownstreamMutationGuard.class
        )).isTrue();
    }

    @Test
    void purchaseInboundServiceShouldRequireStatementGuard() {
        assertThat(hasConstructorDependency(
                PurchaseInboundService.class,
                PurchaseInboundStatementGuard.class
        )).isTrue();
    }

    @Test
    void salesOrderServiceShouldRequireDownstreamMutationGuard() {
        assertThat(hasConstructorDependency(
                SalesOrderService.class,
                SalesOrderDownstreamMutationGuard.class
        )).isTrue();
    }

    private boolean hasConstructorDependency(Class<?> serviceType, Class<?> dependencyType) {
        return Arrays.stream(serviceType.getConstructors())
                .anyMatch(constructor -> Arrays.asList(constructor.getParameterTypes()).contains(dependencyType));
    }
}
