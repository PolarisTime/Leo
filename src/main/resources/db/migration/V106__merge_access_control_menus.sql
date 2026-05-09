-- ============================================================
-- 合并访问控制页面：用户账户 + 角色权限配置 + 权限管理
-- ============================================================

-- 1. 新增统一访问控制菜单（替换原有的三个独立菜单）
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(10014, 'access-control', '访问控制', 'system', '/access-control', 'SafetyCertificateOutlined', 3, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 2. 为基础查看操作
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES (100141, 'access-control', 'READ', '查看')
ON CONFLICT (menu_code, action_code) DO NOTHING;

-- 3. 删除旧的独立菜单及其关联操作
DELETE FROM sys_menu_action WHERE menu_code IN ('user-accounts', 'permission-management', 'role-action-editor', 'role-settings');
DELETE FROM sys_menu WHERE menu_code IN ('user-accounts', 'permission-management', 'role-action-editor', 'role-settings');
