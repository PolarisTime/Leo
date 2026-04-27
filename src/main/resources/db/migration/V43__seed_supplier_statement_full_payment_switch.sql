INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES (
    700500000000000104,
    'SYS_SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE',
    '采购单据生成供应商对账单默认全额付款',
    '供应商对账单生成',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'ON',
    '正常',
    '启用后，由采购入库生成供应商对账单草稿时默认付款金额等于采购金额；关闭后按账期内已付款记录自动汇总'
)
ON CONFLICT (setting_code) DO NOTHING;
