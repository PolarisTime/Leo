INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES (
    700500000000000103,
    'SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER',
    '销售订单生成客户对账单默认收款金额为0',
    '客户对账单生成',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'ON',
    '正常',
    '启用后，由销售订单生成客户对账单草稿时默认收款金额为 0；关闭后默认收款金额等于销售金额'
)
ON CONFLICT (setting_code) DO NOTHING;
