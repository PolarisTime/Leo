-- Seed system switch to forbid users from disabling 2FA in personal settings.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_FORBID_DISABLE_2FA',
    '禁止关闭 2FA',
    '系统开关',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    '',
    '禁用',
    '开启后个人设置中不允许关闭 2FA，默认关闭'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_FORBID_DISABLE_2FA' AND deleted_flag = FALSE
);
