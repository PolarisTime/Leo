ALTER TABLE sys_no_rule
    ADD COLUMN IF NOT EXISTS current_period VARCHAR(32);

ALTER TABLE sys_no_rule
    ADD COLUMN IF NOT EXISTS next_serial_value BIGINT;

INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, current_period, next_serial_value, status, remark
) VALUES (
    700500000000000107,
    'RULE_BATCH_NO',
    '批号生成规则',
    '批号',
    'LOT',
    'yyyy',
    6,
    'YEARLY',
    '2026LOT000001',
    NULL,
    1,
    '正常',
    '用于批号管理商品在明细未填写批号时的自动生成规则'
)
ON CONFLICT (setting_code) DO NOTHING;

INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, current_period, next_serial_value, status, remark
) VALUES (
    700500000000000108,
    'SYS_BATCH_NO_AUTO_GENERATE',
    '批号为空时按规则自动生成',
    '批号管理',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'OFF',
    NULL,
    NULL,
    '禁用',
    '启用后，批号管理商品在未填写批号时按“批号生成规则”自动补齐；关闭后保持现有手工录入与必填校验'
)
ON CONFLICT (setting_code) DO NOTHING;
