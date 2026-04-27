ALTER TABLE so_sales_order_item
    ADD COLUMN IF NOT EXISTS source_inbound_item_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_source_inbound_item
    ON so_sales_order_item (source_inbound_item_id);
