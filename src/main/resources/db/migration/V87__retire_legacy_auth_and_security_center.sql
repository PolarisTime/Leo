DELETE FROM public.casbin_rule
WHERE ptype = 'p'
  AND v1 IN ('api-key', 'session', 'security-key', 'rate-limit');

DELETE FROM public.sys_menu_action
WHERE menu_code IN (
    'api-key',
    'session',
    'security-center',
    'security-key',
    'rate-limit',
    'no-rule',
    'upload-rule'
);

DELETE FROM public.sys_menu
WHERE menu_code IN (
    'api-key',
    'session',
    'security-center',
    'security-key',
    'rate-limit',
    'no-rule',
    'upload-rule'
);

DELETE FROM public.sys_no_rule
WHERE setting_code LIKE 'RULE\_%' ESCAPE '\'
   OR setting_code IN (
       'PAGE_UPLOAD',
       'SYS_BATCH_NO_AUTO_GENERATE',
       'SYS_LOGIN_CAPTCHA',
       'SYS_FORCE_USER_TOTP_ON_FIRST_LOGIN',
       'SYS_FORBID_DISABLE_2FA',
       'SYS_MAX_CONCURRENT_SESSIONS',
       'SYS_USE_SNOWFLAKE_ID_AS_BUSINESS_NO',
       'SYS_OPERATION_LOG_DETAILED_PAGE_ACTIONS',
       'SYS_OPERATION_LOG_RECORD_ALL_WRITE',
       'SYS_OPERATION_LOG_RECORD_AUTH_EVENTS',
       'SYS_ADMIN_VIEW_DELETED_RECORDS'
   );

ALTER TABLE public.sys_no_rule RENAME TO sys_general_setting;

ALTER TABLE public.sys_general_setting
    RENAME COLUMN bill_name TO setting_group;

ALTER TABLE public.sys_general_setting
    RENAME COLUMN sample_no TO setting_value;

ALTER TABLE public.sys_general_setting
    RENAME CONSTRAINT sys_no_rule_pkey TO sys_general_setting_pkey;

ALTER TABLE public.sys_general_setting
    RENAME CONSTRAINT sys_no_rule_setting_code_key TO sys_general_setting_setting_code_key;

ALTER INDEX public.idx_sys_no_rule_bill_name
    RENAME TO idx_sys_general_setting_group;

ALTER TABLE public.sys_general_setting
    DROP COLUMN prefix,
    DROP COLUMN date_rule,
    DROP COLUMN serial_length,
    DROP COLUMN reset_rule,
    DROP COLUMN current_period,
    DROP COLUMN next_serial_value;

DROP TABLE public.auth_api_key;
DROP TABLE public.sys_rate_limit_rule;
DROP TABLE public.sys_upload_rule;
DROP TABLE public.sys_role_permission;
DROP TABLE public.sys_user_role;

UPDATE public.sys_security_secret
SET secret_type = 'DATA_MASTER',
    secret_name = replace(secret_name, '2FA', '数据加密'),
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE secret_type = 'TOTP_MASTER';

ALTER TABLE public.sys_user
    DROP COLUMN totp_secret,
    DROP COLUMN totp_enabled,
    DROP COLUMN require_totp_setup;
