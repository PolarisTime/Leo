-- Seed department number rule and default department for OOBE.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'RULE_DEPT',
    '部门编码规则',
    '部门',
    '{yyyymmdd}-{seq}',
    'yyyyMMdd',
    4,
    'DAILY',
    '',
    '正常',
    'OOBE 自动创建'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'RULE_DEPT' AND deleted_flag = FALSE
);

INSERT INTO sys_department (id, department_code, department_name, parent_id, manager_name, contact_phone, sort_order, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_department), 700000000000000000) + 1,
    'DEPT001',
    '默认部门',
    NULL,
    NULL,
    NULL,
    0,
    '正常',
    'OOBE 自动创建'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_department WHERE department_code = 'DEPT001' AND deleted_flag = FALSE
);
