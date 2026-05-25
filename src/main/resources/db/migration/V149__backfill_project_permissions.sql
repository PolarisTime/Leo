-- Backfill resources added while aligning the RBAC catalog with the refactored UI.
-- Safe to run after V148; all inserts are idempotent.

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type)
VALUES (9007, 'project-ar', '项目应收', 'finance', '/project-ar', 'ProfileOutlined', 9, '菜单')
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES (90071, 'project-ar', 'READ', '查看')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (ORDER BY r.id),
    r.id,
    'project-ar',
    'READ'
FROM sys_role r
WHERE r.deleted_flag = FALSE
  AND (r.role_code = 'ADMIN' OR r.role_name = '系统管理员')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_action ra
      WHERE ra.role_id = r.id
        AND ra.menu_code = 'project-ar'
        AND ra.action_code = 'READ'
        AND ra.deleted_flag = FALSE
  );

WITH catalog(resource_code, action_code) AS (
    VALUES
        ('project', 'read'),
        ('project', 'create'),
        ('project', 'update'),
        ('project', 'delete'),
        ('project-ar', 'read')
),
missing AS (
    SELECT
        r.id AS role_id,
        c.resource_code,
        c.action_code
    FROM sys_role r
    CROSS JOIN catalog c
    WHERE r.deleted_flag = FALSE
      AND (r.role_code = 'ADMIN' OR r.role_name = '系统管理员')
      AND NOT EXISTS (
          SELECT 1
          FROM sys_role_permission rp
          WHERE rp.role_id = r.id
            AND rp.resource_code = c.resource_code
            AND rp.action_code = c.action_code
            AND rp.deleted_flag = FALSE
      )
)
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 700520000000000000) FROM sys_role_permission)
        + ROW_NUMBER() OVER (ORDER BY role_id, resource_code, action_code),
    role_id,
    resource_code,
    action_code
FROM missing;
