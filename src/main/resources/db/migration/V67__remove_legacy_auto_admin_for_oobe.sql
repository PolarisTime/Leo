-- Remove administrator accounts created by the deprecated automatic bootstrap.
-- OOBE must ask the operator to create the first administrator explicitly.

DELETE FROM auth_refresh_token
WHERE user_id IN (
    SELECT id
    FROM sys_user
    WHERE login_name = 'admin'
      AND remark = '系统自动初始化管理员账户'
);

DELETE FROM auth_api_key
WHERE user_id IN (
    SELECT id
    FROM sys_user
    WHERE login_name = 'admin'
      AND remark = '系统自动初始化管理员账户'
);

DELETE FROM sys_user_role
WHERE user_id IN (
    SELECT id
    FROM sys_user
    WHERE login_name = 'admin'
      AND remark = '系统自动初始化管理员账户'
);

DELETE FROM sys_user
WHERE login_name = 'admin'
  AND remark = '系统自动初始化管理员账户';
