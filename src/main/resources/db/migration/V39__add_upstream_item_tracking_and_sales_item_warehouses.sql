ALTER TABLE po_purchase_inbound_item
    ADD COLUMN source_purchase_order_item_id BIGINT NULL;

ALTER TABLE so_sales_order_item
    ADD COLUMN warehouse_name VARCHAR(128) NULL;

ALTER TABLE so_sales_outbound_item
    ADD COLUMN warehouse_name VARCHAR(128) NULL;
