-- Seed system switch for using snowflake ID as business document number.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_USE_SNOWFLAKE_ID_AS_BUSINESS_NO',
    '业务单据号使用雪花ID',
    '系统开关',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'OFF',
    '禁用',
    '启用后，新建业务单据时预分配真实雪花ID，并以该雪花ID作为单据号，不再按编号规则生成'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_USE_SNOWFLAKE_ID_AS_BUSINESS_NO' AND deleted_flag = FALSE
);
