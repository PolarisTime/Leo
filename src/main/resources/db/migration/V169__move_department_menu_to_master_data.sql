UPDATE sys_menu
SET parent_code = 'master',
    sort_order = 8,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'department'
  AND deleted_flag = FALSE;
