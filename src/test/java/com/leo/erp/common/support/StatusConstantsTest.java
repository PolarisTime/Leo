package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusConstantsTest {

    @Test
    void shouldHaveCommonStatuses() {
        assertThat(StatusConstants.NORMAL).isEqualTo("正常");
        assertThat(StatusConstants.DISABLED).isEqualTo("禁用");
        assertThat(StatusConstants.DELETED).isEqualTo("已删除");
    }

    @Test
    void shouldHaveDocumentStatuses() {
        assertThat(StatusConstants.DRAFT).isEqualTo("草稿");
        assertThat(StatusConstants.AUDITED).isEqualTo("已审核");
        assertThat(StatusConstants.COMPLETED).isEqualTo("已完成");
    }

    @Test
    void shouldHaveFinancialStatuses() {
        assertThat(StatusConstants.PAID).isEqualTo("已付款");
        assertThat(StatusConstants.RECEIVED).isEqualTo("已收款");
    }

    @Test
    void shouldHaveAllowedActiveStatus() {
        assertThat(StatusConstants.ALLOWED_ACTIVE_STATUS).containsExactlyInAnyOrder("正常", "禁用");
    }

    @Test
    void shouldHaveAllowedAuditStatus() {
        assertThat(StatusConstants.ALLOWED_AUDIT_STATUS).containsExactlyInAnyOrder("草稿", "已审核");
    }

    @Test
    void shouldHaveDraftAuditTransitions() {
        assertThat(StatusConstants.DRAFT_AUDIT_TRANSITIONS).containsExactlyInAnyOrder(
                "草稿->已审核", "已审核->草稿"
        );
    }

    @Test
    void shouldHaveAllowedPurchaseOrderStatus() {
        assertThat(StatusConstants.ALLOWED_PURCHASE_ORDER_STATUS)
                .containsExactlyInAnyOrder("草稿", "已审核", "完成采购");
    }

    @Test
    void shouldHaveAllowedSalesOrderStatus() {
        assertThat(StatusConstants.ALLOWED_SALES_ORDER_STATUS)
                .containsExactlyInAnyOrder("草稿", "已审核", "完成销售");
    }

    @Test
    void shouldHaveAllowedContractStatus() {
        assertThat(StatusConstants.ALLOWED_CONTRACT_STATUS)
                .containsExactlyInAnyOrder("草稿", "执行中", "已签署", "已归档");
    }

    @Test
    void shouldHaveAllowedPaymentStatus() {
        assertThat(StatusConstants.ALLOWED_PAYMENT_STATUS)
                .containsExactlyInAnyOrder("草稿", "已付款");
    }

    @Test
    void shouldHaveAllowedReceiptStatus() {
        assertThat(StatusConstants.ALLOWED_RECEIPT_STATUS)
                .containsExactlyInAnyOrder("草稿", "已收款");
    }

    @Test
    void shouldHaveProtectedDocumentStatus() {
        assertThat(StatusConstants.PROTECTED_DOCUMENT_STATUS)
                .containsExactlyInAnyOrder(
                        "已审核",
                        "已完成",
                        "完成采购",
                        "完成入库",
                        "完成销售",
                        "已确认",
                        "已付款",
                        "已收款",
                        "已签署",
                        "已开票",
                        "已收票",
                        "已归档"
                );
    }

    @Test
    void shouldHaveFinanceSourceStatusPolicies() {
        assertThat(StatusConstants.INVOICEABLE_SALES_ORDER_STATUS)
                .containsExactlyInAnyOrder("已审核", "完成销售");
        assertThat(StatusConstants.INVOICEABLE_PURCHASE_ORDER_STATUS)
                .containsExactlyInAnyOrder("已审核", "完成采购");
        assertThat(StatusConstants.SETTLEABLE_CUSTOMER_STATEMENT_STATUS)
                .containsExactlyInAnyOrder("已确认");
        assertThat(StatusConstants.SETTLEABLE_SUPPLIER_STATEMENT_STATUS)
                .containsExactlyInAnyOrder("已确认");
        assertThat(StatusConstants.SETTLEABLE_FREIGHT_STATEMENT_STATUS)
                .containsExactlyInAnyOrder("已审核");
    }
}
