UPDATE so_sales_order so
SET status = '待完善',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE so.deleted_flag = FALSE
  AND so.status = '完成销售'
  AND EXISTS (
    SELECT 1
    FROM so_sales_outbound ob
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(ob.sales_order_no, ''), E'\\s*,\\s*') AS relation(order_no)
    WHERE ob.deleted_flag = FALSE
      AND ob.status = '已审核'
      AND relation.order_no = so.order_no
  )
  AND NOT EXISTS (
    SELECT 1
    FROM st_customer_statement st
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(st.source_order_nos, ''), E'\\s*,\\s*') AS relation(order_no)
    WHERE COALESCE(st.deleted_flag, FALSE) = FALSE
      AND relation.order_no = so.order_no
  );
