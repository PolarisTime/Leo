-- Compatibility release: remove enforced cross-domain relationships while retaining
-- deprecated columns for rollback and historical inspection.
ALTER TABLE public.lg_freight_bill
    DROP CONSTRAINT IF EXISTS fk_lg_freight_bill_source_sales_order_v57;

ALTER TABLE public.lg_freight_bill_item
    DROP CONSTRAINT IF EXISTS fk_lg_freight_bill_item_source_identity,
    DROP CONSTRAINT IF EXISTS fk_lg_freight_bill_item_source_sales_order_item_v57,
    DROP CONSTRAINT IF EXISTS chk_lg_freight_bill_item_source_xor_v57,
    ALTER COLUMN source_sales_outbound_item_id DROP NOT NULL;

ALTER TABLE public.so_sales_outbound
    DROP CONSTRAINT IF EXISTS fk_so_sales_outbound_source_freight_bill_v57;

ALTER TABLE public.st_freight_statement_item
    DROP CONSTRAINT IF EXISTS fk_st_freight_stmt_item_source_outbound_identity;

DROP INDEX IF EXISTS public.idx_lg_freight_bill_source_sales_order;
DROP INDEX IF EXISTS public.idx_lg_freight_bill_item_source_sales_order_item;
DROP INDEX IF EXISTS public.idx_lg_freight_bill_item_source_outbound_item;
DROP INDEX IF EXISTS public.idx_so_sales_outbound_source_freight_bill;
DROP INDEX IF EXISTS public.idx_st_freight_statement_item_source_outbound_item;
DROP INDEX IF EXISTS public.uk_lg_freight_bill_active_sales_order;
DROP INDEX IF EXISTS public.uk_so_sales_outbound_active_freight_bill;

COMMENT ON COLUMN public.lg_freight_bill.source_sales_order_id IS
    '已废弃：物流模块不再引用销售订单，后续兼容清理版本删除';
COMMENT ON COLUMN public.lg_freight_bill_item.source_sales_order_item_id IS
    '已废弃：物流模块不再引用销售订单明细，后续兼容清理版本删除';
COMMENT ON COLUMN public.lg_freight_bill_item.source_sales_outbound_item_id IS
    '已废弃：物流模块不再引用销售出库明细，后续兼容清理版本删除';
COMMENT ON COLUMN public.so_sales_outbound.source_freight_bill_id IS
    '已废弃：销售出库不再引用物流单，后续兼容清理版本删除';
COMMENT ON COLUMN public.st_freight_statement_item.source_sales_outbound_item_id IS
    '已废弃：物流对账仅引用物流单明细，后续兼容清理版本删除';
