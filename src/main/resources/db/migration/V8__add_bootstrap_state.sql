CREATE TABLE public.sys_bootstrap_state (
    id bigint PRIMARY KEY,
    completed boolean DEFAULT false NOT NULL,
    completed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_bootstrap_state_singleton CHECK (id = 1)
);

WITH existing_state AS (
    SELECT
        EXISTS (
            SELECT 1
              FROM public.sys_no_rule rule
             WHERE rule.setting_code = 'SYS_OOBE_COMPLETED'
               AND rule.status = '正常'
               AND rule.deleted_flag = false
        ) AS legacy_completed,
        (
            EXISTS (
                SELECT 1
                  FROM public.sys_role role
                  JOIN public.sys_user_role user_role
                    ON user_role.role_id = role.id
                   AND user_role.deleted_flag = false
                  JOIN public.sys_user user_account
                    ON user_account.id = user_role.user_id
                   AND user_account.deleted_flag = false
                 WHERE role.role_code = 'ADMIN'
                   AND role.deleted_flag = false
            )
            AND EXISTS (
                SELECT 1
                  FROM public.sys_company_setting company
                 WHERE company.deleted_flag = false
            )
        ) AS structured_completed
)
INSERT INTO public.sys_bootstrap_state (id, completed, completed_at)
SELECT 1,
       legacy_completed OR structured_completed,
       CASE WHEN legacy_completed OR structured_completed THEN CURRENT_TIMESTAMP ELSE NULL END
  FROM existing_state;
