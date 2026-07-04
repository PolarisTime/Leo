package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderCompletionPolicyTest {

    private final SalesOrderCompletionPolicy policy = new SalesOrderCompletionPolicy();

    @Test
    void shouldSyncAfterAuditedOrderSave() {
        SalesOrder order = order(" " + StatusConstants.AUDITED + " ");

        assertThat(policy.shouldSyncAfterSave(order)).isTrue();
    }

    @Test
    void shouldSkipNonAuditedOrderSave() {
        assertThat(policy.shouldSyncAfterSave(order(StatusConstants.DRAFT))).isFalse();
    }

    @Test
    void shouldSkipNullOrder() {
        assertThat(policy.shouldSyncAfterSave(null)).isFalse();
    }

    @Test
    void shouldSkipOrderWithNullStatus() {
        assertThat(policy.shouldSyncAfterSave(order(null))).isFalse();
    }

    private SalesOrder order(String status) {
        SalesOrder order = new SalesOrder();
        order.setStatus(status);
        return order;
    }
}
