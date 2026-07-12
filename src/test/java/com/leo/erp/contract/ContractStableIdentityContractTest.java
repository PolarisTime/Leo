package com.leo.erp.contract;

import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContractItem;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemResponse;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.contract.sales.domain.entity.SalesContractItem;
import com.leo.erp.contract.sales.web.dto.SalesContractItemRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractItemResponse;
import com.leo.erp.contract.sales.web.dto.SalesContractRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ContractStableIdentityContractTest {

    @Test
    void purchaseContractShouldExposeSupplierAndMaterialIdentity() {
        assertEntityField(PurchaseContract.class, "supplierId", Long.class);
        assertEntityField(PurchaseContract.class, "supplierCode", String.class);
        assertEntityField(PurchaseContractItem.class, "materialId", Long.class);

        assertRecordComponent(PurchaseContractRequest.class, "supplierId", Long.class);
        assertRecordComponent(PurchaseContractRequest.class, "supplierCode", String.class);
        assertRecordComponent(PurchaseContractResponse.class, "supplierId", Long.class);
        assertRecordComponent(PurchaseContractResponse.class, "supplierCode", String.class);
        assertRecordComponent(PurchaseContractItemRequest.class, "materialId", Long.class);
        assertRecordComponent(PurchaseContractItemResponse.class, "materialId", Long.class);
    }

    @Test
    void salesContractShouldExposeCustomerProjectAndMaterialIdentity() {
        assertEntityField(SalesContract.class, "customerId", Long.class);
        assertEntityField(SalesContract.class, "customerCode", String.class);
        assertEntityField(SalesContract.class, "projectId", Long.class);
        assertEntityField(SalesContractItem.class, "materialId", Long.class);

        assertRecordComponent(SalesContractRequest.class, "customerId", Long.class);
        assertRecordComponent(SalesContractRequest.class, "customerCode", String.class);
        assertRecordComponent(SalesContractRequest.class, "projectId", Long.class);
        assertRecordComponent(SalesContractResponse.class, "customerId", Long.class);
        assertRecordComponent(SalesContractResponse.class, "customerCode", String.class);
        assertRecordComponent(SalesContractResponse.class, "projectId", Long.class);
        assertRecordComponent(SalesContractItemRequest.class, "materialId", Long.class);
        assertRecordComponent(SalesContractItemResponse.class, "materialId", Long.class);
    }

    private void assertRecordComponent(Class<?> recordType, String name, Class<?> type) {
        RecordComponent component = Arrays.stream(recordType.getRecordComponents())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(recordType.getSimpleName() + " 缺少 " + name));
        assertThat(component.getType()).isEqualTo(type);
    }

    private void assertEntityField(Class<?> entityType, String name, Class<?> type) {
        var field = Arrays.stream(entityType.getDeclaredFields())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst();
        assertThat(field)
                .as(entityType.getSimpleName() + " 应包含字段 " + name)
                .isPresent();
        assertThat(field.orElseThrow().getType()).isEqualTo(type);
    }
}
