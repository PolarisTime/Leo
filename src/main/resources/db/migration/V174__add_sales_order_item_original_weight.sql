ALTER TABLE so_sales_order_item
    ADD COLUMN IF NOT EXISTS original_weight_ton NUMERIC(14, 3);

COMMENT ON COLUMN so_sales_order_item.original_weight_ton IS '销售订单明细原始重量';
