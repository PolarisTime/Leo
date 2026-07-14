UPDATE public.sys_menu
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'ledger-adjustment';

UPDATE public.sys_menu_action
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'ledger-adjustment';

UPDATE public.sys_role_permission
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code = 'ledger-adjustment';

UPDATE public.sys_no_rule
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_LA';

COMMENT ON TABLE public.fm_ledger_adjustment IS
    '已停用的历史台账调整单；仅供只读审计，不再参与应收应付或供应商净额余额计算';

COMMENT ON COLUMN public.fm_ledger_adjustment.settlement_company_id IS
    '历史调整的结算主体标识；为空的旧记录仅保留在台账调整审计查询，不进入按结算主体查询的财务单据流';
