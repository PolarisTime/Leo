package com.leo.erp.statement;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TradingStatementStableIdentityContractTest {

    @Test
    void customerStatementShouldExposePartyAndInventoryIdentity() {
        assertEntityField(CustomerStatement.class, "customerId", Long.class);
        assertRecordComponent(CustomerStatementRequest.class, "customerId", Long.class);
        assertRecordComponent(CustomerStatementResponse.class, "customerId", Long.class);
        assertRecordComponent(CustomerStatementCandidateResponse.class, "customerId", Long.class);
        assertRecordComponent(CustomerStatementCandidateResponse.class, "projectId", Long.class);

        assertEntityField(CustomerStatementItem.class, "customerId", Long.class);
        assertEntityField(CustomerStatementItem.class, "projectId", Long.class);
        assertInventoryIdentity(
                CustomerStatementItem.class,
                CustomerStatementItemRequest.class,
                CustomerStatementItemResponse.class
        );
        assertRecordComponent(CustomerStatementItemRequest.class, "customerId", Long.class);
        assertRecordComponent(CustomerStatementItemRequest.class, "projectId", Long.class);
        assertRecordComponent(CustomerStatementItemResponse.class, "customerId", Long.class);
        assertRecordComponent(CustomerStatementItemResponse.class, "projectId", Long.class);
    }

    @Test
    void supplierStatementShouldExposeSupplierAndInventoryIdentity() {
        assertEntityField(SupplierStatement.class, "supplierId", Long.class);
        assertRecordComponent(SupplierStatementRequest.class, "supplierId", Long.class);
        assertRecordComponent(SupplierStatementResponse.class, "supplierId", Long.class);
        assertRecordComponent(SupplierStatementCandidateResponse.class, "supplierId", Long.class);
        assertInventoryIdentity(
                SupplierStatementItem.class,
                SupplierStatementItemRequest.class,
                SupplierStatementItemResponse.class
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
