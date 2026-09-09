package com.leo.erp.system.company.service;

import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanySettingMutationGuardService {

    private final MasterDataReferenceGuard referenceGuard;

    public CompanySettingMutationGuardService(MasterDataReferenceGuard referenceGuard) {
        this.referenceGuard = referenceGuard;
    }

    void assertDeletable(CompanySetting entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该结算主体", List.of(
                ReferenceCheck.active("md_carrier", "default_settlement_company_id", entity.getId()),
                ReferenceCheck.active("md_customer", "default_settlement_company_id", entity.getId()),
                ReferenceCheck.active("md_project", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("po_purchase_order", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("po_purchase_inbound", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "po_purchase_inbound_item",
                        "settlement_company_id",
                        entity.getId(),
                        "po_purchase_inbound",
                        "inbound_id"
                ),
                ReferenceCheck.active("so_sales_order", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "so_sales_order_item",
                        "settlement_company_id",
                        entity.getId(),
                        "so_sales_order",
                        "order_id"
                ),
                ReferenceCheck.active("so_sales_outbound", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "so_sales_outbound_item",
                        "settlement_company_id",
                        entity.getId(),
                        "so_sales_outbound",
                        "outbound_id"
                ),
                ReferenceCheck.active("lg_freight_bill", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "lg_freight_bill_item",
                        "settlement_company_id",
                        entity.getId(),
                        "lg_freight_bill",
                        "bill_id"
                ),
                ReferenceCheck.active("st_customer_statement", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("st_supplier_statement", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("st_freight_statement", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "st_freight_statement_item",
                        "settlement_company_id",
                        entity.getId(),
                        "st_freight_statement",
                        "statement_id"
                ),
                ReferenceCheck.active("fm_receipt", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("fm_payment", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("fm_ledger_adjustment", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("sys_print_template", "settlement_company_id", entity.getId())
        ));
    }
}
