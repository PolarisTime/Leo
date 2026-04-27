CREATE TABLE IF NOT EXISTS sys_no_rule (
    id BIGINT PRIMARY KEY,
    setting_code VARCHAR(64) NOT NULL UNIQUE,
    setting_name VARCHAR(128) NOT NULL,
    bill_name VARCHAR(128) NOT NULL,
    prefix VARCHAR(64) NOT NULL,
    date_rule VARCHAR(32) NOT NULL,
    serial_length INTEGER NOT NULL,
    reset_rule VARCHAR(32) NOT NULL,
    sample_no VARCHAR(64) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_sys_no_rule_bill_name ON sys_no_rule (bill_name);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY,
    permission_code VARCHAR(64) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    module_name VARCHAR(64) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    action_name VARCHAR(32) NOT NULL,
    scope_name VARCHAR(32),
    resource_key VARCHAR(128) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_sys_permission_module_name ON sys_permission (module_name);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(128) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    data_scope VARCHAR(32) NOT NULL,
    permission_codes VARCHAR(1000),
    permission_count INTEGER NOT NULL,
    permission_summary VARCHAR(500),
    user_count INTEGER NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_sys_role_role_name ON sys_role (role_name);

CREATE TABLE IF NOT EXISTS ops_ticket (
    id BIGINT PRIMARY KEY,
    ticket_no VARCHAR(32) NOT NULL UNIQUE,
    issue_type VARCHAR(64) NOT NULL,
    priority_level VARCHAR(16) NOT NULL,
    submitter_name VARCHAR(64) NOT NULL,
    handler_name VARCHAR(64),
    submit_date DATE NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_ops_ticket_submit_date ON ops_ticket (submit_date);
