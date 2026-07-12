ALTER TABLE public.auth_api_key
    VALIDATE CONSTRAINT fk_auth_api_key_user;

ALTER TABLE public.auth_refresh_token
    VALIDATE CONSTRAINT fk_auth_refresh_token_user;

ALTER TABLE public.sys_user_role
    VALIDATE CONSTRAINT fk_sys_user_role_user,
    VALIDATE CONSTRAINT fk_sys_user_role_role;

ALTER TABLE public.sys_role_permission
    VALIDATE CONSTRAINT fk_sys_role_permission_role;

ALTER TABLE public.sys_role
    VALIDATE CONSTRAINT fk_sys_role_parent;

ALTER TABLE public.sys_department
    VALIDATE CONSTRAINT fk_sys_department_parent;

ALTER TABLE public.sys_attachment_binding
    VALIDATE CONSTRAINT fk_sys_attachment_binding_attachment;
