-- 报单比价改为「实时派生现货价」后，需要向用户展示这个价是**什么时候改的**。
--
-- 背景（2026-11-14 用户确认的重构口径）：
--   * 网价来源 = 行情同步（前端按参照日期/时段取价，保持不变）；
--   * 现货价来源 = 供应商价格表（实时派生，不再由报单比价手工导入/落快照）；
--   * 报单比价只允许填「规格 + 吨位」，价格全部只读；
--   * 界面需要显示「取到的现货价修改时间」。
--
-- 供应商价格表条目原先没有任何时间戳（只有表头 mk_supplier_price_list 有审计列），
-- 而表头的 updated_at 是"这张表任何一处改过"的时间，粒度太粗：改个备注就会让整张表的
-- 价格看起来"刚刚更新"。这里给条目补自己的 updated_at，并由 Hibernate @UpdateTimestamp
-- 在新写入/更新时维护；回填时以所属表头的 updated_at 作为最可靠的近似值。
ALTER TABLE public.mk_supplier_price_item
    ADD COLUMN IF NOT EXISTS updated_at timestamp(6) without time zone;

COMMENT ON COLUMN public.mk_supplier_price_item.updated_at IS '该条目最后一次改价/改状态的时间; 报单比价的「现货价修改时间」取此列';

UPDATE public.mk_supplier_price_item item
SET updated_at = list.updated_at
FROM public.mk_supplier_price_list list
WHERE item.list_id = list.id
  AND item.updated_at IS NULL;

-- 没有任何可用时间时的兜底：用当前时间，避免前端显示 null 造成"未知时间"。
UPDATE public.mk_supplier_price_item
SET updated_at = CURRENT_TIMESTAMP
WHERE updated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_supplier_price_item_updated_at
    ON public.mk_supplier_price_item USING btree (updated_at);
