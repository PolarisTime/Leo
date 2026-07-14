UPDATE public.sys_menu
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN ('contracts', 'purchase-contract', 'sales-contract');

UPDATE public.sys_menu_action
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN ('purchase-contract', 'sales-contract');

UPDATE public.sys_role_permission
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code IN ('purchase-contract', 'sales-contract');

UPDATE public.sys_no_rule
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code IN ('RULE_PC', 'RULE_SC');

COMMENT ON TABLE public.ct_purchase_contract
    IS '历史采购合同归档；采购合同功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.ct_purchase_contract_item
    IS '历史采购合同明细归档；采购合同功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.ct_contract_purchase_order
    IS '历史采购合同与采购订单关系归档；采购合同功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.ct_sales_contract
    IS '历史销售合同归档；销售合同功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.ct_sales_contract_item
    IS '历史销售合同明细归档；销售合同功能已退役，禁止新增业务数据';
