ALTER TABLE auth_refresh_token
    ADD COLUMN IF NOT EXISTS previous_token_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS previous_token_valid_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_previous_hash
    ON auth_refresh_token (previous_token_hash)
    WHERE previous_token_hash IS NOT NULL AND deleted_flag = FALSE;

ALTER TABLE auth_refresh_token DROP CONSTRAINT IF EXISTS chk_refresh_token_revoke_reason;
ALTER TABLE auth_refresh_token ADD CONSTRAINT chk_refresh_token_revoke_reason
    CHECK (revoke_reason IS NULL OR revoke_reason IN ('MANUAL', 'CONCURRENT_LIMIT', 'EXPIRED', 'REUSE_DETECTED'));
