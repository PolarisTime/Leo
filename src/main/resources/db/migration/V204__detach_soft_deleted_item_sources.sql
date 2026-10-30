-- 软删除下游单据不再物理锁定上游明细（同类问题统一收口）。
--
-- 背景：所有单据删除都是软删除（只置 deleted_flag=true，明细行保留），而明细上的
-- source_*_item_id 通过 RESTRICT / NO ACTION 外键物理锁定上游明细。上游「编辑保存」
-- 经由 ManagedEntityItemSupport.syncById + orphanRemoval 物理删除被移除的行，于是
-- 「一张已删除的单据」会继续阻止上游改单，且落到 GlobalExceptionHandler 的
-- 409「数据被其他单据引用，无法删除或修改」——用户不知道是哪张已删单据。
--
-- 统一口径（与 V203 的采购入库一致）：
--   1) 下游单据**软删除时**由所属模块把自己的明细来源引用置空（应用层，见各 *DeleteService）；
--   2) 迁移期间把历史残留一并置空（本脚本第 2 段）；
--   3) 因此下列列必须允许 NULL：NULL = 来源行已删除/已解绑，只出现在已软删除的下游明细上。
--
-- 休眠模块（po_purchase_refund / st_supplier_statement）没有 Java 写路径，但生产库可能有
-- 历史残留，一并放宽 + 清理；fm_invoice_issue_item / fm_invoice_receipt_item 在库内 0 行且
-- 没有指向单据头的 FK（模块已移除），不在本脚本处理范围内。

-- ============ 0) 销售订单明细「来源必填」下移到应用层 ============
-- chk_so_sales_order_item_source_required_v57 要求 source_inbound_item_id 与
-- source_purchase_order_item_id 恰好一个非空。它无法表达「仅活跃销售订单需要」：
-- 销售订单被软删除后，其明细需要解绑来源（否则会反过来锁死采购订单/采购入库的明细行），
-- 解绑必然让两个来源都为空，被该 CHECK 拒绝。
-- 因此删除「必填」约束，只保留互斥约束（<=1）；「新增/修改活跃销售订单必须指定来源」
-- 改由 SalesOrderSaveValidations 在应用层校验（与原来的库级口径等价）。
ALTER TABLE public.so_sales_order_item
    DROP CONSTRAINT IF EXISTS chk_so_sales_order_item_source_required_v57;

-- ============ 1) 放宽 NOT NULL ============
ALTER TABLE public.so_sales_outbound_item
    ALTER COLUMN source_sales_order_item_id DROP NOT NULL;ALTER TABLE public.so_sales_return_item
    ALTER COLUMN source_sales_outbound_item_id DROP NOT NULL;
ALTER TABLE public.st_customer_statement_item
    ALTER COLUMN source_sales_order_item_id DROP NOT NULL;
ALTER TABLE public.st_freight_statement_item
    ALTER COLUMN source_freight_bill_item_id DROP NOT NULL;
ALTER TABLE public.po_purchase_refund_item
    ALTER COLUMN source_purchase_order_item_id DROP NOT NULL;
ALTER TABLE public.st_supplier_statement_item
    ALTER COLUMN source_inbound_item_id DROP NOT NULL;

COMMENT ON COLUMN public.so_sales_outbound_item.source_sales_order_item_id IS
    '来源销售订单明细 ID; 来源行被删除或出库单软删除后置为 NULL';
COMMENT ON COLUMN public.so_sales_return_item.source_sales_outbound_item_id IS
    '来源销售出库明细 ID; 来源行被删除或退货单软删除后置为 NULL';
COMMENT ON COLUMN public.st_customer_statement_item.source_sales_order_item_id IS
    '来源销售订单明细 ID; 来源行被删除或对账单软删除后置为 NULL';
COMMENT ON COLUMN public.st_freight_statement_item.source_freight_bill_item_id IS
    '来源物流单明细 ID; 来源行被删除或对账单软删除后置为 NULL';
COMMENT ON COLUMN public.po_purchase_refund_item.source_purchase_order_item_id IS
    '来源采购订单明细 ID; 来源行被删除或退款单软删除后置为 NULL';

-- ============ 2) 历史残留置空 ============
-- 只解绑「下游单据头已软删除」的明细；活跃单据的来源链路原样保留。
-- 每条 UPDATE 都带 IS NOT NULL，重复执行是 no-op。

UPDATE public.po_purchase_inbound_item item
   SET source_purchase_order_item_id = NULL
  FROM public.po_purchase_inbound head
 WHERE head.id = item.inbound_id
   AND head.deleted_flag = true
   AND item.source_purchase_order_item_id IS NOT NULL;

UPDATE public.so_sales_order_item item
   SET source_purchase_order_item_id = NULL
  FROM public.so_sales_order head
 WHERE head.id = item.order_id
   AND head.deleted_flag = true
   AND item.source_purchase_order_item_id IS NOT NULL;

UPDATE public.so_sales_order_item item
   SET source_inbound_item_id = NULL
  FROM public.so_sales_order head
 WHERE head.id = item.order_id
   AND head.deleted_flag = true
   AND item.source_inbound_item_id IS NOT NULL;

UPDATE public.so_sales_outbound_item item
   SET source_sales_order_item_id = NULL
  FROM public.so_sales_outbound head
 WHERE head.id = item.outbound_id
   AND head.deleted_flag = true
   AND item.source_sales_order_item_id IS NOT NULL;

UPDATE public.so_sales_return_item item
   SET source_sales_order_item_id = NULL
  FROM public.so_sales_return head
 WHERE head.id = item.return_id
   AND head.deleted_flag = true
   AND item.source_sales_order_item_id IS NOT NULL;

UPDATE public.so_sales_return_item item
   SET source_sales_outbound_item_id = NULL
  FROM public.so_sales_return head
 WHERE head.id = item.return_id
   AND head.deleted_flag = true
   AND item.source_sales_outbound_item_id IS NOT NULL;

UPDATE public.st_customer_statement_item item
   SET source_sales_order_item_id = NULL
  FROM public.st_customer_statement head
 WHERE head.id = item.statement_id
   AND head.deleted_flag = true
   AND item.source_sales_order_item_id IS NOT NULL;

UPDATE public.st_freight_statement_item item
   SET source_freight_bill_item_id = NULL
  FROM public.st_freight_statement head
 WHERE head.id = item.statement_id
   AND head.deleted_flag = true
   AND item.source_freight_bill_item_id IS NOT NULL;

UPDATE public.lg_freight_bill_item item
   SET source_sales_order_item_id = NULL
  FROM public.lg_freight_bill head
 WHERE head.id = item.bill_id
   AND head.deleted_flag = true
   AND item.source_sales_order_item_id IS NOT NULL;

-- 休眠模块：无写路径，但生产库可能存在历史残留，同样清理。
UPDATE public.po_purchase_refund_item item
   SET source_purchase_order_item_id = NULL
  FROM public.po_purchase_refund head
 WHERE head.id = item.refund_id
   AND head.deleted_flag = true
   AND item.source_purchase_order_item_id IS NOT NULL;

UPDATE public.st_supplier_statement_item item
   SET source_inbound_item_id = NULL
  FROM public.st_supplier_statement head
 WHERE head.id = item.statement_id
   AND head.deleted_flag = true
   AND item.source_inbound_item_id IS NOT NULL;
