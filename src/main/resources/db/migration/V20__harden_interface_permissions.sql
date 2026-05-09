INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100052, 'role-settings', 'CREATE', '新增'),
    (100053, 'role-settings', 'EDIT', '编辑'),
    (100054, 'role-settings', 'DELETE', '删除'),
    (100032, 'permission', 'CREATE', '新增'),
    (100033, 'permission', 'EDIT', '编辑'),
    (100034, 'permission', 'DELETE', '删除'),
    (100092, 'print-template', 'CREATE', '新增'),
    (100093, 'print-template', 'EDIT', '编辑'),
    (100094, 'print-template', 'DELETE', '删除'),
    (100072, 'session', 'EDIT', '编辑')
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
      ma.menu_code = 'permission' AND ma.action_code IN ('CREATE', 'EDIT', 'DELETE')
  ) OR (
      ma.menu_code = 'print-template' AND ma.action_code IN ('CREATE', 'EDIT', 'DELETE')
  ) OR (
      ma.menu_code = 'session' AND ma.action_code = 'EDIT'
  )
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
