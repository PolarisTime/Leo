-- Seed system setting for default list pagination page size.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'UI_DEFAULT_LIST_PAGE_SIZE',
    '列表分页默认条数',
    '业务列表',
    'UI',
    'yyyy',
    1,
    'YEARLY',
    '20',
    '正常',
    '用于业务列表与远程候选列表的默认分页条数，范围 1 到 200'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'UI_DEFAULT_LIST_PAGE_SIZE' AND deleted_flag = FALSE
);
