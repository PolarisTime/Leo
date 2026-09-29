-- 采购订单明细行「已开完」手动标记(用户定稿: 纯手动, 不保留任何自动吨位软提示/超量警告)
-- 背景: 比价选单时按采购订单明细行(规格)关联报价, 原先靠"订货吨数 - 已开吨位"的自动吨位提示判断
--       是否还能开; 用户已定稿改为**纯手动标记**: 由人在采购订单明细行上标"已开完"。
-- 说明:
--   1) 标记落在明细行(po_purchase_order_item)而不是订单头: 同一订单不同规格可分别标记;
--   2) 留痕: issued_done_by(操作人ID) / issued_done_name(操作人姓名快照) / issued_done_at(标记时刻),
--      取消标记时一并清空(状态回到"未标"); 幂等由应用层保证;
--   3) 不新增任何自动吨位/超量警告字段; 既有吨位字段(weight_ton/actual_weight_ton)保持原样。

ALTER TABLE public.po_purchase_order_item
    ADD COLUMN issued_done boolean DEFAULT false NOT NULL,
    ADD COLUMN issued_done_by bigint,
    ADD COLUMN issued_done_name character varying(64),
    ADD COLUMN issued_done_at timestamp(0) without time zone;

COMMENT ON COLUMN public.po_purchase_order_item.issued_done IS
    '已开完手动标记(true = 该明细行已开完, 比价选单默认隐藏); 纯手动, 与吨位无关, 无任何自动提示';
COMMENT ON COLUMN public.po_purchase_order_item.issued_done_by IS '标记操作人ID(留痕), 取消标记时清空';
COMMENT ON COLUMN public.po_purchase_order_item.issued_done_name IS '标记操作人姓名快照(留痕), 取消标记时清空';
COMMENT ON COLUMN public.po_purchase_order_item.issued_done_at IS '标记时刻(留痕), 取消标记时清空';

-- 比价选单按标记过滤(默认隐藏已开完)需要走索引
CREATE INDEX idx_purchase_order_item_issued_done
    ON public.po_purchase_order_item (issued_done)
    WHERE issued_done = true;
