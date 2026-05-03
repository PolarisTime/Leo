-- Seed display switches used by business list pages.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'UI_HIDE_AUDITED_LIST_RECORDS',
    '列表页隐藏已审核单据',
    '业务列表',
    'UI',
    'yyyy',
    1,
    'YEARLY',
    'OFF',
    '禁用',
    '启用后，业务列表分页查询默认不显示状态为“已审核”的单据'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'UI_HIDE_AUDITED_LIST_RECORDS' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'UI_SHOW_SNOWFLAKE_ID',
    '显示雪花 ID',
    '业务列表',
    'UI',
    'yyyy',
    1,
    'YEARLY',
    'OFF',
    '禁用',
    '启用后，业务列表显示系统雪花 ID 列，便于排查数据问题'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'UI_SHOW_SNOWFLAKE_ID' AND deleted_flag = FALSE
);
