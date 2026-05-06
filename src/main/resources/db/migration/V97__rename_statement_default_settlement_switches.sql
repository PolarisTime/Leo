UPDATE sys_no_rule
SET bill_name = '销售订单收款情况',
    setting_name = '销售订单默认未收款',
    remark = '启用后，由销售订单生成客户对账单时默认按未收款处理；关闭后默认按已收款处理'
WHERE setting_code = 'SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER';

UPDATE sys_no_rule
SET bill_name = '采购单据付款情况',
    setting_name = '采购单据默认已付款',
    remark = '启用后，由采购单据生成供应商对账单时默认按已付款处理；关闭后默认按未付款处理'
WHERE setting_code = 'SYS_SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE';
