-- Direct source rows are authoritative identities. Introduce unvalidated
-- helper checks first so new writes fail closed while historical rows are
-- verified separately in V45.
ALTER TABLE public.st_customer_statement_item
    ADD CONSTRAINT chk_st_customer_stmt_item_source_identity_nn
        CHECK (source_sales_order_item_id IS NOT NULL) NOT VALID;
ALTER TABLE public.st_supplier_statement_item
    ADD CONSTRAINT chk_st_supplier_stmt_item_source_identity_nn
        CHECK (source_inbound_item_id IS NOT NULL) NOT VALID;
ALTER TABLE public.fm_invoice_issue_item
    ADD CONSTRAINT chk_fm_invoice_issue_item_source_identity_nn
        CHECK (source_sales_order_item_id IS NOT NULL) NOT VALID;
ALTER TABLE public.fm_invoice_receipt_item
    ADD CONSTRAINT chk_fm_invoice_receipt_item_source_identity_nn
        CHECK (source_purchase_order_item_id IS NOT NULL) NOT VALID;

-- Polymorphic ledger identity remains type + ID. Only customer adjustments may
-- carry a project; supplier/carrier rows must not retain either project field.
ALTER TABLE public.fm_ledger_adjustment
    ADD CONSTRAINT chk_fm_ledger_adjustment_typed_party_project
        CHECK (
            counterparty_type IN ('客户', '供应商', '物流商')
            AND counterparty_id IS NOT NULL
            AND (
                counterparty_type = '客户'
                OR (
                    counterparty_type IN ('供应商', '物流商')
                    AND project_id IS NULL
                    AND NULLIF(BTRIM(project_name), '') IS NULL
                )
            )
        ) NOT VALID;
