package com.leo.erp.purchase.order.web.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderImportCandidateResponseTest {

    @Test
    void shouldDefaultSettlementAndTotalsWhenUsingImportCandidateConstructor() {
        LocalDateTime orderDate = LocalDateTime.of(2026, 4, 26, 9, 30);

        PurchaseOrderImportCandidateResponse response = new PurchaseOrderImportCandidateResponse(
                1L,
                "PO-001",
                "供应商A",
                "李四",
                orderDate,
                "已审核",
                12
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.orderNo()).isEqualTo("PO-001");
        assertThat(response.supplierName()).isEqualTo("供应商A");
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.buyerName()).isEqualTo("李四");
        assertThat(response.orderDate()).isEqualTo(orderDate);
        assertThat(response.totalWeight()).isNull();
        assertThat(response.totalAmount()).isNull();
        assertThat(response.status()).isEqualTo("已审核");
        assertThat(response.importableQuantity()).isEqualTo(12);
    }
}
