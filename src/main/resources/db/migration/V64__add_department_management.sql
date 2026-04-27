CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT PRIMARY KEY,
    department_code VARCHAR(64) NOT NULL,
    department_name VARCHAR(128) NOT NULL,
    parent_id BIGINT,
    manager_name VARCHAR(64),
    contact_phone VARCHAR(32),
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_department_parent_id ON sys_department (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_department_status ON sys_department (status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_department_code_active
    ON sys_department (department_code)
    WHERE deleted_flag = FALSE;

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS department_id BIGINT;

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS department_name VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_sys_user_department_id ON sys_user (department_id);

INSERT INTO sys_department (id, department_code, department_name, sort_order, status, remark)
SELECT
    (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_department),
    'HQ',
    '总部',
    1,
    '正常',
    '系统默认部门'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_department WHERE department_code = 'HQ' AND deleted_flag = FALSE
);

UPDATE sys_user user_account
SET
    department_id = department.id,
    department_name = department.department_name
FROM sys_department department
WHERE department.department_code = 'HQ'
  AND department.deleted_flag = FALSE
  AND user_account.deleted_flag = FALSE
  AND user_account.department_id IS NULL;

UPDATE sys_menu
SET sort_order = sort_order + 1
WHERE parent_code = 'system'
  AND sort_order >= 4
  AND menu_code <> 'departments'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE menu_code = 'departments' AND deleted_flag = FALSE
  );

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type)
VALUES (10013, 'departments', '部门管理', 'system', '/departments', 'ApartmentOutlined', 4, '菜单')
ON CONFLICT (menu_code) DO UPDATE
SET
    menu_name = EXCLUDED.menu_name,
    parent_code = EXCLUDED.parent_code,
    route_path = EXCLUDED.route_path,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    menu_type = EXCLUDED.menu_type,
    status = '正常',
    deleted_flag = FALSE;

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100131, 'departments', 'VIEW', '查看'),
    (100132, 'departments', 'CREATE', '新增'),
    (100133, 'departments', 'EDIT', '编辑'),
    (100134, 'departments', 'DELETE', '删除')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (ORDER BY r.id, ma.action_code),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code = 'departments'
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission) + ROW_NUMBER() OVER (ORDER BY r.id, action_map.action_code),
    r.id,
    'department',
    action_map.action_code
FROM sys_role r
CROSS JOIN (VALUES
    ('read'),
    ('create'),
    ('update'),
    ('delete')
) AS action_map(action_code)
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, resource_code, action_code) DO NOTHING;
