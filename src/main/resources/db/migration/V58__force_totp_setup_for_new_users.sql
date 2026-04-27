ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS require_totp_setup BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES (
    700500000000000106,
    'SYS_FORCE_USER_TOTP_ON_FIRST_LOGIN',
    '管理员新增账号首次密码登录后强制绑定2FA',
    '账户安全',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'OFF',
    '禁用',
    '启用后，管理员新增的账号使用初始密码首次登录后，进入系统前必须先完成 2FA 绑定；在绑定前仍允许用当前密码修改密码'
)
ON CONFLICT (setting_code) DO NOTHING;
