-- Typed finance/source invariants are introduced unvalidated so new writes are
-- protected immediately while existing rows remain subject to V41 validation.
ALTER TABLE public.fm_payment
    ADD CONSTRAINT chk_fm_payment_typed_counterparty
        CHECK (
            counterparty_type IN ('供应商', '物流商')
            AND counterparty_id IS NOT NULL
            AND counterparty_type = business_type
        ) NOT VALID;
ALTER TABLE public.fm_ledger_adjustment
    ADD CONSTRAINT chk_fm_ledger_adjustment_counterparty_identity_nn
        CHECK (counterparty_id IS NOT NULL) NOT VALID;
ALTER TABLE public.fm_payment_allocation
    ADD CONSTRAINT chk_fm_payment_allocation_typed_source
        CHECK (
            num_nonnulls(source_supplier_statement_id, source_freight_statement_id) = 1
            AND source_statement_id = COALESCE(
                source_supplier_statement_id,
                source_freight_statement_id
            )
        ) NOT VALID;
ALTER TABLE public.fm_receipt_allocation
    ADD CONSTRAINT chk_fm_receipt_allocation_typed_source
        CHECK (
            source_customer_statement_id IS NOT NULL
            AND source_statement_id = source_customer_statement_id
        ) NOT VALID;
ALTER TABLE public.so_sales_order_item
    ADD CONSTRAINT chk_so_sales_order_item_source_exclusive
        CHECK (num_nonnulls(source_inbound_item_id, source_purchase_order_item_id) <= 1)
        NOT VALID;

-- Validated helper checks let the final nullability stage reuse proof that no
-- historical nulls remain instead of rescanning each table under a stronger lock.
ALTER TABLE public.md_project
    ADD CONSTRAINT chk_md_project_customer_identity_nn
        CHECK (customer_id IS NOT NULL) NOT VALID;

ALTER TABLE public.ct_purchase_contract
    ADD CONSTRAINT chk_ct_purchase_contract_supplier_identity_nn
        CHECK (supplier_id IS NOT NULL) NOT VALID;
ALTER TABLE public.po_purchase_order
    ADD CONSTRAINT chk_po_purchase_order_supplier_identity_nn
        CHECK (supplier_id IS NOT NULL) NOT VALID;
ALTER TABLE public.po_purchase_inbound
    ADD CONSTRAINT chk_po_purchase_inbound_identity_nn
        CHECK (supplier_id IS NOT NULL AND warehouse_id IS NOT NULL) NOT VALID;
ALTER TABLE public.po_purchase_refund
    ADD CONSTRAINT chk_po_purchase_refund_supplier_identity_nn
        CHECK (supplier_id IS NOT NULL) NOT VALID;
ALTER TABLE public.fm_invoice_receipt
    ADD CONSTRAINT chk_fm_invoice_receipt_supplier_identity_nn
        CHECK (supplier_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_supplier_statement
    ADD CONSTRAINT chk_st_supplier_statement_supplier_identity_nn
        CHECK (supplier_id IS NOT NULL) NOT VALID;
ALTER TABLE public.fm_supplier_refund_receipt
    ADD CONSTRAINT chk_fm_supplier_refund_supplier_identity_nn
        CHECK (supplier_id IS NOT NULL) NOT VALID;

ALTER TABLE public.so_sales_order
    ADD CONSTRAINT chk_so_sales_order_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;
ALTER TABLE public.ct_sales_contract
    ADD CONSTRAINT chk_ct_sales_contract_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;
ALTER TABLE public.so_sales_outbound
    ADD CONSTRAINT chk_so_sales_outbound_identity_nn
        CHECK (
            customer_id IS NOT NULL
            AND project_id IS NOT NULL
            AND warehouse_id IS NOT NULL
        ) NOT VALID;
ALTER TABLE public.fm_invoice_issue
    ADD CONSTRAINT chk_fm_invoice_issue_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_customer_statement
    ADD CONSTRAINT chk_st_customer_statement_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_customer_statement_item
    ADD CONSTRAINT chk_st_customer_stmt_item_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;
ALTER TABLE public.fm_receipt
    ADD CONSTRAINT chk_fm_receipt_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;
ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT chk_lg_freight_bill_item_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT chk_st_freight_statement_item_party_identity_nn
        CHECK (customer_id IS NOT NULL AND project_id IS NOT NULL) NOT VALID;

ALTER TABLE public.ct_purchase_contract_item
    ADD CONSTRAINT chk_ct_purchase_contract_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.ct_sales_contract_item
    ADD CONSTRAINT chk_ct_sales_contract_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.po_purchase_order_item
    ADD CONSTRAINT chk_po_purchase_order_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.po_purchase_inbound_item
    ADD CONSTRAINT chk_po_purchase_inbound_item_stock_identity_nn
        CHECK (material_id IS NOT NULL AND warehouse_id IS NOT NULL) NOT VALID;
ALTER TABLE public.po_purchase_refund_item
    ADD CONSTRAINT chk_po_purchase_refund_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.so_sales_order_item
    ADD CONSTRAINT chk_so_sales_order_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.so_sales_outbound_item
    ADD CONSTRAINT chk_so_sales_outbound_item_stock_identity_nn
        CHECK (
            material_id IS NOT NULL
            AND warehouse_id IS NOT NULL
            AND source_sales_order_item_id IS NOT NULL
        ) NOT VALID;
ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT chk_lg_freight_bill_item_stock_source_identity_nn
        CHECK (
            material_id IS NOT NULL
            AND warehouse_id IS NOT NULL
            AND source_sales_outbound_item_id IS NOT NULL
        ) NOT VALID;
ALTER TABLE public.fm_invoice_issue_item
    ADD CONSTRAINT chk_fm_invoice_issue_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.fm_invoice_receipt_item
    ADD CONSTRAINT chk_fm_invoice_receipt_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_customer_statement_item
    ADD CONSTRAINT chk_st_customer_stmt_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_supplier_statement_item
    ADD CONSTRAINT chk_st_supplier_stmt_item_material_identity_nn
        CHECK (material_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT chk_st_freight_stmt_item_material_source_identity_nn
        CHECK (
            material_id IS NOT NULL
            AND source_freight_bill_id IS NOT NULL
            AND source_freight_bill_item_id IS NOT NULL
        ) NOT VALID;

ALTER TABLE public.lg_freight_bill
    ADD CONSTRAINT chk_lg_freight_bill_carrier_identity_nn
        CHECK (carrier_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_freight_statement
    ADD CONSTRAINT chk_st_freight_statement_carrier_identity_nn
        CHECK (carrier_id IS NOT NULL) NOT VALID;
