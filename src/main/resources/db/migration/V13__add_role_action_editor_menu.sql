-- 添加角色权限配置菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(10010, 'role-action-editor', '角色权限配置', 'system', '/role-action-editor', 'SafetyCertificateOutlined', 10, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 为角色权限配置菜单添加查看操作
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES (100101, 'role-action-editor', 'VIEW', '查看')
ON CONFLICT (menu_code, action_code) DO NOTHING;

-- 管理员角色获得该菜单权限
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    'role-action-editor',
    'VIEW'
FROM sys_role r
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
