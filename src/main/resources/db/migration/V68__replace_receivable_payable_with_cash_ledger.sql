UPDATE public.sys_menu
SET menu_code = 'cash-ledger',
    menu_name = '资金流水',
    route_path = '/cash-ledger',
    icon = 'AccountBookOutlined',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'receivable-payable';

UPDATE public.sys_menu_action
SET menu_code = 'cash-ledger',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'receivable-payable';

UPDATE public.sys_menu_action
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'cash-ledger'
  AND action_code NOT IN ('VIEW', 'EXPORT', 'PRINT');

UPDATE public.sys_role_permission
SET resource_code = 'cash-ledger',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code = 'receivable-payable';

UPDATE public.sys_role_permission
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code = 'cash-ledger'
  AND action_code NOT IN ('read', 'export', 'print');
