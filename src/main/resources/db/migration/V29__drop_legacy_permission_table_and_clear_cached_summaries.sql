DROP TABLE IF EXISTS sys_permission;

UPDATE sys_user
SET permission_summary = ''
WHERE COALESCE(permission_summary, '') <> '';

UPDATE sys_role
SET permission_codes = '',
    permission_count = 0,
    permission_summary = '',
    user_count = 0
WHERE COALESCE(permission_codes, '') <> ''
   OR COALESCE(permission_count, 0) <> 0
   OR COALESCE(permission_summary, '') <> ''
   OR COALESCE(user_count, 0) <> 0;
