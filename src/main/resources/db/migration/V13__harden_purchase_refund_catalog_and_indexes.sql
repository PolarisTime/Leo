DROP INDEX public.idx_po_purchase_refund_supplier_date;

CREATE INDEX idx_po_purchase_refund_supplier_date
    ON public.po_purchase_refund (supplier_name, refund_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_settlement_date
    ON public.po_purchase_refund (settlement_company_id, refund_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_refund_no_trgm
    ON public.po_purchase_refund USING gin (refund_no public.gin_trgm_ops)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_purchase_order_no_trgm
    ON public.po_purchase_refund USING gin (purchase_order_no public.gin_trgm_ops)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_supplier_name_trgm
    ON public.po_purchase_refund USING gin (supplier_name public.gin_trgm_ops)
    WHERE deleted_flag = false;

UPDATE public.sys_menu
SET menu_name = '采购退款单',
    parent_code = 'purchase',
    route_path = '/purchase-refund',
    icon = 'FileSyncOutlined',
    sort_order = 3,
    menu_type = '菜单',
    status = '正常',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    deleted_flag = false
WHERE menu_code = 'purchase-refund';

UPDATE public.sys_no_rule
SET setting_name = '采购退款单编号规则',
    bill_name = '采购退款单',
    prefix = 'PR{yyyy}{seq}',
    date_rule = 'yyyy',
    serial_length = 6,
    reset_rule = 'YEARLY',
    sample_no = 'PR2026000001',
    status = '正常',
    remark = '采购退款单系统自动编号',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    deleted_flag = false
WHERE setting_code = 'RULE_PR';
