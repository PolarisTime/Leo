ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS auth_type VARCHAR(16);

UPDATE sys_operation_log SET auth_type = 'WEB' WHERE auth_type IS NULL;
