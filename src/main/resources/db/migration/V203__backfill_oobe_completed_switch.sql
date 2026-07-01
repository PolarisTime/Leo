-- Backfill the permanent OOBE completion marker for databases initialized
-- before InitialSetupService began honoring SYS_OOBE_COMPLETED.
-- Empty/reset databases must not receive this marker, otherwise OOBE would be
-- locked before the first administrator and company are created.

INSERT INTO sys_no_rule (
    id,
    setting_code,
    setting_name,
    bill_name,
    prefix,
    date_rule,
    serial_length,
    reset_rule,
    sample_no,
    status,
    remark
)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_OOBE_COMPLETED',
    'OOBE已完成',
    '系统初始化',
    'SYS',
    'yyyy',
    1,
    'ONCE',
    'COMPLETED',
    '正常',
    '历史初始化完成库自动补齐，禁止重复执行 OOBE 流程'
WHERE EXISTS (
    SELECT 1
    FROM sys_role role
    JOIN sys_user_role user_role
      ON user_role.role_id = role.id
     AND user_role.deleted_flag = FALSE
    JOIN sys_user account
      ON account.id = user_role.user_id
     AND account.deleted_flag = FALSE
    WHERE role.role_code = 'ADMIN'
      AND role.deleted_flag = FALSE
)
AND EXISTS (
    SELECT 1
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
)
AND NOT EXISTS (
    SELECT 1
    FROM sys_no_rule
    WHERE setting_code = 'SYS_OOBE_COMPLETED'
      AND deleted_flag = FALSE
);
