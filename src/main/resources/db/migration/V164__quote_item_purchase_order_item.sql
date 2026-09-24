-- 报单比价: 采购订单吨位扣减由「订单级」下沉到「订单明细行级」, 支持按规格核对。
-- 说明:
--   1) 新增 purchase_order_item_id, 指向 po_purchase_order_item.id; 已开吨位按该明细行聚合,
--      同一订单下不同规格各自核算(剩余可开吨 = 该行订货量 - 该行全部报单已开吨位);
--   2) 回填既有数据: 按 (purchase_order_id, material, spec, length) 唯一匹配订单明细行,
--      匹配不唯一或匹配不到时保持 NULL(退回订单级展示, 不阻塞);
--   3) 不加外键, 与 purchase_order_id 保持一致的「计划态引用」取舍(订单/明细删除后保留历史关联与快照)。

ALTER TABLE public.mk_quote_item
    ADD COLUMN purchase_order_item_id bigint;

COMMENT ON COLUMN public.mk_quote_item.purchase_order_item_id IS '关联采购订单明细行标识(可空, 用于按规格扣减已开出吨位)';

CREATE INDEX idx_quote_item_purchase_order_item ON public.mk_quote_item (purchase_order_item_id);

-- 回填: 仅当 (order_id, material, spec, length) 唯一命中一条订单明细时写入, 避免错配。
WITH matched AS (
    SELECT qi.id AS quote_item_id,
           MIN(poi.id) AS purchase_order_item_id
    FROM public.mk_quote_item qi
    JOIN public.po_purchase_order_item poi
      ON poi.order_id = qi.purchase_order_id
     AND poi.material = qi.material
     AND poi.spec = qi.spec::varchar
     AND COALESCE(poi.length, '') = COALESCE(qi.length, '')
    WHERE qi.purchase_order_id IS NOT NULL
      AND qi.purchase_order_item_id IS NULL
    GROUP BY qi.id
    HAVING COUNT(poi.id) = 1
)
UPDATE public.mk_quote_item qi
SET purchase_order_item_id = matched.purchase_order_item_id
FROM matched
WHERE qi.id = matched.quote_item_id;
