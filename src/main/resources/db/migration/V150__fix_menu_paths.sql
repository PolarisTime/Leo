-- Fix menu path singular/plural mismatches and missing menus
UPDATE sys_menu SET menu_code = 'material-categories', route_path = '/material-categories'
WHERE menu_code = 'material-category' AND deleted_flag = false;

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type, status, created_by, created_name)
SELECT 90001, 'number-rules', '编号规则', 'system', '/number-rules', 'OrderedListOutlined', 50, '菜单', '正常', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'number-rules' AND deleted_flag = false);
