ALTER TABLE sys_operation_log
    ADD COLUMN IF NOT EXISTS record_id BIGINT,
    ADD COLUMN IF NOT EXISTS module_key VARCHAR(64);

COMMENT ON COLUMN sys_operation_log.record_id IS '关联的业务记录ID';
COMMENT ON COLUMN sys_operation_log.module_key IS '关联的业务模块key';
