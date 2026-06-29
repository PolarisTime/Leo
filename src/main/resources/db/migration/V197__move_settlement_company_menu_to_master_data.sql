UPDATE sys_menu
SET menu_name = '结算主体管理',
    parent_code = 'master',
    route_path = '/company-setting',
    icon = 'AccountBookOutlined',
    sort_order = 8,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'company-setting'
  AND deleted_flag = FALSE;
