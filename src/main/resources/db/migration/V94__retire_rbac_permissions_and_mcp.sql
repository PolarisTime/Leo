DELETE FROM public.sys_menu
WHERE menu_code IN ('access-control', 'permission');

UPDATE public.sys_menu
SET menu_name = '账号管理',
    route_path = '/user-accounts',
    icon = 'UserOutlined',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'user-account';

DROP TABLE public.sys_menu_action;
DROP TABLE public.sys_role_conflict;
DROP TABLE public.sys_role;
DROP TABLE public.casbin_rule;
DROP SEQUENCE IF EXISTS public.casbin_sequence;

ALTER TABLE public.sys_user
    DROP COLUMN permission_summary;
