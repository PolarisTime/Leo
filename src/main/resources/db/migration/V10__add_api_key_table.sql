CREATE TABLE IF NOT EXISTS auth_api_key (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    key_name VARCHAR(64) NOT NULL,
    key_prefix VARCHAR(8) NOT NULL,
    key_hash VARCHAR(128) NOT NULL UNIQUE,
    usage_scope VARCHAR(32) NOT NULL DEFAULT '全部接口',
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_auth_api_key_user_id ON auth_api_key (user_id);
