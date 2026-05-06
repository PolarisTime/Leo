UPDATE so_sales_order so
SET status = '完成销售'
WHERE so.deleted_flag = FALSE
  AND so.status <> '完成销售'
  AND EXISTS (
    SELECT 1
    FROM so_sales_outbound ob
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(ob.sales_order_no, ''), E'\\s*,\\s*') AS relation(order_no)
    WHERE ob.deleted_flag = FALSE
      AND ob.status = '已审核'
      AND relation.order_no = so.order_no
  );

UPDATE so_sales_order so
SET status = '已审核'
WHERE so.deleted_flag = FALSE
  AND so.status = '完成销售'
  AND NOT EXISTS (
    SELECT 1
    FROM so_sales_outbound ob
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(ob.sales_order_no, ''), E'\\s*,\\s*') AS relation(order_no)
    WHERE ob.deleted_flag = FALSE
      AND ob.status = '已审核'
      AND relation.order_no = so.order_no
  );
