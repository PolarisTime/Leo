INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES (
    700500000000000110,
    'SYS_OPERATION_LOG_DETAILED_PAGE_ACTIONS',
    '页面操作详细日志',
    '操作日志',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'QUERY,DETAIL,CREATE,EDIT,DELETE,AUDIT,EXPORT,PRINT',
    '禁用',
    '启用后，可按勾选动作记录查询、查看、新增、编辑、删除、审核、导出、打印等页面操作；显式声明的高风险日志仍会继续记录'
)
ON CONFLICT (setting_code) DO NOTHING;
