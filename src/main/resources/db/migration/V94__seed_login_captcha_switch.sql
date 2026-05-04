-- Seed system switch for login CAPTCHA.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_LOGIN_CAPTCHA',
    '登录验证码',
    '系统开关',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'OFF',
    '禁用',
    '是否在登录时要求输入图形验证码，默认关闭'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_LOGIN_CAPTCHA' AND deleted_flag = FALSE
);
