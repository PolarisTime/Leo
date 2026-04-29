-- Seed system switch for forcing batch number management.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_FORCE_BATCH_MANAGEMENT',
    '强制批号管理',
    '系统开关',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    '',
    '禁用',
    '开启后所有出入库单据必须填写批号，默认关闭'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_FORCE_BATCH_MANAGEMENT' AND deleted_flag = FALSE
);
