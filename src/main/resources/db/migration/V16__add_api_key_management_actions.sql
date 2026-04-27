-- 为 API Key 管理菜单补充 CREATE / EDIT 操作
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100082, 'api-key-management', 'CREATE', '新增'),
    (100083, 'api-key-management', 'EDIT',   '编辑')
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
  AND ma.menu_code = 'api-key-management'
  AND ma.action_code IN ('CREATE', 'EDIT')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
