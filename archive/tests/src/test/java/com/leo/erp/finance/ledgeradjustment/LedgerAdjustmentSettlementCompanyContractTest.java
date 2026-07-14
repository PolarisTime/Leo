package com.leo.erp.finance.ledgeradjustment;

import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerAdjustmentSettlementCompanyContractTest {

    @Test
    void shouldExposeSettlementCompanySnapshotAcrossEntityAndDtos() throws NoSuchFieldException {
        assertColumn("settlementCompanyId", "settlement_company_id");
        assertColumn("settlementCompanyName", "settlement_company_name");

        assertThat(recordComponentNames(LedgerAdjustmentRequest.class))
                .containsSubsequence(
                        "counterpartyName",
                        "settlementCompanyId",
                        "settlementCompanyName",
                        "projectId"
                );
        assertThat(recordComponentNames(LedgerAdjustmentResponse.class))
                .containsSubsequence(
                        "counterpartyName",
                        "settlementCompanyId",
                        "settlementCompanyName",
                        "projectId"
                );
    }

    @Test
    void shouldRequireSettlementCompanyOnAdjustmentRequest() {
        RecordComponent settlementCompanyId = Arrays.stream(LedgerAdjustmentRequest.class.getRecordComponents())
                .filter(component -> component.getName().equals("settlementCompanyId"))
                .findFirst()
                .orElseThrow();

        assertThat(settlementCompanyId.getAccessor().getAnnotation(NotNull.class)).isNotNull();
    }

    private void assertColumn(String fieldName, String columnName) throws NoSuchFieldException {
        Column column = LedgerAdjustment.class.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
    }

    private java.util.List<String> recordComponentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
