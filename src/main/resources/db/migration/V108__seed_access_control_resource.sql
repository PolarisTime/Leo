-- ============================================================
-- access-control 独立资源：给所有已持有相关权限的角色授予 read
-- ============================================================

WITH roles_to_grant AS (
    SELECT DISTINCT rp.role_id
    FROM sys_role_permission rp
    WHERE rp.resource_code IN ('user-account', 'permission', 'role')
      AND rp.action_code = 'read'
      AND NOT EXISTS (
        SELECT 1 FROM sys_role_permission ex
        WHERE ex.role_id = rp.role_id
          AND ex.resource_code = 'access-control'
          AND ex.action_code = 'read'
      )
)
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission) + ROW_NUMBER() OVER (),
    role_id,
    'access-control',
    'read'
FROM roles_to_grant;
