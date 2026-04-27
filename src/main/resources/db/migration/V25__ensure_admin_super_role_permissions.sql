WITH admin_roles AS (
    SELECT DISTINCT r.id
    FROM sys_role r
    WHERE r.deleted_flag = FALSE
      AND (r.role_code = 'ADMIN' OR r.role_name = '系统管理员')
)
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (ORDER BY r.id, ma.menu_code, ma.action_code),
    r.id,
    ma.menu_code,
    ma.action_code
FROM admin_roles r
CROSS JOIN sys_menu_action ma
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

WITH admin_roles AS (
    SELECT DISTINCT r.id
    FROM sys_role r
    WHERE r.deleted_flag = FALSE
      AND (r.role_code = 'ADMIN' OR r.role_name = '系统管理员')
),
admin_user AS (
    SELECT u.id
    FROM sys_user u
    WHERE u.deleted_flag = FALSE
      AND u.login_name = 'admin'
)
INSERT INTO sys_user_role (id, user_id, role_id)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_user_role) + ROW_NUMBER() OVER (ORDER BY u.id, r.id),
    u.id,
    r.id
FROM admin_user u
CROSS JOIN admin_roles r
ON CONFLICT (user_id, role_id) DO NOTHING;
