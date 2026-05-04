ALTER TABLE auth_refresh_token
  ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(32);

COMMENT ON COLUMN auth_refresh_token.revoke_reason IS '撤销原因: MANUAL/CONCURRENT_LIMIT/EXPIRED';
