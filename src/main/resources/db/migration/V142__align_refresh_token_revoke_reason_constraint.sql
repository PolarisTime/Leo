-- Align auth_refresh_token.revoke_reason with RevokeReason persisted by @Enumerated(EnumType.STRING).
UPDATE auth_refresh_token
SET revoke_reason = CASE revoke_reason
    WHEN '手动撤销' THEN 'MANUAL'
    WHEN '登出' THEN 'MANUAL'
    WHEN '密码更改' THEN 'MANUAL'
    WHEN '已过期' THEN 'EXPIRED'
    ELSE revoke_reason
END
WHERE revoke_reason IN ('手动撤销', '登出', '密码更改', '已过期');

ALTER TABLE auth_refresh_token DROP CONSTRAINT IF EXISTS chk_refresh_token_revoke_reason;
ALTER TABLE auth_refresh_token ADD CONSTRAINT chk_refresh_token_revoke_reason
    CHECK (revoke_reason IS NULL OR revoke_reason IN ('MANUAL', 'CONCURRENT_LIMIT', 'EXPIRED'));
