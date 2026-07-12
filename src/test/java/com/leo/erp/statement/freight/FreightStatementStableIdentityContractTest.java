package com.leo.erp.statement.freight;

import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.service.FreightStatementCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemView;
import com.leo.erp.statement.freight.service.FreightStatementView;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FreightStatementStableIdentityContractTest {

    @Test
    void statementShouldExposeCarrierIdentityAcrossAllLayers() {
        assertEntityField(FreightStatement.class, "carrierId", Long.class);
        for (Class<?> type : new Class[]{
                FreightStatementRequest.class,
                FreightStatementCommand.class,
                FreightStatementView.class,
                FreightStatementResponse.class,
                FreightStatementCandidateResponse.class
        }) {
            assertRecordComponent(type, "carrierId", Long.class);
        }
    }

    @Test
    void statementItemShouldExposeDirectSourceAndMasterIdentityAcrossAllLayers() {
        for (String field : new String[]{
                "sourceFreightBillId", "sourceFreightBillItemId", "customerId", "projectId",
                "materialId", "warehouseId"
        }) {
            assertEntityField(FreightStatementItem.class, field, Long.class);
            assertRecordComponent(FreightStatementItemCommand.class, field, Long.class);
            assertRecordComponent(FreightStatementItemView.class, field, Long.class);
        }
        assertEntityField(FreightStatementItem.class, "batchNoNormalized", String.class);
        assertRecordComponent(FreightStatementItemView.class, "batchNoNormalized", String.class);
        assertRecordComponent(FreightBillItemRequest.class, "sourceFreightBillId", Long.class);
        assertRecordComponent(FreightBillItemRequest.class, "sourceFreightBillItemId", Long.class);
        assertRecordComponent(FreightBillItemResponse.class, "sourceFreightBillId", Long.class);
        assertRecordComponent(FreightBillItemResponse.class, "sourceFreightBillItemId", Long.class);
    }

    private void assertRecordComponent(Class<?> recordType, String name, Class<?> type) {
        RecordComponent component = Arrays.stream(recordType.getRecordComponents())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElse(null);
        assertThat(component)
                .as(recordType.getSimpleName() + " 应包含字段 " + name)
                .isNotNull();
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
