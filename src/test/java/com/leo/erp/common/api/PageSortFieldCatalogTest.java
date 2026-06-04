package com.leo.erp.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageSortFieldCatalogTest {

    @Test
    void shouldReturnFieldsForKnownKey() {
        var fields = PageSortFieldCatalog.fields("material");
        assertThat(fields).contains("id", "materialCode", "brand");
    }

    @Test
    void shouldReturnFieldsForSupplier() {
        var fields = PageSortFieldCatalog.fields("supplier");
        assertThat(fields).contains("id", "supplierCode", "supplierName");
    }

    @Test
    void shouldReturnFieldsForCustomer() {
        var fields = PageSortFieldCatalog.fields("customer");
        assertThat(fields).contains("id", "customerCode", "customerName");
    }

    @Test
    void shouldReturnFieldsForPurchaseOrder() {
        var fields = PageSortFieldCatalog.fields("purchase-order");
        assertThat(fields).contains("id", "orderNo", "supplierName");
    }

    @Test
    void shouldReturnFieldsForSalesOrder() {
        var fields = PageSortFieldCatalog.fields("sales-order");
        assertThat(fields).contains("id", "orderNo", "customerName");
    }

    @Test
    void shouldReturnFieldsForLedgerAdjustment() {
        var fields = PageSortFieldCatalog.fields("ledger-adjustment");
        assertThat(fields).contains(
                "id",
                "adjustmentNo",
                "direction",
                "counterpartyCode",
                "counterpartyName",
                "adjustmentDate",
                "amount",
                "status"
        );
    }

    @Test
    void shouldReturnFieldsForReceivablePayable() {
        var fields = PageSortFieldCatalog.fields("receivable-payable");
        assertThat(fields).contains(
                "counterpartyCode",
                "counterpartyName",
                "reconciliationStatus",
                "recognizedAmount",
                "settledAmount",
                "balanceAmount",
                "status"
        );
    }

    @Test
    void shouldThrowForUnknownKey() {
        assertThatThrownBy(() -> PageSortFieldCatalog.fields("unknown-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown page sort field key");
    }

    @Test
    void shouldContainExpectedModuleKeys() {
        assertThat(PageSortFieldCatalog.fields("warehouse")).isNotEmpty();
        assertThat(PageSortFieldCatalog.fields("department")).isNotEmpty();
        assertThat(PageSortFieldCatalog.fields("user-account")).isNotEmpty();
        assertThat(PageSortFieldCatalog.fields("role-setting")).isNotEmpty();
        assertThat(PageSortFieldCatalog.fields("ledger-adjustment")).isNotEmpty();
    }
}
