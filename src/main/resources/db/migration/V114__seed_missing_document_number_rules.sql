INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'RULE_PC', '采购合同编号规则', '采购合同', 'PC{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'PC2026000001', '正常', '采购合同系统自动编号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'RULE_PC' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'RULE_SC', '销售合同编号规则', '销售合同', 'SC{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'SC2026000001', '正常', '销售合同系统自动编号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'RULE_SC' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'RULE_RC', '收款单编号规则', '收款单', 'SK{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'SK2026000001', '正常', '收款单系统自动编号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'RULE_RC' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'RULE_PM', '付款单编号规则', '付款单', 'FK{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'FK2026000001', '正常', '付款单系统自动编号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'RULE_PM' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'RULE_SP', '收票单编号规则', '收票单', 'SP{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'SP2026000001', '正常', '收票单系统自动编号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'RULE_SP' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'RULE_KP', '开票单编号规则', '开票单', 'KP{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'KP2026000001', '正常', '开票单系统自动编号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'RULE_KP' AND deleted_flag = FALSE
);
