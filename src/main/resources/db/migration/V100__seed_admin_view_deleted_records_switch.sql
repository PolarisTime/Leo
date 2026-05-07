-- Seed system switch for allowing administrators to view deleted business records.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_ADMIN_VIEW_DELETED_RECORDS',
    '管理员可查看已删除单据',
    '业务列表',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'ON',
    '正常',
    '启用后，管理员在业务单据列表与详情中可查看已删除记录，仅用于排查与审计；关闭后已删除单据对所有人隐藏'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_ADMIN_VIEW_DELETED_RECORDS' AND deleted_flag = FALSE
);
