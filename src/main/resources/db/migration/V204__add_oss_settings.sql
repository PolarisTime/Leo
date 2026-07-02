CREATE TABLE IF NOT EXISTS sys_oss_setting (
    id BIGINT PRIMARY KEY,
    storage_mode VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    bucket VARCHAR(128) NOT NULL,
    region VARCHAR(64) NOT NULL,
    access_key VARCHAR(255) NOT NULL,
    encrypted_secret_key TEXT,
    key_prefix VARCHAR(255) NOT NULL DEFAULT 'attachments',
    path_style_access BOOLEAN NOT NULL DEFAULT TRUE,
    encrypted_storage BOOLEAN NOT NULL DEFAULT FALSE,
    server_proxy_only BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(16) NOT NULL DEFAULT '正常',
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_oss_setting_deleted
    ON sys_oss_setting (deleted_flag, id);
