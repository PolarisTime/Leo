DELETE FROM sys_role_action
WHERE menu_code = 'attachment-management';

DELETE FROM sys_menu_action
WHERE menu_code = 'attachment-management';

DELETE FROM sys_menu
WHERE menu_code = 'attachment-management';
