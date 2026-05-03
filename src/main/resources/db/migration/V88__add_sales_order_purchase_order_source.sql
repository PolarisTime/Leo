ALTER TABLE so_sales_order
    ADD COLUMN IF NOT EXISTS purchase_order_no VARCHAR(256);

ALTER TABLE so_sales_order_item
    ADD COLUMN IF NOT EXISTS source_purchase_order_item_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_source_purchase_order_item
    ON so_sales_order_item (source_purchase_order_item_id);
