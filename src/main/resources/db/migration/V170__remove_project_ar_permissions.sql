DELETE FROM sys_role_permission
WHERE resource_code = 'project-ar';

DELETE FROM sys_role_action
WHERE menu_code = 'project-ar';

DELETE FROM sys_menu_action
WHERE menu_code = 'project-ar';

DELETE FROM sys_menu
WHERE menu_code = 'project-ar'
   OR route_path = '/project-ar';
