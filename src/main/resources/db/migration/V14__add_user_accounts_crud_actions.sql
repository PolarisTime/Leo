-- ============================================================
-- V14：为用户账户和角色权限配置菜单添加缺失的操作权限
-- ============================================================

-- 用户账户菜单添加 CREATE/EDIT/DELETE 操作（系统菜单默认只有 VIEW）
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100042, 'user-accounts', 'CREATE', '新增'),
    (100043, 'user-accounts', 'EDIT',   '编辑'),
    (100044, 'user-accounts', 'DELETE', '删除')
ON CONFLICT (menu_code, action_code) DO NOTHING;

-- 角色权限配置菜单添加 EDIT 操作（用于保存权限配置）
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES (100102, 'role-action-editor', 'EDIT', '编辑')
ON CONFLICT (menu_code, action_code) DO NOTHING;

-- 管理员角色自动获得这些操作权限
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
CROSS JOIN sys_menu_action ma
WHERE r.role_code = 'ADMIN'
  AND (
    (ma.menu_code = 'user-accounts' AND ma.action_code IN ('CREATE', 'EDIT', 'DELETE'))
    OR (ma.menu_code = 'role-action-editor' AND ma.action_code = 'EDIT')
  )
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
