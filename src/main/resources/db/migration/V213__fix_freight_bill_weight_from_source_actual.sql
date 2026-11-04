-- 修复物流单重量口径（数据修复，不改表结构）。
--
-- 背景：物流单「行级拆分」提交 571a276e 把明细重量从「来源实际重量」改成「件重 × 数量」，
-- 件重是名义值（盘螺常统一按 2.3 吨/件），与实际过磅重量不一致时运费会被多计。
-- 生产实例：物流单 374859547451334656 按 9 × 2.3 = 20.7 计价，而来源销售订单与已审核出库单
-- 实际为 20.19 / 20.192（整单 40.382），多计 1.018 吨；对账单随之多算。
--
-- 处理口径（与修复后的 FreightBillApplyService.resolveWeightTon 一致）：
--   1) 明细重量 = 来源实际重量 × 本单数量 / 来源数量（来源重量缺失/为 0 时保持原值不动）；
--   2) 受影响物流单重算 total_weight，BY_TONNAGE 结算方式下重算 total_freight；
--   3) 引用这些物流单的对账单同步明细重量、合计重量、合计运费与未付金额。
-- 幂等：第二次执行时差异集合为空，不会产生任何更新。

-- 1. 找出需要修正的明细重量。
--    带 source_sales_order_item_id 的直接按来源明细；历史行没有该字段的，按
--    来源单号 + 品牌/品类/材质/规格/定尺唯一匹配（match_count = 1），避免一对多写错行。
CREATE TEMP TABLE tmp_freight_weight_fix ON COMMIT DROP AS
WITH candidates AS (
    SELECT i.id AS item_id,
           i.bill_id,
           i.weight_ton AS old_weight,
           ROUND(s.weight_ton * i.quantity::numeric / s.quantity, 3) AS new_weight,
           COUNT(*) OVER (PARTITION BY i.id) AS match_count
    FROM lg_freight_bill_item i
    JOIN so_sales_order_item s ON s.id = i.source_sales_order_item_id
    WHERE s.quantity > 0
      AND i.quantity <= s.quantity
    UNION ALL
    SELECT i.id,
           i.bill_id,
           i.weight_ton,
           ROUND(s.weight_ton * i.quantity::numeric / s.quantity, 3),
           COUNT(*) OVER (PARTITION BY i.id)
    FROM lg_freight_bill_item i
    JOIN so_sales_order o ON o.order_no = i.source_no
    JOIN so_sales_order_item s
      ON s.order_id = o.id
     AND s.brand = i.brand
     AND s.category = i.category
     AND s.material = i.material
     AND s.spec = i.spec
     AND COALESCE(s.length, '-') = COALESCE(i.length, '-')
    WHERE i.source_sales_order_item_id IS NULL
      AND s.quantity > 0
      AND i.quantity <= s.quantity
)
SELECT item_id, bill_id, new_weight
FROM candidates
WHERE match_count = 1
  AND new_weight IS NOT NULL
  AND new_weight > 0
  AND ABS(old_weight - new_weight) > 0.001;

UPDATE lg_freight_bill_item i
SET weight_ton = f.new_weight
FROM tmp_freight_weight_fix f
WHERE i.id = f.item_id;

-- 2. 受影响物流单的合计重量/运费。
UPDATE lg_freight_bill b
SET total_weight = t.total_weight,
    total_freight = CASE
        WHEN b.settlement_mode = 'BY_TONNAGE' THEN ROUND(t.total_weight * b.unit_price, 2)
        ELSE b.total_freight
    END
FROM (
    SELECT i.bill_id, ROUND(SUM(i.weight_ton), 3) AS total_weight
    FROM lg_freight_bill_item i
    WHERE i.bill_id IN (SELECT bill_id FROM tmp_freight_weight_fix)
    GROUP BY i.bill_id
) t
WHERE b.id = t.bill_id;

