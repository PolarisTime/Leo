-- Backfill piece locks for active sales items sourced from completed purchase inbounds.
-- Allocation is deterministic by sales creation order and purchase piece number.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.po_purchase_order_item_piece_weight piece
          JOIN public.so_sales_order_item sales_item
            ON sales_item.id = piece.sales_order_item_id
          JOIN public.po_purchase_inbound_item inbound_item
            ON inbound_item.id = sales_item.source_inbound_item_id
         WHERE piece.purchase_order_item_id <> inbound_item.source_purchase_order_item_id
    ) THEN
        RAISE EXCEPTION 'V75: 销售逐件锁与采购来源不一致，拒绝自动补锁';
    END IF;
END $$;

CREATE TEMP TABLE tmp_sales_piece_needs ON COMMIT DROP AS
SELECT inbound_item.source_purchase_order_item_id AS purchase_order_item_id,
       sales_item.id AS sales_order_item_id,
       sales_order.id AS sales_order_id,
       sales_order.created_at AS sales_order_created_at,
       sales_item.line_no,
       sales_item.quantity,
       COUNT(piece.id)::integer AS locked_quantity,
       sales_item.quantity - COUNT(piece.id)::integer AS needed_quantity
  FROM public.so_sales_order_item sales_item
  JOIN public.so_sales_order sales_order
    ON sales_order.id = sales_item.order_id
   AND sales_order.deleted_flag = FALSE
  JOIN public.po_purchase_inbound_item inbound_item
    ON inbound_item.id = sales_item.source_inbound_item_id
  LEFT JOIN public.po_purchase_order_item_piece_weight piece
    ON piece.sales_order_item_id = sales_item.id
 WHERE sales_item.source_inbound_item_id IS NOT NULL
 GROUP BY inbound_item.source_purchase_order_item_id,
          sales_item.id,
          sales_order.id,
          sales_order.created_at,
          sales_item.line_no,
          sales_item.quantity;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tmp_sales_piece_needs WHERE needed_quantity < 0) THEN
        RAISE EXCEPTION 'V75: 销售明细已锁逐件数超过销售数量，拒绝自动补锁';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM (
              SELECT need.purchase_order_item_id,
                     SUM(need.needed_quantity) AS needed_quantity,
                     (
                         SELECT COUNT(*)
                           FROM public.po_purchase_order_item_piece_weight piece
                          WHERE piece.purchase_order_item_id = need.purchase_order_item_id
                            AND piece.sales_order_item_id IS NULL
                     ) AS available_quantity
                FROM tmp_sales_piece_needs need
               GROUP BY need.purchase_order_item_id
          ) capacity
         WHERE capacity.needed_quantity > capacity.available_quantity
    ) THEN
        RAISE EXCEPTION 'V75: 采购逐件可用数量不足，拒绝自动补锁';
    END IF;
END $$;

CREATE TEMP TABLE tmp_sales_piece_assignments ON COMMIT DROP AS
WITH needed_slots AS (
    SELECT need.purchase_order_item_id,
           need.sales_order_item_id,
           ROW_NUMBER() OVER (
               PARTITION BY need.purchase_order_item_id
               ORDER BY need.sales_order_created_at,
                        need.sales_order_id,
                        need.line_no,
                        need.sales_order_item_id,
                        slot.slot_no
           ) AS allocation_no
      FROM tmp_sales_piece_needs need
      CROSS JOIN LATERAL generate_series(1, need.needed_quantity) slot(slot_no)
),
available_pieces AS (
    SELECT piece.id AS piece_id,
           piece.purchase_order_item_id,
           ROW_NUMBER() OVER (
               PARTITION BY piece.purchase_order_item_id
               ORDER BY piece.piece_no, piece.id
           ) AS allocation_no
      FROM public.po_purchase_order_item_piece_weight piece
     WHERE piece.sales_order_item_id IS NULL
)
SELECT piece.piece_id,
       slot.sales_order_item_id
  FROM needed_slots slot
  JOIN available_pieces piece
    ON piece.purchase_order_item_id = slot.purchase_order_item_id
   AND piece.allocation_no = slot.allocation_no;

UPDATE public.po_purchase_order_item_piece_weight piece
   SET sales_order_item_id = assignment.sales_order_item_id
  FROM tmp_sales_piece_assignments assignment
 WHERE piece.id = assignment.piece_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM tmp_sales_piece_needs need
         WHERE (
             SELECT COUNT(*)
               FROM public.po_purchase_order_item_piece_weight piece
              WHERE piece.sales_order_item_id = need.sales_order_item_id
         ) <> need.quantity
    ) THEN
        RAISE EXCEPTION 'V75: 销售逐件补锁后数量不一致';
    END IF;
END $$;

WITH allocated_weight AS (
    SELECT piece.sales_order_item_id,
           SUM(piece.weight_ton)::numeric(18, 8) AS weight_ton
      FROM public.po_purchase_order_item_piece_weight piece
     WHERE piece.sales_order_item_id IS NOT NULL
     GROUP BY piece.sales_order_item_id
)
UPDATE public.so_sales_order_item sales_item
   SET weight_ton = allocated.weight_ton,
       amount = ROUND(allocated.weight_ton * sales_item.unit_price, 2)
  FROM allocated_weight allocated
  JOIN tmp_sales_piece_needs need
    ON need.sales_order_item_id = allocated.sales_order_item_id
 WHERE sales_item.id = allocated.sales_order_item_id;

WITH affected_orders AS (
    SELECT DISTINCT sales_order_id
      FROM tmp_sales_piece_needs
),
order_totals AS (
    SELECT sales_item.order_id,
           SUM(sales_item.weight_ton)::numeric(18, 8) AS total_weight,
           SUM(sales_item.amount)::numeric(14, 2) AS total_amount
      FROM public.so_sales_order_item sales_item
      JOIN affected_orders affected ON affected.sales_order_id = sales_item.order_id
     GROUP BY sales_item.order_id
)
UPDATE public.so_sales_order sales_order
   SET total_weight = totals.total_weight,
       total_amount = totals.total_amount
  FROM order_totals totals
 WHERE sales_order.id = totals.order_id;
