ALTER TABLE public.st_customer_statement_item
    ALTER COLUMN source_sales_order_item_id SET NOT NULL,
    DROP CONSTRAINT chk_st_customer_stmt_item_source_identity_nn;
ALTER TABLE public.st_supplier_statement_item
    ALTER COLUMN source_inbound_item_id SET NOT NULL,
    DROP CONSTRAINT chk_st_supplier_stmt_item_source_identity_nn;
ALTER TABLE public.fm_invoice_issue_item
    ALTER COLUMN source_sales_order_item_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_invoice_issue_item_source_identity_nn;
ALTER TABLE public.fm_invoice_receipt_item
    ALTER COLUMN source_purchase_order_item_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_invoice_receipt_item_source_identity_nn;
