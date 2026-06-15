package com.leo.erp.master.material.service;

import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.master.material.domain.entity.Material;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MaterialReferenceGuardTest {

    @Test
    void shouldCheckAllBusinessReferencesBeforeDelete() {
        MasterDataReferenceGuard referenceGuard = mock(MasterDataReferenceGuard.class);
        MaterialReferenceGuard guard = new MaterialReferenceGuard(referenceGuard);
        Material material = new Material();
        material.setMaterialCode("MAT-001");

        guard.assertNoReferences(material);

        ArgumentCaptor<List<ReferenceCheck>> captor = ArgumentCaptor.forClass(List.class);
        verify(referenceGuard).assertNoReferences(eq("该商品"), captor.capture());
        assertThat(captor.getValue()).hasSize(10);
        assertThat(captor.getValue())
                .extracting(ReferenceCheck::tableName)
                .containsExactly(
                        "po_purchase_order_item",
                        "po_purchase_inbound_item",
                        "so_sales_order_item",
                        "so_sales_outbound_item",
                        "lg_freight_bill_item",
                        "ct_purchase_contract_item",
                        "ct_sales_contract_item",
                        "st_customer_statement_item",
                        "st_supplier_statement_item",
                        "st_freight_statement_item"
                );
        assertThat(captor.getValue())
                .allSatisfy(check -> {
                    assertThat(check.columnName()).isEqualTo("material_code");
                    assertThat(check.value()).isEqualTo("MAT-001");
                });
    }
}
