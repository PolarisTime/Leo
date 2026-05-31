UPDATE sys_menu
SET menu_name = '编号规则',
    icon = 'ProfileOutlined',
    sort_order = 2,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'number-rules'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'general-setting'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 3,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'company-setting'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 4,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'department'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 5,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'access-control'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 6,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'session'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 7,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'api-key'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 8,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'security-key'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 9,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'print-template'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'operation-log'
  AND deleted_flag = FALSE;

UPDATE sys_menu
SET sort_order = 11,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'database'
  AND deleted_flag = FALSE;
