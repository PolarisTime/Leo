-- Current freight bills originate directly from sales-order items. Repair only that active
-- relation model; legacy freight bills originating from sales-outbound items remain historical
-- and are resolved by the print runtime without being promoted into the current relation table.

LOCK TABLE public.lg_freight_bill_source_order IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.lg_freight_bill_item item
        JOIN public.lg_freight_bill bill
          ON bill.id = item.bill_id
         AND bill.deleted_flag = FALSE
        LEFT JOIN public.so_sales_order_item source_item
          ON source_item.id = item.source_sales_order_item_id
        LEFT JOIN public.so_sales_order source_order
          ON source_order.id = source_item.order_id
         AND source_order.deleted_flag = FALSE
        WHERE item.source_sales_order_item_id IS NOT NULL
          AND source_order.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'V102: 有效物流单存在无法映射的销售订单明细来源';
    END IF;

    IF EXISTS (
        WITH candidate_pairs AS (
            SELECT DISTINCT item.bill_id,
                            source_order.id AS source_order_id
            FROM public.lg_freight_bill_item item
            JOIN public.lg_freight_bill bill
              ON bill.id = item.bill_id
             AND bill.deleted_flag = FALSE
            JOIN public.so_sales_order_item source_item
              ON source_item.id = item.source_sales_order_item_id
            JOIN public.so_sales_order source_order
              ON source_order.id = source_item.order_id
             AND source_order.deleted_flag = FALSE
        ), conflicts AS (
            SELECT candidate.source_order_id
            FROM candidate_pairs candidate
            GROUP BY candidate.source_order_id
            HAVING COUNT(DISTINCT candidate.bill_id) > 1

            UNION ALL

            SELECT candidate.source_order_id
            FROM candidate_pairs candidate
            JOIN public.lg_freight_bill_source_order relation
              ON relation.source_sales_order_id = candidate.source_order_id
             AND relation.active_flag = TRUE
             AND relation.freight_bill_id <> candidate.bill_id
        )
        SELECT 1 FROM conflicts
    ) THEN
        RAISE EXCEPTION
            'V102: 同一销售订单关联多张有效物流单，无法安全补齐物流来源关系';
    END IF;
END $$;

WITH candidate_pairs AS (
    SELECT DISTINCT item.bill_id,
                    source_order.id AS source_order_id,
                    source_order.order_no AS source_order_no
    FROM public.lg_freight_bill_item item
    JOIN public.lg_freight_bill bill
      ON bill.id = item.bill_id
     AND bill.deleted_flag = FALSE
    JOIN public.so_sales_order_item source_item
      ON source_item.id = item.source_sales_order_item_id
    JOIN public.so_sales_order source_order
      ON source_order.id = source_item.order_id
     AND source_order.deleted_flag = FALSE
)
UPDATE public.lg_freight_bill_source_order relation
SET source_sales_order_no = candidate.source_order_no,
    active_flag = TRUE,
    deleted_flag = FALSE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
FROM candidate_pairs candidate
WHERE relation.freight_bill_id = candidate.bill_id
  AND relation.source_sales_order_id = candidate.source_order_id
  AND (relation.source_sales_order_no IS DISTINCT FROM candidate.source_order_no
       OR relation.active_flag = FALSE
       OR relation.deleted_flag = TRUE);

WITH candidate_pairs AS (
    SELECT DISTINCT item.bill_id,
                    source_order.id AS source_order_id,
                    source_order.order_no AS source_order_no,
                    bill.created_by,
                    bill.created_name,
                    bill.created_at
    FROM public.lg_freight_bill_item item
    JOIN public.lg_freight_bill bill
      ON bill.id = item.bill_id
     AND bill.deleted_flag = FALSE
    JOIN public.so_sales_order_item source_item
      ON source_item.id = item.source_sales_order_item_id
    JOIN public.so_sales_order source_order
      ON source_order.id = source_item.order_id
     AND source_order.deleted_flag = FALSE
), missing_pairs AS (
    SELECT candidate.*
    FROM candidate_pairs candidate
    WHERE NOT EXISTS (
        SELECT 1
        FROM public.lg_freight_bill_source_order relation
        WHERE relation.freight_bill_id = candidate.bill_id
          AND relation.source_sales_order_id = candidate.source_order_id
    )
), id_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id
    FROM public.lg_freight_bill_source_order
), numbered_pairs AS (
    SELECT missing.*,
           ROW_NUMBER() OVER (ORDER BY missing.bill_id, missing.source_order_id) AS row_number
    FROM missing_pairs missing
)
INSERT INTO public.lg_freight_bill_source_order (
    id,
    freight_bill_id,
    source_sales_order_id,
    source_sales_order_no,
    active_flag,
    deleted_flag,
    created_by,
    created_name,
    created_at
)
SELECT id_base.max_id + numbered.row_number,
       numbered.bill_id,
       numbered.source_order_id,
       numbered.source_order_no,
       TRUE,
       FALSE,
       numbered.created_by,
       numbered.created_name,
       numbered.created_at
FROM numbered_pairs numbered
CROSS JOIN id_base;

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_item_source_outbound_item
    ON public.lg_freight_bill_item (source_sales_outbound_item_id)
    WHERE source_sales_outbound_item_id IS NOT NULL;
