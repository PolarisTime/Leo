-- Recalculate purchase actual weights from effective inbounds only.
WITH effective_weigh AS (
    SELECT ini.source_purchase_order_item_id AS item_id,
           SUM(ini.quantity)::integer AS received_quantity,
           SUM(ini.weigh_weight_ton)::numeric(18, 8) AS actual_weight_ton
      FROM po_purchase_inbound_item ini
      JOIN po_purchase_inbound inbound
        ON inbound.id = ini.inbound_id
       AND inbound.deleted_flag = FALSE
     WHERE inbound.status IN ('已审核', '完成入库')
       AND ini.weigh_weight_ton IS NOT NULL
     GROUP BY ini.source_purchase_order_item_id
)
UPDATE po_purchase_order_item item
   SET actual_weight_ton = effective.actual_weight_ton,
       actual_piece_weight_ton = CASE
           WHEN MOD((effective.actual_weight_ton * 100000000)::bigint, effective.received_quantity) = 0
               THEN (effective.actual_weight_ton / effective.received_quantity)::numeric(18, 8)
           ELSE NULL
       END
  FROM effective_weigh effective
 WHERE item.id = effective.item_id;

UPDATE po_purchase_order_item item
   SET actual_weight_ton = NULL,
       actual_piece_weight_ton = NULL
 WHERE (item.actual_weight_ton IS NOT NULL OR item.actual_piece_weight_ton IS NOT NULL)
   AND NOT EXISTS (
       SELECT 1
         FROM po_purchase_inbound_item ini
         JOIN po_purchase_inbound inbound
           ON inbound.id = ini.inbound_id
          AND inbound.deleted_flag = FALSE
        WHERE ini.source_purchase_order_item_id = item.id
          AND ini.weigh_weight_ton IS NOT NULL
          AND inbound.status IN ('已审核', '完成入库')
   );

-- Only rebuild sets without sales allocations. Allocated pieces keep their stable identity and weight.
CREATE TEMP TABLE tmp_purchase_piece_weight_repair ON COMMIT DROP AS
WITH effective_inbound AS (
    SELECT ini.source_purchase_order_item_id AS item_id,
           SUM(ini.quantity)::integer AS received_quantity,
           SUM(COALESCE(ini.weigh_weight_ton, ini.weight_ton))::numeric(18, 8) AS received_weight_ton
      FROM po_purchase_inbound_item ini
      JOIN po_purchase_inbound inbound
        ON inbound.id = ini.inbound_id
       AND inbound.deleted_flag = FALSE
     WHERE inbound.status IN ('已审核', '完成入库')
     GROUP BY ini.source_purchase_order_item_id
),
piece_summary AS (
    SELECT piece.purchase_order_item_id AS item_id,
           COUNT(*)::integer AS piece_count,
           COUNT(piece.sales_order_item_id)::integer AS allocated_count,
           COALESCE(SUM(piece.weight_ton), 0)::numeric(18, 8) AS piece_weight_ton
      FROM po_purchase_order_item_piece_weight piece
     GROUP BY piece.purchase_order_item_id
),
target AS (
    SELECT item.id AS item_id,
           item.quantity,
           ROUND((
               COALESCE(effective.received_weight_ton, 0)
               + GREATEST(item.quantity - COALESCE(effective.received_quantity, 0), 0)
                 * item.piece_weight_ton
           ) * 1000)::bigint AS total_weight_units,
           COALESCE(summary.piece_count, 0) AS piece_count,
           COALESCE(summary.allocated_count, 0) AS allocated_count,
           ROUND(COALESCE(summary.piece_weight_ton, 0) * 1000)::bigint AS current_weight_units
      FROM po_purchase_order_item item
      LEFT JOIN effective_inbound effective ON effective.item_id = item.id
      LEFT JOIN piece_summary summary ON summary.item_id = item.id
     WHERE item.quantity > 0
)
SELECT item_id, quantity, total_weight_units
  FROM target
 WHERE allocated_count = 0
   AND (piece_count <> quantity OR current_weight_units <> total_weight_units);

DELETE FROM po_purchase_order_item_piece_weight piece
 USING tmp_purchase_piece_weight_repair repair
 WHERE piece.purchase_order_item_id = repair.item_id
   AND piece.sales_order_item_id IS NULL;

-- IDs use the application Snowflake layout: epoch 2024-01-01, 10 machine bits, 12 sequence bits.
WITH slots AS (
    SELECT repair.item_id,
           repair.quantity,
           repair.total_weight_units,
           series.piece_no,
           ROW_NUMBER() OVER (ORDER BY repair.item_id, series.piece_no) - 1 AS global_no
      FROM tmp_purchase_piece_weight_repair repair
      CROSS JOIN LATERAL generate_series(1, repair.quantity) AS series(piece_no)
),
id_base AS MATERIALIZED (
    SELECT FLOOR(EXTRACT(EPOCH FROM transaction_timestamp()) * 1000)::bigint AS timestamp_ms
)
INSERT INTO po_purchase_order_item_piece_weight (
    id,
    purchase_order_item_id,
    piece_no,
    weight_ton,
    sales_order_item_id
)
SELECT (
           ((id_base.timestamp_ms + slots.global_no / 4096 - 1704038400000)::bigint << 22)
           | (1023::bigint << 12)
           | (slots.global_no % 4096)::bigint
       ) AS id,
       slots.item_id,
       slots.piece_no,
       (
           slots.total_weight_units / slots.quantity
           + CASE
               WHEN slots.piece_no <= MOD(slots.total_weight_units, slots.quantity) THEN 1
               ELSE 0
             END
       )::numeric / 1000 AS weight_ton,
       NULL
  FROM slots
  CROSS JOIN id_base;
