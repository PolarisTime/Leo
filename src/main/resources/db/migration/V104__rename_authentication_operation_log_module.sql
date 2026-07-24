-- 统一身份认证操作日志模块名称，移除“授权”这一已废弃的权限语义。
UPDATE public.sys_operation_log
   SET module_name = '身份认证'
 WHERE module_name = '认证授权';

-- V81 保留的迁移核对副本也同步更新，避免历史查询出现两套模块名称。
UPDATE public.sys_operation_log_unpartitioned
   SET module_name = '身份认证'
 WHERE module_name = '认证授权';
