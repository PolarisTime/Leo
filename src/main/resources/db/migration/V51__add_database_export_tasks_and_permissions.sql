CREATE TABLE IF NOT EXISTS sys_database_export_task (
    id BIGINT PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    download_token VARCHAR(64),
    expires_at TIMESTAMP,
    finished_at TIMESTAMP,
    failure_reason VARCHAR(500),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_database_export_task_created_at
    ON sys_database_export_task (created_at DESC);

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100062, 'ops-support', 'EDIT', '编辑'),
    (100066, 'ops-support', 'EXPORT', '导出')
ON CONFLICT (menu_code, action_code) DO NOTHING;

WITH admin_roles AS (
    SELECT DISTINCT r.id
    FROM sys_role r
    WHERE r.deleted_flag = FALSE
      AND (r.role_code = 'ADMIN' OR r.role_name = '系统管理员')
)
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (ORDER BY r.id, action_code),
    r.id,
    'ops-support',
    action_code
FROM admin_roles r
CROSS JOIN (VALUES ('EDIT'), ('EXPORT')) AS actions(action_code)
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
