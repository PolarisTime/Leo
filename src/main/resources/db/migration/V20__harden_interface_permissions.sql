INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100052, 'role-settings', 'CREATE', '新增'),
    (100053, 'role-settings', 'EDIT', '编辑'),
    (100054, 'role-settings', 'DELETE', '删除'),
    (100032, 'permission-management', 'CREATE', '新增'),
    (100033, 'permission-management', 'EDIT', '编辑'),
    (100034, 'permission-management', 'DELETE', '删除'),
    (100092, 'print-templates', 'CREATE', '新增'),
    (100093, 'print-templates', 'EDIT', '编辑'),
    (100094, 'print-templates', 'DELETE', '删除'),
    (100072, 'session-management', 'EDIT', '编辑')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON (
      ma.menu_code = 'role-settings' AND ma.action_code IN ('CREATE', 'EDIT', 'DELETE')
  ) OR (
      ma.menu_code = 'permission-management' AND ma.action_code IN ('CREATE', 'EDIT', 'DELETE')
  ) OR (
      ma.menu_code = 'print-templates' AND ma.action_code IN ('CREATE', 'EDIT', 'DELETE')
  ) OR (
      ma.menu_code = 'session-management' AND ma.action_code = 'EDIT'
  )
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
