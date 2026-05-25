ALTER TABLE po_purchase_order_item ADD COLUMN IF NOT EXISTS actual_weight_ton NUMERIC(14,3);
ALTER TABLE po_purchase_order_item ADD COLUMN IF NOT EXISTS actual_piece_weight_ton NUMERIC(12,3);
COMMENT ON COLUMN po_purchase_order_item.actual_weight_ton IS '过磅实际总重';
COMMENT ON COLUMN po_purchase_order_item.actual_piece_weight_ton IS '过磅实际单件重';

-- 回填历史数据
WITH actual AS (
    SELECT ini.source_purchase_order_item_id,
           SUM(COALESCE(ini.weigh_weight_ton, ini.weight_ton)) AS total_actual
    FROM po_purchase_inbound_item ini
    JOIN po_purchase_inbound inbound ON inbound.id = ini.inbound_id AND inbound.deleted_flag = FALSE
    WHERE inbound.status IN ('已审核', '完成入库')
    GROUP BY ini.source_purchase_order_item_id
)
UPDATE po_purchase_order_item poi
SET actual_weight_ton = actual.total_actual,
    actual_piece_weight_ton = ROUND(actual.total_actual / poi.quantity, 3)
FROM actual
WHERE poi.id = actual.source_purchase_order_item_id;
