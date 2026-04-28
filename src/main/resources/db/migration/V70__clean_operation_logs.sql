-- Clean operation logs from deleted mock users and expired entries.

DELETE FROM sys_operation_log
WHERE created_by IN (
    SELECT id FROM sys_user
    WHERE login_name IN ('buyer01', 'sales01', 'finance01', 'ops01')
      AND remark = 'Mock 用户'
);

DELETE FROM sys_operation_log
WHERE created_by IN (
    SELECT id FROM sys_user
    WHERE login_name = 'admin'
      AND remark = '系统自动初始化管理员账户'
);

DELETE FROM sys_operation_log
WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '90 days'
  AND id < 700000000000000000;
