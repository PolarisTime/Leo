UPDATE public.sys_menu
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN ('purchase-refund', 'supplier-refund-receipt');

UPDATE public.sys_menu_action
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN ('purchase-refund', 'supplier-refund-receipt');

UPDATE public.sys_role_permission
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code IN ('purchase-refund', 'supplier-refund-receipt');

UPDATE public.sys_no_rule
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code IN ('RULE_PR', 'RULE_SRR');

COMMENT ON TABLE public.po_purchase_refund
    IS '历史采购退款单归档；采购退款功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.po_purchase_refund_item
    IS '历史采购退款单明细归档；采购退款功能已退役，禁止新增业务数据';
