DELETE FROM sys_role_action
WHERE menu_code = 'permission-management'
  AND action_code IN ('CREATE', 'EDIT', 'DELETE');

DELETE FROM sys_menu_action
WHERE menu_code = 'permission-management'
  AND action_code IN ('CREATE', 'EDIT', 'DELETE');
