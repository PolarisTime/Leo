CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT PRIMARY KEY,
    log_no VARCHAR(64) NOT NULL UNIQUE,
    operator_id BIGINT,
    operator_name VARCHAR(64) NOT NULL,
    login_name VARCHAR(64) NOT NULL,
    module_name VARCHAR(64) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    business_no VARCHAR(128),
    request_method VARCHAR(16) NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    client_ip VARCHAR(64),
    result_status VARCHAR(16) NOT NULL,
    operation_time TIMESTAMP NOT NULL,
    remark VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_sys_operation_log_time ON sys_operation_log (operation_time);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_module ON sys_operation_log (module_name);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_operator ON sys_operation_log (login_name);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_business_no ON sys_operation_log (business_no);
