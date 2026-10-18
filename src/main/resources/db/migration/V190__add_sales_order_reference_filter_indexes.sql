-- 补齐销售订单「下游关联」筛选路径上的缺失索引。
-- 背景:
--   1) so_sales_order 列表的 pendingOnly / referenced / referencedBy 筛选要反查
--      「该销售订单是否已被物流单或销售出库引用」。这两条反查分别落在
--      lg_freight_bill_source_order.source_sales_order_id 与
--      so_sales_outbound_item.source_sales_order_item_id 上。
--   2) lg_freight_bill_source_order 当前只有 (freight_bill_id, source_sales_order_id)
--      复合唯一索引，前导列是 freight_bill_id，无法支撑按 source_sales_order_id 反查；
--      V156 曾有意删除订单级唯一索引 (同一销售订单可拆分到多张物流单)，
--      因此这里只建非唯一的部分索引，不恢复唯一性。
--   3) 该索引必须与仓储层改写 (SalesOrderRepository 的 5 处 IN 子查询改为显式关联)
--      同批上线: 查询改写后规划器会走索引反查，缺索引会把 anti-join 退化为全表扫。
-- 幂等: 全部 IF NOT EXISTS。

-- 物流单来源反查: 按销售订单找引用了它的物流单
CREATE INDEX IF NOT EXISTS idx_freight_source_order_sales_order
    ON public.lg_freight_bill_source_order (source_sales_order_id)
    WHERE active_flag = TRUE AND deleted_flag = FALSE;

-- 销售出库明细反查: 按销售订单明细找由它出库的明细行
-- (该索引已存在时跳过; 列出以便全新环境与既有环境收敛到同一形态)
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_item_source_sales_order_item
    ON public.so_sales_outbound_item (source_sales_order_item_id);

-- 采购订单「下游关联」筛选的对称路径: 按采购订单明细找关联的销售明细
CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_source_purchase_order_item
    ON public.so_sales_order_item (source_purchase_order_item_id)
    WHERE source_purchase_order_item_id IS NOT NULL;
