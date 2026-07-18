-- 采购入库直接导入当前编辑器，移除拆分批次和超差审核快照。
ALTER TABLE public.po_purchase_inbound
    DROP CONSTRAINT IF EXISTS fk_purchase_inbound_import_batch,
    DROP COLUMN IF EXISTS import_batch_id;

DROP TABLE IF EXISTS public.po_purchase_inbound_import_batch;

ALTER TABLE public.po_purchase_inbound_item
    DROP CONSTRAINT IF EXISTS chk_purchase_inbound_tolerance_percent,
    DROP CONSTRAINT IF EXISTS chk_purchase_inbound_tolerance_confirmation_complete,
    DROP CONSTRAINT IF EXISTS chk_purchase_inbound_tolerance_direction,
    DROP CONSTRAINT IF EXISTS chk_purchase_inbound_tolerance_reason,
    DROP CONSTRAINT IF EXISTS chk_purchase_inbound_tolerance_other_remark,
    DROP COLUMN IF EXISTS tolerance_direction,
    DROP COLUMN IF EXISTS tolerance_limit_percent,
    DROP COLUMN IF EXISTS tolerance_actual_percent,
    DROP COLUMN IF EXISTS tolerance_reason_code,
    DROP COLUMN IF EXISTS tolerance_remark,
    DROP COLUMN IF EXISTS tolerance_confirmed_by,
    DROP COLUMN IF EXISTS tolerance_confirmed_name,
    DROP COLUMN IF EXISTS tolerance_confirmed_at;

ALTER TABLE public.md_material_category
    DROP CONSTRAINT IF EXISTS chk_material_category_purchase_weigh_over_tolerance_percent,
    DROP CONSTRAINT IF EXISTS chk_material_category_purchase_weigh_under_tolerance_percent,
    DROP COLUMN IF EXISTS purchase_weigh_over_tolerance_percent,
    DROP COLUMN IF EXISTS purchase_weigh_under_tolerance_percent;

ALTER TABLE public.po_purchase_order_item
    DROP COLUMN IF EXISTS actual_piece_weight_ton;

DROP TABLE IF EXISTS public.po_purchase_order_item_piece_weight;
