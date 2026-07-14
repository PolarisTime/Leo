UPDATE public.sys_menu
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'supplier-statement';

UPDATE public.sys_menu_action
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'supplier-statement';

UPDATE public.sys_role_permission
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code = 'supplier-statement';

UPDATE public.sys_no_rule
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code IN (
    'RULE_SS',
    'SYS_SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE'
);

COMMENT ON TABLE public.st_supplier_statement
    IS '历史供应商对账单归档；供应商对账功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.st_supplier_statement_item
    IS '历史供应商对账明细归档；供应商对账功能已退役，禁止新增业务数据';
