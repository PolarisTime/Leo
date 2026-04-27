UPDATE sys_no_rule
SET remark = '启用后，普通写操作按新增、编辑、删除、审核、导出、打印记录；权限变更、角色授权、API Key、2FA、密钥轮转、数据库导入导出等高风险动作按显式细分动作记录。GET、HEAD、OPTIONS 等只读请求不自动记录；关闭后仅记录显式声明的操作日志接口'
WHERE setting_code = 'SYS_OPERATION_LOG_RECORD_ALL_WRITE';
