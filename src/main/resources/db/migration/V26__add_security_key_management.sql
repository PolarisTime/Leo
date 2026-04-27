CREATE TABLE IF NOT EXISTS sys_security_secret (
    id BIGINT PRIMARY KEY,
    secret_type VARCHAR(32) NOT NULL,
    secret_name VARCHAR(64) NOT NULL,
    key_version INTEGER NOT NULL,
    secret_value TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    activated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retired_at TIMESTAMP,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (secret_type, key_version)
);

CREATE INDEX IF NOT EXISTS idx_sys_security_secret_type_status
    ON sys_security_secret (secret_type, status, deleted_flag);

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type)
VALUES (10011, 'security-keys', '安全密钥管理', 'system', '/security-keys', 'SafetyCertificateOutlined', 11, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100111, 'security-keys', 'VIEW', '查看'),
    (100112, 'security-keys', 'EDIT', '编辑')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (ORDER BY ma.action_code),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code = 'security-keys'
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
