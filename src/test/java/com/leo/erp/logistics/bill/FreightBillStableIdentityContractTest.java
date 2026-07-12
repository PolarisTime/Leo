package com.leo.erp.logistics.bill;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.service.FreightBillSourceService;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillImportCandidateResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillStableIdentityContractTest {

    @Test
    void freightBillShouldExposeCarrierAndVehicleIdentity() {
        assertEntityField(FreightBill.class, "carrierId", Long.class);
        assertEntityField(FreightBill.class, "vehicleId", Long.class);
        assertRecordComponent(FreightBillRequest.class, "carrierId", Long.class);
        assertRecordComponent(FreightBillRequest.class, "vehicleId", Long.class);
        assertRecordComponent(FreightBillResponse.class, "carrierId", Long.class);
        assertRecordComponent(FreightBillResponse.class, "vehicleId", Long.class);
    }

    @Test
    void freightBillItemShouldExposeSourceMasterIdentity() {
        assertEntityField(FreightBillItem.class, "customerId", Long.class);
        assertEntityField(FreightBillItem.class, "projectId", Long.class);
        assertEntityField(FreightBillItem.class, "materialId", Long.class);
        assertEntityField(FreightBillItem.class, "warehouseId", Long.class);
        assertEntityField(FreightBillItem.class, "batchNoNormalized", String.class);

        for (String component : new String[]{"customerId", "projectId", "materialId", "warehouseId"}) {
            assertRecordComponent(FreightBillItemRequest.class, component, Long.class);
            assertRecordComponent(FreightBillItemResponse.class, component, Long.class);
        }
        assertRecordComponent(FreightBillItemResponse.class, "batchNoNormalized", String.class);
    }

    @Test
    void importCandidateShouldCarryOutboundMasterIdentity() {
        assertRecordComponent(FreightBillImportCandidateResponse.class, "customerId", Long.class);
        assertRecordComponent(FreightBillImportCandidateResponse.class, "projectId", Long.class);
        assertRecordComponent(FreightBillImportCandidateResponse.class, "warehouseId", Long.class);
    }

    @Test
    void sourceIdentityRepositoriesShouldExposeIntentQueries() {
        assertMethod(SalesOutboundRepository.class, "findAllWithItemsByItemIds");
        assertMethod(SalesOutboundRepository.class, "findSourceOutboundIdsByItemIds");
        assertMethod(FreightStatementRepository.class,
                "findAllBySourceFreightBillIdsExcludingCurrentStatement");
    }

    @Test
    void sourceValidationContextShouldResolveParentByLineNumber() {
        assertThat(Arrays.stream(FreightBillSourceService.SourceValidationContext.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("sourceOutboundAt"))
                .anyMatch(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class})))
                .isTrue();
        assertMethod(FreightBillSourceService.class, "assertSourceItemsAuditable");
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

    private void assertMethod(Class<?> type, String name) {
        assertThat(Arrays.stream(type.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .as(type.getSimpleName() + " 应包含意图查询 " + name)
                .contains(name);
    }
}
