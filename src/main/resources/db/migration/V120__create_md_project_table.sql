CREATE TABLE IF NOT EXISTS md_project (
    id BIGINT PRIMARY KEY,
    project_code VARCHAR(64) NOT NULL UNIQUE,
    project_name VARCHAR(200) NOT NULL,
    project_name_abbr VARCHAR(100),
    project_address VARCHAR(255),
    project_manager VARCHAR(64),
    customer_code VARCHAR(64) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_md_project_customer_code ON md_project (customer_code);
CREATE INDEX IF NOT EXISTS idx_md_project_name ON md_project (project_name);
