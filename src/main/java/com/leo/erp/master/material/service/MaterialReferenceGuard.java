package com.leo.erp.master.material.service;

import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.master.material.domain.entity.Material;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MaterialReferenceGuard {

    private final MasterDataReferenceGuard referenceGuard;

    public MaterialReferenceGuard(MasterDataReferenceGuard referenceGuard) {
        this.referenceGuard = referenceGuard;
    }

    void assertNoReferences(Material entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该商品", materialReferences(entity));
    }

    private List<ReferenceCheck> materialReferences(Material entity) {
        String materialCode = entity.getMaterialCode();
        return List.of(
                ReferenceCheck.any(
                        "po_purchase_order_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "po_purchase_inbound_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "so_sales_order_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "so_sales_outbound_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "lg_freight_bill_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "ct_purchase_contract_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "ct_sales_contract_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "st_customer_statement_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "st_supplier_statement_item",
                        "material_code",
                        materialCode
                ),
                ReferenceCheck.any(
                        "st_freight_statement_item",
                        "material_code",
                        materialCode
                )
        );
    }
}
