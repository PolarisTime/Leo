UPDATE sys_no_rule
SET setting_name = '客户对账单默认按销售订单金额收款为0',
    remark = '启用后，生成客户对账单草稿时默认收款金额为0，期末余额等于所选销售订单总金额；关闭后默认收款金额等于所选销售订单总金额'
WHERE setting_code = 'SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER';

UPDATE sys_no_rule
SET setting_name = '供应商对账单默认按采购单据金额全额付款',
    remark = '启用后，生成供应商对账单草稿时默认付款金额等于所选采购单据总金额；关闭后按账期内已付款记录自动汇总'
WHERE setting_code = 'SYS_SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE';
