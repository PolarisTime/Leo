package com.leo.erp.finance.invoiceissue.domain.entity;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIssueSourceOrderTest {

    @Test
    void shouldStoreSourceOrderAuditFields() {
        InvoiceIssue issue = new InvoiceIssue();
        SalesOrder salesOrder = new SalesOrder();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 4, 10, 30);

        InvoiceIssueSourceOrder sourceOrder = new InvoiceIssueSourceOrder();
        sourceOrder.setId(1L);
        sourceOrder.setIssue(issue);
        sourceOrder.setSalesOrder(salesOrder);
        sourceOrder.setCreatedBy(2L);
        sourceOrder.setCreatedName("财务A");
        sourceOrder.setCreatedAt(createdAt);

        assertThat(sourceOrder.getId()).isEqualTo(1L);
        assertThat(sourceOrder.getIssue()).isSameAs(issue);
        assertThat(sourceOrder.getSalesOrder()).isSameAs(salesOrder);
        assertThat(sourceOrder.getCreatedBy()).isEqualTo(2L);
        assertThat(sourceOrder.getCreatedName()).isEqualTo("财务A");
        assertThat(sourceOrder.getCreatedAt()).isEqualTo(createdAt);
    }
}
