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
                ReferenceCheck.when(
                        "po_purchase_order_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM po_purchase_order parent "
                                + "WHERE parent.id = po_purchase_order_item.order_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "po_purchase_inbound_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM po_purchase_inbound parent "
                                + "WHERE parent.id = po_purchase_inbound_item.inbound_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "so_sales_order_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM so_sales_order parent "
                                + "WHERE parent.id = so_sales_order_item.order_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "so_sales_outbound_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM so_sales_outbound parent "
                                + "WHERE parent.id = so_sales_outbound_item.outbound_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "lg_freight_bill_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM lg_freight_bill parent "
                                + "WHERE parent.id = lg_freight_bill_item.bill_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "ct_purchase_contract_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM ct_purchase_contract parent "
                                + "WHERE parent.id = ct_purchase_contract_item.contract_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "ct_sales_contract_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM ct_sales_contract parent "
                                + "WHERE parent.id = ct_sales_contract_item.contract_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "st_customer_statement_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM st_customer_statement parent "
                                + "WHERE parent.id = st_customer_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "st_supplier_statement_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM st_supplier_statement parent "
                                + "WHERE parent.id = st_supplier_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "st_freight_statement_item",
                        "material_code",
                        materialCode,
                        "EXISTS (SELECT 1 FROM st_freight_statement parent "
                                + "WHERE parent.id = st_freight_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                )
        );
    }
}
