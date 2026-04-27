ALTER TABLE md_material
    ADD COLUMN IF NOT EXISTS batch_no_enabled BOOLEAN;

UPDATE md_material
SET batch_no_enabled = FALSE
WHERE batch_no_enabled IS NULL;

ALTER TABLE md_material
    ALTER COLUMN batch_no_enabled SET DEFAULT FALSE;

ALTER TABLE md_material
    ALTER COLUMN batch_no_enabled SET NOT NULL;

ALTER TABLE po_purchase_order_item
    ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_po_purchase_order_item_batch
    ON po_purchase_order_item (batch_no);

ALTER TABLE so_sales_order_item
    ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_batch
    ON so_sales_order_item (batch_no);

ALTER TABLE st_customer_statement_item
    ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_st_customer_statement_item_batch
    ON st_customer_statement_item (batch_no);
