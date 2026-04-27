CREATE TABLE IF NOT EXISTS sys_attachment (
    id BIGINT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_extension VARCHAR(32) NOT NULL,
    content_type VARCHAR(128),
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_attachment_created_at ON sys_attachment (created_at);

CREATE TABLE IF NOT EXISTS sys_upload_rule (
    id BIGINT PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    rule_name VARCHAR(128) NOT NULL,
    rename_pattern VARCHAR(255) NOT NULL,
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

INSERT INTO sys_upload_rule (id, rule_code, rule_name, rename_pattern, status, remark)
VALUES
    (700540000000000001, 'PAGE_UPLOAD', '页面上传文件命名规则', '{yyyyMMddHHmmss}_{random8}', '正常', '适用于页面选择文件和剪贴板粘贴上传')
ON CONFLICT (rule_code) DO NOTHING;

ALTER TABLE st_freight_statement
    ADD COLUMN IF NOT EXISTS attachment_ids VARCHAR(500);

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100012, 'general-settings', 'EDIT', '编辑'),
    (100062, 'ops-support', 'EDIT', '编辑'),
    (100066, 'ops-support', 'EXPORT', '导出')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code = 'general-settings'
 AND ma.action_code IN ('VIEW', 'EDIT')
WHERE r.role_code IN ('ADMIN', 'FINANCE_MANAGER')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code = 'ops-support'
 AND ma.action_code IN ('EDIT', 'EXPORT')
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
