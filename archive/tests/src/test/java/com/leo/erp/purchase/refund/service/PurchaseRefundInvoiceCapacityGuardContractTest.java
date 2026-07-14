package com.leo.erp.purchase.refund.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class PurchaseRefundInvoiceCapacityGuardContractTest {

    @Test
    void shouldProvideDedicatedInvoiceCapacityGuard() {
        assertThatCode(() -> Class.forName(
                "com.leo.erp.purchase.refund.service.PurchaseRefundInvoiceCapacityGuard"
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldExposeRefundCapacityAssertion() throws Exception {
        Class<?> guardType = Class.forName(
                "com.leo.erp.purchase.refund.service.PurchaseRefundInvoiceCapacityGuard"
        );

        assertThatCode(() -> guardType.getDeclaredMethod(
                "assertRefundFits",
                com.leo.erp.purchase.order.domain.entity.PurchaseOrder.class,
                PurchaseRefundCalculator.Calculation.class
        )).doesNotThrowAnyException();
    }
}
