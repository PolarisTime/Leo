INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES
    (700500000000000101, 'UI_WEIGHT_ONLY_PURCHASE_INBOUNDS', '采购入库重量视图开关', '采购入库视图', 'UI', 'yyyy', 1, 'YEARLY', 'ON', '正常', '开启后采购入库页面隐藏金额与价格，仅显示重量'),
    (700500000000000102, 'UI_WEIGHT_ONLY_SALES_OUTBOUNDS', '销售出库重量视图开关', '销售出库视图', 'UI', 'yyyy', 1, 'YEARLY', 'ON', '正常', '开启后销售出库页面隐藏金额与价格，仅显示重量')
ON CONFLICT (setting_code) DO NOTHING;
