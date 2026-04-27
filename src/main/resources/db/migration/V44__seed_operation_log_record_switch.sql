INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES (
    700500000000000105,
    'SYS_OPERATION_LOG_RECORD_ALL_WRITE',
    '全系统写操作自动记录操作日志',
    '操作日志',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'ON',
    '正常',
    '启用后，未显式声明操作日志的写接口也会按菜单权限自动记录；关闭后仅记录显式配置的操作日志'
)
ON CONFLICT (setting_code) DO NOTHING;
