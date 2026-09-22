-- V156 引入的行级占用表 lg_freight_bill_source_item 与 lg_freight_bill_item 构成冗余双写：
-- 业务决策 2A 保证同一物流单内不重复引用同一来源销售订单明细，物流明细行按 id 物理删除，
-- 因此两表实际 1:1，数量真源统一为 lg_freight_bill_item.quantity（V77 起已带 source_sales_order_item_id）。
-- 本迁移删除冗余占用表；V156 已执行的 DROP INDEX uk_freight_source_order_active_sales 保持不变，
-- 不得恢复订单级唯一占用索引（一订单多物流单的拆分依赖该索引缺省）。
DROP TABLE IF EXISTS public.lg_freight_bill_source_item;
