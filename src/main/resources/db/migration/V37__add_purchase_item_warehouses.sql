ALTER TABLE po_purchase_order_item
    ADD COLUMN IF NOT EXISTS warehouse_name VARCHAR(128);

ALTER TABLE po_purchase_inbound_item
    ADD COLUMN IF NOT EXISTS warehouse_name VARCHAR(128);
