UPDATE sys_menu
SET menu_name = '结算主体管理',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'company-setting'
  AND deleted_flag = FALSE;
