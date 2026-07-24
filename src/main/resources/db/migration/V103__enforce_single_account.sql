-- 单人部署只允许一个未删除账号。保留审计历史，不物理删除旧账号。
-- 迁移前若存在多个活动账号，优先保留最近登录且状态正常的账号。

WITH ranked_accounts AS (
    SELECT id,
           ROW_NUMBER() OVER (
               ORDER BY CASE WHEN status = 'NORMAL' THEN 0 ELSE 1 END,
                        last_login_date DESC NULLS LAST,
                        created_at ASC NULLS LAST,
                        id ASC
           ) AS account_rank
      FROM public.sys_user
     WHERE deleted_flag = FALSE
), retired_accounts AS (
    SELECT id
      FROM ranked_accounts
     WHERE account_rank > 1
)
UPDATE public.auth_refresh_token session
   SET revoked_at = COALESCE(session.revoked_at, CURRENT_TIMESTAMP),
       revoke_reason = COALESCE(session.revoke_reason, 'MANUAL'),
       updated_by = 0,
       updated_name = 'flyway',
       updated_at = CURRENT_TIMESTAMP
 WHERE session.user_id IN (SELECT id FROM retired_accounts)
   AND session.deleted_flag = FALSE
   AND session.revoked_at IS NULL;

WITH ranked_accounts AS (
    SELECT id,
           ROW_NUMBER() OVER (
               ORDER BY CASE WHEN status = 'NORMAL' THEN 0 ELSE 1 END,
                        last_login_date DESC NULLS LAST,
                        created_at ASC NULLS LAST,
                        id ASC
           ) AS account_rank
      FROM public.sys_user
     WHERE deleted_flag = FALSE
)
UPDATE public.sys_user account
   SET status = CASE WHEN ranked.account_rank = 1 THEN 'NORMAL' ELSE 'DISABLED' END,
       deleted_flag = ranked.account_rank <> 1,
       updated_by = 0,
       updated_name = 'flyway',
       updated_at = CURRENT_TIMESTAMP
  FROM ranked_accounts ranked
 WHERE account.id = ranked.id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_single_active
    ON public.sys_user ((1))
    WHERE deleted_flag = FALSE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'chk_sys_user_active_status'
           AND conrelid = 'public.sys_user'::regclass
    ) THEN
        ALTER TABLE public.sys_user
            ADD CONSTRAINT chk_sys_user_active_status
            CHECK (deleted_flag OR status = 'NORMAL');
    END IF;
END $$;

COMMENT ON INDEX public.uk_sys_user_single_active IS
    '单人模式：最多一个未删除账号；首次初始化前允许零个账号';

DELETE FROM public.sys_menu
 WHERE menu_code IN ('access-control', 'permission');

UPDATE public.sys_menu
   SET menu_code = 'account',
       menu_name = '个人账号',
       route_path = '/account',
       updated_by = 0,
       updated_name = 'flyway',
       updated_at = CURRENT_TIMESTAMP
 WHERE menu_code = 'user-account';
