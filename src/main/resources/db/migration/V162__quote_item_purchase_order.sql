-- 报单比价: 商品行「报单吨位」关联采购订单, 用于扣减已开出吨位。
-- 说明:
--   1) 商品行可关联一张采购订单; 关联后该行吨位计入该订单的「已开吨位」,
--      剩余可开吨 = 订单订货吨数(po_purchase_order.total_weight) - 该订单下全部报单行吨位之和;
--   2) purchase_order_id 可空(未关联), purchase_order_no 为保存时快照, 保证订单号变更/删除后仍可读;
--   3) 超额仅前端提示, 不做数据库约束, 也不回写采购订单。

ALTER TABLE public.mk_quote_item
    ADD COLUMN purchase_order_id bigint;

ALTER TABLE public.mk_quote_item
    ADD COLUMN purchase_order_no character varying(64);

COMMENT ON COLUMN public.mk_quote_item.purchase_order_id IS '关联采购订单标识(可空, 用于扣减已开出吨位)';
COMMENT ON COLUMN public.mk_quote_item.purchase_order_no IS '关联采购订单号快照(保存时写入)';

CREATE INDEX idx_quote_item_purchase_order ON public.mk_quote_item (purchase_order_id);
