UPDATE sys_no_rule
SET remark = '启用后，会自动记录带菜单权限的新增、编辑、删除、审核、导出、打印等写操作，记录操作人、模块、动作、业务单号或对象标识、请求路径和结果状态；GET、HEAD、OPTIONS 等只读请求不自动记录。关闭后，仅记录显式声明了操作日志的接口'
WHERE setting_code = 'SYS_OPERATION_LOG_RECORD_ALL_WRITE';
