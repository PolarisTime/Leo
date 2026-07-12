-- Authentication records are security history and block uncoordinated user deletion.
ALTER TABLE public.auth_api_key
    ADD CONSTRAINT fk_auth_api_key_user
        FOREIGN KEY (user_id) REFERENCES public.sys_user (id)
        ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.auth_refresh_token
    ADD CONSTRAINT fk_auth_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES public.sys_user (id)
        ON DELETE RESTRICT NOT VALID;

-- Membership rows are aggregate children and may be removed with their owner.
ALTER TABLE public.sys_user_role
    ADD CONSTRAINT fk_sys_user_role_user
        FOREIGN KEY (user_id) REFERENCES public.sys_user (id)
        ON DELETE CASCADE NOT VALID,
    ADD CONSTRAINT fk_sys_user_role_role
        FOREIGN KEY (role_id) REFERENCES public.sys_role (id)
        ON DELETE CASCADE NOT VALID;

ALTER TABLE public.sys_role_permission
    ADD CONSTRAINT fk_sys_role_permission_role
        FOREIGN KEY (role_id) REFERENCES public.sys_role (id)
        ON DELETE CASCADE NOT VALID;

-- Hierarchies must be detached explicitly so child nodes cannot be orphaned.
ALTER TABLE public.sys_role
    ADD CONSTRAINT fk_sys_role_parent
        FOREIGN KEY (parent_id) REFERENCES public.sys_role (id)
        ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.sys_department
    ADD CONSTRAINT fk_sys_department_parent
        FOREIGN KEY (parent_id) REFERENCES public.sys_department (id)
        ON DELETE RESTRICT NOT VALID;

-- A binding has no meaning after its attachment is hard-deleted. The polymorphic
-- business-record relation deliberately remains outside ordinary foreign keys.
ALTER TABLE public.sys_attachment_binding
    ADD CONSTRAINT fk_sys_attachment_binding_attachment
        FOREIGN KEY (attachment_id) REFERENCES public.sys_attachment (id)
        ON DELETE CASCADE NOT VALID;
