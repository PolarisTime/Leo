ALTER TABLE so_sales_outbound_item
    ADD COLUMN IF NOT EXISTS source_sales_order_item_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_item_source_sales_order_item
    ON so_sales_outbound_item (source_sales_order_item_id);

WITH candidate_links AS (
    SELECT
        outbound_item.id AS outbound_item_id,
        sales_order_item.id AS source_sales_order_item_id,
        ROW_NUMBER() OVER (
            PARTITION BY outbound_item.id
            ORDER BY sales_order_item.line_no, sales_order_item.id
        ) AS row_num
    FROM so_sales_outbound_item outbound_item
    JOIN so_sales_outbound outbound
      ON outbound.id = outbound_item.outbound_id
    JOIN so_sales_order sales_order
      ON sales_order.deleted_flag = FALSE
     AND COALESCE(BTRIM(sales_order.order_no), '') <> ''
     AND POSITION(sales_order.order_no IN COALESCE(outbound.sales_order_no, '')) > 0
    JOIN so_sales_order_item sales_order_item
      ON sales_order_item.order_id = sales_order.id
     AND sales_order_item.material_code = outbound_item.material_code
     AND COALESCE(sales_order_item.brand, '') = COALESCE(outbound_item.brand, '')
     AND COALESCE(sales_order_item.category, '') = COALESCE(outbound_item.category, '')
     AND COALESCE(sales_order_item.material, '') = COALESCE(outbound_item.material, '')
     AND COALESCE(sales_order_item.spec, '') = COALESCE(outbound_item.spec, '')
     AND COALESCE(sales_order_item.length, '') = COALESCE(outbound_item.length, '')
     AND COALESCE(sales_order_item.batch_no, '') = COALESCE(outbound_item.batch_no, '')
    WHERE outbound_item.source_sales_order_item_id IS NULL
)
UPDATE so_sales_outbound_item outbound_item
SET source_sales_order_item_id = candidate_links.source_sales_order_item_id
FROM candidate_links
WHERE outbound_item.id = candidate_links.outbound_item_id
  AND candidate_links.row_num = 1;
