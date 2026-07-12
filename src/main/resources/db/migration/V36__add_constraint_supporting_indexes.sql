CREATE INDEX idx_st_freight_statement_item_customer_id
    ON public.st_freight_statement_item (customer_id);
CREATE INDEX idx_st_freight_statement_item_project_id
    ON public.st_freight_statement_item (project_id);

-- Composite parent keys allow the database to enforce ownership, not just existence.
CREATE UNIQUE INDEX uk_md_project_id_customer_identity
    ON public.md_project (id, customer_id);
CREATE UNIQUE INDEX uk_lg_freight_bill_item_id_bill_identity
    ON public.lg_freight_bill_item (id, bill_id);

-- Existing business indexes are mostly active-row partial indexes. Foreign-key
-- parent deletes must also find soft-deleted references, so these indexes are full.
CREATE INDEX idx_md_carrier_default_settlement_company_id_fk
    ON public.md_carrier (default_settlement_company_id);
CREATE INDEX idx_md_customer_default_settlement_company_id_fk
    ON public.md_customer (default_settlement_company_id);
CREATE INDEX idx_po_purchase_order_settlement_company_id_fk
    ON public.po_purchase_order (settlement_company_id);
CREATE INDEX idx_po_purchase_inbound_settlement_company_id_fk
    ON public.po_purchase_inbound (settlement_company_id);
CREATE INDEX idx_po_purchase_refund_settlement_company_id_fk
    ON public.po_purchase_refund (settlement_company_id);
CREATE INDEX idx_so_sales_order_settlement_company_id_fk
    ON public.so_sales_order (settlement_company_id);
CREATE INDEX idx_so_sales_outbound_settlement_company_id_fk
    ON public.so_sales_outbound (settlement_company_id);
CREATE INDEX idx_lg_freight_bill_settlement_company_id_fk
    ON public.lg_freight_bill (settlement_company_id);
CREATE INDEX idx_st_customer_statement_settlement_company_id_fk
    ON public.st_customer_statement (settlement_company_id);
CREATE INDEX idx_st_supplier_statement_settlement_company_id_fk
    ON public.st_supplier_statement (settlement_company_id);
CREATE INDEX idx_st_freight_statement_settlement_company_id_fk
    ON public.st_freight_statement (settlement_company_id);
CREATE INDEX idx_fm_invoice_issue_settlement_company_id_fk
    ON public.fm_invoice_issue (settlement_company_id);
CREATE INDEX idx_fm_invoice_receipt_settlement_company_id_fk
    ON public.fm_invoice_receipt (settlement_company_id);
CREATE INDEX idx_fm_receipt_settlement_company_id_fk
    ON public.fm_receipt (settlement_company_id);
CREATE INDEX idx_fm_payment_settlement_company_id_fk
    ON public.fm_payment (settlement_company_id);
CREATE INDEX idx_fm_supplier_refund_settlement_company_id_fk
    ON public.fm_supplier_refund_receipt (settlement_company_id);
CREATE INDEX idx_fm_ledger_adjustment_settlement_company_id_fk
    ON public.fm_ledger_adjustment (settlement_company_id);
CREATE INDEX idx_sys_print_template_settlement_company_id_fk
    ON public.sys_print_template (settlement_company_id);

CREATE INDEX idx_fm_receipt_source_customer_statement_id_fk
    ON public.fm_receipt (source_customer_statement_id);
CREATE INDEX idx_fm_payment_source_purchase_order_id_fk
    ON public.fm_payment (source_purchase_order_id);
CREATE INDEX idx_fm_ledger_adjustment_project_id_fk
    ON public.fm_ledger_adjustment (project_id);
CREATE INDEX idx_st_customer_statement_item_project_id_fk
    ON public.st_customer_statement_item (project_id);
