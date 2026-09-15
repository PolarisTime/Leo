-- 刷新令牌撤销原因补齐 ACCOUNT_DISABLED：账号停用/软删时需要吊销其活动刷新会话。
--
-- 背景：
--   * RevokeReason 枚举已包含 ACCOUNT_DISABLED，SessionManagementService 在停用/删除账号时写入该值；
--   * V9 重建的 chk_refresh_token_revoke_reason 未包含 ACCOUNT_DISABLED，
--     导致停用/删除已登录账号时违反检查约束并返回 422。
--
-- 仅调整约束取值集合，不改写历史迁移，也不改写业务数据。

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
                'PASSWORD_CHANGED',
                'ACCOUNT_DISABLED'
            )
        );

COMMENT ON COLUMN public.auth_refresh_token.revoke_reason IS
    '撤销原因: MANUAL/CONCURRENT_LIMIT/EXPIRED/REUSE_DETECTED/PASSWORD_CHANGED/ACCOUNT_DISABLED';
