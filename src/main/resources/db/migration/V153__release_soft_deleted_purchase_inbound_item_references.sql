-- 释放已软删除采购入库单的残留明细对采购订单明细的 RESTRICT 引用。
--
-- 背景: 采购入库单软删除只标记表头 po_purchase_inbound.deleted_flag,
-- 明细行 po_purchase_inbound_item 此前全部保留; 而明细上
-- fk_po_purchase_inbound_item_source_identity (source_purchase_order_item_id -> po_purchase_order_item.id)
-- 为 ON DELETE RESTRICT, 导致来源采购订单明细被已删除入库单持续锁定,
-- 修改采购订单行集合时会触发数据库完整性错误(23001), 被应用层误报为字段校验失败。
--
-- 清理规则: 仅删除"无任何物理引用"的已删除入库单明细行。
-- 仍被销售订单明细 (so_sales_order_item.source_inbound_item_id) 或历史供应商对账明细
-- (st_supplier_statement_item.source_inbound_item_id) 引用的行保留, 由应用层守卫返回明确业务提示。
DELETE FROM public.po_purchase_inbound_item item
WHERE EXISTS (
        SELECT 1
        FROM public.po_purchase_inbound inbound
        WHERE inbound.id = item.inbound_id
          AND inbound.deleted_flag = TRUE
      )
  AND NOT EXISTS (
        SELECT 1
        FROM public.so_sales_order_item sales_item
        WHERE sales_item.source_inbound_item_id = item.id
      )
  AND NOT EXISTS (
        SELECT 1
        FROM public.st_supplier_statement_item statement_item
        WHERE statement_item.source_inbound_item_id = item.id
      );
