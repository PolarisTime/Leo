-- 报单比价: 「已采购」不再作为独立布尔标记, 改为由行级关联采购订单推导(关联了即视为已采购)。
-- 说明:
--   1) V158 新增的 mk_quote_item.purchased 不再写入, 由 mk_quote_item.purchase_order_id 是否为空推导;
--   2) 该列为 V158 新引入且无下游依赖, 直接删除, 避免保留语义已被取代的死列;
--   3) 已采购不再需要前端保存, 请求体也不再接受该字段(服务端忽略未知字段)。

ALTER TABLE public.mk_quote_item
    DROP COLUMN IF EXISTS purchased;
