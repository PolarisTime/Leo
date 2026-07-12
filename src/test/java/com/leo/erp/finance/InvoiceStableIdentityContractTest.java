package com.leo.erp.finance;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemResponse;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceiptItem;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemResponse;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceStableIdentityContractTest {

    @Test
    void invoiceIssueShouldExposeCustomerProjectAndInventoryIdentity() {
        assertEntityField(InvoiceIssue.class, "customerId", Long.class);
        assertEntityField(InvoiceIssue.class, "projectId", Long.class);
        assertRecordComponent(InvoiceIssueRequest.class, "customerId", Long.class);
        assertRecordComponent(InvoiceIssueRequest.class, "projectId", Long.class);
        assertRecordComponent(InvoiceIssueResponse.class, "customerId", Long.class);
        assertRecordComponent(InvoiceIssueResponse.class, "projectId", Long.class);
        assertInventoryIdentity(InvoiceIssueItem.class, InvoiceIssueItemRequest.class, InvoiceIssueItemResponse.class);
    }

    @Test
    void invoiceReceiptShouldExposeSupplierAndInventoryIdentity() {
        assertEntityField(InvoiceReceipt.class, "supplierId", Long.class);
        assertRecordComponent(InvoiceReceiptRequest.class, "supplierId", Long.class);
        assertRecordComponent(InvoiceReceiptResponse.class, "supplierId", Long.class);
        assertInventoryIdentity(
                InvoiceReceiptItem.class,
                InvoiceReceiptItemRequest.class,
                InvoiceReceiptItemResponse.class
        );
    }

    private void assertInventoryIdentity(Class<?> entityType, Class<?> requestType, Class<?> responseType) {
        assertEntityField(entityType, "materialId", Long.class);
        assertEntityField(entityType, "warehouseId", Long.class);
        assertEntityField(entityType, "batchNoNormalized", String.class);
        assertRecordComponent(requestType, "materialId", Long.class);
        assertRecordComponent(requestType, "warehouseId", Long.class);
        assertRecordComponent(responseType, "materialId", Long.class);
        assertRecordComponent(responseType, "warehouseId", Long.class);
        assertRecordComponent(responseType, "batchNoNormalized", String.class);
    }

    private void assertRecordComponent(Class<?> recordType, String name, Class<?> type) {
        RecordComponent component = Arrays.stream(recordType.getRecordComponents())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElse(null);
        assertThat(component).as(recordType.getSimpleName() + " 应包含字段 " + name).isNotNull();
        assertThat(component.getType()).isEqualTo(type);
    }

    private void assertEntityField(Class<?> entityType, String name, Class<?> type) {
        var field = Arrays.stream(entityType.getDeclaredFields())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst();
        assertThat(field).as(entityType.getSimpleName() + " 应包含字段 " + name).isPresent();
        assertThat(field.orElseThrow().getType()).isEqualTo(type);
    }
}