-- 3. 对账单明细：有来源明细 ID 的直接按 ID 同步。
UPDATE st_freight_statement_item si
SET weight_ton = i.weight_ton
FROM lg_freight_bill_item i
WHERE si.source_freight_bill_item_id = i.id
  AND i.bill_id IN (SELECT bill_id FROM tmp_freight_weight_fix)
  AND si.weight_ton <> i.weight_ton;

-- 3.1 历史对账单明细没有来源明细 ID：按「同一物流单内行序」一一对应，
--     并额外要求该物流单在明细行数与对账单行数一致时才同步（部分导入的对账单不做猜测）。
WITH bill_items AS (
    SELECT i.id,
           i.bill_id,
           ROW_NUMBER() OVER (PARTITION BY i.bill_id ORDER BY i.line_no) AS rn,
           i.weight_ton
    FROM lg_freight_bill_item i
    WHERE i.bill_id IN (SELECT bill_id FROM tmp_freight_weight_fix)
),
bill_count AS (
    SELECT bill_id, COUNT(*) AS item_count
    FROM lg_freight_bill_item
    WHERE bill_id IN (SELECT bill_id FROM tmp_freight_weight_fix)
    GROUP BY bill_id
),
stmt_items AS (
    SELECT si.id,
           si.statement_id,
           si.source_freight_bill_id,
           ROW_NUMBER() OVER (
               PARTITION BY si.statement_id, si.source_freight_bill_id ORDER BY si.line_no
           ) AS rn
    FROM st_freight_statement_item si
    WHERE si.source_freight_bill_item_id IS NULL
      AND si.source_freight_bill_id IN (SELECT bill_id FROM tmp_freight_weight_fix)
),
stmt_count AS (
    SELECT statement_id, source_freight_bill_id, COUNT(*) AS item_count
    FROM st_freight_statement_item
    WHERE source_freight_bill_item_id IS NULL
      AND source_freight_bill_id IN (SELECT bill_id FROM tmp_freight_weight_fix)
    GROUP BY statement_id, source_freight_bill_id
)
UPDATE st_freight_statement_item si
SET weight_ton = bi.weight_ton
FROM stmt_items sm
JOIN bill_count bc ON bc.bill_id = sm.source_freight_bill_id
JOIN stmt_count sc
  ON sc.statement_id = sm.statement_id
 AND sc.source_freight_bill_id = sm.source_freight_bill_id
 AND sc.item_count = bc.item_count
JOIN bill_items bi ON bi.bill_id = sm.source_freight_bill_id AND bi.rn = sm.rn
WHERE si.id = sm.id
  AND si.weight_ton <> bi.weight_ton;

-- 4. 引用受影响物流单的对账单：重算合计重量、合计运费与未付金额。
CREATE TEMP TABLE tmp_freight_statement_fix ON COMMIT DROP AS
SELECT DISTINCT statement_id
FROM st_freight_statement_item
WHERE source_freight_bill_id IN (SELECT bill_id FROM tmp_freight_weight_fix);

WITH weights AS (
    SELECT statement_id, ROUND(SUM(weight_ton), 3) AS total_weight
    FROM st_freight_statement_item
    WHERE statement_id IN (SELECT statement_id FROM tmp_freight_statement_fix)
    GROUP BY statement_id
),
freights AS (
    SELECT sb.statement_id, ROUND(SUM(b.total_freight), 2) AS total_freight
    FROM (
        SELECT DISTINCT statement_id, source_freight_bill_id
        FROM st_freight_statement_item
        WHERE statement_id IN (SELECT statement_id FROM tmp_freight_statement_fix)
    ) sb
    JOIN lg_freight_bill b ON b.id = sb.source_freight_bill_id
    GROUP BY sb.statement_id
)
UPDATE st_freight_statement s
SET total_weight = w.total_weight,
    total_freight = f.total_freight,
    unpaid_amount = GREATEST(f.total_freight - s.paid_amount, 0)
FROM weights w
JOIN freights f ON f.statement_id = w.statement_id
WHERE s.id = w.statement_id
  AND (
      s.total_weight <> w.total_weight
      OR s.total_freight <> f.total_freight
      OR s.unpaid_amount <> GREATEST(f.total_freight - s.paid_amount, 0)
  );
