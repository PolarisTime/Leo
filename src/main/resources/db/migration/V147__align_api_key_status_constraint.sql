-- ApiKeyStatusConverter stores displayName: ACTIVE→'有效', DISABLED→'已禁用'
-- Fix constraint to match actual stored values
ALTER TABLE auth_api_key DROP CONSTRAINT IF EXISTS chk_api_key_status;
ALTER TABLE auth_api_key ADD CONSTRAINT chk_api_key_status
    CHECK (status IN ('有效', '已禁用'));
