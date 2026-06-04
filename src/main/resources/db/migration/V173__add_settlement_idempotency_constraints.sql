CREATE UNIQUE INDEX IF NOT EXISTS uk_fm_receipt_allocation_receipt_statement
    ON fm_receipt_allocation (receipt_id, source_statement_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fm_payment_allocation_payment_statement
    ON fm_payment_allocation (payment_id, source_statement_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_st_customer_statement_item_source_line
    ON st_customer_statement_item (statement_id, source_sales_order_item_id)
    WHERE source_sales_order_item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_st_supplier_statement_item_source_line
    ON st_supplier_statement_item (statement_id, source_inbound_item_id)
    WHERE source_inbound_item_id IS NOT NULL;
