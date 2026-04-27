ALTER TABLE fm_invoice_issue
    ALTER COLUMN source_sales_order_nos TYPE VARCHAR(2000);

ALTER TABLE fm_invoice_receipt
    ALTER COLUMN source_purchase_order_nos TYPE VARCHAR(2000);
