INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES (
    700500000000000109,
    'SYS_OPERATION_LOG_RECORD_AUTH_EVENTS',
    '记录登录/退出等认证操作日志',
    '认证授权',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'ON',
    '正常',
    '启用后，登录成功、登录失败、2FA 验证失败和退出登录会写入操作日志；关闭后不记录这类认证事件'
)
ON CONFLICT (setting_code) DO NOTHING;
