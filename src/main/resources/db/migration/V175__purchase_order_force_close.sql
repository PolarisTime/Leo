-- 采购订单「强制结单」: 剩余未入库件数作废(不存在的货不再等入库), 订单手动置为完成采购。
-- 背景: 「完成采购」此前只能由采购入库审核在"每行入库件数 = 订货件数"时自动触发; 当剩余件
--       物理作废(报废/供应商不再供货)时, 订单会永久停在「已审核」, 并继续作为采购入库来源、
--       继续给报单比价提供可开吨位。
-- 说明:
--   1) 状态复用既有的「完成采购」: 下游判定(入库来源、供应商台账锁定、状态筛选、销售/报单
--      可用量)零改动, 强制结单与自动完成的区别由本组字段承载;
--   2) 留痕: force_close_reason(原因, 必填) / force_close_remaining_quantity(结单时未入库件数
--      快照, 即本次作废件数) / force_closed_by(操作人ID) / force_closed_name(操作人姓名快照) /
--      force_closed_at(结单时刻); 撤销强制结单时一并清空并回到「已审核」;
--   3) 结单后未入库件数按 0 对外返回(货已作废), 报单比价不再提供该单剩余吨位;
--   4) 不做破坏性变更: 既有列与约束保持原样, 新列均有默认值, 老数据 force_closed = false。

ALTER TABLE public.po_purchase_order
    ADD COLUMN force_closed boolean DEFAULT false NOT NULL,
    ADD COLUMN force_close_reason character varying(255),
    ADD COLUMN force_close_remaining_quantity integer,
    ADD COLUMN force_closed_by bigint,
    ADD COLUMN force_closed_name character varying(64),
    ADD COLUMN force_closed_at timestamp(0) without time zone;

COMMENT ON COLUMN public.po_purchase_order.force_closed IS
    '强制结单标记(true = 剩余未入库件数已作废, 订单由人工置为完成采购, 区别于入库审核自动完成)';
COMMENT ON COLUMN public.po_purchase_order.force_close_reason IS '强制结单原因(必填), 撤销结单时清空';
COMMENT ON COLUMN public.po_purchase_order.force_close_remaining_quantity IS
    '强制结单时的未入库件数快照(即本次作废件数), 撤销结单时清空';
COMMENT ON COLUMN public.po_purchase_order.force_closed_by IS '强制结单操作人ID(留痕), 撤销结单时清空';
COMMENT ON COLUMN public.po_purchase_order.force_closed_name IS '强制结单操作人姓名快照(留痕), 撤销结单时清空';
COMMENT ON COLUMN public.po_purchase_order.force_closed_at IS '强制结单时刻(留痕), 撤销结单时清空';

-- 列表「强制结单」标记与报单比价/入库来源排除强制结单单据都要按该标记过滤
CREATE INDEX idx_purchase_order_force_closed
    ON public.po_purchase_order (force_closed)
    WHERE force_closed = true;
