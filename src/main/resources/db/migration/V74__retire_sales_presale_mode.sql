-- Retire direct purchase-order sales while preserving historical rows that cannot
-- be mapped to one completed inbound item without ambiguity or capacity loss.
CREATE TEMP TABLE tmp_sales_presale_migration ON COMMIT DROP AS
WITH effective_inbound AS (
    SELECT inbound_item.source_purchase_order_item_id,
           MIN(inbound_item.id) AS inbound_item_id,
           MIN(inbound_item.quantity) AS inbound_quantity,
           COUNT(*) AS inbound_count
      FROM public.po_purchase_inbound_item inbound_item
      JOIN public.po_purchase_inbound inbound
        ON inbound.id = inbound_item.inbound_id
       AND inbound.deleted_flag = FALSE
       AND inbound.status IN ('已审核', '完成入库')
      JOIN public.po_purchase_order_item purchase_item
        ON purchase_item.id = inbound_item.source_purchase_order_item_id
      JOIN public.po_purchase_order purchase_order
        ON purchase_order.id = purchase_item.order_id
       AND purchase_order.deleted_flag = FALSE
       AND purchase_order.status = '完成采购'
     WHERE inbound_item.source_purchase_order_item_id IS NOT NULL
     GROUP BY inbound_item.source_purchase_order_item_id
),
direct_sales AS (
    SELECT sales_item.source_purchase_order_item_id,
           SUM(sales_item.quantity)::bigint AS allocated_quantity
      FROM public.so_sales_order_item sales_item
      JOIN public.so_sales_order sales_order
        ON sales_order.id = sales_item.order_id
       AND sales_order.deleted_flag = FALSE
     WHERE sales_item.source_purchase_order_item_id IS NOT NULL
     GROUP BY sales_item.source_purchase_order_item_id
),
inbound_sales AS (
    SELECT sales_item.source_inbound_item_id,
           SUM(sales_item.quantity)::bigint AS allocated_quantity
      FROM public.so_sales_order_item sales_item
      JOIN public.so_sales_order sales_order
        ON sales_order.id = sales_item.order_id
       AND sales_order.deleted_flag = FALSE
     WHERE sales_item.source_inbound_item_id IS NOT NULL
     GROUP BY sales_item.source_inbound_item_id
),
eligible_source AS (
    SELECT effective.source_purchase_order_item_id,
           effective.inbound_item_id
      FROM effective_inbound effective
      JOIN direct_sales direct
        ON direct.source_purchase_order_item_id = effective.source_purchase_order_item_id
      LEFT JOIN inbound_sales inbound_allocation
        ON inbound_allocation.source_inbound_item_id = effective.inbound_item_id
     WHERE effective.inbound_count = 1
       AND direct.allocated_quantity + COALESCE(inbound_allocation.allocated_quantity, 0)
           <= effective.inbound_quantity
)
SELECT sales_item.id AS sales_order_item_id,
       eligible.inbound_item_id
  FROM public.so_sales_order_item sales_item
  JOIN public.so_sales_order sales_order
    ON sales_order.id = sales_item.order_id
   AND sales_order.deleted_flag = FALSE
  JOIN eligible_source eligible
    ON eligible.source_purchase_order_item_id = sales_item.source_purchase_order_item_id
 WHERE sales_item.source_inbound_item_id IS NULL;

UPDATE public.so_sales_order_item sales_item
   SET source_inbound_item_id = migration.inbound_item_id,
       source_purchase_order_item_id = NULL
  FROM tmp_sales_presale_migration migration
 WHERE sales_item.id = migration.sales_order_item_id;

UPDATE public.po_purchase_order_item_piece_weight piece
   SET sales_order_item_id = NULL
  FROM tmp_sales_presale_migration migration
 WHERE piece.sales_order_item_id = migration.sales_order_item_id;

UPDATE public.so_sales_order
   SET sales_mode = 'NORMAL'
 WHERE sales_mode <> 'NORMAL';

ALTER TABLE public.so_sales_order
    ALTER COLUMN sales_mode SET DEFAULT 'NORMAL',
    DROP CONSTRAINT IF EXISTS chk_so_sales_order_sales_mode_v57;

ALTER TABLE public.so_sales_order
    ADD CONSTRAINT chk_so_sales_order_sales_mode_v74
        CHECK (sales_mode = 'NORMAL') NOT VALID;

ALTER TABLE public.so_sales_order
    VALIDATE CONSTRAINT chk_so_sales_order_sales_mode_v74;

COMMENT ON COLUMN public.so_sales_order.sales_mode IS
    '已废弃：销售订单仅支持入库后销售，兼容清理版本将删除此列';
COMMENT ON COLUMN public.so_sales_order_item.source_purchase_order_item_id IS
    '已废弃：仅保留无法无损映射的历史采购订单直连销售来源';
