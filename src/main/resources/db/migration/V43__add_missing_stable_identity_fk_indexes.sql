-- Composite ownership foreign keys need the same leading column order on the
-- referencing side. These indexes are deliberately full so historical
-- soft-deleted rows remain searchable during parent updates and deletes.
CREATE INDEX idx_so_sales_order_project_customer_fk
    ON public.so_sales_order (project_id, customer_id);
CREATE INDEX idx_ct_sales_contract_project_customer_fk
    ON public.ct_sales_contract (project_id, customer_id);
CREATE INDEX idx_so_sales_outbound_project_customer_fk
    ON public.so_sales_outbound (project_id, customer_id);
CREATE INDEX idx_fm_invoice_issue_project_customer_fk
    ON public.fm_invoice_issue (project_id, customer_id);
CREATE INDEX idx_st_customer_statement_project_customer_fk
    ON public.st_customer_statement (project_id, customer_id);
CREATE INDEX idx_st_customer_stmt_item_project_customer_fk
    ON public.st_customer_statement_item (project_id, customer_id);
CREATE INDEX idx_fm_receipt_project_customer_fk
    ON public.fm_receipt (project_id, customer_id);
CREATE INDEX idx_lg_freight_bill_item_project_customer_fk
    ON public.lg_freight_bill_item (project_id, customer_id);
CREATE INDEX idx_st_freight_stmt_item_project_customer_fk
    ON public.st_freight_statement_item (project_id, customer_id);

-- Existing partial business indexes do not cover FK maintenance for all rows.
CREATE INDEX idx_st_freight_stmt_item_source_bill_pair_fk
    ON public.st_freight_statement_item (
        source_freight_bill_item_id,
        source_freight_bill_id
    );
CREATE INDEX idx_po_inbound_item_source_purchase_item_fk
    ON public.po_purchase_inbound_item (source_purchase_order_item_id);
CREATE INDEX idx_fm_invoice_issue_item_source_sales_item_fk
    ON public.fm_invoice_issue_item (source_sales_order_item_id);
CREATE INDEX idx_st_customer_stmt_item_source_sales_item_fk
    ON public.st_customer_statement_item (source_sales_order_item_id);
CREATE INDEX idx_st_supplier_stmt_item_source_inbound_item_fk
    ON public.st_supplier_statement_item (source_inbound_item_id);
