-- 报单比价: 商品行新增「锁定」状态, 作为关联采购订单的前置门禁。
-- 说明:
--   1) 行级 locked: 未锁定不可关联采购订单; 解锁时服务端强制清除该行采购订单关联与快照;
--   2) 锁定语义 = 该行商品规格与报单吨位定稿(前端锁定后禁改规格/长度/吨位);
--   3) 回填: 既有已关联采购订单的历史行视为已锁定, 避免升级后关联被清空。

ALTER TABLE public.mk_quote_item
    ADD COLUMN locked boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.mk_quote_item.locked IS '是否锁定(未锁定不可关联采购订单; 解锁时清除关联)';

UPDATE public.mk_quote_item
SET locked = true
WHERE purchase_order_id IS NOT NULL;
