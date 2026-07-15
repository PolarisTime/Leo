-- Database backup and monitoring are no longer application responsibilities.
-- Keep sys_database_export_task for rollback compatibility; remove only active access metadata.
DELETE FROM sys_role_permission
WHERE resource_code = 'database';

DELETE FROM sys_menu_action
WHERE menu_code = 'database';

DELETE FROM sys_menu
WHERE menu_code = 'database';

DELETE FROM sys_rate_limit_rule
WHERE rule_key = 'DatabaseBackupController';

DELETE FROM sys_upload_rule
WHERE module_key = 'database'
   OR rule_code = 'PAGE_UPLOAD_DATABASE';
