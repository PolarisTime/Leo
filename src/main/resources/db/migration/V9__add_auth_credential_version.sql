ALTER TABLE public.sys_user
    ADD COLUMN credential_version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.auth_refresh_token
    ADD COLUMN credential_version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.auth_refresh_token
    DROP CONSTRAINT IF EXISTS chk_refresh_token_revoke_reason;

ALTER TABLE public.auth_refresh_token
    ADD CONSTRAINT chk_refresh_token_revoke_reason
        CHECK (
            revoke_reason IS NULL
            OR revoke_reason IN (
                'MANUAL',
                'CONCURRENT_LIMIT',
                'EXPIRED',
                'REUSE_DETECTED',
                'PASSWORD_CHANGED'
            )
        );

COMMENT ON COLUMN public.sys_user.credential_version IS
    '凭据版本，密码变更时递增，用于使旧访问令牌和刷新会话失效';

COMMENT ON COLUMN public.auth_refresh_token.credential_version IS
    '刷新会话签发时的用户凭据版本';

COMMENT ON COLUMN public.auth_refresh_token.revoke_reason IS
    '撤销原因: MANUAL/CONCURRENT_LIMIT/EXPIRED/REUSE_DETECTED/PASSWORD_CHANGED';
