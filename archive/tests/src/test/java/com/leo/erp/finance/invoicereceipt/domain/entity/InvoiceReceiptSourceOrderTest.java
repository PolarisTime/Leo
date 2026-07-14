package com.leo.erp.finance.invoicereceipt.domain.entity;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceReceiptSourceOrderTest {

    @Test
    void shouldStoreSourceOrderAuditFields() {
        InvoiceReceipt receipt = new InvoiceReceipt();
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 4, 10, 45);

        InvoiceReceiptSourceOrder sourceOrder = new InvoiceReceiptSourceOrder();
        sourceOrder.setId(1L);
        sourceOrder.setReceipt(receipt);
        sourceOrder.setPurchaseOrder(purchaseOrder);
        sourceOrder.setCreatedBy(2L);
        sourceOrder.setCreatedName("财务A");
        sourceOrder.setCreatedAt(createdAt);

        assertThat(sourceOrder.getId()).isEqualTo(1L);
        assertThat(sourceOrder.getReceipt()).isSameAs(receipt);
        assertThat(sourceOrder.getPurchaseOrder()).isSameAs(purchaseOrder);
        assertThat(sourceOrder.getCreatedBy()).isEqualTo(2L);
        assertThat(sourceOrder.getCreatedName()).isEqualTo("财务A");
        assertThat(sourceOrder.getCreatedAt()).isEqualTo(createdAt);
    }
}
