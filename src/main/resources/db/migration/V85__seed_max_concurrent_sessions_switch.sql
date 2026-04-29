-- Seed system switch for maximum concurrent sessions per user.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_MAX_CONCURRENT_SESSIONS',
    '最大同时在线会话数',
    '系统开关',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    '3',
    '正常',
    '每个用户允许的最大同时在线会话数（Refresh Token 数量），默认 3'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_MAX_CONCURRENT_SESSIONS' AND deleted_flag = FALSE
);
